package com.studiostefan.krometools.midi

import org.wysko.kmidi.midi.StandardMidiFile
import org.wysko.kmidi.midi.event.NoteEvent
import org.wysko.kmidi.midi.event.ProgramEvent
import org.wysko.kmidi.midi.reader.StandardMidiFileReader
import com.studiostefan.krometools.model.Note
import com.studiostefan.krometools.model.Song
import com.studiostefan.krometools.model.TempoChange
import com.studiostefan.krometools.model.Track
import org.wysko.kmidi.midi.event.MetaEvent
import org.wysko.kmidi.midi.event.ControlChangeEvent
import com.studiostefan.krometools.model.TimeSignature
import com.studiostefan.krometools.model.TimeSignatureChange
import com.studiostefan.krometools.model.ControlChange

object MidiReader {

    private val reader = StandardMidiFileReader()

    /**
     * Lit un fichier MIDI et le convertit en modèle Song.
     */
    fun read(bytes: ByteArray, songName: String = "Untitled"): Song {

        val midiFile = reader.readByteArray(bytes)

        val allEvents = midiFile.tracks
            .flatMap { it.events }

        val tempo = allEvents
            .filterIsInstance<MetaEvent.SetTempo>()
            .minByOrNull { it.tick }

        val tempoChanges = allEvents
            .filterIsInstance<MetaEvent.SetTempo>()
            .sortedBy { it.tick }
            .groupBy { it.tick }
            .map { (tick, eventsAtTick) ->
                TempoChange(
                    tick = tick.toLong(),
                    bpm = eventsAtTick.last().beatsPerMinute
                )
            }
            .sortedBy { it.tick }

        val initialTempo = tempoChanges
            .firstOrNull { it.tick == 0L }
            ?.bpm ?: 120.0

        val timeSignatureChanges = allEvents
            .filterIsInstance<MetaEvent.TimeSignature>()
            .sortedBy { it.tick }
            .groupBy { it.tick }
            .map { (tick, eventsAtTick) ->
                val event = eventsAtTick.last()
                val exponent = event.denominator.toInt() and 0xFF

                TimeSignatureChange(
                    tick = tick.toLong(),
                    timeSignature = TimeSignature(
                        numerator = event.numerator.toInt() and 0xFF,
                        denominator = if (exponent in 0..30) {
                            1 shl exponent
                        } else {
                            4
                        }
                    )
                )
            }
            .sortedBy { it.tick }

        val initialTimeSignature = timeSignatureChanges
            .firstOrNull { it.tick == 0L }
            ?.timeSignature ?: TimeSignature()

        val tracks = midiFile.tracks.flatMapIndexed { index, midiTrack ->
            readTracks(index, midiTrack)
        }

        return Song(
            name = songName,
            ppq = midiFile.tpq.toInt(),
            tempoBpm = initialTempo,
            tempoChanges = tempoChanges,
            timeSignature = initialTimeSignature,
            timeSignatureChanges = timeSignatureChanges,
            tracks = tracks
        )
    }

    /**
     * Transforme une piste MIDI en une ou plusieurs pistes KromeTools.
     *
     * Une partie est définie par le couple :
     *     piste MIDI + canal MIDI
     *
     * Cette approche fonctionne de manière identique pour les
     * fichiers MIDI Format 0 et Format 1.
     */
    private fun readTracks(
        index: Int,
        midiTrack: StandardMidiFile.Track
    ): List<Track> {

        val noteEvents = midiTrack.events.filterIsInstance<NoteEvent>()
        val controlEvents = midiTrack.events
            .filterIsInstance<ControlChangeEvent>()

        val channels = (
                noteEvents.map { it.channel.toInt() and 0xFF } +
                controlEvents.map { it.channel.toInt() and 0xFF }
                )
            .distinct()
            .sorted()

        return channels.map { channel ->

            val program = midiTrack.events
                .filterIsInstance<ProgramEvent>()
                .firstOrNull { it.channel.toInt() == channel }
                ?.program
                ?.toInt()

            val notes = buildNotes(noteEvents, channel)

            val controlChanges = controlEvents
                .filter {
                    (it.channel.toInt() and 0xFF) == channel
                }
                .map { event ->
                    ControlChange(
                        tick = event.tick.toLong(),
                        channel = event.channel.toInt() and 0xFF,
                        controller = event.controller.toInt() and 0xFF,
                        value = event.value.toInt() and 0xFF
                    )
                }
                .sortedBy { it.tick }

            Track(
                name = midiTrack.name ?: "Track ${index + 1}",
                midiChannel = channel,
                program = program,
                notes = notes,
                controlChanges = controlChanges
            )
        }
    }

    /**
     * Associe les NoteOn et NoteOff pour construire nos Notes.
     */
    private fun buildNotes(
        events: List<NoteEvent>,
        channel: Int
    ): List<Note> {

        /*
         * Une même note peut théoriquement être rejouée avant
         * son NoteOff. On utilise donc une file par hauteur de note.
         */
        val pending = mutableMapOf<Int, ArrayDeque<NoteEvent.NoteOn>>()

        val result = mutableListOf<Note>()

        events
            .sortedBy { it.tick }
            .forEach { event ->

                if (event.channel.toInt() != channel) {
                    return@forEach
                }

                when (event) {

                    is NoteEvent.NoteOn -> {

                        // En MIDI, NoteOn avec vélocité 0 = NoteOff.
                        if (event.velocity.toInt() == 0) {
                            closeNote(
                                noteOffTick = event.tick,
                                pitch = event.note.toInt(),
                                pending = pending,
                                result = result
                            )
                        } else {
                            pending
                                .getOrPut(event.note.toInt()) { ArrayDeque() }
                                .addLast(event)
                        }
                    }

                    is NoteEvent.NoteOff -> {
                        closeNote(
                            noteOffTick = event.tick,
                            pitch = event.note.toInt(),
                            pending = pending,
                            result = result
                        )
                    }
                }
            }

        /*
         * Si un MIDI contient une note sans NoteOff,
         * on ne l'invente pas : elle est simplement ignorée.
         */
        return result.sortedBy { it.startTick }
    }

    private fun closeNote(
        noteOffTick: Int,
        pitch: Int,
        pending: MutableMap<Int, ArrayDeque<NoteEvent.NoteOn>>,
        result: MutableList<Note>
    ) {

        val queue = pending[pitch] ?: return
        val noteOn = queue.removeFirstOrNull() ?: return

        val duration = noteOffTick - noteOn.tick

        if (duration > 0) {
            result += Note(
                pitch = noteOn.note.toInt(),
                velocity = noteOn.velocity.toInt(),
                startTick = noteOn.tick.toLong(),
                durationTicks = duration.toLong()
            )
        }

        if (queue.isEmpty()) {
            pending.remove(pitch)
        }
    }



    fun printTimingInfo(bytes: ByteArray) {
        val midiFile = reader.readByteArray(bytes)

        println("=== MIDI FORMAT ===")
        println(midiFile.header.format)

        midiFile.tracks.forEachIndexed { index, track ->
            println()
            println("=== PISTE PHYSIQUE ${index + 1} ===")
            println("Nom : ${track.name ?: "(sans nom)"}")


            track.events
                .filterIsInstance<MetaEvent.TimeSignature>()
                .sortedBy { it.tick }
                .forEach { event ->
                    val denominatorExponent = event.denominator.toInt() and 0xFF
                    val musicalDenominator =
                        if (denominatorExponent in 0..30) {
                            1 shl denominatorExponent
                        } else {
                            null
                        }

                    println(
                        "Métrique : tick=${event.tick}, " +
                                "numérateur=${event.numerator.toInt() and 0xFF}, " +
                                "dénominateur brut MIDI=$denominatorExponent, " +
                                "dénominateur musical=${musicalDenominator ?: "invalide"}"
                    )
                }

            track.events
                .filterIsInstance<MetaEvent.SetTempo>()
                .sortedBy { it.tick }
                .forEach { event ->
                    println(
                        "Tempo : tick=${event.tick}, " +
                                "${event.beatsPerMinute} BPM"
                    )
                }
        }
    }
}
package com.studiostefan.krometools.midi

import org.wysko.kmidi.midi.StandardMidiFile
import org.wysko.kmidi.midi.event.NoteEvent
import org.wysko.kmidi.midi.event.ProgramEvent
import org.wysko.kmidi.midi.reader.StandardMidiFileReader
import com.studiostefan.krometools.model.Note
import com.studiostefan.krometools.model.Song
import com.studiostefan.krometools.model.Track
import org.wysko.kmidi.midi.event.MetaEvent
import com.studiostefan.krometools.model.TimeSignature

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

        val timeSignature = allEvents
            .filterIsInstance<MetaEvent.TimeSignature>()
            .minByOrNull { it.tick }

        val tracks = midiFile.tracks.flatMapIndexed { index, midiTrack ->
            readTracks(index, midiTrack)
        }

        return Song(
            name = songName,
            ppq = midiFile.tpq.toInt(),
            tempoBpm = tempo?.beatsPerMinute ?: 120.0,
            timeSignature = TimeSignature(
                numerator = timeSignature?.numerator?.toInt() ?: 4,
                denominator = timeSignature?.denominator?.toInt() ?: 4
            ),
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

        val channels = noteEvents
            .map { it.channel.toInt() }
            .distinct()
            .sorted()

        return channels.map { channel ->

            val program = midiTrack.events
                .filterIsInstance<ProgramEvent>()
                .firstOrNull { it.channel.toInt() == channel }
                ?.program
                ?.toInt()

            val notes = buildNotes(noteEvents, channel)

            Track(
                name = midiTrack.name ?: "Track ${index + 1}",
                midiChannel = channel,
                program = program,
                notes = notes
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

        val events = midiFile.tracks
            .flatMap { it.events }

        println()
        println("=== TIME SIGNATURES ===")

        events
            .filterIsInstance<MetaEvent.TimeSignature>()
            .sortedBy { it.tick }
            .forEach {
                println(
                    "tick=${it.tick} → " +
                            "${it.numerator.toInt()}/${it.denominator.toInt()}"
                )
            }

        println()
        println("=== TEMPOS ===")

        events
            .filterIsInstance<MetaEvent.SetTempo>()
            .sortedBy { it.tick }
            .forEach {
                println(
                    "tick=${it.tick} → " +
                            "${it.beatsPerMinute} BPM"
                )
            }
    }
}
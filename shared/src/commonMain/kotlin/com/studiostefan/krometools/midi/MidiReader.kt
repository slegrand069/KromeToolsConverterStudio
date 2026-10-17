
package com.studiostefan.krometools.midi

import com.studiostefan.krometools.model.ChannelPressure
import com.studiostefan.krometools.model.ControlChange
import com.studiostefan.krometools.model.Note
import com.studiostefan.krometools.model.PitchBend
import com.studiostefan.krometools.model.PolyphonicKeyPressure
import com.studiostefan.krometools.model.ProgramChange
import com.studiostefan.krometools.model.Song
import com.studiostefan.krometools.model.SysexEvent as ModelSysexEvent
import com.studiostefan.krometools.model.TempoChange
import com.studiostefan.krometools.model.TimeSignature
import com.studiostefan.krometools.model.TimeSignatureChange
import com.studiostefan.krometools.model.Track
import org.wysko.kmidi.midi.StandardMidiFile
import org.wysko.kmidi.midi.event.ChannelPressureEvent
import org.wysko.kmidi.midi.event.ControlChangeEvent
import org.wysko.kmidi.midi.event.MetaEvent
import org.wysko.kmidi.midi.event.MidiEvent
import org.wysko.kmidi.midi.event.NoteEvent
import org.wysko.kmidi.midi.event.PitchWheelChangeEvent
import org.wysko.kmidi.midi.event.PolyphonicKeyPressureEvent
import org.wysko.kmidi.midi.event.ProgramEvent
import org.wysko.kmidi.midi.event.SysexEvent
import org.wysko.kmidi.midi.reader.StandardMidiFileReader

object MidiReader {

    private val reader = StandardMidiFileReader()

    /**
     * Lit un fichier MIDI et le convertit en modèle Song.
     */
    fun read(bytes: ByteArray, songName: String = "Untitled"): Song {
        val midiFile = reader.readByteArray(bytes)
        val allEvents = midiFile.tracks.flatMap { it.events }

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

        val sysexEvents = midiFile.tracks.flatMapIndexed { index, midiTrack ->
            midiTrack.events
                .filterIsInstance<SysexEvent>()
                .map { event ->
                    ModelSysexEvent(
                        tick = event.tick.toLong(),
                        physicalTrackIndex = index,
                        data = event.data.map { byte -> byte.toInt() and 0xFF }
                    )
                }
        }.sortedBy { it.tick }

        return Song(
            name = songName,
            ppq = midiFile.tpq.toInt(),
            tempoBpm = initialTempo,
            tempoChanges = tempoChanges,
            timeSignature = initialTimeSignature,
            timeSignatureChanges = timeSignatureChanges,
            tracks = tracks,
            sysexEvents = sysexEvents
        )
    }

    /**
     * Une piste logique correspond au couple piste physique + canal MIDI.
     */
    private fun readTracks(
        index: Int,
        midiTrack: StandardMidiFile.Track
    ): List<Track> {
        val noteEvents = midiTrack.events.filterIsInstance<NoteEvent>()
        val controlEvents = midiTrack.events.filterIsInstance<ControlChangeEvent>()
        val programEvents = midiTrack.events.filterIsInstance<ProgramEvent>()
        val pitchBendEvents =
            midiTrack.events.filterIsInstance<PitchWheelChangeEvent>()
        val channelPressureEvents =
            midiTrack.events.filterIsInstance<ChannelPressureEvent>()
        val polyPressureEvents =
            midiTrack.events.filterIsInstance<PolyphonicKeyPressureEvent>()

        // Tous les événements de canal participent à la découverte des canaux.
        val channels = midiTrack.events
            .filterIsInstance<MidiEvent>()
            .map { it.channel.toInt() and 0xFF }
            .distinct()
            .sorted()

        return channels.map { channel ->
            val channelControls = controlEvents
                .filter { (it.channel.toInt() and 0xFF) == channel }
                .sortedBy { it.tick }

            val programChanges = buildProgramChanges(
                midiTrack.events,
                channel
            )

            val firstProgramChange = programChanges.firstOrNull()

            val notes = buildNotes(noteEvents, channel)

            val controlChanges = channelControls.map { event ->
                ControlChange(
                    tick = event.tick.toLong(),
                    channel = event.channel.toInt() and 0xFF,
                    controller = event.controller.toInt() and 0xFF,
                    value = event.value.toInt() and 0xFF
                )
            }

            val pitchBends = pitchBendEvents
                .filter { (it.channel.toInt() and 0xFF) == channel }
                .map { event ->
                    PitchBend(
                        tick = event.tick.toLong(),
                        channel = event.channel.toInt() and 0xFF,
                        rawValue = event.value.toInt(),
                        semitones = event.semitones
                    )
                }
                .sortedBy { it.tick }

            val channelPressures = channelPressureEvents
                .filter { (it.channel.toInt() and 0xFF) == channel }
                .map { event ->
                    ChannelPressure(
                        tick = event.tick.toLong(),
                        channel = event.channel.toInt() and 0xFF,
                        pressure = event.pressure.toInt() and 0xFF
                    )
                }
                .sortedBy { it.tick }

            val polyphonicKeyPressures = polyPressureEvents
                .filter { (it.channel.toInt() and 0xFF) == channel }
                .map { event ->
                    PolyphonicKeyPressure(
                        tick = event.tick.toLong(),
                        channel = event.channel.toInt() and 0xFF,
                        pitch = event.note.toInt() and 0xFF,
                        pressure = event.pressure.toInt() and 0xFF
                    )
                }
                .sortedBy { it.tick }

            Track(
                name = midiTrack.name ?: "Track ${index + 1}",
                midiChannel = channel,
                program = firstProgramChange?.program,
                bankMsb = firstProgramChange?.bankMsb,
                bankLsb = firstProgramChange?.bankLsb,
                notes = notes,
                controlChanges = controlChanges,
                programChanges = programChanges,
                pitchBends = pitchBends,
                channelPressures = channelPressures,
                polyphonicKeyPressures = polyphonicKeyPressures
            )
        }
    }

    /**
     * Suit l'état de sélection de banque de chaque canal et le capture
     * à chaque Program Change.
     *
     * Les événements sont triés par tick. En cas d'égalité, l'ordre
     * d'origine de la piste est conservé par l'index explicite.
     */
    private fun buildProgramChanges(
        events: List<*>,
        channel: Int
    ): List<ProgramChange> {
        var bankMsb: Int? = null
        var bankLsb: Int? = null

        val orderedEvents = events
            .mapIndexedNotNull { index, event ->
                when (event) {
                    is ControlChangeEvent ->
                        if ((event.channel.toInt() and 0xFF) == channel &&
                            ((event.controller.toInt() and 0xFF) == 0 ||
                                    (event.controller.toInt() and 0xFF) == 32)
                        ) {
                            Triple(event.tick, index, event)
                        } else {
                            null
                        }

                    is ProgramEvent ->
                        if ((event.channel.toInt() and 0xFF) == channel) {
                            Triple(event.tick, index, event)
                        } else {
                            null
                        }

                    else -> null
                }
            }
            .sortedWith(compareBy<Triple<Int, Int, Any>> { it.first }.thenBy { it.second })

        val result = mutableListOf<ProgramChange>()

        orderedEvents.forEach { (_, _, event) ->
            when (event) {
                is ControlChangeEvent -> {
                    val controller = event.controller.toInt() and 0xFF
                    val value = event.value.toInt() and 0xFF

                    when (controller) {
                        0 -> bankMsb = value
                        32 -> bankLsb = value
                    }
                }

                is ProgramEvent -> {
                    result += ProgramChange(
                        tick = event.tick.toLong(),
                        channel = event.channel.toInt() and 0xFF,
                        program = event.program.toInt() and 0xFF,
                        bankMsb = bankMsb,
                        bankLsb = bankLsb
                    )
                }
                else -> {}
            }
        }

        return result
    }

    /**
     * Associe les NoteOn et NoteOff pour construire nos Notes.
     */
    private fun buildNotes(
        events: List<NoteEvent>,
        channel: Int
    ): List<Note> {
        val pending = mutableMapOf<Int, ArrayDeque<NoteEvent.NoteOn>>()
        val result = mutableListOf<Note>()

        events
            .sortedBy { it.tick }
            .forEach { event ->
                if ((event.channel.toInt() and 0xFF) != channel) {
                    return@forEach
                }

                when (event) {
                    is NoteEvent.NoteOn -> {
                        if ((event.velocity.toInt() and 0xFF) == 0) {
                            closeNote(
                                noteOffTick = event.tick,
                                pitch = event.note.toInt() and 0xFF,
                                pending = pending,
                                result = result
                            )
                        } else {
                            pending
                                .getOrPut(event.note.toInt() and 0xFF) {
                                    ArrayDeque()
                                }
                                .addLast(event)
                        }
                    }

                    is NoteEvent.NoteOff -> {
                        closeNote(
                            noteOffTick = event.tick,
                            pitch = event.note.toInt() and 0xFF,
                            pending = pending,
                            result = result
                        )
                    }
                }
            }

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
                pitch = noteOn.note.toInt() and 0xFF,
                velocity = noteOn.velocity.toInt() and 0xFF,
                startTick = noteOn.tick.toLong(),
                durationTicks = duration.toLong()
            )
        }

        if (queue.isEmpty()) {
            pending.remove(pitch)
        }
    }

    /**
     * Diagnostic temporaire pour examiner tempo et métrique dans les fichiers.
     */
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
                    val exponent = event.denominator.toInt() and 0xFF
                    val denominator = if (exponent in 0..30) {
                        1 shl exponent
                    } else {
                        null
                    }

                    println(
                        "Métrique : tick=${event.tick}, " +
                                "numérateur=${event.numerator.toInt() and 0xFF}, " +
                                "dénominateur brut MIDI=$exponent, " +
                                "dénominateur musical=${denominator ?: "invalide"}"
                    )
                }

            track.events
                .filterIsInstance<MetaEvent.SetTempo>()
                .sortedBy { it.tick }
                .forEach { event ->
                    println("Tempo : tick=${event.tick}, ${event.beatsPerMinute} BPM")
                }
        }
    }
}
package com.studiostefan.krometools.midi

import com.studiostefan.krometools.model.Song

object MidiReport {

    fun generate(song: Song): String {

        val report = buildString {

            appendLine("=== MIDI REPORT ===")
            appendLine()
            appendLine("Song    : ${song.name}")
            appendLine("PPQ     : ${song.ppq}")
            appendLine("Tracks  : ${song.tracks.size}")
            appendLine()

            song.tracks.forEachIndexed { index, track ->

                appendLine("Track ${index + 1}")
                appendLine("  Nom        : ${track.name}")
                appendLine("  Canal      : ${track.midiChannel + 1}")

                val instrument = track.program?.let {
                    GeneralMidiInstruments.name(it)
                } ?: "Non défini"

                appendLine("  Programme  : ${track.program ?: "-"}")
                appendLine("  Instrument : $instrument")
                appendLine("  Notes      : ${track.notes.size}")

                if (track.notes.isNotEmpty()) {
                    val firstNote = track.notes.minBy { it.startTick }
                    val lastNote = track.notes.maxBy {
                        it.startTick + it.durationTicks
                    }

                    appendLine(
                        "  Première   : pitch=${firstNote.pitch}, " +
                                "tick=${firstNote.startTick}"
                    )

                    appendLine(
                        "  Dernière   : pitch=${lastNote.pitch}, " +
                                "tick=${lastNote.startTick + lastNote.durationTicks}"
                    )
                }

                appendLine()
            }
        }

        return report
    }
}
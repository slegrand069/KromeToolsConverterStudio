package com.studiostefan.krometools.midi

import com.studiostefan.krometools.model.Song

object MidiReport {

    fun generate(song: Song): String {

        val report = buildString {

            appendLine("=== MIDI REPORT ===")
            appendLine()
            appendLine("Song    : ${song.name}")
            appendLine("PPQ     : ${song.ppq}")
            appendLine("Tempo   : ${song.tempoBpm} BPM")
            appendLine("Changements de tempo : ${song.tempoChanges.size}")

            song.tempoChanges.forEach { change ->
                appendLine(
                    "  tick=${change.tick} → ${change.bpm} BPM"
                )
            }

            appendLine(
                "Métrique: ${song.timeSignature.numerator}/" +
                        "${song.timeSignature.denominator}"
            )

            appendLine("Changements de métrique : ${song.timeSignatureChanges.size}")

            song.timeSignatureChanges.forEach { change ->
                appendLine(
                    "  tick=${change.tick} → " +
                            "${change.timeSignature.numerator}/" +
                            "${change.timeSignature.denominator}"
                )
            }

            appendLine("Tracks  : ${song.tracks.size}")
            appendLine()

            val totalControlChanges = song.tracks.sumOf {
                it.controlChanges.size
            }

            appendLine("Événements de contrôleur : $totalControlChanges")
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
                appendLine("  Contrôleurs : ${track.controlChanges.size}")

                track.controlChanges
                    .groupBy { it.controller }
                    .toSortedMap()
                    .forEach { (controller, events) ->
                        appendLine(
                            "    CC $controller : ${events.size} événement(s)"
                        )
                    }

                track.controlChanges
                    .take(10)
                    .forEach { event ->
                        appendLine(
                            "    tick=${event.tick}, " +
                                    "CC ${event.controller}, " +
                                    "valeur=${event.value}"
                        )
                    }
                appendLine()
            }
        }

        return report
    }
}
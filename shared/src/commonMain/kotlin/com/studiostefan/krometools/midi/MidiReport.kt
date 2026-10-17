
package com.studiostefan.krometools.midi

import com.studiostefan.krometools.model.Song

object MidiReport {

    fun generate(song: Song): String = buildString {
        appendLine("=== MIDI REPORT ===")
        appendLine()
        appendLine("Song    : ${song.name}")
        appendLine("PPQ     : ${song.ppq}")
        appendLine("Tempo   : ${song.tempoBpm} BPM")
        appendLine("Changements de tempo : ${song.tempoChanges.size}")

        song.tempoChanges.forEach { change ->
            appendLine("  tick=${change.tick} -> ${change.bpm} BPM")
        }

        appendLine(
            "Métrique : ${song.timeSignature.numerator}/" +
                    "${song.timeSignature.denominator}"
        )
        appendLine("Changements de métrique : ${song.timeSignatureChanges.size}")

        song.timeSignatureChanges.forEach { change ->
            appendLine(
                "  tick=${change.tick} -> " +
                        "${change.timeSignature.numerator}/" +
                        "${change.timeSignature.denominator}"
            )
        }

        appendLine("Pistes logiques : ${song.tracks.size}")
        appendLine()

        appendLine("Événements de contrôleur : ${song.tracks.sumOf { it.controlChanges.size }}")
        appendLine("Program Changes : ${song.tracks.sumOf { it.programChanges.size }}")
        appendLine("Pitch Bend : ${song.tracks.sumOf { it.pitchBends.size }}")
        appendLine("Channel Pressure : ${song.tracks.sumOf { it.channelPressures.size }}")
        appendLine(
            "Polyphonic Key Pressure : " +
                    song.tracks.sumOf { it.polyphonicKeyPressures.size }
        )
        appendLine("SysEx : ${song.sysexEvents.size}")
        appendLine()

        song.tracks.forEachIndexed { index, track ->
            appendLine("Track ${index + 1}")
            appendLine("  Nom        : ${track.name}")
            appendLine("  Canal      : ${track.midiChannel + 1}")

            val instrument = track.program?.let {
                GeneralMidiInstruments.name(it)
            } ?: "Non défini"

            appendLine("  Programme initial : ${track.program ?: "-"}")
            appendLine("  Instrument        : $instrument")
            appendLine("  Banque initiale   : MSB=${track.bankMsb ?: "-"}, LSB=${track.bankLsb ?: "-"}")
            appendLine("  Notes              : ${track.notes.size}")

            if (track.notes.isNotEmpty()) {
                val firstNote = track.notes.minBy { it.startTick }
                val lastNote = track.notes.maxBy {
                    it.startTick + it.durationTicks
                }

                appendLine(
                    "  Première note : pitch=${firstNote.pitch}, " +
                            "tick=${firstNote.startTick}"
                )
                appendLine(
                    "  Fin de la dernière note : " +
                            "${lastNote.startTick + lastNote.durationTicks}"
                )
            }

            appendLine("  Contrôleurs : ${track.controlChanges.size}")

            track.controlChanges
                .groupBy { it.controller }
                .toSortedMap()
                .forEach { (controller, events) ->
                    appendLine("    CC $controller : ${events.size}")
                }

            appendLine("  Changements de programme : ${track.programChanges.size}")
            track.programChanges.take(10).forEach { event ->
                appendLine(
                    "    tick=${event.tick}, programme=${event.program}, " +
                            "banque MSB=${event.bankMsb ?: "-"}, " +
                            "LSB=${event.bankLsb ?: "-"}"
                )
            }

            appendLine("  Pitch Bend : ${track.pitchBends.size}")
            track.pitchBends.take(10).forEach { event ->
                appendLine(
                    "    tick=${event.tick}, valeur brute=${event.rawValue}, " +
                            "demi-tons=${event.semitones}"
                )
            }

            appendLine("  Channel Pressure : ${track.channelPressures.size}")
            track.channelPressures.take(5).forEach { event ->
                appendLine(
                    "    tick=${event.tick}, pression=${event.pressure}"
                )
            }

            appendLine(
                "  Polyphonic Key Pressure : " +
                        track.polyphonicKeyPressures.size
            )
            track.polyphonicKeyPressures.take(5).forEach { event ->
                appendLine(
                    "    tick=${event.tick}, pitch=${event.pitch}, " +
                            "pression=${event.pressure}"
                )
            }

            appendLine()
        }

        if (song.sysexEvents.isNotEmpty()) {
            appendLine("=== SYSEX ===")

            song.sysexEvents.take(20).forEach { event ->
                val bytes = event.data
                    .take(32)
                    .joinToString(" ") { byte ->
                        byte.toString(16).uppercase().padStart(2, '0')
                    }

                appendLine(
                    "  piste=${event.physicalTrackIndex + 1}, " +
                            "tick=${event.tick}, octets=${event.data.size}, " +
                            "données=$bytes" +
                            if (event.data.size > 32) " ..." else ""
                )
            }

            if (song.sysexEvents.size > 20) {
                appendLine("  ... autres événements SysEx non affichés")
            }
        }
    }
}

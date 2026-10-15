package com.studiostefan.krometools

import com.studiostefan.krometools.midi.MidiReader
import com.studiostefan.krometools.midi.MidiReport
import java.io.File

fun main() {

    val midiFile = File(
        "C:/Users/legra/Music/BOF/Le_clan_des_siciliens__Ennio_Morricone.MID"
    )
    println("Lecture de : ${midiFile.absolutePath}")

    val bytes = midiFile.readBytes()

    val song = MidiReader.read(
        bytes = bytes,
        songName = midiFile.nameWithoutExtension
    )

    println()
    println(MidiReport.generate(song))
}
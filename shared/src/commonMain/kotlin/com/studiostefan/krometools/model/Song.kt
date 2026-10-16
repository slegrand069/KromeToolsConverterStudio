package com.studiostefan.krometools.model

data class Song(
    val name: String,
    val ppq: Int = 480,
    val tempoBpm: Double = 120.0,
    val tempoChanges: List<TempoChange> = emptyList(),
    val timeSignature: TimeSignature = TimeSignature(),
    val tracks: List<Track> = emptyList()
)

data class TimeSignature(
    val numerator: Int = 4,
    val denominator: Int = 4
)

data class Track(
    val name: String = "",
    val midiChannel: Int = 0,
    val program: Int? = null,
    val bankMsb: Int? = null,
    val bankLsb: Int? = null,
    val notes: List<Note> = emptyList()
)

data class Note(
    val pitch: Int,
    val velocity: Int,
    val startTick: Long,
    val durationTicks: Long
)

data class TempoChange(
    val tick: Long,
    val bpm: Double
)

package com.studiostefan.krometools.model

data class Song(
    val name: String,
    val ppq: Int = 480,
    val tempoBpm: Double = 120.0,
    val tempoChanges: List<TempoChange> = emptyList(),
    val timeSignature: TimeSignature = TimeSignature(),
    val timeSignatureChanges: List<TimeSignatureChange> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val sysexEvents: List<SysexEvent> = emptyList()
)

data class TimeSignature(
    val numerator: Int = 4,
    val denominator: Int = 4
)

data class TimeSignatureChange(
    val tick: Long,
    val timeSignature: TimeSignature
)

data class Track(
    val name: String = "",
    val midiChannel: Int = 0,
    val program: Int? = null,
    val bankMsb: Int? = null,
    val bankLsb: Int? = null,
    val notes: List<Note> = emptyList(),
    val controlChanges: List<ControlChange> = emptyList(),
    val programChanges: List<ProgramChange> = emptyList(),
    val pitchBends: List<PitchBend> = emptyList(),
    val channelPressures: List<ChannelPressure> = emptyList(),
    val polyphonicKeyPressures: List<PolyphonicKeyPressure> = emptyList()
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

data class ControlChange(
    val tick: Long,
    val channel: Int,
    val controller: Int,
    val value: Int
)

/**
 * Changement de programme avec l'état de banque connu
 * au moment de cet événement.
 *
 * Les numéros de programme et de banque sont conservés
 * dans leur représentation MIDI, à partir de zéro.
 */
data class ProgramChange(
    val tick: Long,
    val channel: Int,
    val program: Int,
    val bankMsb: Int?,
    val bankLsb: Int?
)

/**
 * Valeur brute fournie par kmidi, accompagnée de sa
 * représentation en demi-tons telle que calculée par kmidi.
 */
data class PitchBend(
    val tick: Long,
    val channel: Int,
    val rawValue: Int,
    val semitones: Double
)

data class ChannelPressure(
    val tick: Long,
    val channel: Int,
    val pressure: Int
)

data class PolyphonicKeyPressure(
    val tick: Long,
    val channel: Int,
    val pitch: Int,
    val pressure: Int
)

/**
 * SysEx est un événement système sans canal MIDI.
 * physicalTrackIndex est indexé à partir de zéro.
 * Les octets sont conservés tels quels.
 */
data class SysexEvent(
    val tick: Long,
    val physicalTrackIndex: Int,
    val data: List<Int>
)

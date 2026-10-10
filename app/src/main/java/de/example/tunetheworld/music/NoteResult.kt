package de.example.tunetheworld.music

data class NoteResult(
    val pitchClass: Int, // 0..11, 0 = C, independent of language
    val octave: Int, // scientific pitch notation, e.g. 5
    val percent: Float, // -50f..+50f, 1 % = 1 cent
    val frequencyHz: Float
)

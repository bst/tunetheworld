package de.example.tunetheworld.music

import kotlin.math.log2
import kotlin.math.roundToInt

object NoteMapper {
    fun map(frequencyHz: Float, referenceHz: Int, transposition: Transposition): NoteResult {
        val midiExact = 69.0 + 12.0 * log2(frequencyHz.toDouble() / referenceHz)
        val nearest = midiExact.roundToInt() // nearest sounding note (MIDI number)
        val percent = ((midiExact - nearest) * 100.0).toFloat()
        val display = nearest + transposition.semitones // transpose only after rounding
        return NoteResult(
            pitchClass = Math.floorMod(display, 12),
            octave = Math.floorDiv(display, 12) - 1,
            percent = percent,
            frequencyHz = frequencyHz
        )
    }
}

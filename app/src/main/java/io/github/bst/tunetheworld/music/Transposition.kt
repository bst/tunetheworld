package io.github.bst.tunetheworld.music

/**
 * Transposition in semitones: how far the written (displayed) note lies above the sounding note.
 * 0 is concert pitch, +2 a B-flat instrument (trumpet, clarinet), +14 a tenor saxophone,
 * −12 an instrument written an octave below its sound (piccolo).
 */
object Transposition {
    const val CONCERT = 0

    // −24: glockenspiel (sounds two octaves above written). +26: bass saxophone, contrabass clarinet
    const val MIN = -24
    const val MAX = 26

    /** Middle C as written; [writtenCSounds] tells which note actually sounds when it is played. */
    private const val WRITTEN_C4 = 60

    fun clamp(semitones: Int): Int = semitones.coerceIn(MIN, MAX)

    /** The sounding MIDI note of a written C4, e.g. 46 (B♭2) for a tenor saxophone (+14). */
    fun writtenCSounds(semitones: Int): Int = WRITTEN_C4 - semitones

    /** Pitch class of the instrument's key (the sounding note of a written C), e.g. 10 (B♭) for +2 and +14. */
    fun keyPitchClass(semitones: Int): Int = Math.floorMod(-semitones, 12)
}

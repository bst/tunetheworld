package io.github.bst.tunetheworld.music

import org.junit.Assert.assertEquals
import org.junit.Test

class TranspositionTest {
    @Test
    fun keyOfCommonInstruments() {
        assertEquals(0, Transposition.keyPitchClass(0)) // concert C
        assertEquals(10, Transposition.keyPitchClass(2)) // trumpet in B♭
        assertEquals(5, Transposition.keyPitchClass(7)) // horn in F
        assertEquals(3, Transposition.keyPitchClass(9)) // alto sax in E♭
        assertEquals(10, Transposition.keyPitchClass(14)) // tenor sax in B♭
        assertEquals(3, Transposition.keyPitchClass(-3)) // E♭ clarinet
        assertEquals(0, Transposition.keyPitchClass(-12)) // piccolo
    }

    @Test
    fun writtenCSounds() {
        assertEquals(60, Transposition.writtenCSounds(0)) // C4
        assertEquals(58, Transposition.writtenCSounds(2)) // B♭3
        assertEquals(46, Transposition.writtenCSounds(14)) // B♭2
        assertEquals(72, Transposition.writtenCSounds(-12)) // C5
    }

    @Test
    fun clampsToRange() {
        assertEquals(Transposition.MAX, Transposition.clamp(99))
        assertEquals(Transposition.MIN, Transposition.clamp(-99))
        assertEquals(5, Transposition.clamp(5))
    }
}

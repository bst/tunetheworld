package de.example.saxtuner.music

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMapperTest {
    private data class Case(
        val hz: Float,
        val ref: Int,
        val mode: Transposition,
        val pitchClass: Int,
        val octave: Int,
        val percent: Float
    )

    private val cases = listOf(
        Case(440.00f, 440, Transposition.CONCERT, 9, 4, 0f),
        Case(440.00f, 440, Transposition.TENOR_BB, 11, 5, 0f),
        Case(233.08f, 440, Transposition.CONCERT, 10, 3, 0f),
        Case(233.08f, 440, Transposition.TENOR_BB, 0, 5, 0f),
        Case(103.83f, 440, Transposition.TENOR_BB, 10, 3, 0f),
        Case(659.26f, 440, Transposition.TENOR_BB, 6, 6, 0f),
        Case(446.41f, 440, Transposition.CONCERT, 9, 4, 25f),
        Case(435.00f, 440, Transposition.CONCERT, 9, 4, -19.8f),
        Case(442.00f, 442, Transposition.CONCERT, 9, 4, 0f),
        Case(440.00f, 442, Transposition.CONCERT, 9, 4, -7.9f),
        Case(138.59f, 440, Transposition.ALTO_EB, 10, 3, 0f),
        Case(440.00f, 440, Transposition.ALTO_EB, 6, 5, 0f),
        Case(880.00f, 440, Transposition.ALTO_EB, 6, 6, 0f)
    )

    @Test
    fun mapsKnownFrequencies() {
        for (c in cases) {
            val r = NoteMapper.map(c.hz, c.ref, c.mode)
            val label = "$c"
            assertEquals("pitchClass $label", c.pitchClass, r.pitchClass)
            assertEquals("octave $label", c.octave, r.octave)
            assertEquals("percent $label", c.percent, r.percent, 0.1f)
            assertEquals(c.hz, r.frequencyHz, 0f)
        }
    }

    @Test
    fun percentStaysWithinHalfSemitone() {
        var hz = 90f
        while (hz < 1100f) {
            val p = NoteMapper.map(hz, 440, Transposition.CONCERT).percent
            assert(p in -50f..50f) { "percent $p at $hz Hz" }
            hz *= 1.003f
        }
    }
}

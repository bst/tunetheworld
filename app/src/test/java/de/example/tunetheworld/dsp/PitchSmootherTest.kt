package de.example.tunetheworld.dsp

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchSmootherTest {
    private fun last(smoother: PitchSmoother, vararg values: Float): Float {
        var r = 0f
        for (v in values) r = smoother.add(v)
        return r
    }

    @Test
    fun medianOfFiveValues() {
        assertEquals(440f, last(PitchSmoother(), 439f, 441f, 440f, 442f, 438f), 0.001f)
    }

    @Test
    fun singleOutlierIsIgnored() {
        assertEquals(440f, last(PitchSmoother(), 440f, 440f, 440f, 440f, 880f), 0.001f)
    }

    @Test
    fun threeAgreeingOutliersAreARealChange() {
        assertEquals(330f, last(PitchSmoother(), 440f, 440f, 440f, 440f, 330f, 330f, 330f), 0.001f)
    }

    @Test
    fun disagreeingOutliersAreNotAChange() {
        assertEquals(440f, last(PitchSmoother(), 440f, 440f, 440f, 440f, 330f, 660f, 330f), 0.001f)
    }

    @Test
    fun normalValueResetsJumpCount() {
        // Two outliers, a normal value, two more outliers: never three in a row
        assertEquals(440f, last(PitchSmoother(), 440f, 440f, 440f, 440f, 330f, 330f, 440f, 330f, 330f), 0.001f)
    }

    @Test
    fun resetClearsEverything() {
        val s = PitchSmoother()
        last(s, 440f, 440f, 440f, 440f)
        s.reset()
        assertEquals(300f, s.add(300f), 0.001f)
    }
}

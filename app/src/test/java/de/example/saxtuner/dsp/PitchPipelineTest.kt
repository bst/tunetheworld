package de.example.saxtuner.dsp

import de.example.saxtuner.engine.TunerConfig
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PitchPipelineTest {
    /** Feeds a 220 Hz sine of the given amplitude for one second; returns the last result. */
    private fun feed(pipeline: PitchPipeline, amplitude: Float): Float? {
        val block = FloatArray(TunerConfig.HOP_SIZE)
        var n = 0
        var last: Float? = null
        repeat(TunerConfig.SAMPLE_RATE / TunerConfig.HOP_SIZE) {
            for (i in block.indices) {
                block[i] = amplitude * sin(2.0 * PI * 220.0 * n++ / TunerConfig.SAMPLE_RATE).toFloat()
            }
            last = pipeline.process(block, block.size)
        }
        return last
    }

    @Test
    fun quietToneIsIgnoredUntilGateIsLowered() {
        val pipeline = PitchPipeline()
        // Amplitude 0.01 is about -43 dBFS RMS, below the default -40 dB gate
        assertNull(feed(pipeline, 0.01f))
        pipeline.gateDb = TunerConfig.MIN_GATE_DB
        pipeline.reset()
        assertNotNull(feed(pipeline, 0.01f))
    }

    @Test
    fun levelReportsBlockRmsInDb() {
        val pipeline = PitchPipeline()
        feed(pipeline, 0.5f)
        // RMS of a 0.5 sine is 0.354, about -9 dBFS
        assertEquals(-9f, pipeline.levelDb, 0.3f)
    }
}

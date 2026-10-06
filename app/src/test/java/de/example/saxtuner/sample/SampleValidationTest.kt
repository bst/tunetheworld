package de.example.saxtuner.sample

import de.example.saxtuner.dsp.PitchPipeline
import de.example.saxtuner.engine.TunerConfig
import de.example.saxtuner.music.NoteMapper
import de.example.saxtuner.music.Transposition
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.log2

/** Runs the full detection chain on real alto saxophone recordings (see PLAN.md, phase 3b). */
class SampleValidationTest {
    private class Sample(val file: String, val midi: Int, val vibrato: Boolean)

    // File names state the sounding pitch
    private val samples = listOf(
        Sample("AltoSax.NoVib.ff.Db3.stereo.aif", 49, false),
        Sample("AltoSax.NoVib.ff.D3.stereo.aif", 50, false),
        Sample("AltoSax.NoVib.ff.C5.stereo.aif", 72, false),
        Sample("AltoSax.vib.ff.F5.stereo.aif", 77, true),
    )

    private val dir: File by lazy {
        val path = System.getProperty("samples.dir") ?: error("system property samples.dir is not set")
        File(path).also { check(it.isDirectory) { "samples directory missing: $path" } }
    }

    private class Block(val index: Int, val hz: Float?)

    private fun run(file: String): List<Block> {
        val audio = AiffReader.read(File(dir, file))
        val pipeline = PitchPipeline()
        val hop = FloatArray(TunerConfig.HOP_SIZE)
        val blocks = ArrayList<Block>()
        var index = 0
        var pos = 0
        while (pos + hop.size <= audio.samples.size) {
            System.arraycopy(audio.samples, pos, hop, 0, hop.size)
            blocks += Block(index++, pipeline.process(hop, hop.size))
            pos += hop.size
        }
        return blocks
    }

    private fun median(v: List<Float>): Float = v.sorted().let { it[it.size / 2] }

    private fun centsFromExpected(hz: Float, midi: Int): Float {
        val expected = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        return (1200.0 * log2(hz / expected)).toFloat()
    }

    @Test
    fun alignsWithFileNamesInConcertAndAltoMode() {
        val report = StringBuilder("\n%-34s %7s %8s %9s %8s %7s %7s\n".format(
            "file", "scored", "med Hz", "med cents", "mad", "range", "onset"))
        val failures = ArrayList<String>()

        for (s in samples) {
            val blocks = run(s.file)
            val firstFull = TunerConfig.WINDOW_SIZE / TunerConfig.HOP_SIZE - 1
            val eligible = blocks.filter { it.index >= firstFull }
            val scored = eligible.filter { it.hz != null }
            fun fail(msg: String) { failures += "${s.file}: $msg" }

            val ratio = scored.size.toFloat() / eligible.size
            if (ratio < 0.70f) fail("only ${"%.0f".format(ratio * 100)} % of blocks scored")
            if (scored.isEmpty()) continue

            val cents = scored.map { centsFromExpected(it.hz!!, s.midi) }
            val worst = cents.maxOf { abs(it) }
            if (worst > 600f) fail("octave/fifth error, worst deviation $worst cents")
            val medCents = median(cents)
            if (abs(medCents) >= 30f) fail("median deviation $medCents cents")
            val mad = cents.map { abs(it - medCents) }.average().toFloat()
            val range = cents.max() - cents.min()
            if (!s.vibrato && mad > 5f) fail("unsteady: mean deviation $mad cents")
            if (s.vibrato && range <= 10f) fail("no vibrato visible: range $range cents")

            val onset = scored.first().index - firstFull
            val onsetMs = onset * 1000f * TunerConfig.HOP_SIZE / TunerConfig.SAMPLE_RATE
            if (onsetMs > 300f) fail("onset after $onsetMs ms")

            for (mode in listOf(Transposition.CONCERT, Transposition.ALTO_EB)) {
                val expectedDisplay = s.midi + mode.semitones
                val wrong = scored.count {
                    val n = NoteMapper.map(it.hz!!, 440, mode)
                    n.pitchClass != Math.floorMod(expectedDisplay, 12) ||
                        n.octave != Math.floorDiv(expectedDisplay, 12) - 1
                }
                val share = 1f - wrong.toFloat() / scored.size
                if (share < 0.95f) fail("$mode: only ${"%.0f".format(share * 100)} % blocks show the expected note")
            }

            report.append("%-34s %6.0f%% %8.2f %9.1f %8.1f %7.1f %5.0fms\n".format(
                s.file, ratio * 100, median(scored.map { it.hz!! }), medCents, mad, range, onsetMs))
        }
        println(report)
        assertTrue(failures.joinToString("\n", prefix = "\n"), failures.isEmpty())
    }
}

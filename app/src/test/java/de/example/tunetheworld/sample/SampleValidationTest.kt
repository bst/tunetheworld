package de.example.tunetheworld.sample

import de.example.tunetheworld.dsp.PitchPipeline
import de.example.tunetheworld.engine.TunerConfig
import de.example.tunetheworld.music.NoteMapper
import de.example.tunetheworld.music.Transposition
import java.io.File
import kotlin.math.abs
import kotlin.math.log2
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the full detection chain on real alto saxophone recordings in concert and alto mode. */
class SampleValidationTest {
    // octaveJump: the player's tone really jumps one octave up for a moment (checked by ear)
    private class Sample(val file: String, val midi: Int, val vibrato: Boolean, val octaveJump: Boolean = false)

    // File names state the sounding pitch
    private val samples = listOf(
        Sample("AltoSax.NoVib.ff.Db3.stereo.aif", 49, false),
        Sample("AltoSax.NoVib.ff.D3.stereo.aif", 50, false, octaveJump = true),
        Sample("AltoSax.NoVib.ff.C5.stereo.aif", 72, false),
        Sample("AltoSax.vib.ff.F5.stereo.aif", 77, true)
    )

    private companion object {
        const val MAX_OCTAVE_BLOCKS = 8 // about 190 ms
    }

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
        val report =
            StringBuilder(
                "\n%-34s %7s %8s %9s %8s %7s %7s\n".format(
                    "file",
                    "scored",
                    "med Hz",
                    "med cents",
                    "mad",
                    "range",
                    "onset"
                )
            )
        val failures = ArrayList<String>()

        for (s in samples) {
            val blocks = run(s.file)
            val firstFull = TunerConfig.WINDOW_SIZE / TunerConfig.HOP_SIZE - 1
            val eligible = blocks.filter { it.index >= firstFull }
            val allScored = eligible.filter { it.hz != null }
            fun fail(msg: String) {
                failures += "${s.file}: $msg"
            }

            val ratio = allScored.size.toFloat() / eligible.size
            if (ratio < 0.70f) fail("only ${"%.0f".format(ratio * 100)} % of blocks scored")
            if (allScored.isEmpty()) continue

            // Blocks near one octave above the expected note: tolerated only where the recording really jumps
            fun isOctaveUp(b: Block) = abs(centsFromExpected(b.hz!!, s.midi) - 1200f) < 100f
            val octaveBlocks = allScored.filter { isOctaveUp(it) }
            if (s.octaveJump) {
                if (octaveBlocks.size >
                    MAX_OCTAVE_BLOCKS
                ) {
                    fail("${octaveBlocks.size} octave-jump blocks, at most $MAX_OCTAVE_BLOCKS allowed")
                }
                val contiguous = octaveBlocks.isEmpty() ||
                    octaveBlocks.last().index - octaveBlocks.first().index == octaveBlocks.size - 1
                if (!contiguous) fail("octave-jump blocks are not one single run")
            }
            // Everything else is judged on the normal part of the note
            val scored = if (s.octaveJump) allScored.filter { !isOctaveUp(it) } else allScored

            val cents = scored.map { centsFromExpected(it.hz!!, s.midi) }
            val worst = cents.maxOf { abs(it) }
            if (worst > 600f) fail("octave/fifth error, worst deviation $worst cents")
            val medCents = median(cents)
            if (abs(medCents) >= 30f) fail("median deviation $medCents cents")
            val mad = cents.map { abs(it - medCents) }.average().toFloat()
            val range = cents.max() - cents.min()
            if (!s.vibrato && mad > 5f) fail("unsteady: mean deviation $mad cents")
            if (s.vibrato && range <= 10f) fail("no vibrato visible: range $range cents")

            val onset = allScored.first().index - firstFull
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

            report.append(
                "%-34s %6.0f%% %8.2f %9.1f %8.1f %7.1f %5.0fms\n".format(
                    s.file,
                    ratio * 100,
                    median(
                        scored.map {
                            it.hz!!
                        }
                    ),
                    medCents,
                    mad,
                    range,
                    onsetMs
                )
            )
            if (octaveBlocks.isNotEmpty()) {
                report.append(
                    "    ${octaveBlocks.size} octave-up blocks " +
                        "(indices ${octaveBlocks.first().index}..${octaveBlocks.last().index})" +
                        if (s.octaveJump) ", expected\n" else ", UNEXPECTED\n"
                )
            }
        }
        println(report)
        assertTrue(failures.joinToString("\n", prefix = "\n"), failures.isEmpty())
    }
}

package de.example.tunetheworld.sample

import de.example.tunetheworld.dsp.PitchPipeline
import de.example.tunetheworld.engine.TunerConfig
import de.example.tunetheworld.music.NoteMapper
import java.io.File
import kotlin.math.abs
import kotlin.math.log2
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the full detection chain on real instrument recordings, see [Samples]. Each recording is checked in concert
 * pitch and in its instrument's own transposition.
 *
 * A note is split into three phases:
 * - attack: the first [ATTACK_MS] after the tuner first shows a note. The sound itself may still be settling there:
 *   overblown notes can speak in the octave below for a moment (flute), low brass slides into the pitch (tuba).
 *   Only the onset time is checked.
 * - release: blocks more than [RELEASE_DB] ([PLUCKED_RELEASE_DB] for plucked notes, which fade from the start) below
 *   the note's peak level. Low strings lose their fundamental as they die away, so readings an octave up are
 *   tolerated here and listed in the report. Anything else is an error.
 * - settled: everything in between, judged strictly.
 *
 * Players are not always in tune: some recordings sit 50–80 cents off the named note. Each recording is therefore
 * judged against its reference pitch, the named note shifted by the median of the settled readings within ±100 cents
 * of it. The detector's own accuracy is covered by the sine tests in YinPitchDetectorTest.
 *
 * Struck and plucked notes that are too short for a settled part are only checked for wrong readings. Recordings
 * marked known-failure in samples.tsv are run and reported, but do not fail the test; see docs/research/RESEARCH_LOG.md.
 */
class SampleValidationTest {
    private companion object {
        const val MAX_OCTAVE_BLOCKS = 8 // about 190 ms
        const val ATTACK_MS = 300f
        const val RELEASE_DB = 12f
        const val PLUCKED_RELEASE_DB = 20f
        const val MIN_VIBRATO_CENTS = 6f // range of a vibrato note; the smoothing must not flatten it
        const val MAX_ONSET_MS = 300f
        const val MAX_REFERENCE_CENTS = 100f // how far the player may be from the named note
        const val MIN_ELIGIBLE_BLOCKS = 10 // shorter notes (about 230 ms of full windows) are only reported
        const val MAX_JITTER_CENTS = 3f
        const val MIN_NOTE_SHARE = 0.95f
        const val MIN_SCORED = 0.70f
        const val MIN_SCORED_PLUCKED = 0.40f // the attack of a struck note often gives no reading
    }

    private val dir: File by lazy {
        val path = System.getProperty("samples.dir") ?: error("system property samples.dir is not set")
        File(path).also { check(it.isDirectory) { "samples directory missing: $path" } }
    }

    private class Block(val index: Int, val hz: Float?, val levelDb: Float, val loud: Boolean)

    private fun run(file: String): List<Block> {
        val audio = AiffReader.read(File(dir, file))
        val pipeline = PitchPipeline()
        val hop = FloatArray(TunerConfig.HOP_SIZE)
        val blocks = ArrayList<Block>()
        var index = 0
        var pos = 0
        while (pos + hop.size <= audio.samples.size) {
            System.arraycopy(audio.samples, pos, hop, 0, hop.size)
            val hz = pipeline.process(hop, hop.size)
            blocks += Block(index++, hz, pipeline.levelDb, pipeline.levelDb >= pipeline.gateDb)
            pos += hop.size
        }
        return blocks
    }

    private fun median(v: List<Float>): Float = v.sorted().let { it[it.size / 2] }

    private fun ms(blocks: Int) = blocks * 1000f * TunerConfig.HOP_SIZE / TunerConfig.SAMPLE_RATE

    private fun centsFromExpected(hz: Float, midi: Int): Float {
        val expected = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
        return (1200.0 * log2(hz / expected)).toFloat()
    }

    @Test
    fun detectsTheNoteOfEveryRecording() {
        val report = StringBuilder(
            "\n%-38s %6s %7s %8s %9s %6s %6s %6s %7s\n".format(
                "file",
                "midi",
                "scored",
                "med Hz",
                "med cents",
                "mad",
                "jitter",
                "range",
                "onset"
            )
        )
        val failures = ArrayList<String>()
        val known = ArrayList<String>()

        for (s in Samples.ALL) {
            val blocks = run(s.fileName)
            val firstFull = TunerConfig.WINDOW_SIZE / TunerConfig.HOP_SIZE - 1
            // Only blocks above the level gate count: below it the tuner is silent by design
            val eligible = blocks.filter { it.index >= firstFull && it.loud }
            val allScored = eligible.filter { it.hz != null }
            val plucked = s.kind == Samples.Kind.PLUCKED
            val problems = ArrayList<String>()
            fun fail(msg: String) {
                problems += msg
            }

            fun finish() {
                val list = if (s.knownFailure) known else failures
                problems.forEach { list += "${s.fileName}: $it" }
            }
            if (eligible.size < MIN_ELIGIBLE_BLOCKS) {
                report.append(
                    "%-38s %6d  too short: %d full windows above the gate\n".format(s.fileName, s.midi, eligible.size)
                )
                continue
            }

            val ratio = allScored.size.toFloat() / eligible.size
            if (ratio < (if (plucked) MIN_SCORED_PLUCKED else MIN_SCORED)) {
                fail("only ${"%.0f".format(ratio * 100)} % of blocks scored")
            }
            if (allScored.isEmpty()) {
                report.append("%-38s %6d %6.0f%%\n".format(s.fileName, s.midi, 0f))
                finish()
                continue
            }

            val onsetMs = ms(allScored.first().index - eligible.first().index)
            if (onsetMs > MAX_ONSET_MS) fail("onset after ${"%.0f".format(onsetMs)} ms")

            val attackEnd = allScored.first().index + (ATTACK_MS / ms(1)).toInt()
            val peakDb = eligible.maxOf { it.levelDb }
            val releaseDb = if (s.kind == Samples.Kind.PLUCKED) PLUCKED_RELEASE_DB else RELEASE_DB
            fun inRelease(b: Block) = b.levelDb < peakDb - releaseDb
            fun named(b: Block) = centsFromExpected(b.hz!!, s.midi)
            val near = allScored.filter { it.index >= attackEnd && !inRelease(it) }.map { named(it) }
                .filter { abs(it) <= MAX_REFERENCE_CENTS }
            val reference = if (near.size >= 5) median(near) else 0f
            fun cents(b: Block) = named(b) - reference
            fun isOctaveUp(b: Block) = abs(cents(b) - 1200f) < 100f

            val release = allScored.filter { it.index >= attackEnd && inRelease(it) }
            val releaseOctave = release.count { isOctaveUp(it) }
            val releaseWrong = release.count { !isOctaveUp(it) && abs(cents(it)) > 600f }
            if (releaseWrong > 0) fail("$releaseWrong blocks in the release are far off and not an octave up")

            val settledAll = allScored.filter { it.index >= attackEnd && !inRelease(it) }
            if (settledAll.isEmpty()) {
                // A short struck or plucked note may die away before it settles; then only wrong readings count
                if (!plucked) fail("no settled part after the attack")
                val wrong = allScored.count { it.index >= attackEnd && abs(cents(it)) > 600f && !isOctaveUp(it) }
                if (plucked && wrong > 0) fail("$wrong blocks far off")
                report.append("%-38s %6d %6.0f%%  no settled part\n".format(s.fileName, s.midi, ratio * 100))
                finish()
                continue
            }
            // Octave-up blocks in the settled part: tolerated only where the recording really jumps
            val octaveBlocks = settledAll.filter { isOctaveUp(it) }
            if (s.octaveJump) {
                if (octaveBlocks.size > MAX_OCTAVE_BLOCKS) {
                    fail("${octaveBlocks.size} octave-jump blocks, at most $MAX_OCTAVE_BLOCKS allowed")
                }
                val contiguous = octaveBlocks.isEmpty() ||
                    octaveBlocks.last().index - octaveBlocks.first().index == octaveBlocks.size - 1
                if (!contiguous) fail("octave-jump blocks are not one single run")
            }
            val settled = if (s.octaveJump) settledAll.filter { !isOctaveUp(it) } else settledAll

            val cents = settled.map { cents(it) }
            val worst = cents.maxOf { abs(it) }
            if (worst > 600f) fail("octave/fifth error, worst deviation ${"%.0f".format(worst)} cents")
            val medCents = median(cents)
            val mad = cents.map { abs(it - medCents) }.average().toFloat()
            val range = cents.max() - cents.min()
            // Median step from block to block: a player drifting or sliding is fine, a detector jumping around is not
            val jitter = if (cents.size < 2) 0f else median(cents.zipWithNext { a, b -> abs(b - a) })
            if (s.kind != Samples.Kind.VIBRATO && jitter > MAX_JITTER_CENTS) {
                fail("jittery: ${"%.1f".format(jitter)} cents per block")
            }
            if (s.kind == Samples.Kind.VIBRATO && range < MIN_VIBRATO_CENTS) {
                fail("no vibrato visible: range ${"%.1f".format(range)} cents")
            }

            for (transposition in setOf(0, s.transposition)) {
                val expectedDisplay = s.midi + transposition
                val wrong = settled.count {
                    // The note the player meant: the reading with the player's offset from the reference removed
                    val hz = it.hz!! * Math.pow(2.0, -reference / 1200.0).toFloat()
                    val n = NoteMapper.map(hz, 440, transposition)
                    n.pitchClass != Math.floorMod(expectedDisplay, 12) ||
                        n.octave != Math.floorDiv(expectedDisplay, 12) - 1
                }
                val share = 1f - wrong.toFloat() / settled.size
                if (share < MIN_NOTE_SHARE) {
                    fail("transposition $transposition: only ${"%.0f".format(share * 100)} % show the expected note")
                }
            }

            report.append(
                "%-38s %6d %6.0f%% %8.2f %9.1f %6.1f %6.1f %6.1f %5.0fms\n".format(
                    s.fileName,
                    s.midi,
                    ratio * 100,
                    median(settled.map { it.hz!! }),
                    reference + medCents,
                    mad,
                    jitter,
                    range,
                    onsetMs
                )
            )
            val attackOff = allScored.count { it.index < attackEnd && abs(cents(it)) > 50f }
            if (attackOff > 0) report.append("    attack: $attackOff blocks more than 50 cents off\n")
            if (octaveBlocks.isNotEmpty()) {
                report.append(
                    "    ${octaveBlocks.size} octave-up blocks " +
                        "(indices ${octaveBlocks.first().index}..${octaveBlocks.last().index}), expected\n"
                )
            }
            if (releaseOctave > 0) report.append("    release: $releaseOctave octave-up blocks, tolerated\n")
            if (s.knownFailure) {
                report.append(
                    "    known failure: ${if (problems.isEmpty()) "passes now" else problems.joinToString("; ")}\n"
                )
            }
            finish()
        }
        if (known.isNotEmpty()) report.append("\nKnown failures (not counted):\n" + known.joinToString("\n") + "\n")
        println(report)
        assertTrue(failures.joinToString("\n", prefix = "\n"), failures.isEmpty())
    }
}

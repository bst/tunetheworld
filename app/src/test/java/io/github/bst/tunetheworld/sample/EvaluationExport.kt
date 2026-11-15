package io.github.bst.tunetheworld.sample

import io.github.bst.tunetheworld.dsp.PitchPipeline
import io.github.bst.tunetheworld.dsp.YinPitchDetector
import io.github.bst.tunetheworld.dsp.dbToAmplitude
import io.github.bst.tunetheworld.dsp.rms
import io.github.bst.tunetheworld.engine.TunerConfig
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes the reading of every analysis block of every test recording to a CSV file, for comparing detector variants
 * block by block with docs/research/evaluate.py. Skipped unless EVAL_OUT is set. The experiments in
 * docs/research/RESEARCH_LOG.md used switchable variants (EVAL_VARIANT), which are in commit 2b4d5ad.
 */
class EvaluationExport {
    private fun detector() = YinPitchDetector(
        TunerConfig.SAMPLE_RATE,
        TunerConfig.WINDOW_SIZE,
        TunerConfig.MIN_FREQ_HZ,
        TunerConfig.MAX_FREQ_HZ,
        TunerConfig.YIN_THRESHOLD
    )

    @Test
    fun export() {
        val out = System.getenv("EVAL_OUT")
        assumeTrue("EVAL_OUT is not set", out != null)
        val dir = File(System.getProperty("samples.dir") ?: error("system property samples.dir is not set"))
        val hop = TunerConfig.HOP_SIZE
        val size = TunerConfig.WINDOW_SIZE
        val csv = StringBuilder("file,block,level_db,loud,raw_hz,raw_conf,hz\n")
        var nanos = 0L
        var calls = 0
        for (s in Samples.ALL) {
            val samples = AiffReader.read(File(dir, s.fileName)).samples
            val pipeline = PitchPipeline(detector = detector())
            val raw = detector()
            val gate = dbToAmplitude(pipeline.gateDb)
            val block = FloatArray(hop)
            var index = 0
            var pos = 0
            while (pos + hop <= samples.size) {
                System.arraycopy(samples, pos, block, 0, hop)
                val hz = pipeline.process(block, hop)
                // The same window the pipeline analyses, without its confidence filter and smoothing
                val start = pos + hop - size
                var rawHz = ""
                var rawConf = ""
                val loud = start >= 0 && rms(samples, start, hop) >= gate && rms(samples, pos, hop) >= gate
                if (!loud) raw.reset()
                if (loud) {
                    val window = samples.copyOfRange(start, start + size)
                    val t0 = System.nanoTime()
                    val r = raw.detect(window)
                    nanos += System.nanoTime() - t0
                    calls++
                    if (r != null) {
                        rawHz = r.frequencyHz.toString()
                        rawConf = r.confidence.toString()
                    }
                }
                csv.append(
                    "${s.fileName},$index,${pipeline.levelDb},${if (loud) 1 else 0},$rawHz,$rawConf,${hz ?: ""}\n"
                )
                index++
                pos += hop
            }
        }
        File(out).apply { parentFile?.mkdirs() }.writeText(csv.toString())
        println("EVAL: $calls analyses, ${nanos / 1000 / maxOf(calls, 1)} µs each")
    }
}

package de.example.tunetheworld.sample

import de.example.tunetheworld.dsp.PitchPipeline
import de.example.tunetheworld.dsp.YinOptions
import de.example.tunetheworld.dsp.YinPitchDetector
import de.example.tunetheworld.dsp.dbToAmplitude
import de.example.tunetheworld.dsp.rms
import de.example.tunetheworld.engine.TunerConfig
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes the reading of every analysis block of every test recording to a CSV file, for comparing detector variants
 * block by block with docs/research/evaluate.py. Skipped unless EVAL_OUT is set; EVAL_VARIANT picks the variant.
 */
class EvaluationExport {
    private fun detector(variant: String): YinPitchDetector {
        val options = when (variant) {
            "baseline" -> YinOptions()

            else -> {
                // key=value pairs separated by commas, e.g. "octaveMinDip=0.03"
                val map = variant.split(',').associate { it.substringBefore('=') to it.substringAfter('=') }
                var o = YinOptions()
                map["octaveMinDip"]?.let { o = o.copy(octaveMinDip = it.toFloat()) }
                map["interpolateDips"]?.let { o = o.copy(interpolateDips = it.toInt()) }
                map["amplitudeModel"]?.let { o = o.copy(amplitudeModel = it.toInt()) }
                map["bestLocal"]?.let { o = o.copy(bestLocal = it == "1") }
                map["bestLocalMargin"]?.let { o = o.copy(bestLocalMargin = it.toFloat()) }
                map["bestLocalMinDepth"]?.let { o = o.copy(bestLocalMinDepth = it.toFloat()) }
                map["bestLocalRatio"]?.let { o = o.copy(bestLocalRatio = it.toFloat()) }
                map["lowpass"]?.let { o = o.copy(lowpass = it.toInt()) }
                map["dcModel"]?.let { o = o.copy(dcModel = it == "1") }
                map["maxMultiple"]?.let { o = o.copy(maxMultiple = it.toInt()) }
                map["octaveMinLag"]?.let { o = o.copy(octaveMinLag = it.toInt()) }
                map["interpolatedOctave"]?.let { o = o.copy(interpolatedOctave = it == "1") }
                map["tripleMinLag"]?.let { o = o.copy(tripleMinLag = it.toInt()) }
                o
            }
        }
        val threshold = Regex("threshold=([0-9.]+)").find(variant)?.groupValues?.get(1)?.toFloat()
            ?: TunerConfig.YIN_THRESHOLD
        return YinPitchDetector(
            TunerConfig.SAMPLE_RATE,
            TunerConfig.WINDOW_SIZE,
            TunerConfig.MIN_FREQ_HZ,
            TunerConfig.MAX_FREQ_HZ,
            threshold,
            options
        )
    }

    @Test
    fun export() {
        val out = System.getenv("EVAL_OUT")
        assumeTrue("EVAL_OUT is not set", out != null)
        val variant = System.getenv("EVAL_VARIANT") ?: "baseline"
        val dir = File(System.getProperty("samples.dir") ?: error("system property samples.dir is not set"))
        val hop = TunerConfig.HOP_SIZE
        val size = TunerConfig.WINDOW_SIZE
        val csv = StringBuilder("file,block,level_db,loud,raw_hz,raw_conf,hz\n")
        var nanos = 0L
        var calls = 0
        for (s in Samples.ALL) {
            val samples = AiffReader.read(File(dir, s.fileName)).samples
            val pipeline = PitchPipeline(detector = detector(variant))
            val raw = detector(variant)
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
        println("EVAL $variant: $calls analyses, ${nanos / 1000 / maxOf(calls, 1)} µs each")
    }
}

package de.example.tunetheworld.sample

/** The test recordings listed in `samples.tsv` (test resources), which also feeds the downloadSamples task. */
object Samples {
    enum class Kind { STEADY, VIBRATO, PLUCKED }

    class Sample(val path: String, val transposition: Int, val kind: Kind, val octaveJump: Boolean) {
        val fileName: String = path.substringAfterLast('/')

        /** Sounding MIDI note, taken from the file name, e.g. "Horn.ff.Bb1.stereo.aif" -> 34. */
        val midi: Int = run {
            val match = NOTE.find(fileName) ?: error("no note in file name: $fileName")
            val (letter, flat, octave) = match.destructured
            val pitchClass = "C D EF G A B".indexOf(letter) - (if (flat.isEmpty()) 0 else 1)
            (octave.toInt() + 1) * 12 + pitchClass
        }
    }

    private val NOTE = Regex("""\.([A-G])(b?)(\d)\.stereo\.aif$""")

    val ALL: List<Sample> by lazy {
        val text = Samples::class.java.getResource("/samples.tsv")?.readText() ?: error("samples.tsv not on classpath")
        text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val cols = line.split('\t')
            Sample(cols[0], cols[2].toInt(), Kind.valueOf(cols[3]), cols[4] == "octave-jump")
        }
    }
}

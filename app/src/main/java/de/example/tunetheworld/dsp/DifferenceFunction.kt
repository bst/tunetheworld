package de.example.tunetheworld.dsp

/**
 * YIN difference function d(τ) = Σ (x[j] − x[j+τ])² over j = 0 until [w], for τ = 0..[maxLag], computed with an FFT.
 *
 * Expanding the square gives d(τ) = Σ x[j]² + Σ x[j+τ]² − 2·Σ x[j]·x[j+τ]. The two energy terms come from running
 * sums. The last term is the cross-correlation of the first w samples with the whole window, which one forward and one
 * inverse FFT give for every lag at once: O(n log n) instead of O(w · maxLag).
 *
 * Near a dip d(τ) is a small difference of large terms, so everything is computed in double precision.
 * Pure Kotlin; all arrays are allocated once, [compute] allocates nothing.
 */
class DifferenceFunction(private val w: Int, private val maxLag: Int) {
    /** Samples read from the buffer: index j + τ goes up to w − 1 + maxLag. */
    val inputSize = w + maxLag

    // FFT size: large enough that the circular correlation does not wrap into the lags we need
    private val n = nextPowerOfTwo(inputSize)
    private val re = DoubleArray(n)
    private val im = DoubleArray(n)
    private val corrRe = DoubleArray(n)
    private val corrIm = DoubleArray(n)
    private val energy = DoubleArray(inputSize + 1) // energy[i] = Σ x[j]² for j < i
    private val cos = DoubleArray(n / 2) { Math.cos(2.0 * Math.PI * it / n) }
    private val sin = DoubleArray(n / 2) { Math.sin(2.0 * Math.PI * it / n) }
    private val bitReversed = IntArray(n) { Integer.reverse(it) ushr (Integer.numberOfLeadingZeros(n) + 1) }

    /** Writes d(τ) for τ = 0..maxLag into [out]. */
    fun compute(buffer: FloatArray, out: FloatArray) {
        require(buffer.size >= inputSize && out.size > maxLag)

        // Both real signals in one complex FFT: a = first w samples as real part, b = all samples as imaginary part
        for (i in 0 until n) {
            val x = if (i < inputSize) buffer[i].toDouble() else 0.0
            re[i] = if (i < w) x else 0.0
            im[i] = x
        }
        fft(re, im)

        // Split the spectra (A_k = (Z_k + conj Z_−k) / 2, B_k = (Z_k − conj Z_−k) / 2i) and form conj(A)·B,
        // the spectrum of the correlation r(τ) = Σ a[j]·b[j+τ]. Stored conjugated, ready for the inverse transform.
        for (k in 0 until n) {
            val m = (n - k) and (n - 1)
            val ar = 0.5 * (re[k] + re[m])
            val ai = 0.5 * (im[k] - im[m])
            val br = 0.5 * (im[k] + im[m])
            val bi = 0.5 * (re[m] - re[k])
            corrRe[k] = ar * br + ai * bi
            corrIm[k] = -(ar * bi - ai * br)
        }
        // Inverse FFT as conj(FFT(conj(X))) / n; the result is real, so only the real part is needed
        fft(corrRe, corrIm)

        energy[0] = 0.0
        for (i in 0 until inputSize) {
            val x = buffer[i].toDouble()
            energy[i + 1] = energy[i] + x * x
        }
        val head = energy[w]
        for (tau in 0..maxLag) {
            val d = head + (energy[tau + w] - energy[tau]) - 2.0 * corrRe[tau] / n
            // Rounding can leave a tiny negative value where the exact result is 0 (silence, a perfectly periodic tone)
            out[tau] = if (d > 0.0) d.toFloat() else 0f
        }
    }

    /** In-place iterative radix-2 FFT, forward direction (e^−iωt). */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = bitReversed[i]
            if (j > i) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val l = j + half
                    val c = cos[k]
                    val s = sin[k]
                    val tr = re[l] * c + im[l] * s
                    val ti = im[l] * c - re[l] * s
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }

    private companion object {
        fun nextPowerOfTwo(x: Int): Int {
            var n = 1
            while (n < x) n = n shl 1
            return n
        }
    }
}

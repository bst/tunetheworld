package de.example.saxtuner.engine

import de.example.saxtuner.audio.AudioInput
import de.example.saxtuner.dsp.PitchPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/** Reads microphone blocks and hands them to [PitchPipeline]. The caller must hold RECORD_AUDIO. */
class TunerEngine(
    private val audioInput: AudioInput,
    private val pipeline: PitchPipeline = PitchPipeline()
) {
    /** Emits the smoothed frequency per block, or null when there is no usable tone. */
    fun frequencies(): Flow<Float?> = flow {
        val hop = FloatArray(TunerConfig.HOP_SIZE)
        pipeline.reset()
        audioInput.start()
        try {
            while (currentCoroutineContext().isActive) {
                val n = audioInput.read(hop)
                if (n < 0) error("AudioRecord read failed: $n")
                // Null while the window is still filling, on silence, or on low confidence
                emit(pipeline.process(hop, n))
            }
        } finally {
            audioInput.stop()
        }
    }.flowOn(Dispatchers.Default)
}

package io.github.bst.tunetheworld.audio

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import io.github.bst.tunetheworld.engine.TunerConfig

/** Wraps [AudioRecord]; delivers mono float blocks at [TunerConfig.SAMPLE_RATE]. */
class AudioInput
@RequiresPermission(Manifest.permission.RECORD_AUDIO)
constructor(context: Context) {
    private val record: AudioRecord

    init {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Never MIC: it often enables noise suppression and automatic gain control
        val source =
            if (audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }
        val minBytes = AudioRecord.getMinBufferSize(
            TunerConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferBytes = maxOf(minBytes * 4, TunerConfig.HOP_SIZE * 4 * 4)
        record = AudioRecord(
            source,
            TunerConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferBytes
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            error("AudioRecord failed to initialize")
        }
    }

    fun start() = record.startRecording()

    /** Blocks until [dest] is filled; returns the number of samples read or a negative error code. */
    fun read(dest: FloatArray): Int = record.read(dest, 0, dest.size, AudioRecord.READ_BLOCKING)

    fun stop() {
        record.stop()
        record.release()
    }
}

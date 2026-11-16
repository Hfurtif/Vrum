package com.example.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavEncoder {

    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    /**
     * Encodes 16-bit mono PCM samples at 16 kHz into a complete RIFF/WAVE byte array.
     */
    fun encodePcm16ToWav(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataByteCount = samples.size * 2
        val totalByteCount = 44 + dataByteCount
        val buffer = ByteBuffer.allocate(totalByteCount).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(36 + dataByteCount)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt sub-chunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // PCM subchunk size
        buffer.putShort(1.toShort()) // AudioFormat = 1 (PCM)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(sampleRate)
        val byteRate = sampleRate * CHANNELS * (BITS_PER_SAMPLE / 8)
        buffer.putInt(byteRate)
        val blockAlign = (CHANNELS * (BITS_PER_SAMPLE / 8)).toShort()
        buffer.putShort(blockAlign)
        buffer.putShort(BITS_PER_SAMPLE.toShort())

        // data sub-chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(dataByteCount)

        for (sample in samples) {
            buffer.putShort(sample)
        }

        return buffer.array()
    }
}

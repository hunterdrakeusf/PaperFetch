package com.example.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural Synthesizer for the iconic Jeopardy "Think Music" theme
 * and completion arrival chime bell ("Ding!").
 *
 * Synthesizes the memorable melody with bright mallet/marimba harmonics,
 * bouncy staccato bass, and the classic countdown clock ticking.
 */
class ElevatorMusicPlayer {

    private val sampleRate = 44100
    private var musicJob: Job? = null
    private var isPlayingMusic = false
    private var isMuted = false

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted && isPlayingMusic) {
            stopMusic()
        }
    }

    fun isMuted(): Boolean = isMuted

    /**
     * Start playing the looping Jeopardy theme song.
     */
    fun startMusic(scope: CoroutineScope) {
        if (isMuted || isPlayingMusic) return
        isPlayingMusic = true

        musicJob?.cancel()
        musicJob = scope.launch(Dispatchers.Default) {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufSize, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            try {
                audioTrack.play()

                // Generate the iconic 8-measure Jeopardy Think Theme loop
                val loopSamples = generateJeopardyLoop()

                while (isActive && isPlayingMusic) {
                    var written = 0
                    while (written < loopSamples.size && isActive && isPlayingMusic) {
                        val chunkSize = minOf(4096, loopSamples.size - written)
                        val count = audioTrack.write(loopSamples, written, chunkSize)
                        if (count > 0) {
                            written += count
                        } else {
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // AudioTrack cleanup
            } finally {
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {
                }
                isPlayingMusic = false
            }
        }
    }

    /**
     * Stop the theme music cleanly.
     */
    fun stopMusic() {
        isPlayingMusic = false
        musicJob?.cancel()
        musicJob = null
    }

    /**
     * Play the signature bright arrival chime ("Ding!").
     */
    fun playElevatorDing(scope: CoroutineScope) {
        if (isMuted) return

        scope.launch(Dispatchers.Default) {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufSize, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            try {
                audioTrack.play()
                val dingSamples = generateDingChime()
                audioTrack.write(dingSamples, 0, dingSamples.size)
                Thread.sleep(1200)
            } catch (_: Exception) {
            } finally {
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Synthesize the iconic Jeopardy "Think Music" (8 bars, ~15.5 seconds at 124 BPM).
     * Features:
     * - Iconic bouncy melody (bright marimba / mallet synthesizer)
     * - Pizzicato / staccato walking bass line
     * - Studio clock countdown ticking on each beat
     */
    private fun generateJeopardyLoop(): ShortArray {
        val bpm = 124
        val beatDuration = 60.0 / bpm // ~0.484 sec
        val totalBeats = 32.0 // 8 measures of 4/4
        val totalSeconds = totalBeats * beatDuration
        val totalSamples = (totalSeconds * sampleRate).toInt()
        val buffer = FloatArray(totalSamples)

        // Note frequencies (Hz)
        val C4 = 261.63
        val D4 = 293.66
        val E4 = 329.63
        val F4 = 349.23
        val G4 = 392.00
        val A4 = 440.00
        val B4 = 493.88

        val C5 = 523.25
        val D5 = 587.33
        val E5 = 659.25
        val F5 = 698.46
        val G5 = 783.99
        val A5 = 880.00

        // Bass frequencies
        val C3 = 130.81
        val D3 = 146.83
        val E3 = 164.81
        val F3 = 174.61
        val G3 = 196.00
        val A3 = 220.00
        val Bb3 = 233.08
        val B3 = 246.94

        // 1. MELODY NOTES: (startBeat, durationBeats, frequency)
        val melody = listOf(
            // --- Phrase 1 ---
            // Bar 1: Da-da-da-da
            Triple(0.0, 0.85, C5),
            Triple(1.0, 0.85, F5),
            Triple(2.0, 0.85, C5),
            Triple(3.0, 0.85, F4),
            // Bar 2: Da-da-da
            Triple(4.0, 0.85, C5),
            Triple(5.0, 0.85, F5),
            Triple(6.0, 1.7, C5),
            // Bar 3: Da-da-da-da
            Triple(8.0, 0.85, C5),
            Triple(9.0, 0.85, F5),
            Triple(10.0, 0.85, C5),
            Triple(11.0, 0.85, F5),
            // Bar 4: Da-da-da-da-da
            Triple(12.0, 1.1, A5),
            Triple(13.2, 0.65, G5),
            Triple(14.0, 0.65, F5),
            Triple(14.7, 0.55, E5),
            Triple(15.25, 0.65, D5),

            // --- Phrase 2 ---
            // Bar 5: Da-da-da-da
            Triple(16.0, 0.85, C5),
            Triple(17.0, 0.85, F5),
            Triple(18.0, 0.85, C5),
            Triple(19.0, 0.85, F4),
            // Bar 6: Da-da-da
            Triple(20.0, 0.85, C5),
            Triple(21.0, 0.85, F5),
            Triple(22.0, 1.7, C5),
            // Bar 7: Descending turnaround
            Triple(24.0, 0.85, F5),
            Triple(25.0, 0.85, D5),
            Triple(26.0, 0.85, B4),
            Triple(27.0, 0.85, G4),
            // Bar 8: Final cadence
            Triple(28.0, 0.85, E4),
            Triple(29.0, 0.85, D4),
            Triple(30.0, 1.8, C4)
        )

        // Synthesize Melody (Bright Mallet / Marimba tone with overtone sparkle)
        for ((startBeat, durBeats, freq) in melody) {
            val startSample = (startBeat * beatDuration * sampleRate).toInt()
            val noteDurationSec = durBeats * beatDuration
            val noteSamples = (noteDurationSec * sampleRate).toInt()

            for (s in 0 until noteSamples) {
                val idx = startSample + s
                if (idx >= totalSamples) break
                val t = s.toDouble() / sampleRate
                // Crisp mallet attack with smooth natural decay
                val env = (1.0 - exp(-t * 120.0)) * exp(-t * 3.8)
                // Marimba / synth overtone formula (fundamental + octave + 2.76x ring)
                val sample = (
                    sin(2.0 * Math.PI * freq * t) * 0.70 +
                    sin(2.0 * Math.PI * (freq * 2.0) * t) * 0.22 +
                    sin(2.0 * Math.PI * (freq * 2.76) * t) * 0.12 +
                    sin(2.0 * Math.PI * (freq * 4.0) * t) * 0.06
                ) * env * 0.32
                buffer[idx] += sample.toFloat()
            }
        }

        // 2. BASS LINE: Walking bouncy pizzicato bass on beats
        val bassNotes = listOf(
            // Bar 1
            F3, A3, C4, A3,
            // Bar 2
            F3, A3, C4, A3,
            // Bar 3
            F3, A3, C4, F3,
            // Bar 4
            Bb3, G3, C4, E3,
            // Bar 5
            F3, A3, C4, A3,
            // Bar 6
            F3, A3, C4, A3,
            // Bar 7
            D3, F3, G3, B3,
            // Bar 8
            C3, E3, G3, C4
        )

        for (beat in bassNotes.indices) {
            val startSample = (beat * beatDuration * sampleRate).toInt()
            val bassDurationSec = 0.7 * beatDuration
            val noteSamples = (bassDurationSec * sampleRate).toInt()
            val freq = bassNotes[beat]

            for (s in 0 until noteSamples) {
                val idx = startSample + s
                if (idx >= totalSamples) break
                val t = s.toDouble() / sampleRate
                // Plucked bass envelope (fast attack, snappy exponential release)
                val env = (1.0 - exp(-t * 80.0)) * exp(-t * 5.0)
                val sample = (
                    sin(2.0 * Math.PI * freq * t) * 0.75 +
                    sin(2.0 * Math.PI * (freq * 2.0) * t) * 0.30
                ) * env * 0.22
                buffer[idx] += sample.toFloat()
            }
        }

        // 3. COUNTDOWN CLOCK TICKING: Subtle woodblock / timer tick on each beat
        for (beat in 0 until 32) {
            val startSample = (beat * beatDuration * sampleRate).toInt()
            val tickSamples = (0.025 * sampleRate).toInt() // 25ms click

            for (s in 0 until tickSamples) {
                val idx = startSample + s
                if (idx >= totalSamples) break
                val t = s.toDouble() / sampleRate
                val clickEnv = exp(-t * 180.0)
                val click = sin(2.0 * Math.PI * 1800.0 * t) * clickEnv * 0.08
                buffer[idx] += click.toFloat()
            }
        }

        // Convert normalized FloatArray to ShortArray PCM
        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val clamped = buffer[i].coerceIn(-1.0f, 1.0f)
            pcm[i] = (clamped * 32767.0f).toInt().toShort()
        }
        return pcm
    }

    /**
     * Synthesize authentic arrival chime ("Ding!").
     * Clean, bright, resonant bell chime.
     */
    private fun generateDingChime(): ShortArray {
        val duration = 1.6 // seconds
        val totalSamples = (duration * sampleRate).toInt()
        val buffer = FloatArray(totalSamples)

        // Note 1: High crisp "Ding" (G5, 783.99 Hz)
        // Note 2: Warm resolving "Dong" (E5, 659.25 Hz) after 0.38s
        val chimeNotes = listOf(
            Triple(0.0, 783.99, 1.2), // Ding
            Triple(0.38, 659.25, 1.2) // Dong
        )

        for ((startSec, freq, lenSec) in chimeNotes) {
            val startSample = (startSec * sampleRate).toInt()
            val lenSamples = (lenSec * sampleRate).toInt()

            for (s in 0 until lenSamples) {
                val idx = startSample + s
                if (idx >= totalSamples) break
                val t = s.toDouble() / sampleRate
                val env = exp(-t * 2.8)
                val sample = (
                    sin(2.0 * Math.PI * freq * t) * 0.65 +
                    sin(2.0 * Math.PI * (freq * 2.75) * t) * 0.22 +
                    sin(2.0 * Math.PI * (freq * 4.07) * t) * 0.12
                ) * env * 0.45
                buffer[idx] += sample.toFloat()
            }
        }

        val pcm = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val clamped = buffer[i].coerceIn(-1.0f, 1.0f)
            pcm[i] = (clamped * 32767.0f).toInt().toShort()
        }
        return pcm
    }
}

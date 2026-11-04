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
 * Procedural Elevator Music & Arrival Ding Synthesizer.
 * Generates an authentic, gentle, looping lounge/bossa-nova chord progression
 * using soft electric-piano and walking-bass tones, and a crystal-clear elevator bell chime.
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
     * Start playing the looping elevator music.
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

                // Generate a delightful 4-measure smooth elevator music lounge loop:
                // Chord progression: Fmaj7 -> Dm7 -> Gm7 -> C7
                val loopSamples = generateElevatorLoop()

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
     * Stop the elevator music cleanly.
     */
    fun stopMusic() {
        isPlayingMusic = false
        musicJob?.cancel()
        musicJob = null
    }

    /**
     * Play the signature two-tone elevator arrival chime ("Ding-Dong!").
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
                // Wait briefly for buffer to flush
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
     * Synthesize 4 bars of classic elevator music (approx. 7.5 seconds at 128 bpm).
     * Soft Rhodes electric piano chords + gentle walking upright bass + vibraphone melody line.
     */
    private fun generateElevatorLoop(): ShortArray {
        val bpm = 120
        val beatDuration = 60.0 / bpm // 0.5 sec per beat
        val totalBeats = 16 // 4 bars of 4/4
        val totalSeconds = totalBeats * beatDuration
        val totalSamples = (totalSeconds * sampleRate).toInt()
        val buffer = FloatArray(totalSamples)

        // Fmaj7, Dm7, Gm7, C9 chords (frequencies in Hz)
        val chords = listOf(
            // Fmaj7 (F3, A3, C4, E4)
            listOf(174.61, 220.00, 261.63, 329.63),
            // Dm7 (D3, F3, A3, C4)
            listOf(146.83, 174.61, 220.00, 261.63),
            // Gm7 (G3, Bb3, D4, F4)
            listOf(196.00, 233.08, 293.66, 349.23),
            // C9 / C7 (C3, E3, G3, Bb3, D4)
            listOf(130.81, 164.81, 196.00, 233.08, 293.66)
        )

        // Bass root notes
        val bassNotes = listOf(87.31, 73.42, 98.00, 65.41) // F2, D2, G2, C2

        // Soft melody notes (vibraphone / rhodes arpeggios)
        val melodyNotes = listOf(
            // Bar 1 (Fmaj7): A4, C5, E5, C5
            listOf(440.0, 523.25, 659.25, 523.25),
            // Bar 2 (Dm7): F5, E5, C5, A4
            listOf(698.46, 659.25, 523.25, 440.0),
            // Bar 3 (Gm7): D5, F5, A5, F5
            listOf(587.33, 698.46, 880.0, 698.46),
            // Bar 4 (C9): G5, E5, D5, C5
            listOf(783.99, 659.25, 587.33, 523.25)
        )

        val barSamples = (4 * beatDuration * sampleRate).toInt()

        for (bar in 0 until 4) {
            val barStart = bar * barSamples
            val chord = chords[bar]
            val bass = bassNotes[bar]
            val melody = melodyNotes[bar]

            // 1. Play soft warm electric piano chord (with gentle tremolo & slow decay)
            for (chordNote in chord) {
                val noteDuration = 3.6 * beatDuration
                val noteSamples = (noteDuration * sampleRate).toInt()
                for (s in 0 until noteSamples) {
                    val idx = barStart + s
                    if (idx >= totalSamples) break
                    val t = s.toDouble() / sampleRate
                    // Envelope: soft attack, slow decay
                    val env = (1.0 - exp(-t * 20.0)) * exp(-t * 0.9)
                    // Rhodes sound: sine + subtle 2nd harmonic + gentle 5Hz chorus tremolo
                    val tremolo = 1.0 + 0.15 * sin(2.0 * Math.PI * 5.0 * t)
                    val sample = (sin(2.0 * Math.PI * chordNote * t) * 0.7 +
                            sin(2.0 * Math.PI * (chordNote * 2.0) * t) * 0.2) * env * tremolo * 0.14
                    buffer[idx] += sample.toFloat()
                }
            }

            // 2. Walking warm bass notes on beat 1 and beat 3
            for (beat in listOf(0, 2)) {
                val bassStart = barStart + (beat * beatDuration * sampleRate).toInt()
                val bassFreq = if (beat == 0) bass else bass * 1.5 // fifth on beat 3
                val bassDuration = 1.6 * beatDuration
                val bassSamples = (bassDuration * sampleRate).toInt()
                for (s in 0 until bassSamples) {
                    val idx = bassStart + s
                    if (idx >= totalSamples) break
                    val t = s.toDouble() / sampleRate
                    val env = (1.0 - exp(-t * 40.0)) * exp(-t * 2.2)
                    val sample = (sin(2.0 * Math.PI * bassFreq * t) * 0.8 +
                            sin(2.0 * Math.PI * (bassFreq * 2.0) * t) * 0.3) * env * 0.22
                    buffer[idx] += sample.toFloat()
                }
            }

            // 3. Gentle vibraphone melody notes on each beat
            for (beat in 0 until 4) {
                val melStart = barStart + (beat * beatDuration * sampleRate).toInt()
                val melFreq = melody[beat]
                val melDuration = 0.9 * beatDuration
                val melSamples = (melDuration * sampleRate).toInt()
                for (s in 0 until melSamples) {
                    val idx = melStart + s
                    if (idx >= totalSamples) break
                    val t = s.toDouble() / sampleRate
                    // Bell/vibraphone envelope: fast crisp attack, exponential decay
                    val env = (1.0 - exp(-t * 80.0)) * exp(-t * 3.5)
                    // Vibraphone overtone structure
                    val sample = (sin(2.0 * Math.PI * melFreq * t) * 0.75 +
                            sin(2.0 * Math.PI * (melFreq * 3.0) * t) * 0.18 +
                            sin(2.0 * Math.PI * (melFreq * 2.0) * t) * 0.1) * env * 0.16
                    buffer[idx] += sample.toFloat()
                }
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
     * Synthesize authentic elevator arrival chime ("Ding-Dong!").
     * Clean, bright, resonant brass chime bell.
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
                // Crisp chime envelope: instantaneous attack, smooth bell decay
                val env = exp(-t * 2.8)
                // Bell harmonics
                val sample = (sin(2.0 * Math.PI * freq * t) * 0.65 +
                        sin(2.0 * Math.PI * (freq * 2.75) * t) * 0.22 +
                        sin(2.0 * Math.PI * (freq * 4.07) * t) * 0.12) * env * 0.45
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

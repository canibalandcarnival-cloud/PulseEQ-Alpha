package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.EqPresetsCatalog
import com.example.model.NightIntensity
import com.example.model.TestAudioSource
import com.example.model.VisualizerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time soft-knee Dynamic Range Compressor matching the Web Audio DynamicsCompressorNode
 * specification (Threshold, 20 dB Knee, 4:1 Ratio, 5ms Attack, 250ms Release, Makeup Gain).
 */
class DynamicRangeCompressor(private val sampleRate: Int = 44100) {

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var thresholdDb: Float = NightIntensity.STANDARD.thresholdDb

    @Volatile
    private var kneeDb: Float = NightIntensity.STANDARD.kneeDb

    @Volatile
    private var ratio: Float = NightIntensity.STANDARD.ratio

    @Volatile
    private var makeupGainLinear: Double = 10.0.pow(NightIntensity.STANDARD.makeupGainDb / 20.0)

    private var attackCoeff: Double = exp(-1.0 / (0.005 * sampleRate))
    private var releaseCoeff: Double = exp(-1.0 / (0.250 * sampleRate))
    private var envelopeDb: Double = -60.0

    @Volatile
    var latestGainReductionDb: Float = 0f
        private set

    fun configure(isEnabled: Boolean, intensity: NightIntensity) {
        enabled = isEnabled
        thresholdDb = intensity.thresholdDb
        kneeDb = intensity.kneeDb
        ratio = intensity.ratio
        makeupGainLinear = 10.0.pow(intensity.makeupGainDb / 20.0)
        attackCoeff = exp(-1.0 / ((intensity.attackMs / 1000.0) * sampleRate))
        releaseCoeff = exp(-1.0 / ((intensity.releaseMs / 1000.0) * sampleRate))
        if (!isEnabled) {
            latestGainReductionDb = 0f
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!enabled) {
            latestGainReductionDb = 0f
            return inL to inR
        }

        val detectorAmp = max(abs(inL), abs(inR)).coerceAtLeast(1e-6)
        val inputDb = 20.0 * log10(detectorAmp)

        val coeff = if (inputDb > envelopeDb) attackCoeff else releaseCoeff
        envelopeDb = coeff * envelopeDb + (1.0 - coeff) * inputDb

        val grDb = computeGainReductionDb(
            inputDb = envelopeDb.toFloat(),
            thresholdDb = thresholdDb,
            kneeDb = kneeDb,
            ratio = ratio
        )
        latestGainReductionDb = grDb

        val totalGainLinear = 10.0.pow(grDb / 20.0) * makeupGainLinear
        return (inL * totalGainLinear) to (inR * totalGainLinear)
    }

    companion object {
        /**
         * Soft-knee compression curve returning gain reduction in dB (<= 0f).
         */
        fun computeGainReductionDb(
            inputDb: Float,
            thresholdDb: Float,
            kneeDb: Float,
            ratio: Float
        ): Float {
            if (ratio <= 1f) return 0f
            val halfKnee = kneeDb * 0.5f
            val over = inputDb - thresholdDb
            val outputDb = when {
                kneeDb > 0f && over > -halfKnee && over < halfKnee -> {
                    val delta = inputDb - thresholdDb + halfKnee
                    inputDb + ((1f / ratio) - 1f) * (delta * delta) / (2f * kneeDb)
                }

                over >= halfKnee -> {
                    thresholdDb + over / ratio
                }

                else -> inputDb
            }
            return (outputDb - inputDb).coerceAtMost(0f)
        }
    }
}

/**
 * Cascaded 10-band stereo biquad peaking EQ filter + preamp + dynamic range compressor + soft limiter
 * matching exact center frequencies: 32, 64, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz.
 */
class BiquadEqFilterBank(private val sampleRate: Int = 44100) {

    private class BiquadCoeffs(
        var b0: Double = 1.0,
        var b1: Double = 0.0,
        var b2: Double = 0.0,
        var a1: Double = 0.0,
        var a2: Double = 0.0
    )

    private class ChannelState {
        val z1 = DoubleArray(10)
        val z2 = DoubleArray(10)
    }

    private val coeffs = Array(10) { BiquadCoeffs() }
    private val leftState = ChannelState()
    private val rightState = ChannelState()
    private val compressor = DynamicRangeCompressor(sampleRate)

    @Volatile
    private var preampLinear: Double = 1.0

    @Volatile
    private var bypassed: Boolean = false

    val latestGainReductionDb: Float
        get() = if (bypassed) 0f else compressor.latestGainReductionDb

    init {
        updateParameters(List(10) { 0f }, 0f, false, false, NightIntensity.STANDARD)
    }

    fun updateParameters(
        gainsDb: List<Float>,
        preampDb: Float,
        isBypassed: Boolean,
        lateNightEnabled: Boolean = false,
        nightIntensity: NightIntensity = NightIntensity.STANDARD
    ) {
        bypassed = isBypassed
        preampLinear = 10.0.pow(preampDb.toDouble() / 20.0)
        compressor.configure(isEnabled = lateNightEnabled && !isBypassed, intensity = nightIntensity)

        val q = 1.414
        for (i in 0 until 10) {
            val freq = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble()
            val db = gainsDb.getOrElse(i) { 0f }.toDouble()
            val a = 10.0.pow(db / 40.0)
            val w0 = 2.0 * PI * freq / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)

            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * cosW0
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * cosW0
            val a2 = 1.0 - alpha / a

            coeffs[i].b0 = b0 / a0
            coeffs[i].b1 = b1 / a0
            coeffs[i].b2 = b2 / a0
            coeffs[i].a1 = a1 / a0
            coeffs[i].a2 = a2 / a0
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (bypassed) {
            return inL to inR
        }
        var l = inL * preampLinear
        var r = inR * preampLinear

        for (band in 0 until 10) {
            val c = coeffs[band]

            val outL = c.b0 * l + leftState.z1[band]
            leftState.z1[band] = c.b1 * l - c.a1 * outL + leftState.z2[band]
            leftState.z2[band] = c.b2 * l - c.a2 * outL
            l = outL

            val outR = c.b0 * r + rightState.z1[band]
            rightState.z1[band] = c.b1 * r - c.a1 * outR + rightState.z2[band]
            rightState.z2[band] = c.b2 * r - c.a2 * outR
            r = outR
        }

        val (compL, compR) = compressor.processStereoSample(l, r)

        // Soft studio limiter to prevent digital clipping when boosting +12dB
        return tanh(compL * 0.9) to tanh(compR * 0.9)
    }
}

/**
 * Built-in real-time audio synthesizer so users can audition and visualize
 * Speaker / Headphone / Bluetooth EQ tuning & Late Night compression immediately.
 */
class TestAudioSynthesizer {
    private val sampleRate = 44100
    private val filterBank = BiquadEqFilterBank(sampleRate)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _currentSource = MutableStateFlow(TestAudioSource.OFF)
    val currentSource: StateFlow<TestAudioSource> = _currentSource.asStateFlow()

    private val _visualizerFrame = MutableStateFlow(VisualizerFrame())
    val visualizerFrame: StateFlow<VisualizerFrame> = _visualizerFrame.asStateFlow()

    @Volatile
    var audioSessionId: Int = 0
        private set

    fun updateEq(
        gainsDb: List<Float>,
        preampDb: Float,
        isBypassed: Boolean,
        lateNightEnabled: Boolean,
        nightIntensity: NightIntensity
    ) {
        filterBank.updateParameters(gainsDb, preampDb, isBypassed, lateNightEnabled, nightIntensity)
    }

    fun setSource(source: TestAudioSource) {
        if (_currentSource.value == source) return
        stop()
        _currentSource.value = source
        if (source != TestAudioSource.OFF) {
            startPlayback(source)
        } else {
            _visualizerFrame.value = VisualizerFrame()
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Throwable) {
        }
        audioTrack = null
        audioSessionId = 0
    }

    private fun startPlayback(source: TestAudioSource) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = try {
            AudioTrack.Builder()
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
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Throwable) {
            _currentSource.value = TestAudioSource.OFF
            return
        }

        audioTrack = track
        audioSessionId = track.audioSessionId
        try {
            track.play()
        } catch (_: Throwable) {
            stop()
            _currentSource.value = TestAudioSource.OFF
            return
        }

        playJob = scope.launch {
            val frameCount = 1024
            val pcmBuffer = ShortArray(frameCount * 2)
            val monoAnalysis = DoubleArray(512)
            var sampleCursor = 0L

            // Voss-McCartney pink noise state
            val pinkRows = DoubleArray(16)
            var pinkRunningSum = 0.0
            var pinkCounter = 0

            while (isActive) {
                var peak = 0.0
                for (i in 0 until frameCount) {
                    val t = sampleCursor.toDouble() / sampleRate
                    val (rawL, rawR) = when (source) {
                        TestAudioSource.AMBIENT_CHORD -> {
                            // Dynamic range swells + full-spectrum harmonics so compression is clearly audible & visible
                            val swell = 0.45 + 0.55 * abs(sin(2.0 * PI * 0.28 * t))
                            val sub = sin(2.0 * PI * 55.0 * t) * 0.24
                            val bass = sin(2.0 * PI * 110.0 * t) * 0.22
                            val lowMid = sin(2.0 * PI * 220.0 * t) * 0.16
                            val mid1 = sin(2.0 * PI * 329.63 * t) * 0.14
                            val mid2 = sin(2.0 * PI * 440.0 * t) * 0.12
                            val pres = sin(2.0 * PI * 1318.5 * t) * 0.08
                            val air1 = sin(2.0 * PI * 3520.0 * t) * 0.05
                            val air2 = sin(2.0 * PI * 7040.0 * t) * 0.04
                            val air3 = sin(2.0 * PI * 12000.0 * t) * 0.025
                            val stereoSpread = sin(2.0 * PI * 0.7 * t) * 0.04
                            val base = (sub + bass + lowMid + mid1 + mid2 + pres + air1 + air2 + air3) * swell
                            (base + stereoSpread) to (base - stereoSpread)
                        }

                        TestAudioSource.BASS_GROOVE -> {
                            // 120 BPM electronic kick + bassline + hi-hat transient (great for GR meter)
                            val beatPos = (t * 2.0) % 1.0
                            val kickEnv = exp(-beatPos * 11.0)
                            val kickFreq = 42.0 + 110.0 * exp(-beatPos * 26.0)
                            val kick = sin(2.0 * PI * kickFreq * beatPos) * kickEnv * 0.50

                            val bassNote = if (((t * 2.0).toInt() % 4) < 2) 65.41 else 82.41
                            val bassWave = (sin(2.0 * PI * bassNote * t) + 0.4 * sin(2.0 * PI * bassNote * 2.0 * t)) * 0.22

                            val hatPos = (t * 4.0) % 1.0
                            val hatEnv = exp(-hatPos * 35.0)
                            val white = (Math.random() * 2.0 - 1.0)
                            val hat = white * hatEnv * 0.15

                            val pad = sin(2.0 * PI * 523.25 * t) * 0.07 + sin(2.0 * PI * 1568.0 * t) * 0.04
                            val l = (kick + bassWave + hat + pad) * 0.85
                            val r = (kick + bassWave + hat * 0.85 + pad * 1.1) * 0.85
                            l to r
                        }

                        TestAudioSource.PINK_NOISE -> {
                            pinkCounter = (pinkCounter + 1) and 0xFFFF
                            var idx = 0
                            var k = pinkCounter
                            while (idx < 16 && (k and 1) == 0) {
                                k = k shr 1
                                idx++
                            }
                            if (idx < 16) {
                                pinkRunningSum -= pinkRows[idx]
                                val newVal = Math.random() * 2.0 - 1.0
                                pinkRows[idx] = newVal
                                pinkRunningSum += newVal
                            }
                            val white = Math.random() * 2.0 - 1.0
                            val pink = ((pinkRunningSum + white) / 17.0) * 0.75
                            pink to pink
                        }

                        TestAudioSource.OFF -> 0.0 to 0.0
                    }

                    val (procL, procR) = filterBank.processStereoSample(rawL, rawR)
                    val clampedL = procL.coerceIn(-1.0, 1.0)
                    val clampedR = procR.coerceIn(-1.0, 1.0)
                    pcmBuffer[i * 2] = (clampedL * 32767.0).toInt().toShort()
                    pcmBuffer[i * 2 + 1] = (clampedR * 32767.0).toInt().toShort()

                    val mono = (clampedL + clampedR) * 0.5
                    if (i < 512) monoAnalysis[i] = mono
                    val absMono = abs(mono)
                    if (absMono > peak) peak = absMono
                    sampleCursor++
                }

                try {
                    track.write(pcmBuffer, 0, pcmBuffer.size)
                } catch (_: Throwable) {
                    break
                }

                val spectrum = computeLogSpectrum(monoAnalysis)
                val waveform = List(96) { idx ->
                    val srcIdx = (idx * 512) / 96
                    monoAnalysis[srcIdx].toFloat().coerceIn(-1f, 1f)
                }
                val peakDb = if (peak > 1e-4) (20.0 * log10(peak)).toFloat().coerceIn(-60f, 0f) else -60f

                _visualizerFrame.value = VisualizerFrame(
                    spectrumBins = spectrum,
                    waveformSamples = waveform,
                    peakDb = peakDb,
                    gainReductionDb = filterBank.latestGainReductionDb,
                    isSignalLive = true
                )
            }
        }
    }

    private fun computeLogSpectrum(samples: DoubleArray): List<Float> {
        val n = samples.size
        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        for (i in 0 until n) {
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            real[i] = samples[i] * w
        }
        fftInPlace(real, imag)

        val mags = DoubleArray(n / 2)
        for (k in 0 until n / 2) {
            mags[k] = hypot(real[k], imag[k]) / (n * 0.25)
        }

        val bars = MutableList(32) { 0f }
        val minFreq = 30.0
        val maxFreq = 18000.0
        for (b in 0 until 32) {
            val fLow = minFreq * (maxFreq / minFreq).pow(b / 32.0)
            val fHigh = minFreq * (maxFreq / minFreq).pow((b + 1) / 32.0)
            val binLow = ((fLow * n) / sampleRate).toInt().coerceIn(1, n / 2 - 1)
            val binHigh = ((fHigh * n) / sampleRate).toInt().coerceIn(binLow, n / 2 - 1)

            var maxMag = 0.0
            for (k in binLow..binHigh) {
                if (mags[k] > maxMag) maxMag = mags[k]
            }
            val db = if (maxMag > 1e-5) 20.0 * log10(maxMag) else -60.0
            bars[b] = ((db + 54.0) / 54.0).toFloat().coerceIn(0f, 1f)
        }
        return bars
    }

    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenR = cos(ang)
            val wLenI = sin(ang)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                    val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR
                    imag[i + k + len / 2] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }
}

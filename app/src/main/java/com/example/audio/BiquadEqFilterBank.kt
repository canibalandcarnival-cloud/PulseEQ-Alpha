package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.BackgroundSoundPreset
import com.example.model.EqPresetsCatalog
import com.example.model.NightIntensity
import com.example.model.OutputTarget
import com.example.model.SpatialMode
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
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Reusable single stereo biquad filter supporting Peaking EQ, High-Pass, and Band-Pass modes.
 */
class StereoBiquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var z1L = 0.0
    private var z2L = 0.0
    private var z1R = 0.0
    private var z2R = 0.0

    fun setPeaking(sampleRate: Int, freqHz: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val nb0 = 1.0 + alpha * a
        val nb1 = -2.0 * cosW0
        val nb2 = 1.0 - alpha * a
        val na0 = 1.0 + alpha / a
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha / a

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setHighPass(sampleRate: Int, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val nb0 = (1.0 + cosW0) * 0.5
        val nb1 = -(1.0 + cosW0)
        val nb2 = (1.0 + cosW0) * 0.5
        val na0 = 1.0 + alpha
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setBandPass(sampleRate: Int, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val nb0 = alpha
        val nb1 = 0.0
        val nb2 = -alpha
        val na0 = 1.0 + alpha
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setLowPass(sampleRate: Int, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val nb0 = (1.0 - cosW0) * 0.5
        val nb1 = 1.0 - cosW0
        val nb2 = (1.0 - cosW0) * 0.5
        val na0 = 1.0 + alpha
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setHighShelf(sampleRate: Int, freqHz: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val nb0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val nb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val nb2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val na0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        val na1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val na2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha

        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun processStereo(inL: Double, inR: Double): Pair<Double, Double> {
        val outL = b0 * inL + z1L
        z1L = b1 * inL - a1 * outL + z2L
        z2L = b2 * inL - a2 * outL

        val outR = b0 * inR + z1R
        z1R = b1 * inR - a1 * outR + z2R
        z2R = b2 * inR - a2 * outR

        return outL to outR
    }
}

/**
 * Real-time soft-knee Dynamic Range Compressor / Smart Limiter.
 * Used both for Late Night Mode (20 dB knee, 4:1 ratio, 5ms/250ms) and
 * iPhone Speaker Smart Limiter (-6 dB threshold, 12:1 ratio, 1ms/80ms, +3 dB makeup).
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
        configureCustom(
            isEnabled = isEnabled,
            threshold = intensity.thresholdDb,
            knee = intensity.kneeDb,
            compRatio = intensity.ratio,
            attackMs = intensity.attackMs,
            releaseMs = intensity.releaseMs,
            makeupGainDb = intensity.makeupGainDb
        )
    }

    fun configureCustom(
        isEnabled: Boolean,
        threshold: Float,
        knee: Float,
        compRatio: Float,
        attackMs: Float,
        releaseMs: Float,
        makeupGainDb: Float
    ) {
        enabled = isEnabled
        thresholdDb = threshold
        kneeDb = knee
        ratio = compRatio
        makeupGainLinear = 10.0.pow(makeupGainDb / 20.0)
        attackCoeff = exp(-1.0 / ((attackMs.coerceAtLeast(0.1f) / 1000.0) * sampleRate))
        releaseCoeff = exp(-1.0 / ((releaseMs.coerceAtLeast(1f) / 1000.0) * sampleRate))
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
 * 4-Stage "iPhone Speaker DSP" Chain tailored for mobile micro-drivers:
 * 1. 110Hz Steep High-Pass Filter (4th-order Butterworth, 24 dB/octave)
 * 2. Psychoacoustic Virtual Bass (120Hz-250Hz harmonic exciter/waveshaper)
 * 3. Boxiness & Resonance Correction (400Hz -3dB narrow Q, 2kHz +2dB, 3.5kHz -1.5dB, 10kHz +2.5dB)
 * 4. Smart Speaker Limiter (-6 dB threshold, 12:1 ratio, 1ms attack, 80ms release, +3 dB makeup)
 */
class PhoneSpeakerDspChain(sampleRate: Int = 44100) {

    // Stage 1: Cascaded 4th-order Butterworth HPF @ 110Hz (24 dB/oct)
    private val hpfStage1 = StereoBiquad().apply { setHighPass(sampleRate, 110.0, 0.5411961) }
    private val hpfStage2 = StereoBiquad().apply { setHighPass(sampleRate, 110.0, 1.3065630) }

    // Stage 2: Psychoacoustic Virtual Bass (extract 40-110Hz -> waveshape -> bandpass 120-250Hz)
    private val subExtractor = StereoBiquad().apply { setBandPass(sampleRate, 72.0, 0.95) }
    private val harmonicBandPass = StereoBiquad().apply { setBandPass(sampleRate, 180.0, 1.25) }

    // Stage 3: Boxiness & Tinny Resonance Removal
    private val boxinessDip400Hz = StereoBiquad().apply { setPeaking(sampleRate, 400.0, 3.2, -3.0) }
    private val vocalClarity2kHz = StereoBiquad().apply { setPeaking(sampleRate, 2000.0, 1.2, 2.0) }
    private val screechTame3500Hz = StereoBiquad().apply { setPeaking(sampleRate, 3500.0, 2.0, -1.5) }
    private val crispAir10kHz = StereoBiquad().apply { setPeaking(sampleRate, 10000.0, 1.0, 2.5) }

    // Stage 4: iPhone-Style Smart Speaker Limiter
    private val smartLimiter = DynamicRangeCompressor(sampleRate).apply {
        configureCustom(
            isEnabled = true,
            threshold = -6f,
            knee = 4f,
            compRatio = 12f,
            attackMs = 1f,
            releaseMs = 80f,
            makeupGainDb = 3f
        )
    }

    @Volatile
    var speakerDspEnabled: Boolean = false

    @Volatile
    var virtualBassEnabled: Boolean = false

    val latestLimiterReductionDb: Float
        get() = if (speakerDspEnabled) smartLimiter.latestGainReductionDb else 0f

    fun configure(isSpeakerDspEnabled: Boolean, isVirtualBassEnabled: Boolean) {
        speakerDspEnabled = isSpeakerDspEnabled
        virtualBassEnabled = isVirtualBassEnabled
        smartLimiter.configureCustom(
            isEnabled = isSpeakerDspEnabled,
            threshold = -6f,
            knee = 4f,
            compRatio = 12f,
            attackMs = 1f,
            releaseMs = 80f,
            makeupGainDb = 3f
        )
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (!speakerDspEnabled) return inL to inR

        // Stage 2 extraction happens pre-HPF so sub-bass energy below 110Hz feeds harmonic synthesis
        val (harmL, harmR) = if (virtualBassEnabled) {
            val (subL, subR) = subExtractor.processStereo(inL, inR)
            val shapedL = tanh(subL * 3.2) + 0.35 * (subL * subL)
            val shapedR = tanh(subR * 3.2) + 0.35 * (subR * subR)
            harmonicBandPass.processStereo(shapedL, shapedR)
        } else {
            0.0 to 0.0
        }

        // Stage 1: 24 dB/octave steep high-pass at 110Hz
        val (hp1L, hp1R) = hpfStage1.processStereo(inL, inR)
        val (hp2L, hp2R) = hpfStage2.processStereo(hp1L, hp1R)

        // Blend synthesized 120Hz-250Hz virtual bass harmonics back into protected signal
        var l = hp2L + harmL * 0.48
        var r = hp2R + harmR * 0.48

        // Stage 3: Boxiness & tinny resonance shaping
        val (bL, bR) = boxinessDip400Hz.processStereo(l, r)
        val (vL, vR) = vocalClarity2kHz.processStereo(bL, bR)
        val (sL, sR) = screechTame3500Hz.processStereo(vL, vR)
        val (aL, aR) = crispAir10kHz.processStereo(sL, sR)
        l = aL
        r = aR

        // Stage 4: Smart Speaker Limiter (-6dB, 12:1, 1ms/80ms, +3dB makeup)
        return smartLimiter.processStereoSample(l, r)
    }
}

/**
 * Cascaded 10-band stereo biquad peaking EQ filter + preamp + Late Night compressor +
 * Phone Speaker DSP chain + soft clip guard.
 */
class BiquadEqFilterBank(private val sampleRate: Int = 44100) {

    private val bandFilters = Array(10) { StereoBiquad() }
    private val lateNightCompressor = DynamicRangeCompressor(sampleRate)
    private val speakerDspChain = PhoneSpeakerDspChain(sampleRate)
    private val spatialAudioEngine = SpatialAudioEngine(sampleRate)
    private val soundCheckNormalizer = SoundCheckNormalizer(sampleRate)
    private val voiceIsolationProcessor = VoiceIsolationProcessor(sampleRate)

    @Volatile
    private var preampLinear: Double = 1.0

    @Volatile
    private var bypassed: Boolean = false

    @Volatile
    private var abCompareHeld: Boolean = false

    @Volatile
    private var abLoudnessMatchLinear: Double = 1.0

    @Volatile
    var conversationDuckLinear: Double = 1.0

    val latestGainReductionDb: Float
        get() = when {
            bypassed || abCompareHeld -> 0f
            lateNightCompressor.latestGainReductionDb < -0.05f || speakerDspChain.latestLimiterReductionDb < -0.05f -> {
                minOf(lateNightCompressor.latestGainReductionDb, speakerDspChain.latestLimiterReductionDb)
            }
            else -> 0f
        }

    val latestInputLufs: Float
        get() = soundCheckNormalizer.latestLufs

    val latestSoundCheckTrimDb: Float
        get() = if (bypassed || abCompareHeld) 0f else soundCheckNormalizer.latestTrimDb

    val latestVoiceGateDb: Float
        get() = if (bypassed || abCompareHeld) 0f else voiceIsolationProcessor.latestGateAttenuationDb

    init {
        updateParameters(
            gainsDb = List(10) { 0f },
            preampDb = 0f,
            isBypassed = false,
            lateNightEnabled = false,
            nightIntensity = NightIntensity.STANDARD,
            speakerDspEnabled = false,
            virtualBassEnabled = false,
            outputTarget = OutputTarget.HEADPHONES,
            spatialMode = SpatialMode.OFF,
            spatialDepth = SpatialMode.ATMOS_MUSIC.defaultDepth,
            heightPresence = SpatialMode.ATMOS_MUSIC.defaultHeight,
            vocalAnchor = true,
            soundCheckEnabled = false,
            voiceIsolationEnabled = false,
            abCompareHeld = false
        )
    }

    fun updateParameters(
        gainsDb: List<Float>,
        preampDb: Float,
        isBypassed: Boolean,
        lateNightEnabled: Boolean = false,
        nightIntensity: NightIntensity = NightIntensity.STANDARD,
        speakerDspEnabled: Boolean = false,
        virtualBassEnabled: Boolean = false,
        outputTarget: OutputTarget = OutputTarget.HEADPHONES,
        spatialMode: SpatialMode = SpatialMode.OFF,
        spatialDepth: Float = SpatialMode.ATMOS_MUSIC.defaultDepth,
        heightPresence: Float = SpatialMode.ATMOS_MUSIC.defaultHeight,
        vocalAnchor: Boolean = true,
        soundCheckEnabled: Boolean = false,
        voiceIsolationEnabled: Boolean = false,
        abCompareHeld: Boolean = false
    ) {
        bypassed = isBypassed
        this.abCompareHeld = abCompareHeld
        preampLinear = 10.0.pow(preampDb.toDouble() / 20.0)

        val effectiveProcessing = !isBypassed && !abCompareHeld
        soundCheckNormalizer.enabled = soundCheckEnabled && effectiveProcessing
        voiceIsolationProcessor.enabled = voiceIsolationEnabled && effectiveProcessing
        lateNightCompressor.configure(isEnabled = lateNightEnabled && effectiveProcessing, intensity = nightIntensity)
        speakerDspChain.configure(
            isSpeakerDspEnabled = speakerDspEnabled && effectiveProcessing,
            isVirtualBassEnabled = virtualBassEnabled && effectiveProcessing
        )
        spatialAudioEngine.configure(
            mode = if (effectiveProcessing) spatialMode else SpatialMode.OFF,
            target = outputTarget,
            spatialDepth = spatialDepth,
            heightPresence = heightPresence,
            vocalAnchor = vocalAnchor
        )

        val matchDb = AudiogramCheckEngine.computeLoudnessMatchGainDb(
            bandGainsDb = gainsDb,
            preampDb = preampDb,
            lateNightEnabled = lateNightEnabled,
            nightIntensity = nightIntensity,
            target = outputTarget,
            speakerDspEnabled = speakerDspEnabled,
            spatialMode = spatialMode
        )
        abLoudnessMatchLinear = 10.0.pow(matchDb.toDouble() / 20.0)

        for (i in 0 until 10) {
            val freq = EqPresetsCatalog.BANDS[i].frequencyHz.toDouble()
            val db = gainsDb.getOrElse(i) { 0f }.toDouble()
            bandFilters[i].setPeaking(sampleRate, freq, 1.414, db)
        }
    }

    fun processStereoSample(inL: Double, inR: Double): Pair<Double, Double> {
        val duck = conversationDuckLinear.coerceIn(0.15, 1.0)
        if (bypassed) {
            return (inL * duck) to (inR * duck)
        }
        // Sound Check always monitors input LUFS; when A/B Compare is held, switch cleanly to loudness-matched original
        val (scL, scR) = soundCheckNormalizer.processStereoSample(inL, inR)
        if (abCompareHeld) {
            val abL = tanh(inL * abLoudnessMatchLinear * duck * 0.92)
            val abR = tanh(inR * abLoudnessMatchLinear * duck * 0.92)
            return abL to abR
        }

        var l = scL * preampLinear
        var r = scR * preampLinear

        for (band in 0 until 10) {
            val (nextL, nextR) = bandFilters[band].processStereo(l, r)
            l = nextL
            r = nextR
        }

        val (isoL, isoR) = voiceIsolationProcessor.processStereoSample(l, r)
        val (compL, compR) = lateNightCompressor.processStereoSample(isoL, isoR)
        val (spatL, spatR) = spatialAudioEngine.processStereoSample(compL, compR)
        val (spkL, spkR) = speakerDspChain.processStereoSample(spatL, spatR)

        // Final safety ceiling + Conversation Awareness auto-ducking
        return tanh(spkL * duck * 0.92) to tanh(spkR * duck * 0.92)
    }
}

/**
 * Built-in real-time audio synthesizer so users can audition and visualize
 * Speaker DSP, Headphones, Bluetooth EQ tuning, Spatial Atmos, Late Night compression,
 * Sound Check, Voice Isolation & Background Sounds underneath immediately.
 */
class TestAudioSynthesizer {
    private val sampleRate = 44100
    private val filterBank = BiquadEqFilterBank(sampleRate)
    private val backgroundSynth = BackgroundSoundsSynthesizer(sampleRate)
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

    @Volatile
    private var backgroundPreset: BackgroundSoundPreset = BackgroundSoundPreset.OFF

    @Volatile
    private var backgroundVolume: Float = 0.28f

    val latestInputLufs: Float
        get() = filterBank.latestInputLufs

    val latestSoundCheckTrimDb: Float
        get() = filterBank.latestSoundCheckTrimDb

    val latestVoiceGateDb: Float
        get() = filterBank.latestVoiceGateDb

    fun setConversationDuckGain(duckGainLinear: Float) {
        filterBank.conversationDuckLinear = duckGainLinear.toDouble()
    }

    fun updateEq(
        gainsDb: List<Float>,
        preampDb: Float,
        isBypassed: Boolean,
        lateNightEnabled: Boolean,
        nightIntensity: NightIntensity,
        speakerDspEnabled: Boolean,
        virtualBassEnabled: Boolean,
        outputTarget: OutputTarget,
        spatialMode: SpatialMode,
        spatialDepth: Float,
        heightPresence: Float,
        vocalAnchor: Boolean,
        soundCheckEnabled: Boolean = false,
        voiceIsolationEnabled: Boolean = false,
        abCompareHeld: Boolean = false,
        bgSoundPreset: BackgroundSoundPreset = BackgroundSoundPreset.OFF,
        bgSoundVolume: Float = 0.28f
    ) {
        backgroundPreset = bgSoundPreset
        backgroundVolume = bgSoundVolume
        filterBank.updateParameters(
            gainsDb = gainsDb,
            preampDb = preampDb,
            isBypassed = isBypassed,
            lateNightEnabled = lateNightEnabled,
            nightIntensity = nightIntensity,
            speakerDspEnabled = speakerDspEnabled,
            virtualBassEnabled = virtualBassEnabled,
            outputTarget = outputTarget,
            spatialMode = spatialMode,
            spatialDepth = spatialDepth,
            heightPresence = heightPresence,
            vocalAnchor = vocalAnchor,
            soundCheckEnabled = soundCheckEnabled,
            voiceIsolationEnabled = voiceIsolationEnabled,
            abCompareHeld = abCompareHeld
        )
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

            val pinkRows = DoubleArray(16)
            var pinkRunningSum = 0.0
            var pinkCounter = 0

            while (isActive) {
                var peak = 0.0
                for (i in 0 until frameCount) {
                    val t = sampleCursor.toDouble() / sampleRate
                    val (rawL, rawR) = when (source) {
                        TestAudioSource.AMBIENT_CHORD -> {
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
                    val (bgL, bgR) = backgroundSynth.nextStereoSample(backgroundPreset, backgroundVolume)
                    val mixedL = procL + bgL * filterBank.conversationDuckLinear
                    val mixedR = procR + bgR * filterBank.conversationDuckLinear
                    val clampedL = mixedL.coerceIn(-1.0, 1.0)
                    val clampedR = mixedR.coerceIn(-1.0, 1.0)
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

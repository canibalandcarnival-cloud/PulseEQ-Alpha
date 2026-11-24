package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import com.example.model.ListeningMode
import com.example.model.ListeningModeSpecs
import com.example.model.OutputTarget
import com.example.model.StudioHiResSpecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * iOS 27 Listening Mode DSP Engine:
 * 1. Off: Pure direct playback, zero ambient mic pass-through, neutral reference curve.
 * 2. Transparency: Live low-latency ambient mic pass-through (220 Hz HPF wind/rumble cut + 2.5 kHz +1.5 dB vocal presence)
 *    mixed cleanly underneath music with adjustable 0%–100% Ambient Pass-Through Level.
 * 3. Adaptive (iOS 27 Smart Blend): Monitors real-time ambient RMS & <400 Hz low-frequency rumble:
 *    - Quiet environments: blends subtle 25% ambient awareness.
 *    - Loud rumble/noise spikes (<400 Hz): ducks ambient pass-through down toward ~3% and dynamically engages
 *      +2.0 dB low-end warmth compensation at 120 Hz.
 * 4. Noise Cancellation (Active Isolation Focus): Strictly mutes all mic pass-through (0%) and engages
 *    an Isolation Focus Curve (110 Hz +2.0 dB, 320 Hz -2.0 dB, 2.2 kHz +1.5 dB).
 * 5. Safe Feedback Protection: Hard-mutes all live mic pass-through (0.0) whenever OutputTarget.SPEAKER is active
 *    so the microphone never picks up the phone speaker and causes feedback howling, while automatically unlocking
 *    pass-through on Headphones / Bluetooth.
 */
class ListeningModeProcessor(private val sampleRate: Int = StudioHiResSpecs.SAMPLE_RATE_HZ) {

    // Ambient Mic Pass-Through Shaping (220 Hz HPF + 2.5 kHz +1.5 dB vocal presence + optional +2 dB speech clarity)
    private val ambientHpf220 = StereoBiquad().apply {
        setHighPass(sampleRate, ListeningModeSpecs.TRANSPARENCY_HPF_HZ, ListeningModeSpecs.TRANSPARENCY_HPF_Q)
    }
    private val ambientVocalPresence2500 = StereoBiquad().apply {
        setPeaking(
            sampleRate,
            ListeningModeSpecs.TRANSPARENCY_VOCAL_HZ,
            ListeningModeSpecs.TRANSPARENCY_VOCAL_Q,
            ListeningModeSpecs.TRANSPARENCY_VOCAL_DB
        )
    }

    // Adaptive <400 Hz Rumble Sidechain & 120 Hz +2.0 dB Warmth Compensation
    private val rumbleDetectorLpf400 = StereoBiquad().apply {
        setLowPass(sampleRate, ListeningModeSpecs.ADAPTIVE_RUMBLE_LPF_HZ, 0.7071)
    }
    private val adaptiveWarmth120 = StereoBiquad().apply {
        setPeaking(
            sampleRate,
            ListeningModeSpecs.ADAPTIVE_WARMTH_HZ,
            ListeningModeSpecs.ADAPTIVE_WARMTH_Q,
            0.0
        )
    }

    // Noise Cancellation: Isolation Focus Curve (110 Hz +2.0 dB, 320 Hz -2.0 dB, 2.2 kHz +1.5 dB)
    private val ancWarmth110 = StereoBiquad().apply {
        setPeaking(
            sampleRate,
            ListeningModeSpecs.ANC_WARMTH_HZ,
            ListeningModeSpecs.ANC_WARMTH_Q,
            ListeningModeSpecs.ANC_WARMTH_DB
        )
    }
    private val ancMudCut320 = StereoBiquad().apply {
        setPeaking(
            sampleRate,
            ListeningModeSpecs.ANC_MUD_CUT_HZ,
            ListeningModeSpecs.ANC_MUD_CUT_Q,
            ListeningModeSpecs.ANC_MUD_CUT_DB
        )
    }
    private val ancVocal2200 = StereoBiquad().apply {
        setPeaking(
            sampleRate,
            ListeningModeSpecs.ANC_VOCAL_HZ,
            ListeningModeSpecs.ANC_VOCAL_Q,
            ListeningModeSpecs.ANC_VOCAL_DB
        )
    }

    private val rmsAttackAlpha = exp(-1.0 / (0.025 * sampleRate))
    private val rmsReleaseAlpha = exp(-1.0 / (0.280 * sampleRate))
    private val blendAttackAlpha = exp(-1.0 / (0.035 * sampleRate))
    private val blendReleaseAlpha = exp(-1.0 / (0.350 * sampleRate))
    private val transparencyRampAlpha = exp(-1.0 / (0.030 * sampleRate))

    private var ambientPowerEnv = 1e-8
    private var rumblePowerEnv = 1e-8
    private var smoothedPassThroughGain = 0.0
    private var smoothedAdaptiveWarmthRatio = 0.0
    private var warmthUpdateCounter = 0

    @Volatile
    var mode: ListeningMode = ListeningMode.OFF
        private set

    @Volatile
    var outputTarget: OutputTarget = OutputTarget.SPEAKER
        private set

    @Volatile
    var isBypassed: Boolean = false
        private set

    @Volatile
    var ambientPassThroughPercent: Float = 0.65f
        private set

    @Volatile
    var conversationSpeechActive: Boolean = false
        private set

    @Volatile
    var latestPassThroughGain: Float = 0f
        private set

    @Volatile
    var latestAmbientRmsDb: Float = -60f
        private set

    @Volatile
    var latestRumbleRmsDb: Float = -60f
        private set

    @Volatile
    var isAdaptiveRumbleDuckActive: Boolean = false
        private set

    @Volatile
    var latestAdaptiveWarmthRatio: Float = 0f
        private set

    val isFeedbackProtectionEngaged: Boolean
        get() = outputTarget == OutputTarget.SPEAKER &&
            (mode == ListeningMode.TRANSPARENCY || mode == ListeningMode.ADAPTIVE || conversationSpeechActive)

    fun reset() {
        ambientHpf220.reset()
        ambientHpf220.setHighPass(sampleRate, ListeningModeSpecs.TRANSPARENCY_HPF_HZ, ListeningModeSpecs.TRANSPARENCY_HPF_Q)
        ambientVocalPresence2500.reset()
        updateAmbientVocalFilter()
        rumbleDetectorLpf400.reset()
        rumbleDetectorLpf400.setLowPass(sampleRate, ListeningModeSpecs.ADAPTIVE_RUMBLE_LPF_HZ, 0.7071)
        adaptiveWarmth120.reset()
        ambientPowerEnv = 1e-8
        rumblePowerEnv = 1e-8
        smoothedPassThroughGain = 0.0
        smoothedAdaptiveWarmthRatio = 0.0
        latestPassThroughGain = 0f
        latestAmbientRmsDb = -60f
        latestRumbleRmsDb = -60f
        isAdaptiveRumbleDuckActive = false
        latestAdaptiveWarmthRatio = 0f
    }

    fun configure(
        mode: ListeningMode,
        outputTarget: OutputTarget,
        isBypassed: Boolean,
        ambientPassThroughPercent: Float,
        conversationSpeechActive: Boolean = false
    ) {
        val speechChanged = this.conversationSpeechActive != conversationSpeechActive
        this.mode = mode
        this.outputTarget = outputTarget
        this.isBypassed = isBypassed
        this.ambientPassThroughPercent = ambientPassThroughPercent.coerceIn(0f, 1f)
        this.conversationSpeechActive = conversationSpeechActive

        if (speechChanged) {
            updateAmbientVocalFilter()
        }

        if (isBypassed || mode == ListeningMode.OFF || outputTarget == OutputTarget.SPEAKER) {
            if (!conversationSpeechActive || outputTarget == OutputTarget.SPEAKER || isBypassed) {
                smoothedPassThroughGain = 0.0
                latestPassThroughGain = 0f
            }
            if (mode != ListeningMode.ADAPTIVE || isBypassed) {
                smoothedAdaptiveWarmthRatio = 0.0
                latestAdaptiveWarmthRatio = 0f
                isAdaptiveRumbleDuckActive = false
                adaptiveWarmth120.setPeaking(sampleRate, ListeningModeSpecs.ADAPTIVE_WARMTH_HZ, ListeningModeSpecs.ADAPTIVE_WARMTH_Q, 0.0)
            }
        } else if (mode == ListeningMode.NOISE_CANCELLATION && !conversationSpeechActive) {
            smoothedPassThroughGain = 0.0
            latestPassThroughGain = 0f
            smoothedAdaptiveWarmthRatio = 0.0
            latestAdaptiveWarmthRatio = 0f
            isAdaptiveRumbleDuckActive = false
        }
    }

    private fun updateAmbientVocalFilter() {
        val vocalBoostDb = ListeningModeSpecs.TRANSPARENCY_VOCAL_DB +
            if (conversationSpeechActive) ListeningModeSpecs.CONVERSATION_VOICE_CLARITY_EXTRA_DB else 0.0
        ambientVocalPresence2500.setPeaking(
            sampleRate,
            ListeningModeSpecs.TRANSPARENCY_VOCAL_HZ,
            ListeningModeSpecs.TRANSPARENCY_VOCAL_Q,
            vocalBoostDb
        )
    }

    /**
     * Shapes an incoming mono ambient microphone sample through the 220 Hz HPF + 2.5 kHz vocal clarity
     * chain, updates Adaptive <400 Hz noise/rumble telemetry, and returns the scaled mono sample
     * (strictly 0.0 whenever Safe Feedback Protection or ANC/Off muting applies).
     */
    fun processAmbientMicSample(rawMicSample: Double): Double {
        // Track broadband ambient energy & <400 Hz rumble envelope continuously
        val instPower = rawMicSample * rawMicSample
        val ampAlpha = if (instPower > ambientPowerEnv) rmsAttackAlpha else rmsReleaseAlpha
        ambientPowerEnv = ampAlpha * ambientPowerEnv + (1.0 - ampAlpha) * instPower
        val ambDb = if (ambientPowerEnv > 1e-9) {
            (10.0 * log10(ambientPowerEnv)).coerceIn(-60.0, 0.0)
        } else {
            -60.0
        }
        latestAmbientRmsDb = ambDb.toFloat()

        val (rumbleSample, _) = rumbleDetectorLpf400.processStereo(rawMicSample, 0.0)
        val rumblePower = rumbleSample * rumbleSample
        val rAlpha = if (rumblePower > rumblePowerEnv) rmsAttackAlpha else rmsReleaseAlpha
        rumblePowerEnv = rAlpha * rumblePowerEnv + (1.0 - rAlpha) * rumblePower
        val rumbleDb = if (rumblePowerEnv > 1e-9) {
            (10.0 * log10(rumblePowerEnv)).coerceIn(-60.0, 0.0)
        } else {
            -60.0
        }
        latestRumbleRmsDb = rumbleDb.toFloat()

        // Shape ambient mic signal cleanly: 220 Hz HPF wind/rumble cut -> 2.5 kHz +1.5 dB vocal presence
        val (hp, _) = ambientHpf220.processStereo(rawMicSample, 0.0)
        val (shapedMic, _) = ambientVocalPresence2500.processStereo(hp, 0.0)

        // Compute Adaptive <400 Hz rumble / ambient noise spike ducking & +120 Hz warmth ratio
        val drivingNoiseDb = maxOf(ambDb, rumbleDb + 1.5)
        val adaptiveSpikeFactor = if (mode == ListeningMode.ADAPTIVE && !isBypassed) {
            ((drivingNoiseDb - ListeningModeSpecs.ADAPTIVE_QUIET_THRESHOLD_DB) /
                (ListeningModeSpecs.ADAPTIVE_SPIKE_THRESHOLD_DB - ListeningModeSpecs.ADAPTIVE_QUIET_THRESHOLD_DB))
                .coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val wAlpha = if (adaptiveSpikeFactor > smoothedAdaptiveWarmthRatio) blendAttackAlpha else blendReleaseAlpha
        smoothedAdaptiveWarmthRatio = wAlpha * smoothedAdaptiveWarmthRatio + (1.0 - wAlpha) * adaptiveSpikeFactor
        latestAdaptiveWarmthRatio = smoothedAdaptiveWarmthRatio.toFloat()
        isAdaptiveRumbleDuckActive = mode == ListeningMode.ADAPTIVE && !isBypassed && smoothedAdaptiveWarmthRatio > 0.22

        if (mode == ListeningMode.ADAPTIVE && !isBypassed && (warmthUpdateCounter++ and 0x1F) == 0) {
            adaptiveWarmth120.setPeaking(
                sampleRate,
                ListeningModeSpecs.ADAPTIVE_WARMTH_HZ,
                ListeningModeSpecs.ADAPTIVE_WARMTH_Q,
                ListeningModeSpecs.ADAPTIVE_WARMTH_DB * smoothedAdaptiveWarmthRatio
            )
        }

        // Strict Safe Feedback Protection: mute live mic pass-through to 0.0 on Phone Speaker
        val canPassLiveMic = outputTarget != OutputTarget.SPEAKER && !isBypassed
        val targetPassThroughGain = when {
            !canPassLiveMic -> 0.0
            conversationSpeechActive && mode == ListeningMode.NOISE_CANCELLATION ->
                ListeningModeSpecs.CONVERSATION_EXTERNAL_VOICE_GAIN.toDouble()
            conversationSpeechActive && mode == ListeningMode.ADAPTIVE ->
                maxOf(
                    ListeningModeSpecs.ADAPTIVE_QUIET_PASS_THROUGH.toDouble(),
                    ListeningModeSpecs.CONVERSATION_EXTERNAL_VOICE_GAIN.toDouble()
                )
            conversationSpeechActive && mode == ListeningMode.TRANSPARENCY ->
                maxOf(
                    ambientPassThroughPercent.toDouble(),
                    ListeningModeSpecs.CONVERSATION_EXTERNAL_VOICE_GAIN.toDouble()
                )
            mode == ListeningMode.TRANSPARENCY -> ambientPassThroughPercent.toDouble()
            mode == ListeningMode.ADAPTIVE -> {
                val quiet = ListeningModeSpecs.ADAPTIVE_QUIET_PASS_THROUGH.toDouble()
                val ducked = ListeningModeSpecs.ADAPTIVE_DUCKED_PASS_THROUGH.toDouble()
                quiet + (ducked - quiet) * smoothedAdaptiveWarmthRatio
            }
            else -> 0.0
        }

        val gAlpha = when {
            mode == ListeningMode.TRANSPARENCY || conversationSpeechActive -> transparencyRampAlpha
            targetPassThroughGain < smoothedPassThroughGain -> blendAttackAlpha
            else -> blendReleaseAlpha
        }
        smoothedPassThroughGain = if (!canPassLiveMic) {
            0.0
        } else {
            gAlpha * smoothedPassThroughGain + (1.0 - gAlpha) * targetPassThroughGain
        }
        latestPassThroughGain = smoothedPassThroughGain.toFloat()

        return if (!canPassLiveMic || smoothedPassThroughGain <= 1e-5) {
            0.0
        } else {
            shapedMic * smoothedPassThroughGain
        }
    }

    /**
     * Applies the music-path EQ curve for the active Listening Mode:
     * - Off / Transparency: bit-transparent (0 dB).
     * - Adaptive: dynamic +2.0 dB warmth compensation at 120 Hz during loud rumble spikes.
     * - Noise Cancellation: Isolation Focus Curve (110 Hz +2.0 dB, 320 Hz -2.0 dB, 2.2 kHz +1.5 dB).
     */
    fun processMusicSample(inL: Double, inR: Double): Pair<Double, Double> {
        if (isBypassed) return inL to inR
        return when (mode) {
            ListeningMode.OFF,
            ListeningMode.TRANSPARENCY -> inL to inR

            ListeningMode.ADAPTIVE -> {
                if (smoothedAdaptiveWarmthRatio <= 0.01) {
                    inL to inR
                } else {
                    adaptiveWarmth120.processStereo(inL, inR)
                }
            }

            ListeningMode.NOISE_CANCELLATION -> {
                val (wL, wR) = ancWarmth110.processStereo(inL, inR)
                val (mL, mR) = ancMudCut320.processStereo(wL, wR)
                ancVocal2200.processStereo(mL, mR)
            }
        }
    }

    /**
     * Combined processing step for unit tests & integrated playback paths mixing music with
     * shaped ambient microphone audio safely.
     */
    fun processStereoSampleWithAmbient(
        musicL: Double,
        musicR: Double,
        rawMicSample: Double
    ): Pair<Double, Double> {
        val ambientOut = processAmbientMicSample(rawMicSample)
        val (shapedL, shapedR) = processMusicSample(musicL, musicR)
        return (shapedL + ambientOut) to (shapedR + ambientOut)
    }

    companion object {
        fun isFeedbackProtectionRequired(target: OutputTarget): Boolean {
            return target == OutputTarget.SPEAKER
        }

        /**
         * Frequency response helper for Android DynamicsProcessing PreEq & Visualizer EQ preview.
         */
        fun computeMusicResponseDbAt(
            freqHz: Double,
            mode: ListeningMode,
            adaptiveWarmthRatio: Float = 0f,
            sampleRate: Int = StudioHiResSpecs.SAMPLE_RATE_HZ
        ): Float {
            return when (mode) {
                ListeningMode.OFF,
                ListeningMode.TRANSPARENCY -> 0f

                ListeningMode.ADAPTIVE -> {
                    val r = adaptiveWarmthRatio.coerceIn(0f, 1f).toDouble()
                    if (r <= 0.01) return 0f
                    StereoBiquad().apply {
                        setPeaking(
                            sampleRate,
                            ListeningModeSpecs.ADAPTIVE_WARMTH_HZ,
                            ListeningModeSpecs.ADAPTIVE_WARMTH_Q,
                            ListeningModeSpecs.ADAPTIVE_WARMTH_DB * r
                        )
                    }.magnitudeDbAt(sampleRate, freqHz).toFloat()
                }

                ListeningMode.NOISE_CANCELLATION -> {
                    val b1 = StereoBiquad().apply {
                        setPeaking(
                            sampleRate,
                            ListeningModeSpecs.ANC_WARMTH_HZ,
                            ListeningModeSpecs.ANC_WARMTH_Q,
                            ListeningModeSpecs.ANC_WARMTH_DB
                        )
                    }
                    val b2 = StereoBiquad().apply {
                        setPeaking(
                            sampleRate,
                            ListeningModeSpecs.ANC_MUD_CUT_HZ,
                            ListeningModeSpecs.ANC_MUD_CUT_Q,
                            ListeningModeSpecs.ANC_MUD_CUT_DB
                        )
                    }
                    val b3 = StereoBiquad().apply {
                        setPeaking(
                            sampleRate,
                            ListeningModeSpecs.ANC_VOCAL_HZ,
                            ListeningModeSpecs.ANC_VOCAL_Q,
                            ListeningModeSpecs.ANC_VOCAL_DB
                        )
                    }
                    (b1.magnitudeDbAt(sampleRate, freqHz) +
                        b2.magnitudeDbAt(sampleRate, freqHz) +
                        b3.magnitudeDbAt(sampleRate, freqHz)).toFloat()
                }
            }
        }
    }
}

/**
 * Hardware Low-Latency Live Microphone Ambient Pass-Through & Unified Conversation Awareness Monitor.
 * - Uses UnprocessedMicFactory (48 kHz, hardware AEC/NS/AGC explicitly disabled) for studio-grade transparency.
 * - Strictly enforces Safe Feedback Protection: whenever OutputTarget.SPEAKER is selected, live AudioTrack
 *   pass-through playback is completely stopped and muted so the mic never feeds back into the speaker.
 * - Automatically unlocks low-latency AudioTrack pass-through when OutputTarget.HEADPHONES or OutputTarget.BLUETOOTH
 *   is active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveAmbientPassThroughMonitor(
    private val onTelemetryUpdate: (
        passThroughGain: Float,
        ambientNoiseRmsDb: Float,
        ambientRumbleRmsDb: Float,
        adaptiveRumbleDuckActive: Boolean,
        isRunning: Boolean
    ) -> Unit,
    private val onConversationDuckUpdate: (speechDetected: Boolean, duckGainLinear: Float) -> Unit
) {
    private val hwDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val hwScope = CoroutineScope(SupervisorJob() + hwDispatcher)
    private val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reconcileSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var monitorJob: Job? = null

    @Volatile
    private var activeCapture: UnprocessedMicFactory.OpenMicCapture? = null

    @Volatile
    private var activeSampleRate: Int = StudioHiResSpecs.SAMPLE_RATE_HZ

    private var audioTrack: AudioTrack? = null

    @Volatile
    private var mode: ListeningMode = ListeningMode.OFF

    @Volatile
    private var outputTarget: OutputTarget = OutputTarget.SPEAKER

    @Volatile
    private var isBypassed: Boolean = false

    @Volatile
    private var ambientPassThroughPercent: Float = 0.65f

    @Volatile
    private var conversationAwarenessEnabled: Boolean = false

    @Volatile
    private var hasRecordPermission: Boolean = false

    @Volatile
    private var speechDetected: Boolean = false

    @Volatile
    private var currentDuckGain: Float = 1f

    @Volatile
    private var shouldHandleConversationMic: Boolean = false

    val handlesConversationMic: Boolean
        get() = shouldHandleConversationMic

    init {
        hwScope.launch {
            for (signal in reconcileSignal) {
                reconcileHardwareState()
            }
        }
    }

    fun configure(
        mode: ListeningMode,
        outputTarget: OutputTarget,
        isBypassed: Boolean,
        ambientPassThroughPercent: Float,
        conversationAwarenessEnabled: Boolean,
        hasRecordPermission: Boolean
    ) {
        this.mode = mode
        this.outputTarget = outputTarget
        this.isBypassed = isBypassed
        this.ambientPassThroughPercent = ambientPassThroughPercent.coerceIn(0f, 1f)
        this.conversationAwarenessEnabled = conversationAwarenessEnabled
        this.hasRecordPermission = hasRecordPermission

        val needsUnifiedMic = hasRecordPermission && !isBypassed &&
            (mode == ListeningMode.TRANSPARENCY ||
                mode == ListeningMode.ADAPTIVE ||
                (conversationAwarenessEnabled && outputTarget != OutputTarget.SPEAKER))
        shouldHandleConversationMic = needsUnifiedMic && conversationAwarenessEnabled

        if (!conversationAwarenessEnabled) {
            speechDetected = false
            if (currentDuckGain != 1f) {
                currentDuckGain = 1f
                onConversationDuckUpdate(false, 1f)
            }
        }

        reconcileSignal.trySend(Unit)
    }

    @SuppressLint("MissingPermission")
    private suspend fun reconcileHardwareState() {
        val needsUnifiedMic = hasRecordPermission && !isBypassed &&
            (mode == ListeningMode.TRANSPARENCY ||
                mode == ListeningMode.ADAPTIVE ||
                (conversationAwarenessEnabled && outputTarget != OutputTarget.SPEAKER))

        if (!needsUnifiedMic) {
            stopBlocking(emitZeroTelemetry = true)
            return
        }

        val currentJob = monitorJob
        if (currentJob == null || !currentJob.isActive) {
            stopBlocking(emitZeroTelemetry = false)
            startCaptureLoop()
        } else {
            reconcilePlaybackTrack(activeSampleRate)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureLoop() {
        val capture = UnprocessedMicFactory.openRawMicCapture()
        if (capture == null) {
            onTelemetryUpdate(0f, -60f, -60f, false, false)
            return
        }

        activeCapture = capture
        val record = capture.audioRecord
        val sr = capture.sampleRate
        activeSampleRate = sr

        val localProcessor = ListeningModeProcessor(sr).apply {
            configure(
                mode = mode,
                outputTarget = outputTarget,
                isBypassed = isBypassed,
                ambientPassThroughPercent = ambientPassThroughPercent,
                conversationSpeechActive = conversationAwarenessEnabled && speechDetected
            )
        }
        val speechLowCut = StereoBiquad().apply { setHighPass(sr, 300.0, 0.707) }
        val speechHighCut = StereoBiquad().apply { setLowPass(sr, 3000.0, 0.707) }

        reconcilePlaybackTrack(sr)

        monitorJob = streamScope.launch {
            val frameSamples = (sr * 0.012).toInt().coerceAtLeast(256) // 12ms low-latency audio blocks
            val micBuf = ShortArray(frameSamples)
            val stereoOutBuf = ShortArray(frameSamples * 2)
            var lastVoiceTimestampMs = 0L
            var localSpeechActive = false
            var gain = 1f
            var lastReportedSpeech = false
            var lastReportedGain = 1f
            var lastTelemetryReportMs = 0L

            try {
                record.startRecording()
                while (isActive) {
                    val read = record.read(micBuf, 0, micBuf.size)
                    if (read > 0) {
                        val cfgMode = mode
                        val cfgTarget = outputTarget
                        val cfgBypass = isBypassed
                        val cfgPercent = ambientPassThroughPercent
                        val cfgConv = conversationAwarenessEnabled

                        var sumSpeechSq = 0.0
                        val allowAudioOut = cfgTarget != OutputTarget.SPEAKER &&
                            !cfgBypass &&
                            (cfgMode == ListeningMode.TRANSPARENCY ||
                                cfgMode == ListeningMode.ADAPTIVE ||
                                (cfgConv && localSpeechActive))

                        if (allowAudioOut && audioTrack == null) {
                            reconcilePlaybackTrack(sr, localSpeechActive)
                        } else if (!allowAudioOut && audioTrack != null) {
                            releasePlaybackTrack()
                        }

                        localProcessor.configure(
                            mode = cfgMode,
                            outputTarget = cfgTarget,
                            isBypassed = cfgBypass,
                            ambientPassThroughPercent = cfgPercent,
                            conversationSpeechActive = cfgConv && localSpeechActive
                        )

                        for (i in 0 until read) {
                            val norm = micBuf[i] / 32768.0
                            if (cfgConv) {
                                val (hp, _) = speechLowCut.processStereo(norm, 0.0)
                                val (bp, _) = speechHighCut.processStereo(hp, 0.0)
                                sumSpeechSq += bp * bp
                            }
                            val outSample = localProcessor.processAmbientMicSample(norm)
                            val pcm = if (allowAudioOut) {
                                (outSample.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
                            } else {
                                0
                            }
                            stereoOutBuf[i * 2] = pcm
                            stereoOutBuf[i * 2 + 1] = pcm
                        }

                        if (allowAudioOut) {
                            val track = audioTrack
                            if (track != null) {
                                try {
                                    track.write(stereoOutBuf, 0, read * 2)
                                } catch (_: Throwable) {
                                    releasePlaybackTrack()
                                }
                            }
                        }

                        if (cfgConv) {
                            val speechRms = sqrt(sumSpeechSq / read)
                            val speechDb = if (speechRms > 1e-6) 20.0 * log10(speechRms) else -90.0
                            val now = System.currentTimeMillis()
                            if (speechDb > -33.0) {
                                lastVoiceTimestampMs = now
                                localSpeechActive = true
                            } else if (
                                localSpeechActive &&
                                (now - lastVoiceTimestampMs) >= ListeningModeSpecs.CONVERSATION_RESTORE_HOLD_MS
                            ) {
                                localSpeechActive = false
                            }

                            val targetDuck = if (localSpeechActive) ListeningModeSpecs.CONVERSATION_DUCK_GAIN else 1.0f
                            val stepAlpha = if (targetDuck < gain) 0.25f else 0.08f
                            gain += (targetDuck - gain) * stepAlpha
                            if (abs(gain - targetDuck) < 0.005f) gain = targetDuck

                            speechDetected = localSpeechActive
                            currentDuckGain = gain

                            val speechFlipped = localSpeechActive != lastReportedSpeech
                            val gainMovedEnough = abs(gain - lastReportedGain) >= 0.02f ||
                                (gain == targetDuck && gain != lastReportedGain)
                            if (speechFlipped || gainMovedEnough) {
                                lastReportedSpeech = localSpeechActive
                                lastReportedGain = gain
                                onConversationDuckUpdate(localSpeechActive, gain)
                            }
                        }

                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastTelemetryReportMs >= 60L) {
                            lastTelemetryReportMs = nowMs
                            onTelemetryUpdate(
                                localProcessor.latestPassThroughGain,
                                localProcessor.latestAmbientRmsDb,
                                localProcessor.latestRumbleRmsDb,
                                localProcessor.isAdaptiveRumbleDuckActive,
                                true
                            )
                        }
                    } else {
                        delay(16)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                releasePlaybackTrack()
                capture.release()
                if (activeCapture === capture) {
                    activeCapture = null
                }
                onTelemetryUpdate(0f, -60f, -60f, false, false)
            }
        }
    }

    @Synchronized
    private fun reconcilePlaybackTrack(sampleRate: Int, speechActive: Boolean = speechDetected) {
        val allowPlayback = outputTarget != OutputTarget.SPEAKER &&
            !isBypassed &&
            (mode == ListeningMode.TRANSPARENCY ||
                mode == ListeningMode.ADAPTIVE ||
                (conversationAwarenessEnabled && speechActive))
        if (!allowPlayback) {
            releasePlaybackTrack()
            return
        }
        if (audioTrack != null) return

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()
        } catch (_: Throwable) {
            null
        } ?: return

        try {
            track.play()
            audioTrack = track
        } catch (_: Throwable) {
            try {
                track.release()
            } catch (_: Throwable) {
            }
        }
    }

    @Synchronized
    private fun releasePlaybackTrack() {
        val track = audioTrack ?: return
        audioTrack = null
        try {
            track.pause()
            track.flush()
            track.release()
        } catch (_: Throwable) {
        }
    }

    private suspend fun stopBlocking(emitZeroTelemetry: Boolean) {
        val job = monitorJob
        monitorJob = null
        try {
            activeCapture?.audioRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            job?.cancelAndJoin()
        } catch (_: Throwable) {
        }
        releasePlaybackTrack()
        activeCapture?.release()
        activeCapture = null
        if (emitZeroTelemetry) {
            onTelemetryUpdate(0f, -60f, -60f, false, false)
        }
    }

    fun stop() {
        mode = ListeningMode.OFF
        conversationAwarenessEnabled = false
        shouldHandleConversationMic = false
        speechDetected = false
        if (currentDuckGain != 1f) {
            currentDuckGain = 1f
            onConversationDuckUpdate(false, 1f)
        }
        reconcileSignal.trySend(Unit)
    }
}

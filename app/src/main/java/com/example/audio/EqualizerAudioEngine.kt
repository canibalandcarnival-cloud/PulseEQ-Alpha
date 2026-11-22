package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.AudioSessionRegistry
import com.example.model.ConnectedDeviceState
import com.example.model.EqPresetsCatalog
import com.example.model.NightIntensity
import com.example.model.OutputTarget
import com.example.model.TestAudioSource
import com.example.model.VisualizerFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

object EqualizerEngineHolder {
    @Volatile
    private var instance: EqualizerAudioEngine? = null

    fun get(context: Context): EqualizerAudioEngine {
        return instance ?: synchronized(this) {
            instance ?: EqualizerAudioEngine(context.applicationContext).also {
                instance = it
            }
        }
    }
}

class EqualizerAudioEngine(private val context: Context) {

    private data class SessionEffect(
        val sessionId: Int,
        val dynamicsProcessing: DynamicsProcessing? = null,
        val legacyEqualizer: Equalizer? = null
    ) {
        fun release() {
            try {
                dynamicsProcessing?.release()
            } catch (_: Throwable) {
            }
            try {
                legacyEqualizer?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val testSynthesizer = TestAudioSynthesizer()

    private val sessionEffects = ConcurrentHashMap<Int, SessionEffect>()
    private var systemVisualizer: Visualizer? = null

    private val _connectedDevices = MutableStateFlow(ConnectedDeviceState())
    val connectedDevices: StateFlow<ConnectedDeviceState> = _connectedDevices.asStateFlow()

    private val _dspStatusLabel = MutableStateFlow("Initializing DSP...")
    val dspStatusLabel: StateFlow<String> = _dspStatusLabel.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(checkAudioPermission())
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _globalMixFallbackEnabled = MutableStateFlow(true)
    val globalMixFallbackEnabled: StateFlow<Boolean> = _globalMixFallbackEnabled.asStateFlow()

    private val _systemVisualizerFrame = MutableStateFlow(VisualizerFrame())
    private val _visualizerFrame = MutableStateFlow(VisualizerFrame())
    val visualizerFrame: StateFlow<VisualizerFrame> = _visualizerFrame.asStateFlow()

    // Upper cutoff frequencies for the 10 octave-spaced bands (32Hz..16kHz)
    private val bandUpperCutoffsHz = floatArrayOf(
        45f, 90f, 180f, 355f, 710f, 1400f, 2800f, 5600f, 11200f, 20000f
    )

    @Volatile
    var currentGainsDb: List<Float> = List(10) { 0f }
        private set

    @Volatile
    var currentPreampDb: Float = 0f
        private set

    @Volatile
    var currentBypassed: Boolean = false
        private set

    @Volatile
    var currentLateNightEnabled: Boolean = false
        private set

    @Volatile
    var currentNightIntensity: NightIntensity = NightIntensity.STANDARD
        private set

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshConnectedDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshConnectedDevices()
        }
    }

    init {
        refreshConnectedDevices()
        try {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
        } catch (_: Throwable) {
        }

        reconcileAttachedSessions()
        refreshSystemVisualizer()

        scope.launch {
            combine(
                AudioSessionRegistry.activeSessions,
                _globalMixFallbackEnabled
            ) { sessions, globalFallback ->
                sessions.keys to globalFallback
            }.collect {
                reconcileAttachedSessions()
            }
        }

        scope.launch {
            combine(
                testSynthesizer.currentSource,
                testSynthesizer.visualizerFrame,
                _systemVisualizerFrame
            ) { testSource, synthFrame, sysFrame ->
                if (testSource != TestAudioSource.OFF) {
                    synthFrame
                } else {
                    sysFrame
                }
            }.collect { frame ->
                _visualizerFrame.value = frame
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult(granted: Boolean) {
        _hasAudioPermission.value = granted
        refreshSystemVisualizer()
    }

    fun setGlobalMixFallback(enabled: Boolean) {
        _globalMixFallbackEnabled.value = enabled
        reconcileAttachedSessions()
    }

    fun refreshConnectedDevices() {
        val outputs = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Throwable) {
            emptyArray()
        }

        var headphonesConnected = false
        var headphonesName: String? = null
        var bluetoothConnected = false
        var bluetoothName: String? = null

        for (device in outputs) {
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    headphonesConnected = true
                    val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                    if (headphonesName == null && name != null) {
                        headphonesName = name
                    }
                }

                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_HEARING_AID -> {
                    bluetoothConnected = true
                    val name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                    if (bluetoothName == null && name != null) {
                        bluetoothName = name
                    }
                }
            }
        }

        val activeRoute = when {
            bluetoothConnected -> OutputTarget.BLUETOOTH
            headphonesConnected -> OutputTarget.HEADPHONES
            else -> OutputTarget.SPEAKER
        }

        _connectedDevices.value = ConnectedDeviceState(
            activeRoute = activeRoute,
            speakerAvailable = true,
            headphonesConnected = headphonesConnected,
            headphonesName = headphonesName,
            bluetoothConnected = bluetoothConnected,
            bluetoothName = bluetoothName
        )
    }

    fun reconcileAttachedSessions() {
        val targetSessions = buildSet {
            addAll(AudioSessionRegistry.activeSessions.value.keys)
            if (_globalMixFallbackEnabled.value) {
                add(0)
            }
        }

        // Detach effects for closed sessions or disabled Session 0
        val toRemove = sessionEffects.keys.filter { it !in targetSessions }
        for (id in toRemove) {
            sessionEffects.remove(id)?.release()
        }

        // Attach effects for newly detected media sessions or Session 0
        for (sessionId in targetSessions) {
            if (!sessionEffects.containsKey(sessionId)) {
                createEffectForSession(sessionId)?.let { created ->
                    sessionEffects[sessionId] = created
                }
            }
        }

        applyToAllSessions()
    }

    private fun createEffectForSession(sessionId: Int): SessionEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true, 10,
                    true, 1,
                    false, 0,
                    true
                )
                val eq = DynamicsProcessing.Eq(true, true, 10)
                for (i in 0 until 10) {
                    eq.setBand(i, DynamicsProcessing.EqBand(true, bandUpperCutoffsHz[i], 0f))
                }
                builder.setPreEqAllChannelsTo(eq)

                val mbc = DynamicsProcessing.Mbc(true, false, 1)
                mbc.setBand(
                    0,
                    DynamicsProcessing.MbcBand(
                        false,
                        22000f,
                        NightIntensity.STANDARD.attackMs,
                        NightIntensity.STANDARD.releaseMs,
                        NightIntensity.STANDARD.ratio,
                        NightIntensity.STANDARD.thresholdDb,
                        NightIntensity.STANDARD.kneeDb,
                        -90f,
                        1f,
                        0f,
                        NightIntensity.STANDARD.makeupGainDb
                    )
                )
                builder.setMbcAllChannelsTo(mbc)

                val limiter = DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    1.0f,
                    60.0f,
                    10.0f,
                    -0.5f,
                    0.0f
                )
                builder.setLimiterAllChannelsTo(limiter)

                val dp = DynamicsProcessing(0, sessionId, builder.build())
                dp.enabled = true
                return SessionEffect(sessionId = sessionId, dynamicsProcessing = dp)
            } catch (_: Throwable) {
            }
        }

        return try {
            val eq = Equalizer(0, sessionId)
            eq.enabled = true
            SessionEffect(sessionId = sessionId, legacyEqualizer = eq)
        } catch (_: Throwable) {
            null
        }
    }

    fun applyParameters(
        gainsDb: List<Float>,
        preampDb: Float,
        isBypassed: Boolean,
        lateNightEnabled: Boolean = currentLateNightEnabled,
        nightIntensity: NightIntensity = currentNightIntensity
    ) {
        currentGainsDb = gainsDb
        currentPreampDb = preampDb
        currentBypassed = isBypassed
        currentLateNightEnabled = lateNightEnabled
        currentNightIntensity = nightIntensity

        // Always update the internal 10-band biquad + dynamic compressor test synth engine
        testSynthesizer.updateEq(gainsDb, preampDb, isBypassed, lateNightEnabled, nightIntensity)
        applyToAllSessions()
    }

    private fun applyToAllSessions() {
        var anyDpActive = false
        var anyLegacyActive = false

        for (bundle in sessionEffects.values) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bundle.dynamicsProcessing != null) {
                val dp = bundle.dynamicsProcessing
                try {
                    dp.enabled = !currentBypassed
                    if (!currentBypassed) {
                        dp.setInputGainAllChannelsTo(currentPreampDb)
                        for (i in 0 until 10) {
                            val gain = currentGainsDb.getOrElse(i) { 0f }
                                .coerceIn(EqPresetsCatalog.MIN_GAIN_DB, EqPresetsCatalog.MAX_GAIN_DB)
                            dp.setPreEqBandAllChannelsTo(
                                i,
                                DynamicsProcessing.EqBand(true, bandUpperCutoffsHz[i], gain)
                            )
                        }

                        val compEnabled = currentLateNightEnabled
                        val mbc = DynamicsProcessing.Mbc(true, compEnabled, 1)
                        mbc.setBand(
                            0,
                            DynamicsProcessing.MbcBand(
                                compEnabled,
                                22000f,
                                currentNightIntensity.attackMs,
                                currentNightIntensity.releaseMs,
                                currentNightIntensity.ratio,
                                currentNightIntensity.thresholdDb,
                                currentNightIntensity.kneeDb,
                                -90f,
                                1f,
                                0f,
                                if (compEnabled) currentNightIntensity.makeupGainDb else 0f
                            )
                        )
                        dp.setMbcAllChannelsTo(mbc)

                        val limiterCeiling = if (compEnabled) -0.5f else -0.2f
                        dp.setLimiterAllChannelsTo(
                            DynamicsProcessing.Limiter(
                                true,
                                true,
                                0,
                                1.0f,
                                60.0f,
                                10.0f,
                                limiterCeiling,
                                0.0f
                            )
                        )
                    }
                    anyDpActive = true
                } catch (_: Throwable) {
                }
            } else if (bundle.legacyEqualizer != null) {
                val legEq = bundle.legacyEqualizer
                try {
                    legEq.enabled = !currentBypassed
                    if (!currentBypassed) {
                        val range = legEq.bandLevelRange
                        val minMb = range[0].toInt()
                        val maxMb = range[1].toInt()
                        val numBands = legEq.numberOfBands.toInt()
                        for (b in 0 until numBands) {
                            val centerHz = legEq.getCenterFreq(b.toShort()) / 1000f
                            val mappedDb = interpolateGainForFrequency(centerHz, currentGainsDb) + currentPreampDb * 0.5f
                            val levelMb = (mappedDb * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                            legEq.setBandLevel(b.toShort(), levelMb)
                        }
                    }
                    anyLegacyActive = true
                } catch (_: Throwable) {
                }
            }
        }

        val sessionCount = AudioSessionRegistry.activeSessions.value.size
        _dspStatusLabel.value = when {
            currentBypassed -> "DSP Bypassed"
            anyDpActive && sessionCount > 0 -> {
                if (currentLateNightEnabled) "App DSP + Late Night ($sessionCount)" else "App DSP ($sessionCount active)"
            }
            anyDpActive -> {
                if (currentLateNightEnabled) "Global DSP + Late Night" else "10-Band Global DSP"
            }
            anyLegacyActive -> "System EQ Active"
            !_globalMixFallbackEnabled.value && sessionCount == 0 -> "Waiting for Media App"
            else -> if (currentLateNightEnabled) "Studio DSP + Late Night" else "Studio 10-Band DSP"
        }
    }

    private fun interpolateGainForFrequency(freqHz: Float, gainsDb: List<Float>): Float {
        val bands = EqPresetsCatalog.BANDS
        if (freqHz <= bands.first().frequencyHz) return gainsDb.firstOrNull() ?: 0f
        if (freqHz >= bands.last().frequencyHz) return gainsDb.lastOrNull() ?: 0f

        for (i in 0 until bands.size - 1) {
            val f1 = bands[i].frequencyHz.toFloat()
            val f2 = bands[i + 1].frequencyHz.toFloat()
            if (freqHz in f1..f2) {
                val t = (log10(freqHz) - log10(f1)) / (log10(f2) - log10(f1))
                val g1 = gainsDb.getOrElse(i) { 0f }
                val g2 = gainsDb.getOrElse(i + 1) { 0f }
                return g1 + (g2 - g1) * t
            }
        }
        return 0f
    }

    fun refreshSystemVisualizer() {
        releaseSystemVisualizer()
        if (!checkAudioPermission()) {
            _systemVisualizerFrame.value = VisualizerFrame()
            return
        }

        try {
            val vis = Visualizer(0)
            val sizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = sizeRange[1].coerceAtMost(1024).coerceAtLeast(sizeRange[0])
            vis.captureSize = captureSize

            vis.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null || waveform.isEmpty()) return
                        val samples = MutableList(96) { idx ->
                            val raw = waveform[(idx * waveform.size) / 96].toInt() and 0xFF
                            ((raw - 128) / 128f).coerceIn(-1f, 1f)
                        }
                        var peak = 0f
                        for (s in samples) {
                            val a = kotlin.math.abs(s)
                            if (a > peak) peak = a
                        }
                        val live = peak > 0.01f
                        val peakDb = if (peak > 1e-4f) (20f * log10(peak)).coerceIn(-60f, 0f) else -60f
                        val grDb = if (live && currentLateNightEnabled && !currentBypassed) {
                            DynamicRangeCompressor.computeGainReductionDb(
                                inputDb = peakDb,
                                thresholdDb = currentNightIntensity.thresholdDb,
                                kneeDb = currentNightIntensity.kneeDb,
                                ratio = currentNightIntensity.ratio
                            )
                        } else {
                            0f
                        }
                        _systemVisualizerFrame.value = _systemVisualizerFrame.value.copy(
                            waveformSamples = samples,
                            peakDb = peakDb,
                            gainReductionDb = grDb,
                            isSignalLive = live
                        )
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 4) return
                        val n = fft.size / 2
                        val sr = (samplingRate / 1000.0).coerceAtLeast(8000.0)
                        val bars = MutableList(32) { 0f }
                        val minFreq = 30.0
                        val maxFreq = (sr * 0.45).coerceAtLeast(8000.0)

                        var anySignal = false
                        for (b in 0 until 32) {
                            val fLow = minFreq * (maxFreq / minFreq).pow(b / 32.0)
                            val fHigh = minFreq * (maxFreq / minFreq).pow((b + 1) / 32.0)
                            val binLow = ((fLow * fft.size) / sr).toInt().coerceIn(1, n - 1)
                            val binHigh = ((fHigh * fft.size) / sr).toInt().coerceIn(binLow, n - 1)

                            var maxMag = 0.0
                            for (k in binLow..binHigh) {
                                val re = fft[2 * k].toDouble()
                                val im = fft[2 * k + 1].toDouble()
                                val mag = hypot(re, im)
                                if (mag > maxMag) maxMag = mag
                            }
                            if (maxMag > 1.5) anySignal = true
                            val norm = if (maxMag > 0.5) {
                                ((20.0 * log10(maxMag / 128.0) + 48.0) / 48.0).toFloat().coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            bars[b] = norm
                        }

                        _systemVisualizerFrame.value = _systemVisualizerFrame.value.copy(
                            spectrumBins = bars,
                            isSignalLive = anySignal || _systemVisualizerFrame.value.isSignalLive
                        )
                    }
                },
                Visualizer.getMaxCaptureRate().coerceAtMost(20000),
                true,
                true
            )
            vis.enabled = true
            systemVisualizer = vis
        } catch (_: Throwable) {
            systemVisualizer = null
        }
    }

    fun setTestAudioSource(source: TestAudioSource) {
        testSynthesizer.setSource(source)
        testSynthesizer.updateEq(
            currentGainsDb,
            currentPreampDb,
            currentBypassed,
            currentLateNightEnabled,
            currentNightIntensity
        )
    }

    private fun releaseSystemVisualizer() {
        try {
            systemVisualizer?.enabled = false
            systemVisualizer?.release()
        } catch (_: Throwable) {
        }
        systemVisualizer = null
    }

    fun releaseAllSessions() {
        sessionEffects.values.forEach { it.release() }
        sessionEffects.clear()
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Throwable) {
        }
        releaseSystemVisualizer()
        testSynthesizer.stop()
        releaseAllSessions()
    }
}

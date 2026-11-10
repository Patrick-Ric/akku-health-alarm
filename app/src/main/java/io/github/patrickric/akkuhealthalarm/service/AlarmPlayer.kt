package io.github.patrickric.akkuhealthalarm.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.patrickric.akkuhealthalarm.R
import io.github.patrickric.akkuhealthalarm.data.AlarmSettings
import io.github.patrickric.akkuhealthalarm.data.ToneMode
import io.github.patrickric.akkuhealthalarm.data.VoiceMode
import java.util.Calendar

/**
 * Plays an alarm: first the tone, then the announcement. The phone voice is only used
 * when an offline voice exists; otherwise the built-in English clip plays.
 */
class AlarmPlayer(context: Context) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var session = 0

    private sealed interface Step {
        data class Clip(val uri: Uri) : Step
        data class Speak(val text: String, val fallback: Uri) : Step
    }

    /** How an alarm reached the user; stored with the alarm in the log. */
    enum class Delivery { SOUND, QUIET_HOURS, MUTED_VIBRATE, MUTED, SILENT_BY_SETTINGS }

    /** [force] = test button: ignores night rest and the muted phone. */
    fun play(type: AlarmType, level: Int, s: AlarmSettings, force: Boolean = false): Delivery {
        stop()
        if (!force && s.quietEnabled) {
            val now = Calendar.getInstance()
            val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            if (QuietHours.contains(minute, s.quietStart, s.quietEnd)) return Delivery.QUIET_HOURS
        }
        val ringer = audio.ringerMode
        val soundAllowed = force || s.loudWhenSilent || ringer == AudioManager.RINGER_MODE_NORMAL
        val vibrateAllowed = s.vibrate && (force || s.loudWhenSilent || ringer != AudioManager.RINGER_MODE_SILENT)
        if (vibrateAllowed) vibrate(type)
        if (!soundAllowed) return if (vibrateAllowed) Delivery.MUTED_VIBRATE else Delivery.MUTED

        val steps = buildList {
            when (s.toneMode) {
                ToneMode.OFF -> Unit
                ToneMode.BUILTIN -> add(Step.Clip(raw(toneRes(type))))
                ToneMode.SYSTEM -> add(Step.Clip(s.toneUri?.let(Uri::parse) ?: raw(toneRes(type))))
            }
            when (s.voiceMode) {
                VoiceMode.OFF -> Unit
                VoiceMode.BUILTIN -> add(Step.Clip(raw(voiceRes(type))))
                VoiceMode.SYSTEM -> add(Step.Speak(spokenText(type, level, s.voicePercent), raw(voiceRes(type))))
            }
        }
        if (steps.isEmpty()) return Delivery.SILENT_BY_SETTINGS
        val attrs = AudioAttributes.Builder()
            .setUsage(if (s.loudWhenSilent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val id = ++session
        if (steps.any { it is Step.Speak }) prepareTts()
        acquireWakeLock()
        requestFocus(attrs, s.loudWhenSilent)
        runSteps(id, steps, attrs)
        return Delivery.SOUND
    }

    /** Plays only the tone (no voice, no vibration), e.g. to listen to it in the settings. */
    fun previewTone(s: AlarmSettings) {
        play(AlarmType.HIGH, 0, s.copy(voiceMode = VoiceMode.OFF, vibrate = false), force = true)
    }

    fun stop() {
        session++
        player?.release()
        player = null
        tts?.stop()
        abandonFocus()
        releaseWakeLock()
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = null
    }

    private fun runSteps(id: Int, steps: List<Step>, attrs: AudioAttributes) {
        if (id != session) return
        if (steps.isEmpty()) {
            abandonFocus()
            releaseWakeLock()
            return
        }
        val next = { main.postDelayed({ runSteps(id, steps.drop(1), attrs) }, 120) }
        when (val step = steps.first()) {
            is Step.Clip -> playUri(id, step.uri, attrs) { next() }
            is Step.Speak -> speak(id, step, attrs) { next() }
        }
    }

    private fun playUri(id: Int, uri: Uri, attrs: AudioAttributes, onDone: () -> Unit) {
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(context, uri)
            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
                if (id == session) onDone()
            }
            mp.setOnErrorListener { p, _, _ ->
                p.release()
                if (player === p) player = null
                if (id == session) onDone()
                true
            }
            mp.prepare()
            player = mp
            mp.start()
        } catch (e: Exception) {
            mp.release()
            onDone()
        }
    }

    private fun prepareTts() {
        if (tts != null) return
        ttsReady = null
        tts = TextToSpeech(context) { status ->
            main.post { ttsReady = status == TextToSpeech.SUCCESS }
        }
    }

    private fun speak(id: Int, step: Step.Speak, attrs: AudioAttributes, onDone: () -> Unit) {
        var finished = false
        var started = false
        val fallback = Runnable {
            if (!finished && id == session) {
                finished = true
                tts?.stop()
                playUri(id, step.fallback, attrs, onDone)
            }
        }

        fun start(engine: TextToSpeech) {
            if (!selectOfflineVoice(engine)) {
                fallback.run()
                return
            }
            engine.setAudioAttributes(attrs)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    main.post {
                        started = true
                        main.removeCallbacks(fallback)
                        main.postDelayed(fallback, 10_000)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    main.post {
                        main.removeCallbacks(fallback)
                        // Some engines report "done" without speaking when no voice is installed.
                        if (!started) {
                            fallback.run()
                        } else if (!finished && id == session) {
                            finished = true
                            onDone()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { main.removeCallbacks(fallback); fallback.run() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post { main.removeCallbacks(fallback); fallback.run() }
                }
            })
            val result = engine.speak(step.text, TextToSpeech.QUEUE_FLUSH, Bundle(), "alarm")
            if (result != TextToSpeech.SUCCESS) {
                main.removeCallbacks(fallback)
                fallback.run()
            }
        }

        // The engine may still be starting; give it a few seconds.
        main.postDelayed(fallback, 5_000)
        fun waitForEngine(tries: Int) {
            if (finished || id != session) return
            val engine = tts
            when {
                engine == null || ttsReady == false -> { main.removeCallbacks(fallback); fallback.run() }
                ttsReady == true -> start(engine)
                tries > 0 -> main.postDelayed({ waitForEngine(tries - 1) }, 100)
            }
        }
        waitForEngine(45)
    }

    /** Picks an installed voice for the app language that needs no network. */
    private fun selectOfflineVoice(engine: TextToSpeech): Boolean {
        val locale = context.resources.configuration.locales[0]
        if (engine.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) return false
        val voices = try {
            engine.voices
        } catch (e: Exception) {
            null
        }
        // Without a voice list we cannot check that the voice works offline.
        if (voices.isNullOrEmpty()) return false
        val offline = voices.filter {
            it.locale.language == locale.language &&
                !it.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        }
        val best = offline.maxByOrNull { (if (it.locale.country == locale.country) 1000 else 0) + it.quality }
            ?: return false
        return engine.setVoice(best) == TextToSpeech.SUCCESS
    }

    private fun spokenText(type: AlarmType, level: Int, withPercent: Boolean): String = when (type) {
        AlarmType.HIGH ->
            if (withPercent) context.getString(R.string.tts_charged_percent, level) else context.getString(R.string.tts_charged)
        AlarmType.LOW ->
            if (withPercent) context.getString(R.string.tts_low_percent, level) else context.getString(R.string.tts_low)
        AlarmType.HOT -> context.getString(R.string.tts_hot)
        AlarmType.COLD -> context.getString(R.string.tts_cold)
    }

    private fun raw(res: Int): Uri = Uri.parse("android.resource://${context.packageName}/$res")

    private fun toneRes(type: AlarmType) = when (type) {
        AlarmType.HIGH -> R.raw.tone_charged
        AlarmType.LOW -> R.raw.tone_low
        AlarmType.HOT, AlarmType.COLD -> R.raw.tone_warning
    }

    private fun voiceRes(type: AlarmType) = when (type) {
        AlarmType.HIGH -> R.raw.voice_charged
        AlarmType.LOW -> R.raw.voice_low
        AlarmType.HOT -> R.raw.voice_hot
        AlarmType.COLD -> R.raw.voice_cold
    }

    private fun vibrate(type: AlarmType) {
        val pattern = when (type) {
            AlarmType.HIGH -> longArrayOf(0, 120, 90, 120, 90, 320)
            AlarmType.LOW -> longArrayOf(0, 320, 140, 320)
            AlarmType.HOT, AlarmType.COLD -> longArrayOf(0, 150, 100, 150, 100, 150)
        }
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun requestFocus(attrs: AudioAttributes, alarmStream: Boolean) {
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
            focusRequest = request
            audio.requestAudioFocus(request)
        } else {
            val stream = if (alarmStream) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(null, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { audio.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(null)
        }
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AkkuHealthAlarm:alarm").apply {
            setReferenceCounted(false)
            acquire(30_000)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}

package com.mathyusolutions.calarmi.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.mathyusolutions.calarmi.R
import com.mathyusolutions.calarmi.data.ImportedTonesStore
import com.mathyusolutions.calarmi.model.AlarmTone

/**
 * Reproduce el tono de una alarma en el canal de ALARMA: suena aunque el
 * teléfono esté en silencio o vibración, como en iOS. También sirve para la
 * vista previa del selector de tonos (`preview`).
 */
class TonePlayer(private val context: Context, private val importedTones: ImportedTonesStore) {
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val audio = context.getSystemService(AudioManager::class.java)

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Empieza a sonar en bucle. Si el archivo falla, cae al tono de alarma del sistema y luego al de la app. */
    fun play(tone: AlarmTone, loop: Boolean = true) {
        stop()
        requestFocus()
        val candidates = buildList {
            uriFor(tone)?.let(::add)
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)?.let(::add)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
            add(rawUri(R.raw.tone_chime))
        }
        for (uri in candidates) {
            val started = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(alarmAttributes)
                    setDataSource(context, uri)
                    isLooping = loop
                    prepare()
                    start()
                }
            }.onFailure { Log.w(TAG, "No se pudo reproducir $uri", it) }.getOrNull()
            if (started != null) {
                player = started
                return
            }
        }
    }

    /** Vista previa en el selector: una vez, sin bucle. */
    fun preview(tone: AlarmTone) = play(tone, loop = false)

    fun pause() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    fun resume() {
        runCatching { player?.takeIf { !it.isPlaying }?.start() }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        abandonFocus()
    }

    private fun uriFor(tone: AlarmTone): Uri? = when (tone) {
        AlarmTone.System -> null
        is AlarmTone.Imported -> importedTones.file(tone.id)?.let(Uri::fromFile)
        else -> tone.rawRes?.let(::rawUri)
    }

    private fun rawUri(res: Int): Uri = Uri.parse("android.resource://${context.packageName}/$res")

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(alarmAttributes)
                .build()
            focusRequest = request
            audio.requestAudioFocus(request)
        }
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val TAG = "TonePlayer"
    }
}

/** Vibración en bucle mientras suena. */
class AlarmVibrator(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun start() {
        val pattern = longArrayOf(0, 600, 400, 600, 900)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 1), attrs)
    }

    fun stop() = vibrator.cancel()
}

package com.sautique.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * Sons et musique. Les fichiers se trouvent dans app/src/main/res/raw/ (ogg, mp3 ou wav)
 * et sont tous facultatifs : un son absent est simplement ignoré.
 */
class Sounds(private val context: Context) {

    enum class Sfx(val file: String) {
        JUMP("saut"),
        LAND("atterrissage"),
        PERFECT("parfait"),
        FALL("chute"),
        SCARE("peur"),
        CRUMBLE("effondrement"),
        BONUS_START("bonus"),
        BONUS_WIN("bonus_gagne"),
        BONUS_LOSE("bonus_rate"),
        TAP("tape"),
        STAR("etoile"),
        WHEEL_TICK("roue"),
        UNLOCK("deblocage"),
        CLICK("clic")
    }

    private val prefs = context.getSharedPreferences("sautique", Context.MODE_PRIVATE)
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = HashMap<Sfx, Int>()
    private var music: MediaPlayer? = null
    private val musicRes = resId("musique")

    /** Volume des effets sonores, de 0 (coupé) à 1. */
    var soundVolume: Float = prefs.getFloat("sound_volume", 0.8f)
        set(value) {
            field = value.coerceIn(0f, 1f)
            prefs.edit().putFloat("sound_volume", field).apply()
        }

    /** Volume de la musique, de 0 (coupée) à 1. */
    var musicVolume: Float = prefs.getFloat("music_volume", 0.5f)
        set(value) {
            field = value.coerceIn(0f, 1f)
            prefs.edit().putFloat("music_volume", field).apply()
            if (field <= 0f) {
                pauseMusic()
            } else {
                music?.setVolume(field, field)
                resumeMusic()
            }
        }

    val hasMusic get() = musicRes != 0

    init {
        for (s in Sfx.values()) {
            val res = resId(s.file)
            if (res != 0) ids[s] = pool.load(context, res, 1)
        }
    }

    @Suppress("DiscouragedApi")
    private fun resId(name: String) = context.resources.getIdentifier(name, "raw", context.packageName)

    fun play(s: Sfx, volume: Float = 1f, rate: Float = 1f) {
        val v = (volume * soundVolume).coerceIn(0f, 1f)
        if (v <= 0f) return
        val id = ids[s] ?: return
        pool.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun resumeMusic() {
        if (musicVolume <= 0f || musicRes == 0) return
        val m = music ?: MediaPlayer.create(context, musicRes)?.apply {
            isLooping = true
            setVolume(musicVolume, musicVolume)
        }.also { music = it } ?: return
        if (!m.isPlaying) m.start()
    }

    fun pauseMusic() {
        music?.let { if (it.isPlaying) it.pause() }
    }

    fun release() {
        music?.release()
        music = null
        pool.release()
    }
}

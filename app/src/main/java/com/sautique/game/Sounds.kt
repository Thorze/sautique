package com.sautique.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import kotlin.random.Random

/**
 * Sons et musiques, intégrés dans l'appli (app/src/main/res/raw/).
 * Tous les fichiers sont facultatifs : un son absent est simplement ignoré.
 *
 * - Musique de fond : boucle1..boucle4, chacune répétée plusieurs fois puis une autre au hasard.
 * - Pendant un niveau bonus : « bonus » en boucle.
 * - En cas de défaite : gameover2.
 */
class Sounds(private val context: Context) {

    /** Effets ; quand plusieurs fichiers sont listés, l'un d'eux est choisi au hasard. */
    enum class Sfx(vararg val files: String) {
        JUMP("saut"),
        LAND("atterrissage"),
        GREAT("great"),
        PERFECT("perfect", "oh_yeah", "ifeelgood"),
        SCARE("limite", "limite2", "limite3"),
        FALL("chute"),
        CRUMBLE("effondrement"),
        BONUS_WIN("bonus_gagne"),
        BONUS_LOSE("bonus_rate"),
        TAP("tape"),
        STAR("etoile"),
        COIN("piece"),
        WHEEL_TICK("roue"),
        UNLOCK("congratulations", "deblocage"),
        REVIVE("ange"),
        CLICK("clic")
    }

    private enum class Mode { PLAYLIST, BONUS, GAME_OVER }

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

    /** Sons courts (SoundPool, latence minimale) et sons plus longs (MediaPlayer). */
    private val shortIds = HashMap<Sfx, List<Int>>()
    private val longRes = HashMap<Sfx, List<Int>>()
    private val voices = ArrayList<MediaPlayer>()

    private val loops = listOf("boucle1", "boucle2", "boucle3", "boucle4").map { resId(it) }.filter { it != 0 }
    private val bonusRes = resId("bonus")
    private val gameOverSeq = listOf("gameover2").map { resId(it) }.filter { it != 0 }

    private var mode = Mode.PLAYLIST
    private var music: MediaPlayer? = null
    private var loopIndex = -1
    private var repeatsLeft = 0
    private var paused = true

    /** Volume des effets sonores, de 0 (coupé) à 1. */
    var soundVolume: Float = prefs.getFloat("sound_volume", 0.8f)
        set(value) {
            field = value.coerceIn(0f, 1f)
            prefs.edit().putFloat("sound_volume", field).apply()
            if (mode == Mode.GAME_OVER) music?.setVolume(field, field)
        }

    /** Volume de la musique, de 0 (coupée) à 1. */
    var musicVolume: Float = prefs.getFloat("music_volume", 0.5f)
        set(value) {
            field = value.coerceIn(0f, 1f)
            prefs.edit().putFloat("music_volume", field).apply()
            if (mode != Mode.GAME_OVER) {
                if (field <= 0f) stopMusic() else {
                    music?.setVolume(field, field)
                    if (music == null && !paused) startMode()
                }
            }
        }

    val hasMusic get() = loops.isNotEmpty() || bonusRes != 0

    init {
        for (s in Sfx.values()) {
            val short = ArrayList<Int>()
            val long = ArrayList<Int>()
            for (f in s.files) {
                val res = resId(f)
                if (res == 0) continue
                // Les sons longs (fichier > 20 Ko, soit plus de ~2 s en OGG) passent par MediaPlayer :
                // SoundPool risquerait de les couper
                val size = try {
                    context.resources.openRawResourceFd(res).use { it.length }
                } catch (e: Exception) { 0L }
                if (size in 1..20_000) short += pool.load(context, res, 1) else long += res
            }
            if (short.isNotEmpty()) shortIds[s] = short
            if (long.isNotEmpty()) longRes[s] = long
        }
    }

    @Suppress("DiscouragedApi")
    private fun resId(name: String) = context.resources.getIdentifier(name, "raw", context.packageName)

    // ---------- Effets ----------

    fun has(s: Sfx) = shortIds.containsKey(s) || longRes.containsKey(s)

    fun play(s: Sfx, volume: Float = 1f, rate: Float = 1f) {
        val v = (volume * soundVolume).coerceIn(0f, 1f)
        if (v <= 0f) return
        val short = shortIds[s]
        val long = longRes[s]
        val total = (short?.size ?: 0) + (long?.size ?: 0)
        if (total == 0) return
        val pick = Random.nextInt(total)
        if (short != null && pick < short.size) {
            pool.play(short[pick], v, v, 1, 0, rate.coerceIn(0.5f, 2f))
        } else {
            val res = long!![pick - (short?.size ?: 0)]
            playVoice(res, v)
        }
    }

    private fun playVoice(res: Int, volume: Float) {
        val mp = MediaPlayer.create(context, res) ?: return
        mp.setVolume(volume, volume)
        mp.setOnCompletionListener {
            voices.remove(it)
            it.release()
        }
        voices += mp
        mp.start()
    }

    // ---------- Musique ----------

    /** Musique d'ambiance : les boucles s'enchaînent. */
    fun playPlaylist() {
        if (mode == Mode.PLAYLIST && music != null) return
        mode = Mode.PLAYLIST
        stopMusic()
        if (!paused) startMode()
    }

    /** Pendant un niveau bonus. */
    fun playBonusMusic() {
        mode = Mode.BONUS
        stopMusic()
        if (!paused) startMode()
    }

    /** Défaite : gameover2, puis [onDone] et retour à la musique d'ambiance. */
    fun playGameOver(onDone: () -> Unit) {
        mode = Mode.GAME_OVER
        stopMusic()
        if (gameOverSeq.isEmpty() || soundVolume <= 0f || paused) {
            mode = Mode.PLAYLIST
            onDone()
            if (!paused) startMode()
            return
        }
        playSequence(0, onDone)
    }

    private fun playSequence(i: Int, onDone: () -> Unit) {
        if (mode != Mode.GAME_OVER) return
        if (i >= gameOverSeq.size) {
            music = null
            mode = Mode.PLAYLIST
            onDone()
            if (!paused) startMode()
            return
        }
        val mp = MediaPlayer.create(context, gameOverSeq[i])
        if (mp == null) {
            playSequence(i + 1, onDone)
            return
        }
        mp.setVolume(soundVolume, soundVolume)
        mp.setOnCompletionListener {
            it.release()
            if (music === it) music = null
            playSequence(i + 1, onDone)
        }
        music = mp
        mp.start()
    }

    private fun startMode() {
        if (music != null) return
        when (mode) {
            Mode.PLAYLIST -> nextLoop()
            Mode.BONUS -> {
                if (bonusRes == 0 || musicVolume <= 0f) return
                music = MediaPlayer.create(context, bonusRes)?.apply {
                    isLooping = true
                    setVolume(musicVolume, musicVolume)
                    start()
                }
            }
            Mode.GAME_OVER -> {}
        }
    }

    /** Choisit une autre boucle au hasard et la répète 3 ou 4 fois. */
    private fun nextLoop() {
        if (loops.isEmpty() || musicVolume <= 0f) return
        var idx = Random.nextInt(loops.size)
        if (loops.size > 1 && idx == loopIndex) idx = (idx + 1 + Random.nextInt(loops.size - 1)) % loops.size
        loopIndex = idx
        repeatsLeft = 3 + Random.nextInt(2)
        val mp = MediaPlayer.create(context, loops[idx]) ?: return
        mp.setVolume(musicVolume, musicVolume)
        mp.setOnCompletionListener {
            if (music !== it || mode != Mode.PLAYLIST) return@setOnCompletionListener
            repeatsLeft--
            if (repeatsLeft > 0) {
                it.seekTo(0)
                it.start()
            } else {
                it.release()
                music = null
                nextLoop()
            }
        }
        music = mp
        mp.start()
    }

    private fun stopMusic() {
        music?.let {
            it.setOnCompletionListener(null)
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        music = null
    }

    /** A appeler dans onResume. */
    fun resumeMusic() {
        paused = false
        val m = music
        if (m != null) {
            if (!m.isPlaying) m.start()
        } else if (mode != Mode.GAME_OVER) {
            startMode()
        }
    }

    /** A appeler dans onPause. */
    fun pauseMusic() {
        paused = true
        music?.let { if (it.isPlaying) it.pause() }
        voices.forEach { it.release() }
        voices.clear()
    }

    fun release() {
        stopMusic()
        voices.forEach { it.release() }
        voices.clear()
        pool.release()
    }
}

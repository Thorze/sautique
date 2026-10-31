package com.sautique.game

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

class MainActivity : Activity() {
    private lateinit var game: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        game = GameView(this)
        setContentView(game)
        if (savedInstanceState == null) Updater.check(this)
    }

    override fun onResume() {
        super.onResume()
        Updater.onResume(this)
        game.sounds.resumeMusic()
    }

    override fun onPause() {
        super.onPause()
        game.sounds.pauseMusic()
        game.onHostPause()
    }

    override fun onDestroy() {
        game.sounds.release()
        super.onDestroy()
    }
}

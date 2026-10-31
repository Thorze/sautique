package com.sautique.game

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(GameView(this))
        if (savedInstanceState == null) Updater.check(this)
    }

    override fun onResume() {
        super.onResume()
        Updater.onResume(this)
    }
}

package com.sautique.game

import android.app.Activity
import android.widget.Toast

/** Version Play Store : les mises à jour passent par le Play Store, pas par le jeu. */
object Updater {
    fun check(activity: Activity, manual: Boolean = false) {
        if (manual) Toast.makeText(activity, "Sautique v${BuildConfig.VERSION_NAME}", Toast.LENGTH_SHORT).show()
    }

    fun onResume(activity: Activity) {}
}

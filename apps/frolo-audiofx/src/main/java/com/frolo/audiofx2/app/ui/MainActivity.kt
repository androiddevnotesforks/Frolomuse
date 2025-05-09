package com.frolo.audiofx2.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.frolo.audiofx.app.R
import com.frolo.audiofx2.app.ui.instruction.InstructionDialog
import com.frolo.audiofx2.ui.AudioFx2Feature
import com.frolo.core.ui.ApplicationWatcher

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Frolo_AudioFx)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupWindowInsets()
        addControlPanelScreen()
        maybeShowInstructions()
    }

    private fun addControlPanelScreen() {
        AudioFx2Feature.createControlPanelFragment().also { fragment ->
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .commitNow()
        }
    }

    private fun setupWindowInsets() {
        skipWindowInsets(findViewById(R.id.root))
        skipWindowInsets(findViewById(R.id.container))
    }

    private fun skipWindowInsets(view: View) {
        view.fitsSystemWindows = true
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets -> insets }
        view.requestApplyInsets()
    }

    private fun maybeShowInstructions() {
        val showInstructions: Boolean = ApplicationWatcher.appStartUpInfoProvider.let { provider ->
            provider.coldStartCount + provider.hotStartCount <= 3
        }
        if (showInstructions) {
            val dialog = InstructionDialog(this)
            dialog.show()
        }
    }
}
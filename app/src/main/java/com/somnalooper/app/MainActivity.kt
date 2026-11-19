package com.somnalooper.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.somnalooper.app.ui.SomnaRoot
import com.somnalooper.app.ui.theme.SomnaTheme
import kotlinx.coroutines.launch

/**
 * Single activity. Extends AppCompatActivity so the per-app language chosen in Settings
 * (AppCompatDelegate.setApplicationLocales) also works on Android 8-12; on Android 13+
 * the system per-app language setting is used. By default the phone's language is followed.
 */
class MainActivity : AppCompatActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SomnaTheme {
                SomnaRoot(activity = this)
            }
        }
        maybeAskNotifications()
    }

    override fun onStart() {
        super.onStart()
        // Binding a controller creates the MediaSessionService; Media3 then promotes it to a
        // foreground service with a media notification whenever sound is playing.
        if (controllerFuture == null) {
            val token = SessionToken(this, ComponentName(this, com.somnalooper.app.playback.PlaybackService::class.java))
            controllerFuture = MediaController.Builder(this, token).buildAsync().also {
                it.addListener({}, MoreExecutors.directExecutor())
            }
        }
        container.billing.refreshPurchases()
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }

    private fun maybeAskNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        lifecycleScope.launch {
            val c = container
            val s = c.settingsRepo
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                !c.settings.value.onboarded
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            s.setOnboarded(true)
        }
    }
}

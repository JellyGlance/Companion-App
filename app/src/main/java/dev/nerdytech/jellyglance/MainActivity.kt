package dev.nerdytech.jellyglance

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import dev.nerdytech.jellyglance.ui.JellyGlanceRoot
import dev.nerdytech.jellyglance.ui.TabRequest

class MainActivity : FragmentActivity() {
    /** Tab requested by a launcher shortcut or notification ("streams", "requests", ...). */
    private val startTab = mutableStateOf<TabRequest?>(null)
    /** App lock: true until the user passes the biometric / screen-lock prompt. */
    private val locked = mutableStateOf(false)
    private var leftAt = 0L

    private val lockOn get() = (application as JellyGlanceApp).settings.value.appLock

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startTab.value = requestFrom(intent)
        locked.value = lockOn
        setContent { JellyGlanceRoot(startTab.value, locked = locked.value, onUnlock = ::unlock) }
    }

    override fun onStart() {
        super.onStart()
        // Lock again after more than 30 seconds away.
        if (lockOn && leftAt > 0 && SystemClock.elapsedRealtime() - leftAt > 30_000) locked.value = true
        if (locked.value) unlock()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) leftAt = SystemClock.elapsedRealtime()
    }

    private fun unlock() {
        val allowed = if (Build.VERSION.SDK_INT >= 30) Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        else Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock JellyGlance")
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(allowed)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked.value = false }
        }).authenticate(info)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // TabRequest carries a timestamp, so the same shortcut twice in a row still navigates.
        startTab.value = requestFrom(intent)
    }

    /** A tab from a shortcut/notification, or a title shared from another app ("Share to JellyGlance"). */
    private fun requestFrom(intent: Intent?): TabRequest? {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT)).joinToString("\n")
            if (text.isNotBlank()) return TabRequest("share", share = text)
        }
        return intent?.getStringExtra(EXTRA_TAB)?.let { TabRequest(it) }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}

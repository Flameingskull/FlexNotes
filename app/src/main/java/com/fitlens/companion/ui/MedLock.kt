package com.fitlens.companion.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import com.fitlens.companion.R
import com.fitlens.companion.data.Settings
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GoldButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Medications lock (#214): with Settings › Medications › Lock Medications on, the Medications screens open only
 * after the phone's own fingerprint, face or screen-lock PIN prompt ([BiometricPrompt], no extra library). Unlocking
 * lasts while FlexNotes is in use and for [GRACE_MS] after it leaves the screen (long enough to pick a file or share a
 * report), then locks again; it never survives the app being closed. While locked, medication overlays are hidden from
 * other graphs and reminders name no medication. Nothing leaves the phone.
 */
object MedLock {
    /** How long FlexNotes can be in the background before Medications locks again. */
    const val GRACE_MS = 2 * 60_000L

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked
    private var stoppedAt: Long? = null
    /** A prompt is on screen: two Medications screens composed during a transition ask only once. */
    private var prompting = false

    /** Whether the phone has a screen lock: without one there is nothing to ask for, so the lock can't be used. */
    fun available(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Whether medication data is hidden right now: the lock is on, works on this phone and hasn't been passed. */
    fun hidden(context: Context): Boolean = Settings.current().medLock && available(context) && !_unlocked.value

    fun onAppStopped() {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    fun onAppStarted() {
        val at = stoppedAt ?: return
        stoppedAt = null
        if (SystemClock.elapsedRealtime() - at > GRACE_MS) _unlocked.value = false
    }

    fun lock() {
        _unlocked.value = false
    }

    /**
     * Shows the phone's prompt and calls [onResult] with true once the user passes it. A phone with no screen lock
     * passes at once. Errors and cancelling answer false.
     */
    fun prompt(context: Context, subtitle: String, onResult: (Boolean) -> Unit) {
        val activity = context.findActivity()
        if (!available(context)) {
            _unlocked.value = true
            onResult(true)
            return
        }
        if (activity == null || prompting) {
            onResult(false)
            return
        }
        prompting = true
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.ml_prompt_title))
            .setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        // A phone whose prompt can't be built (no usable authenticator) answers false rather than crashing.
        val prompt = try {
            builder.build()
        } catch (e: IllegalArgumentException) {
            prompting = false
            onResult(false)
            return
        }
        prompt.authenticate(
            CancellationSignal(), activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    prompting = false
                    _unlocked.value = true
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    prompting = false
                    onResult(false)
                }
            }
        )
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private var secureScreens = 0

    /**
     * Keeps the screen out of the recent-apps preview and screenshots while unlocked medications are shown. Counted,
     * because the next Medications screen is composed before the last one leaves.
     */
    internal fun secure(context: Context, on: Boolean) {
        secureScreens = (secureScreens + if (on) 1 else -1).coerceAtLeast(0)
        val window = context.findActivity()?.window ?: return
        if (secureScreens > 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/** Whether medication data should stay out of sight right now (#214): the Composable form of [MedLock.hidden]. */
@Composable
internal fun medicationsHidden(): Boolean {
    val ctx = LocalContext.current
    val device by Settings.device.collectAsState()
    val unlocked by MedLock.unlocked.collectAsState()
    return device.medLock && !unlocked && MedLock.available(ctx)
}

/**
 * Wraps each Medications screen (#214): its [content] while unlocked or with the lock off, else a locked page that
 * asks for the phone's prompt straight away, with Unlock to ask again and Back to leave.
 */
@Composable
internal fun MedLockGate(nav: Nav, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val device by Settings.device.collectAsState()
    if (!device.medLock) {
        content()
        return
    }
    if (!medicationsHidden()) {
        DisposableEffect(Unit) {
            MedLock.secure(ctx, true)
            onDispose { MedLock.secure(ctx, false) }
        }
        content()
        return
    }
    val subtitle = stringResource(R.string.ml_prompt_open)
    LaunchedEffect(Unit) { MedLock.prompt(ctx, subtitle) { } }
    Column(Modifier.fillMaxSize()) {
        FitTopBar(title = stringResource(R.string.md_title), onBack = { nav.pop() })
        EmptyState(stringResource(R.string.ml_locked_title), stringResource(R.string.ml_locked_body)) {
            GoldButton(onClick = { MedLock.prompt(ctx, subtitle) { } }) { Text(stringResource(R.string.ml_unlock)) }
        }
    }
}

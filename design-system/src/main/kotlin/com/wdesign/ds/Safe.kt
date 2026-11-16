package com.wdesign.ds

import android.content.ActivityNotFoundException
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.wdesign.core.data.CrashLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The app-wide snackbar host, provided by the app shell so any screen can report errors. */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }

/**
 * Coroutine scope for click handlers. Every error except cancellation is logged, stored in
 * [CrashLog] and shown as a snackbar instead of killing the app.
 */
class SafeScope(
    private val scope: CoroutineScope,
    private val context: Context,
    private val snackbar: SnackbarHostState?,
) {
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            report(t)
        }
    }

    /** Runs a plain (non-suspending) action, e.g. launching an activity or file picker. */
    fun run(action: () -> Unit) {
        try {
            action()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            report(t)
        }
    }

    fun report(t: Throwable) {
        CrashLog.record(t)
        val msg = context.getString(R.string.ds_error_generic, t.localizedMessage?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
        snackbar?.let { s -> scope.launch { runCatching { s.showSnackbar(msg) } } }
    }
}

@Composable
fun rememberSafeScope(snackbar: SnackbarHostState? = LocalSnackbar.current): SafeScope {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    return remember(scope, snackbar) { SafeScope(scope, ctx, snackbar) }
}

/** LaunchedEffect whose failures are reported instead of crashing the app. */
@Composable
fun SafeLaunchedEffect(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val ctx = LocalContext.current
    val snackbar = LocalSnackbar.current
    LaunchedEffect(*keys) {
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            CrashLog.record(t)
            Log.e("WDesign", "Effect failed", t)
            val msg = ctx.getString(R.string.ds_error_generic, t.localizedMessage?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
            runCatching { snackbar?.showSnackbar(msg) }
        }
    }
}

/** Starts a file picker/creator; a missing system document UI becomes a message, not a crash. */
fun <I> ActivityResultLauncher<I>.launchSafely(input: I, scope: SafeScope) {
    scope.run {
        try {
            launch(input)
        } catch (e: ActivityNotFoundException) {
            throw IllegalStateException("No app on this phone can open files (system file picker missing)", e)
        }
    }
}

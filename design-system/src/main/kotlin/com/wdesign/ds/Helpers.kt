package com.wdesign.ds

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.wdesign.core.model.SupportStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders a bitmap off the main thread; re-renders when any key changes. */
@Composable
fun rememberBitmap(vararg keys: Any?, producer: () -> Bitmap?): State<Bitmap?> =
    produceState<Bitmap?>(initialValue = null, *keys) {
        value = withContext(Dispatchers.Default) { runCatching(producer).getOrNull() }
    }

@Composable
fun statusLabel(s: SupportStatus): String = stringResource(
    when (s) {
        SupportStatus.SUPPORTED -> R.string.ds_status_supported
        SupportStatus.PARTIAL -> R.string.ds_status_partial
        SupportStatus.REQUIRES_ACTION -> R.string.ds_status_action
        SupportStatus.UNSUPPORTED -> R.string.ds_status_unsupported
    },
)

/** Navigation routes shared by the app shell and feature modules. */
object Routes {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val CREATE = "create"
    const val DESIGNS = "designs"
    const val SETTINGS = "settings"
    const val XIAOMI_LAB = "xiaomilab"
    const val IMPORT = "import"
    const val PRESET = "preset/{id}"
    const val LOCKSCREEN = "lockscreen?project={project}"
    const val MTZ_EXPORT = "mtz?project={project}"

    fun preset(id: String) = "preset/$id"
    fun lockscreen(projectId: String?) = if (projectId == null) "lockscreen" else "lockscreen?project=$projectId"
    fun mtz(projectId: String?) = if (projectId == null) "mtz" else "mtz?project=$projectId"

    val TABS = setOf(HOME, DISCOVER, CREATE, DESIGNS, SETTINGS)
}

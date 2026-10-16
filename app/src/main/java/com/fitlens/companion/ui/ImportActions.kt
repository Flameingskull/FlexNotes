package com.fitlens.companion.ui

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.ui.design.MenuAction
import androidx.compose.ui.platform.LocalContext
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.PhotoImportResult
import com.fitlens.companion.data.PhotoImporter
import com.fitlens.companion.data.Poses
import com.fitlens.companion.data.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.app.Application
import android.content.res.Resources
import com.fitlens.companion.R

/** Scope that outlives individual screens so long imports/exports aren't cancelled by navigation. */
object AppScope {
    /** The app's resources, for messages from jobs that have no screen (#94). Set by [init] when the app starts. */
    @Volatile
    private var res: Resources? = null

    fun init(app: Application) {
        res = app.resources
    }

    /** "Something went wrong: …" for a failed job, in the app's language. */
    fun failure(detail: String): String = res?.getString(R.string.err_generic, detail) ?: detail
    /**
     * Reports a failed background job instead of letting it reach the default handler, which kills the process.
     * `SupervisorJob` stops one failed child cancelling its siblings, but it does not swallow the exception, so
     * without this every unguarded `AppScope.scope.launch` was one throw away from closing the app (#68).
     * The busy overlay is cleared too: a job that died on its way to `finally` would otherwise leave it stuck.
     */
    private val reportErrors = CoroutineExceptionHandler { _, e ->
        Log.e("FlexNotes", "Background job failed", e)
        UiEvents.busy.value = null
        UiEvents.show(failure(e.message ?: e::class.java.simpleName), ResultLevel.Failure)
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + reportErrors)
}

suspend fun runPhotoImport(ctx: Context, uris: List<Uri>, forcedDate: String?, pose: String = Poses.NONE): PhotoImportResult {
    UiEvents.busy.value = ctx.resources.getQuantityString(R.plurals.ia_importing, uris.size, uris.size)
    try {
        val r = PhotoImporter.importUris(ctx, uris, forcedDate, pose) { d, t -> UiEvents.busy.value = ctx.getString(R.string.ia_progress, d, t) }
        UiEvents.show(importResultText(ctx.resources, r))

        return r
    } finally {
        UiEvents.busy.value = null
    }
}

/**
 * Runs a long task with the busy overlay and shows its result message: a failure as a dialog the user has to
 * acknowledge, a success as a snackbar that is also kept for Settings → Backup (#62).
 */
fun runBusy(label: String, block: suspend () -> ImportSummary?) {
    AppScope.scope.launch {
        UiEvents.busy.value = label
        try {
            block()?.let { UiEvents.show(it.message, it.level()) }
        } catch (e: Exception) {
            UiEvents.show(AppScope.failure(e.message.orEmpty()), ResultLevel.Failure)
        } finally {
            UiEvents.busy.value = null
        }
    }
}

/** The weight a finished job's result deserves (#62). */
fun ImportSummary.level(): ResultLevel = if (ok) ResultLevel.Success else ResultLevel.Failure

/** Photos that were picked or shared and are waiting for the user to choose their pose before import. */
class PendingPhotoImport(
    val uris: List<Uri>,
    val forcedDate: String? = null,
    val onDone: (PhotoImportResult) -> Unit = {},
    /** The pose, when it was already chosen (the camera, #183): no pose question is asked. */
    val pose: String? = null
)

/** Every bulk photo import goes through here, so each one asks for a pose first (see [PhotoImportHost]). */
object PhotoImports {
    val pending = MutableStateFlow<PendingPhotoImport?>(null)
    fun request(p: PendingPhotoImport) { pending.value = p }
}

/**
 * Shows the pose question for a pending photo import, then runs the import. Placed once in the app root. With a
 * pose for new photos chosen in Settings (#46), the question is skipped and that pose is used.
 */
@Composable
fun PhotoImportHost() {
    val pending by PhotoImports.pending.collectAsState()
    val prefs by Settings.portable.collectAsState()
    val ctx = LocalContext.current.applicationContext
    val p = pending ?: return
    val defaultPose = p.pose ?: prefs.photoDefaultPose
    if (defaultPose != null) {
        LaunchedEffect(p) {
            PhotoImports.pending.value = null
            AppScope.scope.launch {
                try {
                    p.onDone(runPhotoImport(ctx, p.uris, p.forcedDate, defaultPose))
                } catch (e: Exception) {
                    UiEvents.show(AppScope.failure(e.message.orEmpty()))
                }
            }
        }
        return
    }
    ImportPoseDialog(
        count = p.uris.size,
        onCancel = {
            PhotoImports.pending.value = null
            UiEvents.show(ctx.getString(R.string.ia_cancelled))
        },
        onPick = { pose ->
            PhotoImports.pending.value = null
            AppScope.scope.launch {
                try {
                    p.onDone(runPhotoImport(ctx, p.uris, p.forcedDate, pose))
                } catch (e: Exception) {
                    UiEvents.show(AppScope.failure(e.message.orEmpty()))
                }
            }
        }
    )
}

/** Multi-select photo picker (keeps full EXIF metadata, unlike the photo picker). */
@Composable
fun rememberPhotoImporter(forcedDate: String? = null, onDone: (PhotoImportResult) -> Unit = {}): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) PhotoImports.request(PendingPhotoImport(uris, forcedDate, onDone))
    }
    return { launcher.launch(arrayOf("image/*")) }
}

/**
 * The ways to add photos (#182): Take photo, FlexNotes's camera, when the phone has one, and Choose from phone. Every
 * "add photos" control offers these, dated to [date] (null: the camera uses today, the picker each photo's own date).
 */
@Composable
fun rememberAddPhotoActions(nav: Nav, date: String?, onDone: (PhotoImportResult) -> Unit = {}): List<MenuAction> {
    val ctx = LocalContext.current
    val hasCamera = remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val choose = rememberPhotoImporter(forcedDate = date, onDone = onDone)
    val take = stringResource(R.string.photo_take)
    val pick = stringResource(R.string.photo_choose)
    return listOfNotNull(
        if (hasCamera) MenuAction(take) { nav.push(Screen.Camera(date)) } else null,
        MenuAction(pick) { choose() }
    )
}

/** Imports every image in a chosen folder (and its sub-folders). */
@Composable
fun rememberFolderPhotoImporter(onDone: (PhotoImportResult) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current.applicationContext
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) AppScope.scope.launch {
            UiEvents.busy.value = ctx.getString(R.string.ia_scanning)
            val uris = try { PhotoImporter.listFolderImages(ctx, tree) } finally { UiEvents.busy.value = null }
            if (uris.isEmpty()) UiEvents.show(ctx.getString(R.string.ia_no_images))
            else PhotoImports.request(PendingPhotoImport(uris, null, onDone))
        }
    }
    return { launcher.launch(null) }
}

package com.fitlens.companion.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.Poses
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a shot waits for Retake or Use photo. The import copies it into the photo folder, then it's deleted. */
private const val CAMERA_DIR = "camera"

/** Self-timer choices, in seconds (0 is off). */
private val TIMERS = listOf(0, 3, 10)

/**
 * FlexNotes's own camera for progress photos (#183), opened from Take photo (#182). It asks for the camera permission
 * when it opens; refused, it says why and offers the phone's settings, and comes back to the viewfinder once allowed.
 * Before a shot the pose is chosen, and a faint outline of the last photo in that pose can be shown so the shots line
 * up. A shot is reviewed (Retake or Use photo) and then goes through the same import as Choose from phone, on [date]
 * (null: today) with its pose. Nothing is written to the phone's gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(snap: Snapshot, nav: Nav, date: String?) {
    val ctx = LocalContext.current
    val day = date ?: Dates.today()
    var granted by remember { mutableStateOf(cameraAllowed(ctx)) }
    var refused by rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
    }
    // Back from the phone's settings with the camera allowed: straight to the viewfinder.
    LifecycleResumeEffect(Unit) {
        granted = cameraAllowed(ctx)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        // Shots left behind by an import that failed or a closed app; recent ones may still be importing.
        val stale = System.currentTimeMillis() - 10 * 60 * 1000L
        File(ctx.cacheDir, CAMERA_DIR).listFiles()?.forEach { if (it.lastModified() < stale) it.delete() }
        if (!granted && !asked) {
            asked = true
            ask.launch(Manifest.permission.CAMERA)
        }
    }
    var pose by rememberSaveable {
        mutableStateOf(Settings.portable.value.photoDefaultPose?.takeIf { it in Poses.all } ?: Poses.all.first())
    }
    // The shot being reviewed, as a path, so it survives rotation.
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    fun discard() {
        shot?.let { File(it).delete() }
        shot = null
    }
    BackHandler(enabled = shot != null) { discard() }

    Column(Modifier.fillMaxSize().background(Brand.Black)) {
        FitTopBar(
            title = stringResource(R.string.cam_title),
            subtitle = poseText(ctx.resources, pose),
            onBack = { discard(); nav.pop() }
        )
        val file = shot?.let { File(it) }
        when {
            !granted -> PermissionNote(
                refused = refused,
                onAsk = { ask.launch(Manifest.permission.CAMERA) },
                onSettings = { openAppSettings(ctx) }
            )
            file != null -> Review(
                file = file,
                onRetake = { discard() },
                onUse = {
                    shot = null
                    PhotoImports.request(
                        PendingPhotoImport(listOf(Uri.fromFile(file)), day, onDone = { file.delete() }, pose = pose)
                    )
                    nav.pop()
                }
            )
            else -> Viewfinder(snap, pose, onPose = { pose = it }, onShot = { shot = it.path })
        }
    }
}

private fun cameraAllowed(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Why the camera needs the permission, with Allow camera and, once refused, the phone's settings. */
@Composable
private fun PermissionNote(refused: Boolean, onAsk: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(FitIcons.Camera, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(Spacing.xxl))
        Spacer(Modifier.size(Spacing.lg))
        Text(
            stringResource(if (refused) R.string.cam_refused else R.string.cam_why),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(Spacing.xl))
        GoldButton(onClick = onAsk) { Text(stringResource(R.string.cam_allow)) }
        if (refused) {
            Spacer(Modifier.size(Spacing.sm))
            GlassOutlinedButton(onClick = onSettings) { Text(stringResource(R.string.cam_open_settings)) }
        }
    }
}

/**
 * The live preview and its controls: the pose, the outline of the last photo in that pose, the self-timer, the
 * shutter and the lens switch. [onShot] gets the saved file.
 */
@Composable
private fun Viewfinder(snap: Snapshot, pose: String, onPose: (String) -> Unit, onShot: (File) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var lens by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var outline by rememberSaveable { mutableStateOf(true) }
    var timer by rememberSaveable { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            provider = try { future.get() } catch (e: Exception) { failed = true; null }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { provider?.unbindAll() }
    }
    val lenses = remember(provider) {
        val p = provider
        if (p == null) emptyList() else listOf(CameraSelector.LENS_FACING_BACK, CameraSelector.LENS_FACING_FRONT).filter { facing ->
            try { p.hasCamera(CameraSelector.Builder().requireLensFacing(facing).build()) } catch (e: Exception) { false }
        }
    }
    val facing = if (lens in lenses) lens else lenses.firstOrNull()
    DisposableEffect(provider, facing) {
        val p = provider
        if (p != null) {
            if (facing == null) {
                failed = true
            } else {
                try {
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    p.unbindAll()
                    p.bindToLifecycle(owner, CameraSelector.Builder().requireLensFacing(facing).build(), preview, capture)
                    failed = false
                } catch (e: Exception) {
                    failed = true
                }
            }
        }
        onDispose { }
    }

    fun shoot() {
        val dir = File(ctx.cacheDir, CAMERA_DIR).apply { mkdirs() }
        val file = File(dir, "FlexNotes_${System.currentTimeMillis()}.jpg")
        // A selfie is kept as it was seen in the preview, as a mirror shows it.
        val meta = ImageCapture.Metadata().apply { isReversedHorizontal = facing == CameraSelector.LENS_FACING_FRONT }
        previewView.display?.let { capture.targetRotation = it.rotation }
        busy = true
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).setMetadata(meta).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    busy = false
                    onShot(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    file.delete()
                    UiEvents.show(ctx.getString(R.string.cam_failed))
                }
            }
        )
    }

    fun shutter() {
        if (busy) return
        val running = countdownJob
        if (running != null) {
            // Tapping during the countdown cancels it.
            running.cancel()
            countdownJob = null
            countdown = 0
            return
        }
        if (timer == 0) {
            shoot()
            return
        }
        countdownJob = scope.launch {
            for (s in timer downTo 1) {
                countdown = s
                delay(1000)
            }
            countdown = 0
            countdownJob = null
            shoot()
        }
    }

    val last: Photo? = remember(snap.photosKey, pose) {
        snap.photos.filter { it.pose == pose && it.date != null }.maxByOrNull { (it.date ?: "") + (it.takenAt ?: "") }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (outline && last != null) {
                AsyncImage(
                    model = snap.photoFile(last),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.3f)
                )
            }
            if (countdown > 0) {
                Text(
                    countdown.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Brand.Gold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (failed) {
                Text(
                    stringResource(R.string.cam_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl)
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Brand.Black).navigationBarsPadding().padding(vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PoseChoice(pose, onPose)
            if (last != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.cam_outline, poseText(ctx.resources, pose)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = outline, onCheckedChange = { outline = it })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timerLabel = if (timer == 0) stringResource(R.string.cam_timer_off) else stringResource(R.string.cam_timer_seconds, timer)
                val timerDescription = stringResource(R.string.cam_timer)
                Column(
                    Modifier
                        .width(72.dp)
                        .clip(FitShapes.row)
                        .clickable { timer = TIMERS[(TIMERS.indexOf(timer) + 1) % TIMERS.size] }
                        .semantics(mergeDescendants = true) {
                            contentDescription = timerDescription
                            stateDescription = timerLabel
                        }
                        .padding(vertical = Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(FitIcons.Timer, contentDescription = null, tint = Brand.Gold)
                    Text(timerLabel, style = MaterialTheme.typography.labelSmall)
                }
                Shutter(enabled = !failed && provider != null && !busy, counting = countdown > 0) { shutter() }
                Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                    if (lenses.size > 1) {
                        IconButton(onClick = {
                            lens = if (facing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                        }) {
                            Icon(FitIcons.CameraSwitch, contentDescription = stringResource(R.string.cam_switch), tint = Brand.Gold)
                        }
                    }
                }
            }
        }
    }
}

/** The pose for the next shot, one of [Poses.all], as a row of choices. */
@Composable
private fun PoseChoice(pose: String, onPose: (String) -> Unit) {
    val res = LocalContext.current.resources
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Poses.all.forEach { p ->
            val chosen = p == pose
            Text(
                poseText(res, p).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (chosen) Brand.Gold else Brand.Muted,
                modifier = Modifier
                    .clip(FitShapes.row)
                    .border(1.dp, if (chosen) Brand.Gold else Brand.Hairline, FitShapes.row)
                    .background(if (chosen) Brand.GoldDusk else Brand.Black)
                    .selectable(selected = chosen, role = Role.RadioButton) { onPose(p) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
        }
    }
}

/** The round gold shutter; while the self-timer counts down it shows a hollow ring, and a tap cancels. */
@Composable
private fun Shutter(enabled: Boolean, counting: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (counting) R.string.cam_cancel_timer else R.string.cam_shutter)
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(3.dp, if (enabled) Brand.Gold else Brand.Outline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (!counting) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(if (enabled) Brand.Gold else Brand.Outline))
        }
    }
}

/** The shot, with Retake and Use photo. */
@Composable
private fun Review(file: File, onRetake: () -> Unit, onUse: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AsyncImage(
            model = file,
            contentDescription = stringResource(R.string.cam_review),
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            GlassOutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cam_retake)) }
            GoldButton(onClick = onUse, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cam_use)) }
        }
    }
}

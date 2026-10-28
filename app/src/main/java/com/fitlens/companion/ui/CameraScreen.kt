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
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.fitlens.companion.data.BodyOutline
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.PoseOutlines
import com.fitlens.companion.data.Poses
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.design.FitIcons
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a shot waits for Retake or Use photo. The import copies it into the photo folder, then it's deleted. */
private const val CAMERA_DIR = "camera"

/** Self-timer choices, in seconds (0 is off). */
private val TIMERS = listOf(0, 3, 10)

/**
 * FlexNotes's own camera for progress photos (#183), opened from Take photo (#182). It asks for the camera permission
 * when it opens; refused, it says why and offers the phone's settings, and comes back to the viewfinder once allowed.
 * Before a shot the pose is chosen, and the last photo in that pose can be shown, translucent and with a bold white
 * outline of the body traced on the phone (#230), so the shots line up. A shot is reviewed (Retake or Use photo) and
 * then goes through the same import as Choose from phone, on [date] (null: today) with its pose. Nothing is written to the phone's gallery.
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
        withContext(Dispatchers.IO) {
            File(ctx.cacheDir, CAMERA_DIR).listFiles()?.forEach { if (it.lastModified() < stale) it.delete() }
        }
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
    val device by Settings.device.collectAsState()
    // The body in the last photo, worked out once per photo off the main thread and cached (#230).
    var body by remember(last) { mutableStateOf<BodyOutline?>(null) }
    var tracing by remember(last) { mutableStateOf(last != null) }
    LaunchedEffect(last) {
        body = last?.let { PoseOutlines.of(ctx, snap.photoFile(it)) }
        tracing = false
    }
    var opacity by remember(device.cameraPhotoOpacity) { mutableFloatStateOf(device.cameraPhotoOpacity.toFloat()) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (outline && last != null) {
                if (opacity > 0f) {
                    AsyncImage(
                        model = snap.photoFile(last),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(opacity / 100f)
                    )
                }
                body?.takeIf { it.found }?.let { OutlineLines(it, device.cameraOutlineLine, Modifier.fillMaxSize()) }
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
                if (outline) {
                    OutlineControls(
                        opacity = opacity,
                        onOpacity = { opacity = it },
                        onOpacityDone = { val v = opacity.toInt(); Settings.updateDevice { it.copy(cameraPhotoOpacity = v) } },
                        line = device.cameraOutlineLine,
                        onLine = { v -> Settings.updateDevice { it.copy(cameraOutlineLine = v) } },
                        noBody = !tracing && body?.found != true
                    )
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
    ChoiceRow(Poses.all.map { it to poseText(res, it) }, pose, onPose, Modifier.padding(horizontal = Spacing.lg))
}

/** A row of single choices, as gold-outlined chips; it scrolls sideways when they don't fit. */
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, chosen: T, onChoose: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        options.forEach { (value, label) ->
            val on = value == chosen
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (on) Brand.Gold else Brand.Muted,
                modifier = Modifier
                    .clip(FitShapes.row)
                    .border(1.dp, if (on) Brand.Gold else Brand.Hairline, FitShapes.row)
                    .background(if (on) Brand.GoldDusk else Brand.Black)
                    .selectable(selected = on, role = Role.RadioButton) { onChoose(value) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )
        }
    }
}

/**
 * The body outline over the preview (#230): white, the one colour outside the brand, used only to line shots up, over
 * a soft dark stroke so it shows on light backgrounds too. Mapped with the same centre crop as the photo under it.
 */
@Composable
private fun OutlineLines(outline: BodyOutline, line: Int, modifier: Modifier) {
    Canvas(modifier.clipToBounds()) {
        val scale = maxOf(size.width / outline.width, size.height / outline.height)
        val w = outline.width * scale
        val h = outline.height * scale
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val path = Path()
        outline.loops.forEach { loop ->
            path.moveTo(left + loop[0] * w, top + loop[1] * h)
            var i = 2
            while (i + 1 < loop.size) {
                path.lineTo(left + loop[i] * w, top + loop[i + 1] * h)
                i += 2
            }
            path.close()
        }
        val stroke = when (line) {
            BodyOutline.THIN -> 1.5.dp
            BodyOutline.MEDIUM -> 2.5.dp
            else -> 4.dp
        }.toPx()
        drawPath(path, Brand.AlignShadow, style = Stroke(stroke + 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Brand.AlignWhite, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The outline's settings, both remembered on this phone: how much of the last photo shows, and the line's weight. */
@Composable
private fun OutlineControls(
    opacity: Float,
    onOpacity: (Float) -> Unit,
    onOpacityDone: () -> Unit,
    line: Int,
    onLine: (Int) -> Unit,
    noBody: Boolean
) {
    val opacityLabel = stringResource(R.string.cam_photo_opacity)
    val opacityValue = stringResource(R.string.cam_photo_opacity_value, opacity.toInt())
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        if (noBody) {
            Text(
                stringResource(R.string.cam_no_body),
                style = MaterialTheme.typography.bodySmall,
                color = Brand.Muted,
                modifier = Modifier.padding(bottom = Spacing.xs)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(opacityValue, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(96.dp))
            Slider(
                value = opacity,
                onValueChange = onOpacity,
                valueRange = 0f..60f,
                steps = 11,
                onValueChangeFinished = onOpacityDone,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = opacityLabel
                    stateDescription = opacityValue
                }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cam_line), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(96.dp))
            ChoiceRow(
                listOf(
                    BodyOutline.THIN to stringResource(R.string.cam_line_thin),
                    BodyOutline.MEDIUM to stringResource(R.string.cam_line_medium),
                    BodyOutline.BOLD to stringResource(R.string.cam_line_bold)
                ),
                line,
                onLine
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

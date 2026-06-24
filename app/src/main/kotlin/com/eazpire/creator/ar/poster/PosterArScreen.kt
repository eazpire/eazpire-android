package com.eazpire.creator.ar.poster

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.eazpire.creator.R
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.modal.EazFullScreenDialog
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Session
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberAREnvironment
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelLoader
import com.google.ar.core.Pose

private class DeferredArLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resumeAr() {
        if (registry.currentState == Lifecycle.State.CREATED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (registry.currentState == Lifecycle.State.STARTED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    fun destroy() {
        if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}

private enum class ArCoreSupport {
    Checking,
    Ready,
    Unsupported,
    NeedsInstall,
    InstallInProgress,
    InstallFailed,
}

private fun resolveArCoreSupport(context: android.content.Context): ArCoreSupport =
    when (ArCoreApk.getInstance().checkAvailability(context)) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCoreSupport.Ready
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
        -> ArCoreSupport.NeedsInstall
        else -> ArCoreSupport.Unsupported
    }

private fun requestArCoreInstall(activity: Activity): ArCoreSupport =
    runCatching {
        when (ArCoreApk.getInstance().requestInstall(activity, true)) {
            ArCoreApk.InstallStatus.INSTALLED -> ArCoreSupport.Ready
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> ArCoreSupport.InstallInProgress
            else -> ArCoreSupport.InstallFailed
        }
    }.getOrElse { ArCoreSupport.InstallFailed }

@Composable
fun PosterArOverlay(
    config: PosterArSessionConfig,
    onDismiss: () -> Unit,
) {
    EazFullScreenDialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxSize()) {
            PosterArScreen(config = config, onClose = onDismiss)
        }
    }
}

@Composable
fun PosterArScreen(
    config: PosterArSessionConfig,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    var arCoreSupport by remember { mutableStateOf(ArCoreSupport.Checking) }

    LaunchedEffect(hasCamera) {
        if (!hasCamera) return@LaunchedEffect
        arCoreSupport = resolveArCoreSupport(context)
    }

    LaunchedEffect(arCoreSupport) {
        if (arCoreSupport != ArCoreSupport.NeedsInstall) return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        arCoreSupport = requestArCoreInstall(activity)
    }

    DisposableEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    when {
        !hasCamera -> PosterArMessagePanel(
            message = stringResource(R.string.poster_ar_camera_denied),
            actionLabel = stringResource(R.string.poster_ar_allow_camera),
            onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onClose = onClose,
        )

        arCoreSupport == ArCoreSupport.Checking -> PosterArLoadingPanel(onClose)

        arCoreSupport == ArCoreSupport.Unsupported -> PosterArMessagePanel(
            message = stringResource(R.string.poster_ar_unsupported),
            onClose = onClose,
        )

        arCoreSupport == ArCoreSupport.InstallFailed ||
            arCoreSupport == ArCoreSupport.NeedsInstall ||
            arCoreSupport == ArCoreSupport.InstallInProgress -> PosterArMessagePanel(
            message = when (arCoreSupport) {
                ArCoreSupport.InstallInProgress -> stringResource(R.string.poster_ar_installing)
                else -> stringResource(R.string.poster_ar_install_failed)
            },
            actionLabel = stringResource(R.string.poster_ar_retry),
            onAction = {
                val activity = context as? Activity ?: return@PosterArMessagePanel
                arCoreSupport = requestArCoreInstall(activity)
            },
            onClose = onClose,
        )

        else -> {
            DisposableEffect(Unit) {
                ArCoreSensorWarmup.start(context)
                onDispose { ArCoreSensorWarmup.stop() }
            }
            PosterArScene(config = config, onClose = onClose)
        }
    }
}

@Composable
private fun PosterArScene(
    config: PosterArSessionConfig,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val arLifecycleOwner = remember { DeferredArLifecycleOwner() }
    var arSessionFailed by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    LaunchedEffect(isClosing) {
        if (!isClosing) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        onClose()
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        if (!isClosing) arLifecycleOwner.resumeAr()
    }

    DisposableEffect(arLifecycleOwner) {
        onDispose {
            if (!isClosing) arLifecycleOwner.destroy()
        }
    }

    if (arSessionFailed) {
        PosterArMessagePanel(
            message = stringResource(R.string.poster_ar_session_failed),
            onClose = onClose,
        )
        return
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environment = rememberAREnvironment(engine)
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 120_000f
    }

    var placementAnchor by remember { mutableStateOf<Anchor?>(null) }
    var previewAnchor by remember { mutableStateOf<Anchor?>(null) }
    var lastPreviewPose by remember { mutableStateOf<Pose?>(null) }
    var lastPlaneHit by remember { mutableStateOf<HitResult?>(null) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }
    var hasValidSurface by remember { mutableStateOf(false) }
    var placementIsFloor by remember { mutableStateOf(false) }

    var sizeIndex by remember(config) {
        mutableIntStateOf(config.initialSizeIndex.coerceIn(0, config.sizeEntries.lastIndex))
    }
    var paperIndex by remember(config) {
        mutableIntStateOf(config.initialPaperIndex.coerceAtLeast(0))
    }
    var artworkBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageLoading by remember { mutableStateOf(false) }

    val currentEntry = config.resolveEntry(sizeIndex, paperIndex)
        ?: config.sizeEntries.getOrNull(sizeIndex)
        ?: config.sizeEntries.first()
    val isPlaced = placementAnchor != null

    fun requestClose() {
        if (isClosing) return
        isClosing = true
        previewAnchor?.detach()
        placementAnchor?.detach()
        arLifecycleOwner.destroy()
    }

    fun placeAt(screenX: Float, screenY: Float) {
        val frame = latestFrame ?: return
        val hit = findPosterWallHitAtScreenPoint(frame, screenX, screenY)
            ?.takeIf { it.isValidWallHit() || it.isValidFloorHit() }
            ?: lastPlaneHit?.takeIf { it.isValidWallHit() || it.isValidFloorHit() }
            ?: return
        placementIsFloor = hit.isValidFloorHit()
        placementAnchor?.detach()
        placementAnchor = hit.createAnchor()
        previewAnchor?.detach()
        previewAnchor = null
        lastPreviewPose = null
    }

    LaunchedEffect(currentEntry.imageUrl) {
        imageLoading = true
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(currentEntry.imageUrl)
                .allowHardware(false)
                .build(),
        )
        artworkBitmap = (result as? SuccessResult)?.drawable?.toBitmap()
        imageLoading = false
    }

    LaunchedEffect(sizeIndex, paperIndex) {
        config.onSelectionChange(sizeIndex, paperIndex)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isClosing) {
            ARScene(
                modifier = Modifier.fillMaxSize(),
                surfaceType = SurfaceType.TextureSurface,
                engine = engine,
                modelLoader = modelLoader,
                environment = environment,
                mainLightNode = mainLightNode,
                planeRenderer = !isPlaced,
                lifecycle = arLifecycleOwner.lifecycle,
                sessionConfiguration = { _, configAr ->
                    configAr.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    configAr.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                },
                onSessionFailed = {
                    if (!isClosing) arSessionFailed = true
                },
                onSessionUpdated = { _: Session, frame: Frame ->
                    if (isClosing) return@ARScene
                    latestFrame = frame
                    val planeHit = findPosterWallHit(frame, screenWidthPx, screenHeightPx)
                    hasValidSurface = planeHit != null
                    lastPlaneHit = planeHit

                    if (!isPlaced && planeHit != null) {
                        val hitPose = planeHit.hitPose
                        if (shouldUpdatePosterPreviewAnchor(lastPreviewPose, hitPose)) {
                            previewAnchor?.detach()
                            previewAnchor = planeHit.createAnchor()
                            lastPreviewPose = hitPose
                        }
                    } else if (isPlaced) {
                        previewAnchor?.detach()
                        previewAnchor = null
                        lastPreviewPose = null
                    }
                },
            ) {
                val size = currentEntry.physicalSize
                val posterSize = Size(x = size.widthM, y = size.heightM)
                val planeRotation = if (placementIsFloor) Rotation(x = -90f) else Rotation()

                if (!isPlaced) {
                    previewAnchor?.let { preview ->
                        AnchorNode(anchor = preview) {
                            artworkBitmap?.let { bitmap ->
                                Node(rotation = planeRotation) {
                                    ImageNode(
                                        bitmap = bitmap,
                                        size = posterSize,
                                    )
                                }
                            }
                        }
                    }
                }

                placementAnchor?.let { anchor ->
                    AnchorNode(anchor = anchor) {
                        artworkBitmap?.let { bitmap ->
                            Node(rotation = planeRotation) {
                                ImageNode(
                                    bitmap = bitmap,
                                    size = posterSize,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isPlaced) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(hasValidSurface) {
                        detectTapGestures { offset ->
                            if (hasValidSurface) placeAt(offset.x, offset.y)
                        }
                    },
            )
            if (!hasValidSurface) {
                Text(
                    text = stringResource(R.string.poster_ar_scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.poster_ar_tap_place),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(config.sizeEntries.size) {
                        var totalDrag = 0f
                        val threshold = 72f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, drag -> totalDrag += drag },
                            onDragEnd = {
                                if (config.sizeEntries.size <= 1) return@detectHorizontalDragGestures
                                when {
                                    totalDrag > threshold -> {
                                        sizeIndex = (sizeIndex - 1 + config.sizeEntries.size) % config.sizeEntries.size
                                    }
                                    totalDrag < -threshold -> {
                                        sizeIndex = (sizeIndex + 1) % config.sizeEntries.size
                                    }
                                }
                            },
                        )
                    },
            )
        }

        IconButton(
            onClick = { requestClose() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
                .zIndex(4f)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.poster_ar_close), tint = Color.White)
        }

        if (imageLoading) {
            CircularProgressIndicator(
                color = EazColors.Orange,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        PosterArControls(
            config = config,
            sizeIndex = sizeIndex,
            paperIndex = paperIndex,
            price = currentEntry.price,
            onSizeSelected = { sizeIndex = it },
            onPaperSelected = { paperIndex = it },
            onAddToCart = config.onAddToCart,
            onAddToFavorite = config.onAddToFavorite,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .zIndex(3f),
        )
    }
}

@Composable
private fun PosterArControls(
    config: PosterArSessionConfig,
    sizeIndex: Int,
    paperIndex: Int,
    price: Double,
    onSizeSelected: (Int) -> Unit,
    onPaperSelected: (Int) -> Unit,
    onAddToCart: () -> Unit,
    onAddToFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CHF %.2f".format(price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = config.sizeEntries.getOrNull(sizeIndex)?.label.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onAddToFavorite,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape),
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onAddToCart,
                    modifier = Modifier
                        .size(40.dp)
                        .background(EazColors.Orange, CircleShape),
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (config.paperValues.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                config.paperValues.forEachIndexed { index, paper ->
                    val active = index == paperIndex
                    Text(
                        text = paper,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) Color.White else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                if (active) EazColors.Orange else Color.White.copy(alpha = 0.12f),
                            )
                            .border(
                                width = 1.dp,
                                color = if (active) EazColors.Orange else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(100.dp),
                            )
                            .clickable { onPaperSelected(index) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            config.sizeEntries.forEachIndexed { index, entry ->
                val active = index == sizeIndex
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) Color.White else Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
                        .border(
                            width = if (active) 1.5.dp else 1.dp,
                            color = if (active) EazColors.Orange else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSizeSelected(index) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PosterArMessagePanel(
    message: String,
    onClose: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.ViewInAr,
                contentDescription = null,
                tint = EazColors.Orange,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = EazColors.Orange)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.poster_ar_close), color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun PosterArLoadingPanel(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = EazColors.Orange)
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
        }
    }
}

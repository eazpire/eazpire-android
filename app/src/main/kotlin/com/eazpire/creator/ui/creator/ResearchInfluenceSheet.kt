package com.eazpire.creator.ui.creator

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

data class ResearchInfluenceResult(
    val dataUrl: String,
    val originalDataUrl: String,
    val croppedDataUrl: String,
    val view: String,
    val mode: String,
    val similarity: Float,
)

@Composable
fun ResearchInfluenceSheet(
    handoff: ResearchGeneratorHandoff,
    translationStore: TranslationStore,
    onApply: (ResearchInfluenceResult) -> Unit,
    onCancel: () -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    val context = LocalContext.current
    var view by remember { mutableStateOf("cropped") }
    var genMode by remember { mutableStateOf("i2i") }
    var originalBmp by remember { mutableStateOf<Bitmap?>(null) }
    var croppedBmp by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(handoff.imageUrl) {
        if (handoff.imageUrl.isBlank()) {
            loadFailed = true
            return@LaunchedEffect
        }
        val bmp = loadResearchBitmap(context, handoff.imageUrl)
        originalBmp = bmp
        croppedBmp = if (bmp != null) ResearchPrintArea.crop(bmp) else null
        loadFailed = bmp == null
        if (bmp != null && croppedBmp === bmp) view = "original"
    }

    val shown = if (view == "original") originalBmp else (croppedBmp ?: originalBmp)

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20A0618))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                tr("creator.reference_influence.title", "Reference image influence"),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xB80A0618)),
                ) {
                    SegBtn(
                        label = tr("creator.generator.mode_image_to_image", "Image to Image"),
                        on = genMode == "i2i",
                        onClick = { genMode = "i2i" },
                    )
                    SegBtn(
                        label = tr("creator.generator.mode_text_to_image", "Text to Image"),
                        on = genMode == "t2i",
                        onClick = { genMode = "t2i" },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B1430)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    shown != null -> Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                    !loadFailed && handoff.imageUrl.isNotBlank() -> AsyncImage(
                        model = handoff.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                    )
                    else -> Text(tr("creator.research.unknown", "Unknown"), color = Color.White.copy(alpha = 0.6f))
                }
                if (genMode == "t2i") {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color(0x9E080618)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            tr("creator.generator.mode_text_to_image", "Text to Image"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xB80A0618)),
                ) {
                    SegBtn(
                        label = tr("creator.generator.view_original", "Original"),
                        on = view == "original",
                        onClick = { view = "original" },
                    )
                    SegBtn(
                        label = tr("creator.generator.view_cropped", "Cropped"),
                        on = view == "cropped",
                        onClick = { view = "cropped" },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(tr("creator.reference_influence.cancel", "Cancel"), color = Color.White)
                }
                TextButton(
                    onClick = {
                        val origUrl = originalBmp?.toDataUrl() ?: handoff.imageUrl
                        val cropUrl = croppedBmp?.toDataUrl() ?: origUrl
                        val shownUrl = if (view == "original") origUrl else cropUrl
                        onApply(
                            ResearchInfluenceResult(
                                dataUrl = shownUrl,
                                originalDataUrl = origUrl,
                                croppedDataUrl = cropUrl,
                                view = view,
                                mode = genMode,
                                similarity = 0.6f,
                            )
                        )
                    },
                ) {
                    Text(tr("creator.reference_influence.apply", "Apply"), color = EazColors.Orange)
                }
            }
        }
    }
}

@Composable
private fun SegBtn(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Color(0xFF0B1220) else Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(if (on) EazColors.Orange else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private const val RESEARCH_IMAGE_PROXY =
    "https://creator-engine.eazpire.workers.dev/apps/creator-dispatch?op=artifacts-mint-image-proxy&url="

private suspend fun loadResearchBitmap(context: Context, url: String): Bitmap? {
    val proxy = RESEARCH_IMAGE_PROXY + URLEncoder.encode(url, "UTF-8")
    for (candidate in listOf(url, proxy)) {
        try {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(candidate)
                    .allowHardware(false)
                    .build(),
            )
            val bmp = (result as? SuccessResult)?.drawable?.toBitmap()
            if (bmp != null) return bmp
        } catch (_: Exception) {
        }
    }
    return null
}

private fun Bitmap.toDataUrl(): String {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 90, out)
    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    return "data:image/png;base64,$b64"
}

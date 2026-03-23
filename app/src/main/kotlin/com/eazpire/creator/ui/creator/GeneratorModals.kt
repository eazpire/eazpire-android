package com.eazpire.creator.ui.creator

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.ApiLanguageChildren
import com.eazpire.creator.api.ApiLanguageItem
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.components.GlassCircularFlag
import com.eazpire.creator.ui.header.AVAILABLE_LANGUAGES
import com.eazpire.creator.ui.header.LocaleModalItem
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Dark theme colors matching web gen-select-overlay */
private val GenModalOverlay = Color(0x33000000)
private val GenModalBg = Color(0x730B1220)
private val GenModalBorder = Color(0x14FFFFFF)
private val GenModalHeaderBg = Color(0x8C0B1220)
private val GenModalBodyBg = Color(0x610B1220)
private val GenCardBg = Color(0x0FFFFFFF)
private val GenCardBorder = Color(0x1AFFFFFF)
private val GenCardBorderHover = Color(0x73F59E0B)
private val GenAccentSoft = Color(0x33F59E0B)
private val GenText = Color(0xFFF9FAFB)
private val GenMuted = Color(0xB3FFFFFF)

/** Light modal chrome for shop design studio (matches web eaz-shop-create-product). */
private val ShopLightModalOverlay = Color(0x990F172A)
private val ShopLightModalBg = Color(0xFFFFFFFF)
private val ShopLightModalHeaderBg = Color(0xFFF9FAFB)
private val ShopLightModalBodyBg = Color(0xFFF3F4F6)
private val ShopLightModalBorder = Color(0xFFE5E7EB)
private val ShopLightText = Color(0xFF111827)
private val ShopLightMuted = Color(0xFF6B7280)
private val ShopLightCardBg = Color(0xFFF9FAFB)
private val ShopLightCardBorder = Color(0xFFE5E7EB)

private data class GenModalChrome(
    val overlay: Color,
    val modalBg: Color,
    val headerBg: Color,
    val bodyBg: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val cardBg: Color,
    val cardBorder: Color
)

private fun genModalChrome(shopLight: Boolean): GenModalChrome {
    return if (shopLight) {
        GenModalChrome(
            overlay = ShopLightModalOverlay,
            modalBg = ShopLightModalBg,
            headerBg = ShopLightModalHeaderBg,
            bodyBg = ShopLightModalBodyBg,
            border = ShopLightModalBorder,
            text = ShopLightText,
            muted = ShopLightMuted,
            cardBg = ShopLightCardBg,
            cardBorder = ShopLightCardBorder
        )
    } else {
        GenModalChrome(
            overlay = GenModalOverlay,
            modalBg = GenModalBg,
            headerBg = GenModalHeaderBg,
            bodyBg = GenModalBodyBg,
            border = GenModalBorder,
            text = GenText,
            muted = GenMuted,
            cardBg = GenCardBg,
            cardBorder = GenCardBorder
        )
    }
}

private val REF_SOURCE_OPTIONS = listOf(
    "device" to Triple("Device", "Photo or image from your device", Icons.Default.Image),
    "camera" to Triple("Camera", "Take a photo now", Icons.Default.CameraAlt),
    "inspirations" to Triple("Inspirations", "Public designs from the community", Icons.Default.Star),
    "designs" to Triple("My Designs", "Generated & saved designs", Icons.Default.Folder),
    "canvas" to Triple("Canvas", "Create from scratch", Icons.Default.GridOn)
)

private data class GenStyleOption(
    val value: String,
    val labelKey: String,
    val fallback: String
)

private data class GenStyleCategory(
    val titleKey: String,
    val titleFallback: String,
    val options: List<GenStyleOption>
)

data class GenLanguageState(
    val mode: String = "as-design",
    val langCode: String = "",
    val langLabel: String = "",
    val dialectCode: String = "",
    val dialectLabel: String = "",
    val scriptCode: String = "",
    val scriptLabel: String = ""
)

data class GenColorState(
    val designColors: List<String> = emptyList(),
    val backgroundColors: List<String> = emptyList(),
    val backgroundTransparent: Boolean = true
)

private val GEN_COLOR_PRESETS = listOf(
    "#000000", "#ffffff", "#ef4444", "#f59e0b", "#22c55e", "#3b82f6", "#8b5cf6", "#ec4899"
)

private val GEN_STYLE_CATEGORIES = listOf(
    GenStyleCategory(
        "creator.style_modal.categories.modern_design",
        "Modern & Design Styles",
        listOf(
            GenStyleOption("modern", "creator.style_modal.styles.modern", "Modern"),
            GenStyleOption("minimalist", "creator.style_modal.styles.minimalist", "Minimalist"),
            GenStyleOption("clean-line-art", "creator.style_modal.styles.clean_line_art", "Clean Line Art"),
            GenStyleOption("flat-design", "creator.style_modal.styles.flat_design", "Flat Design"),
            GenStyleOption("bold-graphic", "creator.style_modal.styles.bold_graphic", "Bold Graphic"),
            GenStyleOption("geometric", "creator.style_modal.styles.geometric", "Geometric")
        )
    ),
    GenStyleCategory(
        "creator.style_modal.categories.drawing_illustration",
        "Drawing & Illustration",
        listOf(
            GenStyleOption("zeichnung", "creator.style_modal.styles.drawing", "Drawing"),
            GenStyleOption("handgezeichnet", "creator.style_modal.styles.hand_drawn", "Hand Drawn"),
            GenStyleOption("line-art", "creator.style_modal.styles.line_art", "Line Art"),
            GenStyleOption("cartoon", "creator.style_modal.styles.cartoon", "Cartoon"),
            GenStyleOption("comic-style", "creator.style_modal.styles.comic_style", "Comic Style"),
            GenStyleOption("manga", "creator.style_modal.styles.manga", "Manga")
        )
    ),
    GenStyleCategory(
        "creator.style_modal.categories.painting_art",
        "Painting & Art Techniques",
        listOf(
            GenStyleOption("painting", "creator.style_modal.styles.painting", "Painting"),
            GenStyleOption("acryl", "creator.style_modal.styles.acryl", "Acryl"),
            GenStyleOption("oelmalerei", "creator.style_modal.styles.oil_painting", "Oil Painting"),
            GenStyleOption("aquarell", "creator.style_modal.styles.watercolor", "Watercolor"),
            GenStyleOption("gouache", "creator.style_modal.styles.gouache", "Gouache"),
            GenStyleOption("pastell", "creator.style_modal.styles.pastel", "Pastel")
        )
    ),
    GenStyleCategory(
        "creator.style_modal.categories.material_surface",
        "Material & Surface Styles",
        listOf(
            GenStyleOption("gestickt", "creator.style_modal.styles.embroidered", "Embroidered"),
            GenStyleOption("stickerei-3d", "creator.style_modal.styles.embroidery_3d", "3D Embroidery"),
            GenStyleOption("siebdruck", "creator.style_modal.styles.screen_print", "Screen Print"),
            GenStyleOption("vintage-print", "creator.style_modal.styles.vintage_print", "Vintage Print"),
            GenStyleOption("graffiti", "creator.style_modal.styles.graffiti", "Graffiti"),
            GenStyleOption("pixel-art", "creator.style_modal.styles.pixel_art", "Pixel Art")
        )
    ),
    GenStyleCategory(
        "creator.style_modal.categories.retro_eras",
        "Retro & Time Eras",
        listOf(
            GenStyleOption("retro", "creator.style_modal.styles.retro", "Retro"),
            GenStyleOption("70s-style", "creator.style_modal.styles.70s_style", "70s Style"),
            GenStyleOption("80s-style", "creator.style_modal.styles.80s_style", "80s Style"),
            GenStyleOption("90s-style", "creator.style_modal.styles.90s_style", "90s Style"),
            GenStyleOption("y2k", "creator.style_modal.styles.y2k", "Y2K"),
            GenStyleOption("vintage", "creator.style_modal.styles.vintage", "Vintage")
        )
    ),
    GenStyleCategory(
        "creator.style_modal.categories.special_pod",
        "Special POD Styles",
        listOf(
            GenStyleOption("t-shirt-graphic", "creator.style_modal.styles.t_shirt_graphic", "T-Shirt Graphic"),
            GenStyleOption("streetwear", "creator.style_modal.styles.streetwear", "Streetwear"),
            GenStyleOption("merch-style", "creator.style_modal.styles.merch_style", "Merch Style"),
            GenStyleOption("print-ready", "creator.style_modal.styles.print_ready", "Print Ready"),
            GenStyleOption("sticker-style", "creator.style_modal.styles.sticker_style", "Sticker Style"),
            GenStyleOption("logo-style", "creator.style_modal.styles.logo_style", "Logo Style")
        )
    )
)

@Composable
fun GenRefSourceModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    onDevice: () -> Unit,
    onCamera: () -> Unit,
    onInspirations: () -> Unit,
    onMyDesigns: () -> Unit,
    onCanvas: () -> Unit,
    shopLightChrome: Boolean = false
) {
    if (!visible) return
    val c = genModalChrome(shopLightChrome)
    GenModalBase(
        title = translationStore.t("creator.generator.select_reference", "Select reference image"),
        onDismiss = onDismiss,
        shopLightChrome = shopLightChrome
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            REF_SOURCE_OPTIONS.forEach { (source, triple) ->
                val (titleKey, descKey, icon) = triple
                val title = when (source) {
                    "device" -> translationStore.t("creator.generator.source_device", "Device")
                    "camera" -> translationStore.t("creator.generator.source_camera", "Camera")
                    "inspirations" -> translationStore.t("creator.upload_source.inspirations", "Inspirations")
                    "designs" -> translationStore.t("creator.generator.source_designs", "My Designs")
                    "canvas" -> translationStore.t("creator.generator.source_canvas", "Canvas")
                    else -> titleKey
                }
                val desc = when (source) {
                    "device" -> translationStore.t("creator.generator.source_device_desc", "Photo or image from your device")
                    "camera" -> translationStore.t("creator.generator.source_camera_desc", "Take a photo now")
                    "inspirations" -> translationStore.t("creator.upload_source.inspirations_desc", "Public designs from the community")
                    "designs" -> translationStore.t("creator.generator.source_designs_desc", "Generated & saved designs")
                    "canvas" -> translationStore.t("creator.generator.source_canvas_desc", "Create from scratch")
                    else -> descKey
                }
                val iconColor = when (source) {
                    "device" -> Color(0xFFA78BFA)
                    "camera" -> Color(0xFF67E8F9)
                    "inspirations" -> c.text
                    "designs" -> Color(0xFFEAB308)
                    "canvas" -> Color(0xFF60A5FA)
                    else -> c.text
                }
                GenRefSourceCard(
                    title = title,
                    desc = desc,
                    icon = icon,
                    iconColor = iconColor,
                    chrome = c,
                    onClick = {
                        onDismiss()
                        when (source) {
                            "device" -> onDevice()
                            "camera" -> onCamera()
                            "inspirations" -> onInspirations()
                            "designs" -> onMyDesigns()
                            "canvas" -> onCanvas()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GenRefSourceCard(
    title: String,
    desc: String,
    icon: ImageVector,
    iconColor: Color,
    chrome: GenModalChrome,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(chrome.cardBg)
            .border(1.dp, chrome.cardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = chrome.text
            )
            Text(
                text = desc,
                fontSize = 13.sp,
                color = chrome.muted
            )
        }
    }
}

@Composable
fun GenTargetProductModal(
    visible: Boolean,
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    translationStore: TranslationStore
) {
    if (!visible) return
    val options = listOf(
        "all" to translationStore.t("creator.generator.anything", "Anything"),
        "unisex-softstyle-cotton-tee" to "Unisex Softstyle Cotton Tee"
    )
    GenModalBase(
        title = translationStore.t("creator.generator.target_product", "Target product"),
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { (value, label) ->
                GenSelectOptionCard(
                    label = label,
                    selected = currentValue == value,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSelect(value)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun GenDesignTypeModal(
    visible: Boolean,
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    translationStore: TranslationStore
) {
    if (!visible) return
    val options = listOf(
        "classic" to translationStore.t("creator.design_type.classic", "Classic"),
        "pattern" to "Pattern",
        "all-over" to "All-Over",
        "full-coverage" to "Full-Coverage",
        "panorama" to "Panorama"
    )
    GenModalBase(
        title = translationStore.t("creator.generator.design_type", "Design type"),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { (value, label) ->
                        GenSelectOptionCard(
                            label = label,
                            selected = currentValue == value,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenSelectOptionCard(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GenAccentSoft else GenCardBg)
            .border(
                1.dp,
                if (selected) EazColors.Orange else GenCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp))
    {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = GenText
        )
    }
}

@Composable
fun GenOptionsModal(
    visible: Boolean,
    ratio: String,
    contentType: String,
    selectedStyles: List<String>,
    languageState: GenLanguageState,
    colorState: GenColorState,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onRatioChange: (String) -> Unit,
    onContentTypeChange: (String) -> Unit,
    onStylesChange: (List<String>) -> Unit,
    onLanguageChange: (GenLanguageState) -> Unit,
    onColorStateChange: (GenColorState) -> Unit,
    api: CreatorApi,
    translationStore: TranslationStore
) {
    val applyLabel = translationStore.t("creator.common.apply", "Apply")
    if (!visible) return
    var showStylesModal by remember { mutableStateOf(false) }
    var showLanguageModal by remember { mutableStateOf(false) }
    var showColorsModal by remember { mutableStateOf(false) }

    GenStylesModal(
        visible = showStylesModal,
        selected = selectedStyles,
        onDismiss = { showStylesModal = false },
        onApply = {
            onStylesChange(it)
            showStylesModal = false
        },
        translationStore = translationStore
    )
    GenLanguageModal(
        visible = showLanguageModal,
        state = languageState,
        onDismiss = { showLanguageModal = false },
        onApply = {
            onLanguageChange(it)
            showLanguageModal = false
        },
        api = api,
        translationStore = translationStore
    )
    GenColorsModal(
        visible = showColorsModal,
        state = colorState,
        onDismiss = { showColorsModal = false },
        onApply = {
            onColorStateChange(it)
            showColorsModal = false
        },
        translationStore = translationStore
    )

    GenModalBase(
        title = translationStore.t("creator.generator.more_options", "More options"),
        onDismiss = onDismiss,
        showApply = true,
        applyLabel = applyLabel,
        onApply = onApply
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            GenOptionsRow(
                label = translationStore.t("creator.generator.ratio_title", "Design Ratio"),
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            "square" to translationStore.t("creator.generator.ratio_square", "Square"),
                            "portrait" to translationStore.t("creator.generator.ratio_portrait", "Portrait"),
                            "landscape" to translationStore.t("creator.generator.ratio_landscape", "Landscape")
                        ).forEach { (value, label) ->
                            GenRatioButton(
                                modifier = Modifier.weight(1f),
                                ratioValue = value,
                                label = label,
                                selected = ratio == value,
                                onClick = { onRatioChange(value) }
                            )
                        }
                    }
                }
            )
            GenOptionsRow(
                label = translationStore.t("creator.generator.content_type_title", "Content Type"),
                content = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            "design-text" to translationStore.t("creator.generator.content_design_text", "Design + Text"),
                            "design-only" to translationStore.t("creator.generator.content_design_only", "Design Only"),
                            "text-only" to translationStore.t("creator.generator.content_text_only", "Text Only")
                        ).forEach { (value, label) ->
                            GenContentTypeCard(
                                modifier = Modifier.weight(1f),
                                contentTypeValue = value,
                                label = label,
                                selected = contentType == value,
                                onClick = { onContentTypeChange(value) }
                            )
                        }
                    }
                }
            )
            GenOptionsRow(
                label = translationStore.t("creator.generator.style_title", "Design Style"),
                content = {
                    GenSummaryButton(
                        text = "${selectedStyles.size} ${translationStore.t("creator.generator.styles_selected", "styles selected")}",
                        onClick = { showStylesModal = true }
                    )
                }
            )
            GenOptionsRow(
                label = translationStore.t("creator.generator.language_title", "Language"),
                content = {
                    GenSummaryButton(
                        text = languageSummary(languageState, translationStore),
                        onClick = { showLanguageModal = true }
                    )
                }
            )
            GenOptionsRow(
                label = translationStore.t("creator.generator.design_colors", "Design colors"),
                content = {
                    GenSummaryButton(
                        text = colorSummary(colorState, translationStore),
                        onClick = { showColorsModal = true }
                    )
                    GenColorPreviewRow(colorState = colorState, translationStore = translationStore)
                }
            )
        }
    }
}

@Composable
private fun GenOptionsRow(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = GenMuted
        )
        content()
    }
}

@Composable
private fun GenRatioButton(
    ratioValue: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GenAccentSoft else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (selected) EazColors.Orange else GenCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GenRatioIcon(ratioValue = ratioValue)
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = GenText
            )
        }
    }
}

@Composable
private fun GenContentTypeCard(
    contentTypeValue: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GenAccentSoft else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (selected) EazColors.Orange else GenCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            GenContentTypeIcon(contentTypeValue = contentTypeValue)
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GenText
            )
        }
    }
}

@Composable
private fun GenRatioIcon(ratioValue: String) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val iconColor = GenText
        val (w, h) = when (ratioValue) {
            "square" -> Pair(size.width * 0.72f, size.height * 0.72f)
            "landscape" -> Pair(size.width * 0.86f, size.height * 0.58f)
            else -> Pair(size.width * 0.58f, size.height * 0.86f)
        }
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        drawRoundRect(
            color = iconColor,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
    }
}

@Composable
private fun GenContentTypeIcon(contentTypeValue: String) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val iconColor = GenText
        when (contentTypeValue) {
            "design-text" -> {
                val pad = 2.dp.toPx()
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(pad, pad),
                    size = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
                drawLine(iconColor, Offset(size.width * 0.5f, pad + 1.dp.toPx()), Offset(size.width * 0.5f, size.height - pad - 1.dp.toPx()), stroke)
                drawLine(iconColor, Offset(size.width * 0.6f, size.height * 0.45f), Offset(size.width * 0.85f, size.height * 0.45f), stroke)
                drawLine(iconColor, Offset(size.width * 0.6f, size.height * 0.62f), Offset(size.width * 0.8f, size.height * 0.62f), stroke)
                drawLine(iconColor, Offset(size.width * 0.17f, size.height * 0.66f), Offset(size.width * 0.28f, size.height * 0.5f), stroke)
                drawLine(iconColor, Offset(size.width * 0.28f, size.height * 0.5f), Offset(size.width * 0.38f, size.height * 0.6f), stroke)
            }
            "design-only" -> {
                drawLine(iconColor, Offset(size.width * 0.15f, size.height * 0.74f), Offset(size.width * 0.35f, size.height * 0.44f), stroke)
                drawLine(iconColor, Offset(size.width * 0.35f, size.height * 0.44f), Offset(size.width * 0.52f, size.height * 0.62f), stroke)
                drawLine(iconColor, Offset(size.width * 0.52f, size.height * 0.62f), Offset(size.width * 0.74f, size.height * 0.3f), stroke)
                drawLine(iconColor, Offset(size.width * 0.74f, size.height * 0.3f), Offset(size.width * 0.9f, size.height * 0.74f), stroke)
                drawCircle(iconColor, radius = 2.dp.toPx(), center = Offset(size.width * 0.72f, size.height * 0.22f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
            }
            else -> {
                drawLine(iconColor, Offset(size.width * 0.2f, size.height * 0.26f), Offset(size.width * 0.8f, size.height * 0.26f), stroke)
                drawLine(iconColor, Offset(size.width * 0.5f, size.height * 0.26f), Offset(size.width * 0.5f, size.height * 0.82f), stroke)
            }
        }
    }
}

@Composable
private fun GenSummaryButton(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .border(1.dp, GenCardBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp, 14.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = GenText
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = GenMuted
        )
    }
}

@Composable
private fun GenColorPreviewRow(
    colorState: GenColorState,
    translationStore: TranslationStore
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = translationStore.t("creator.generator.design_label", "Design"),
                fontSize = 11.sp,
                color = GenMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (colorState.designColors.isEmpty()) {
                    Text("0", color = GenMuted, fontSize = 12.sp)
                } else {
                    colorState.designColors.take(5).forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(parseHexColor(hex))
                                .border(1.dp, GenCardBorder, RoundedCornerShape(7.dp))
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = translationStore.t("creator.generator.background_label", "Background"),
                fontSize = 11.sp,
                color = GenMuted
            )
            if (colorState.backgroundTransparent) {
                Text(
                    text = translationStore.t("creator.generator.background_transparent", "Transparent"),
                    color = GenMuted,
                    fontSize = 12.sp
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (colorState.backgroundColors.isEmpty()) {
                        Text("0", color = GenMuted, fontSize = 12.sp)
                    } else {
                        colorState.backgroundColors.take(5).forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(parseHexColor(hex))
                                    .border(1.dp, GenCardBorder, RoundedCornerShape(7.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenStylesModal(
    visible: Boolean,
    selected: List<String>,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit,
    translationStore: TranslationStore
) {
    if (!visible) return
    var searchQuery by remember { mutableStateOf("") }
    var selectedDraft by remember(visible, selected) { mutableStateOf(selected) }
    var collapsed by remember { mutableStateOf<Set<String>>(emptySet()) }
    val maxSelected = 5

    GenModalBase(
        title = translationStore.t("creator.generator.style_title", "Design Style"),
        onDismiss = onDismiss,
        showApply = true,
        applyLabel = translationStore.t("creator.common.apply", "Apply"),
        onApply = { onApply(selectedDraft.toList()) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        translationStore.t("creator.inspiration.search_placeholder", "Search..."),
                        color = GenMuted
                    )
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GenText,
                    unfocusedTextColor = GenText,
                    focusedBorderColor = EazColors.Orange.copy(alpha = 0.5f),
                    unfocusedBorderColor = GenCardBorder,
                    cursorColor = EazColors.Orange,
                    focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = translationStore.t("creator.style_modal.max_selected", "Max. 5 styles selectable"),
                    fontSize = 12.sp,
                    color = GenMuted
                )
                Text(
                    text = "${selectedDraft.size}/$maxSelected",
                    fontSize = 12.sp,
                    color = EazColors.Orange,
                    fontWeight = FontWeight.Bold
                )
            }

            GEN_STYLE_CATEGORIES.forEach { category ->
                val title = translationStore.t(category.titleKey, category.titleFallback)
                val visibleOptions = category.options.filter { option ->
                    searchQuery.isBlank() ||
                        translationStore.t(option.labelKey, option.fallback).contains(searchQuery, ignoreCase = true)
                }
                if (visibleOptions.isEmpty()) return@forEach
                val isCollapsed = title in collapsed
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.16f))
                        .border(1.dp, GenCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                collapsed = if (isCollapsed) collapsed - title else collapsed + title
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GenText
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = GenMuted
                        )
                    }
                    if (!isCollapsed) {
                        visibleOptions.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { style ->
                                    val label = translationStore.t(style.labelKey, style.fallback)
                                    val isSelected = style.value in selectedDraft
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) GenAccentSoft else Color.White.copy(alpha = 0.07f))
                                            .border(
                                                1.dp,
                                                if (isSelected) EazColors.Orange else GenCardBorder,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                selectedDraft = when {
                                                    isSelected -> selectedDraft - style.value
                                                    selectedDraft.size < maxSelected -> selectedDraft + style.value
                                                    else -> selectedDraft
                                                }
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = GenText
                                        )
                                    }
                                }
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenLanguageModal(
    visible: Boolean,
    state: GenLanguageState,
    onDismiss: () -> Unit,
    onApply: (GenLanguageState) -> Unit,
    api: CreatorApi,
    translationStore: TranslationStore
) {
    if (!visible) return
    var draft by remember(visible, state) { mutableStateOf(state) }
    var loading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var standard by remember { mutableStateOf(AVAILABLE_LANGUAGES) }
    var children by remember { mutableStateOf<Map<String, ApiLanguageChildren>>(emptyMap()) }
    var showDialectPicker by remember { mutableStateOf(false) }
    var showScriptPicker by remember { mutableStateOf(false) }
    var listExpanded by remember(visible, state) { mutableStateOf(state.langCode.isBlank()) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        loading = true
        try {
            val resp = api.getLanguages()
            if (resp.standard.isNotEmpty()) {
                standard = resp.standard.map { LocaleModalItem(it.code, it.label, it.flagCode.lowercase()) }
            }
            children = resp.children
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    val baseLang = draft.langCode.lowercase().split("-", "_").firstOrNull().orEmpty()
    val languageChildren = children[baseLang]
    val showVariants = draft.mode == "manual" && draft.langCode.isNotBlank() && languageChildren != null

    GenLanguageVariantModal(
        visible = showDialectPicker,
        title = "Dialect",
        options = languageChildren?.dialects ?: emptyList(),
        selectedCode = draft.dialectCode,
        onDismiss = { showDialectPicker = false },
        onClear = {
            draft = draft.copy(dialectCode = "", dialectLabel = "")
            showDialectPicker = false
        },
        onSelect = { item ->
            draft = draft.copy(dialectCode = item.code, dialectLabel = item.label)
            showDialectPicker = false
        },
        translationStore = translationStore
    )
    GenLanguageVariantModal(
        visible = showScriptPicker,
        title = "Script",
        options = languageChildren?.scripts ?: emptyList(),
        selectedCode = draft.scriptCode,
        onDismiss = { showScriptPicker = false },
        onClear = {
            draft = draft.copy(scriptCode = "", scriptLabel = "")
            showScriptPicker = false
        },
        onSelect = { item ->
            draft = draft.copy(scriptCode = item.code, scriptLabel = item.label)
            showScriptPicker = false
        },
        translationStore = translationStore
    )

    GenModalBase(
        title = translationStore.t("creator.generator.language_title", "Language"),
        onDismiss = onDismiss,
        showApply = true,
        applyLabel = translationStore.t("creator.common.apply", "Apply"),
        onApply = { onApply(draft) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "as-design" to translationStore.t("creator.generator.language_as_design", "As in design"),
                    "as-prompt" to translationStore.t("creator.generator.language_as_prompt", "As in prompt"),
                    "manual" to translationStore.t("creator.generator.language_manual", "Choose manually")
                ).forEach { (mode, label) ->
                    val selected = draft.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) GenAccentSoft else Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                if (selected) EazColors.Orange else GenCardBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                draft = if (mode == "manual") {
                                    draft.copy(mode = mode)
                                } else {
                                    draft.copy(
                                        mode = mode,
                                        langCode = "",
                                        langLabel = "",
                                        dialectCode = "",
                                        dialectLabel = "",
                                        scriptCode = "",
                                        scriptLabel = ""
                                    )
                                }
                                if (mode == "manual") listExpanded = draft.langCode.isBlank()
                            }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = GenText
                        )
                    }
                }
            }

            if (draft.mode == "manual") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            translationStore.t("creator.inspiration.search_placeholder", "Search..."),
                            color = GenMuted
                        )
                    },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GenText,
                        unfocusedTextColor = GenText,
                        focusedBorderColor = EazColors.Orange.copy(alpha = 0.5f),
                        unfocusedBorderColor = GenCardBorder,
                        cursorColor = EazColors.Orange,
                        focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                    )
                )
                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EazColors.Orange)
                    }
                } else {
                    val filtered = standard.filter {
                        searchQuery.isBlank() ||
                            it.label.contains(searchQuery, ignoreCase = true) ||
                            it.code.contains(searchQuery, ignoreCase = true)
                    }
                    if (draft.langCode.isNotBlank() && !listExpanded) {
                        val selectedLang = standard.find { it.code.equals(draft.langCode, ignoreCase = true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(GenAccentSoft)
                                .border(1.dp, EazColors.Orange, RoundedCornerShape(10.dp))
                                .clickable { listExpanded = true }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                selectedLang?.let {
                                    GlassCircularFlag(countryCode = it.flagCode.uppercase(), size = 22.dp)
                                    Text(it.label, color = GenText, fontSize = 13.sp)
                                } ?: Text(draft.langLabel, color = GenText, fontSize = 13.sp)
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = GenText)
                        }
                    } else {
                        filtered.forEach { item ->
                            val isSelected = draft.langCode.equals(item.code, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) GenAccentSoft else Color.White.copy(alpha = 0.07f))
                                    .border(
                                        1.dp,
                                        if (isSelected) EazColors.Orange else GenCardBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        draft = draft.copy(
                                            mode = "manual",
                                            langCode = item.code,
                                            langLabel = item.label,
                                            dialectCode = "",
                                            dialectLabel = "",
                                            scriptCode = "",
                                            scriptLabel = ""
                                        )
                                        listExpanded = false
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    GlassCircularFlag(countryCode = item.flagCode.uppercase(), size = 22.dp)
                                    Text(item.label, color = GenText, fontSize = 13.sp)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = EazColors.Orange)
                                }
                            }
                        }
                    }
                }

                if (showVariants) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!languageChildren!!.dialects.isNullOrEmpty()) {
                            GenSummaryButton(
                                text = draft.dialectLabel.ifBlank { "Dialect" },
                                onClick = { showDialectPicker = true }
                            )
                        }
                        if (!languageChildren.scripts.isNullOrEmpty()) {
                            GenSummaryButton(
                                text = draft.scriptLabel.ifBlank { "Script" },
                                onClick = { showScriptPicker = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenLanguageVariantModal(
    visible: Boolean,
    title: String,
    options: List<ApiLanguageItem>,
    selectedCode: String,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onSelect: (ApiLanguageItem) -> Unit,
    translationStore: TranslationStore
) {
    if (!visible) return
    GenModalBase(
        title = title,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedCode.isBlank()) GenAccentSoft else Color.White.copy(alpha = 0.07f))
                    .border(
                        1.dp,
                        if (selectedCode.isBlank()) EazColors.Orange else GenCardBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onClear() }
                    .padding(10.dp)
            ) {
                Text(
                    text = translationStore.t("creator.common.default", "Default"),
                    color = GenText
                )
            }
            options.forEach { item ->
                val selected = item.code.equals(selectedCode, ignoreCase = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) GenAccentSoft else Color.White.copy(alpha = 0.07f))
                        .border(
                            1.dp,
                            if (selected) EazColors.Orange else GenCardBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(item) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassCircularFlag(countryCode = item.flagCode, size = 20.dp)
                        Text(item.label, color = GenText)
                    }
                    if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = EazColors.Orange)
                }
            }
        }
    }
}

@Composable
private fun GenColorsModal(
    visible: Boolean,
    state: GenColorState,
    onDismiss: () -> Unit,
    onApply: (GenColorState) -> Unit,
    translationStore: TranslationStore
) {
    if (!visible) return
    var draft by remember(visible, state) { mutableStateOf(state) }
    var tab by remember { mutableStateOf("design") }
    var designHex by remember { mutableStateOf("#f59e0b") }
    var backgroundHex by remember { mutableStateOf("#ffffff") }

    GenModalBase(
        title = translationStore.t("creator.generator.design_colors", "Design colors"),
        onDismiss = onDismiss,
        showApply = true,
        applyLabel = translationStore.t("creator.common.apply", "Apply"),
        onApply = { onApply(draft) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "design" to translationStore.t("creator.generator.design_label", "Design"),
                    "background" to translationStore.t("creator.generator.background_label", "Background")
                ).forEach { (value, label) ->
                    val selected = tab == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) GenAccentSoft else Color.White.copy(alpha = 0.08f))
                            .border(
                                1.dp,
                                if (selected) EazColors.Orange else GenCardBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { tab = value }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = GenText, fontSize = 12.sp)
                    }
                }
            }

            if (tab == "design") {
                GenColorPickerSection(
                    selectedColors = draft.designColors,
                    inputHex = designHex,
                    onInputHexChange = { designHex = it },
                    onAdd = {
                        addColor(draft.designColors, designHex)?.let {
                            draft = draft.copy(designColors = it)
                        }
                    },
                    onPreset = { hex ->
                        addColor(draft.designColors, hex)?.let { draft = draft.copy(designColors = it) }
                    },
                    onRemove = { idx ->
                        draft = draft.copy(designColors = draft.designColors.toMutableList().apply { removeAt(idx) })
                    },
                    translationStore = translationStore
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translationStore.t("creator.generator.background_transparent", "Transparent"),
                        color = GenText
                    )
                    Switch(
                        checked = draft.backgroundTransparent,
                        onCheckedChange = { draft = draft.copy(backgroundTransparent = it) }
                    )
                }
                if (!draft.backgroundTransparent) {
                    GenColorPickerSection(
                        selectedColors = draft.backgroundColors,
                        inputHex = backgroundHex,
                        onInputHexChange = { backgroundHex = it },
                        onAdd = {
                            addColor(draft.backgroundColors, backgroundHex)?.let {
                                draft = draft.copy(backgroundColors = it)
                            }
                        },
                        onPreset = { hex ->
                            addColor(draft.backgroundColors, hex)?.let { draft = draft.copy(backgroundColors = it) }
                        },
                        onRemove = { idx ->
                            draft = draft.copy(backgroundColors = draft.backgroundColors.toMutableList().apply { removeAt(idx) })
                        },
                        translationStore = translationStore
                    )
                }
            }
        }
    }
}

@Composable
private fun GenColorPickerSection(
    selectedColors: List<String>,
    inputHex: String,
    onInputHexChange: (String) -> Unit,
    onAdd: () -> Unit,
    onPreset: (String) -> Unit,
    onRemove: (Int) -> Unit,
    translationStore: TranslationStore
) {
    val normalized = normalizeHex(inputHex) ?: "#f59e0b"
    var hue by remember(inputHex) { mutableStateOf(hexToHsv(normalized).first) }
    var sat by remember(inputHex) { mutableStateOf(hexToHsv(normalized).second) }
    var value by remember(inputHex) { mutableStateOf(hexToHsv(normalized).third) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GEN_COLOR_PRESETS.forEach { hex ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(parseHexColor(hex))
                        .border(1.dp, GenCardBorder, RoundedCornerShape(13.dp))
                        .clickable {
                            onPreset(hex)
                            onInputHexChange(hex)
                            val hsv = hexToHsv(hex)
                            hue = hsv.first
                            sat = hsv.second
                            value = hsv.third
                        }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputHex,
                onValueChange = {
                    onInputHexChange(it)
                    normalizeHex(it)?.let { valid ->
                        val hsv = hexToHsv(valid)
                        hue = hsv.first
                        sat = hsv.second
                        value = hsv.third
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("#hex", color = GenMuted) },
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GenText,
                    unfocusedTextColor = GenText,
                    focusedBorderColor = EazColors.Orange.copy(alpha = 0.5f),
                    unfocusedBorderColor = GenCardBorder,
                    cursorColor = EazColors.Orange,
                    focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                )
            )
            TextButton(
                onClick = onAdd,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(GenAccentSoft)
                    .border(1.dp, EazColors.Orange, RoundedCornerShape(10.dp))
            ) {
                Text(translationStore.t("creator.common.add", "Add"), color = GenText)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .border(1.dp, GenCardBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color picker", color = GenText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(parseHexColor(hsvToHex(hue, sat, value)))
                        .border(1.dp, GenCardBorder, RoundedCornerShape(6.dp))
                )
            }
            var colorFieldSize by remember { mutableStateOf(Offset.Zero) }
            fun setSatValueFromPoint(p: Offset) {
                if (colorFieldSize.x <= 0f || colorFieldSize.y <= 0f) return
                sat = (p.x / colorFieldSize.x).coerceIn(0f, 1f)
                value = (1f - (p.y / colorFieldSize.y)).coerceIn(0f, 1f)
                onInputHexChange(hsvToHex(hue, sat, value))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, GenCardBorder, RoundedCornerShape(10.dp))
                    .onSizeChanged { colorFieldSize = Offset(it.width.toFloat(), it.height.toFloat()) }
                    .pointerInput(hue) {
                        detectTapGestures { p -> setSatValueFromPoint(p) }
                    }
                    .pointerInput(hue) {
                        detectDragGestures(
                            onDragStart = { p -> setSatValueFromPoint(p) },
                            onDrag = { change, _ ->
                                setSatValueFromPoint(change.position)
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.White, parseHexColor(hsvToHex(hue, 1f, 1f)))
                        )
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black)
                        )
                    )
                    val x = sat * size.width
                    val y = (1f - value) * size.height
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = Offset(x, y),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.55f),
                        radius = 10.dp.toPx(),
                        center = Offset(x, y),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
            }

            Text("Hue", color = GenMuted, fontSize = 11.sp)
            var hueBarWidth by remember { mutableStateOf(1f) }
            fun setHueFromX(x: Float) {
                if (hueBarWidth <= 0f) return
                hue = ((x / hueBarWidth) * 360f).coerceIn(0f, 360f)
                onInputHexChange(hsvToHex(hue, sat, value))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, GenCardBorder, RoundedCornerShape(10.dp))
                    .onSizeChanged { hueBarWidth = it.width.toFloat() }
                    .pointerInput(Unit) {
                        detectTapGestures { p -> setHueFromX(p.x) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { p -> setHueFromX(p.x) },
                            onDrag = { change, _ -> setHueFromX(change.position.x) }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                        )
                    )
                    val x = (hue / 360f) * size.width
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = Offset(x, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.5f),
                        radius = 9.dp.toPx(),
                        center = Offset(x, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
        Text(
            text = translationStore.t("creator.color_modal.max_colors", "Max. 5 colors"),
            fontSize = 12.sp,
            color = GenMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selectedColors.forEachIndexed { idx, hex ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(parseHexColor(hex))
                        .border(1.dp, GenCardBorder, RoundedCornerShape(14.dp))
                        .clickable { onRemove(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun languageSummary(
    languageState: GenLanguageState,
    translationStore: TranslationStore
): String {
    return when (languageState.mode) {
        "as-prompt" -> translationStore.t("creator.generator.language_as_prompt", "As in prompt")
        "manual" -> {
            if (languageState.langLabel.isBlank()) {
                translationStore.t("creator.generator.language_manual", "Choose manually")
            } else {
                buildString {
                    append(languageState.langLabel)
                    if (languageState.dialectLabel.isNotBlank()) append(" + ${languageState.dialectLabel}")
                    if (languageState.scriptLabel.isNotBlank()) append(" (${languageState.scriptLabel})")
                }
            }
        }
        else -> translationStore.t("creator.generator.language_as_design", "As in design")
    }
}

private fun colorSummary(
    colorState: GenColorState,
    translationStore: TranslationStore
): String {
    val backgroundSummary = if (colorState.backgroundTransparent) {
        translationStore.t("creator.generator.background_transparent", "transparent")
    } else {
        "${colorState.backgroundColors.size} ${translationStore.t("creator.generator.colors", "colors")}"
    }
    return "${translationStore.t("creator.generator.design_label", "Design")}: ${colorState.designColors.size} · " +
        "${translationStore.t("creator.generator.background_label", "Background")}: $backgroundSummary"
}

private fun addColor(colors: List<String>, hexRaw: String): List<String>? {
    val hex = normalizeHex(hexRaw) ?: return null
    if (hex in colors || colors.size >= 5) return null
    return colors + hex
}

private fun normalizeHex(hexRaw: String): String? {
    val raw = hexRaw.trim().removePrefix("#")
    if (raw.length != 6) return null
    return if (raw.matches(Regex("[0-9a-fA-F]{6}"))) "#${raw.lowercase()}" else null
}

private fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color.Transparent
    }
}

private fun hexToHsv(hex: String): Triple<Float, Float, Float> {
    return try {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), hsv)
        Triple(hsv[0], hsv[1], hsv[2])
    } catch (_: Exception) {
        Triple(34f, 0.93f, 0.96f)
    }
}

private fun hsvToHex(h: Float, s: Float, v: Float): String {
    val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    return String.format("#%06X", 0xFFFFFF and colorInt).lowercase()
}

@Composable
fun GenInspirationModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    api: CreatorApi,
    translationStore: TranslationStore,
    onSelect: (String) -> Unit,
    shopLightChrome: Boolean = false
) {
    if (!visible) return
    val c = genModalChrome(shopLightChrome)
    var designs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(visible, searchQuery) {
        if (!visible) return@LaunchedEffect
        loading = true
        try {
            val resp = api.listPublic(limit = 100, search = searchQuery.ifBlank { null })
            designs = if (resp.optBoolean("ok", false)) {
                resp.optJSONArray("items")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            designs = emptyList()
        } finally {
            if (coroutineContext.isActive) loading = false
        }
    }

    GenModalBase(
        title = translationStore.t("creator.inspiration.title", "Inspirations"),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        translationStore.t("creator.inspiration.search_placeholder", "Search designs..."),
                        color = GenMuted
                    )
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GenText,
                    unfocusedTextColor = GenText,
                    focusedBorderColor = EazColors.Orange.copy(alpha = 0.5f),
                    unfocusedBorderColor = GenCardBorder,
                    cursorColor = EazColors.Orange,
                    focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${designs.size} ${translationStore.t("creator.inspiration.designs", "Designs")}",
                fontSize = 12.sp,
                color = GenMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EazColors.Orange)
                }
            } else if (designs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = translationStore.t("creator.inspiration.empty", "No public designs found."),
                        color = c.muted
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(designs) { design ->
                        val url = design.optString("preview_url", "").ifBlank {
                            design.optString("original_url", "")
                        }
                        if (url.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.cardBg)
                                    .border(1.dp, c.cardBorder, RoundedCornerShape(10.dp))
                                    .clickable { onSelect(url) },
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                    loading = { GenGridImageShimmer(Modifier.fillMaxSize()) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenMyDesignsModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    api: CreatorApi,
    ownerId: String,
    translationStore: TranslationStore,
    onSelect: (String) -> Unit,
    shopLightChrome: Boolean = false
) {
    if (!visible) return
    val c = genModalChrome(shopLightChrome)
    var designs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(visible, ownerId) {
        if (!visible || ownerId.isBlank()) return@LaunchedEffect
        loading = true
        try {
            val savedResp = api.listDesigns(ownerId, limit = 100)
            val genResp = api.listGenerated(ownerId, limit = 200)
            val saved = if (savedResp.optBoolean("ok", false)) {
                savedResp.optJSONArray("items")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                } ?: emptyList()
            } else emptyList()
            val generated = if (genResp.optBoolean("ok", false)) {
                genResp.optJSONArray("items")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                } ?: emptyList()
            } else emptyList()
            val savedJobIds = saved.mapNotNull { it.optString("job_id", "").takeIf { s -> s.isNotBlank() } }.toSet()
            val merged = saved.map { obj ->
                obj.put("_url", obj.optString("original_url", "").ifBlank { obj.optString("preview_url", "") })
                obj.put("_title", obj.optString("title", "Design"))
            } + generated.filter { it.optString("job_id", "").let { j -> j.isBlank() || j !in savedJobIds } }.map { obj ->
                obj.put("_url", obj.optString("image_url", "").ifBlank { obj.optString("preview_url", "") })
                obj.put("_title", obj.optString("prompt", "Generated design"))
            }
            designs = merged.filter { it.optString("_url", "").isNotBlank() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            designs = emptyList()
        } finally {
            if (coroutineContext.isActive) loading = false
        }
    }

    val filteredDesigns = remember(designs, searchQuery) {
        if (searchQuery.isBlank()) designs
        else {
            val q = searchQuery.lowercase()
            designs.filter {
                (it.optString("_title", "") + it.optString("title", "") + it.optString("prompt", "")).lowercase().contains(q)
            }
        }
    }

    GenModalBase(
        title = translationStore.t("creator.generator.source_designs", "My Designs"),
        onDismiss = onDismiss,
        shopLightChrome = shopLightChrome
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        translationStore.t("creator.inspiration.search_placeholder", "Search designs..."),
                        color = c.muted
                    )
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text,
                    focusedBorderColor = EazColors.Orange.copy(alpha = 0.5f),
                    unfocusedBorderColor = c.cardBorder,
                    cursorColor = EazColors.Orange,
                    focusedContainerColor = if (shopLightChrome) Color.White else Color.Black.copy(alpha = 0.2f),
                    unfocusedContainerColor = if (shopLightChrome) Color.White else Color.Black.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EazColors.Orange)
                }
            } else if (filteredDesigns.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = translationStore.t("creator.my_designs.empty", "No designs found."),
                        color = c.muted
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDesigns) { design ->
                        val url = design.optString("_url", "")
                        if (url.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.cardBg)
                                    .border(1.dp, c.cardBorder, RoundedCornerShape(10.dp))
                                    .clickable { onSelect(url) },
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                    loading = { GenGridImageShimmer(Modifier.fillMaxSize()) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenGridImageShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "gen-grid-shimmer")
    val shift by transition.animateFloat(
        initialValue = -280f,
        targetValue = 760f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gen-grid-shift"
    )
    Box(
        modifier = modifier.background(Color(0xFF2D3748))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        start = Offset(shift - 220f, 0f),
                        end = Offset(shift, 280f)
                    )
                )
        )
    }
}

private data class CanvasStroke(val points: List<Offset>, val color: Color, val width: Float)

private val CANVAS_COLORS = listOf(
    Color.Black, Color.White, Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308),
    Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899)
)

@Composable
private fun DataUrlImage(
    dataUrl: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var bitmap by remember(dataUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(dataUrl) {
        if (dataUrl.isBlank()) bitmap = null
        else {
            bitmap = withContext(Dispatchers.Default) {
                try {
                    val base64 = dataUrl.substringAfter(",", "")
                    if (base64.isBlank()) return@withContext null
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bmp?.asImageBitmap()
                } catch (_: Exception) { null }
            }
        }
    }
    bitmap?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
fun GenCanvasEditModal(
    visible: Boolean,
    backgroundImageDataUrl: String?,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    onConfirm: (String) -> Unit
) {
    if (!visible || backgroundImageDataUrl.isNullOrBlank()) return
    var drawColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableStateOf(8f) }
    val strokes = remember { mutableStateListOf<CanvasStroke>() }
    var currentStroke by remember { mutableStateOf<MutableList<Offset>?>(null) }
    var canvasSize by remember { mutableStateOf(400f) }
    val scope = rememberCoroutineScope()

    fun exportToDataUrl() {
        val strokesSnapshot = strokes.toList()
        val currentSnapshot = currentStroke?.takeIf { it.size >= 2 }?.let { CanvasStroke(it, drawColor, strokeWidth) }
        val allStrokesSnapshot = strokesSnapshot + (currentSnapshot?.let { listOf(it) } ?: emptyList())
        val sizeSnapshot = if (canvasSize < 10f) 400f else canvasSize
        val bgSnapshot = backgroundImageDataUrl
        scope.launch {
            val dataUrl = withContext(Dispatchers.Default) {
                val outSize = 512
                val bitmap = android.graphics.Bitmap.createBitmap(outSize, outSize, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                try {
                    val base64 = bgSnapshot.substringAfter(",", "")
                    if (base64.isNotBlank()) {
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                        val bg = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bg != null) {
                            val dst = android.graphics.Rect(0, 0, outSize, outSize)
                            canvas.drawBitmap(bg, null, dst, android.graphics.Paint().apply { isFilterBitmap = true })
                            if (!bg.isRecycled) bg.recycle()
                        } else {
                            canvas.drawColor(android.graphics.Color.WHITE)
                        }
                    } else {
                        canvas.drawColor(android.graphics.Color.WHITE)
                    }
                } catch (_: Exception) {
                    canvas.drawColor(android.graphics.Color.WHITE)
                }
                val paint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val scale = outSize.toFloat() / sizeSnapshot
                allStrokesSnapshot.forEach { stroke ->
                    if (stroke.points.size < 2) return@forEach
                    paint.color = stroke.color.toArgb()
                    paint.strokeWidth = stroke.width * scale
                    val path = android.graphics.Path()
                    path.moveTo(stroke.points[0].x * scale, stroke.points[0].y * scale)
                    stroke.points.drop(1).forEach { path.lineTo(it.x * scale, it.y * scale) }
                    canvas.drawPath(path, paint)
                }
                val out = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                "data:image/png;base64,${android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)}"
            }
            onConfirm(dataUrl)
        }
    }

    GenModalBase(
        title = translationStore.t("creator.canvas.edit_title", "Edit design"),
        onDismiss = onDismiss,
        showApply = true,
        applyLabel = translationStore.t("creator.canvas.save_edit", "Save & use"),
        onApply = { exportToDataUrl() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translationStore.t("creator.canvas.color", "Color"),
                        fontSize = 12.sp,
                        color = GenMuted
                    )
                    CANVAS_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(color)
                                .border(
                                    width = if (drawColor == color) 2.dp else 1.dp,
                                    color = if (drawColor == color) EazColors.Orange else GenCardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { drawColor = color }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Text(
                    text = translationStore.t("creator.canvas.brush_size", "Size"),
                    fontSize = 12.sp,
                    color = GenMuted
                )
                Slider(
                    value = strokeWidth,
                    onValueChange = { strokeWidth = it },
                    valueRange = 2f..40f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = EazColors.Orange,
                        activeTrackColor = EazColors.Orange
                    )
                )
                TextButton(onClick = {
                    strokes.clear()
                    currentStroke = null
                }) {
                    Text(
                        text = translationStore.t("creator.common.clear", "Clear"),
                        color = GenText
                    )
                }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, GenCardBorder, RoundedCornerShape(12.dp))
                    .onSizeChanged { canvasSize = minOf(it.width, it.height).toFloat() }
                    .pointerInput(backgroundImageDataUrl) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = mutableListOf(offset)
                            },
                            onDrag = { change, _ ->
                                currentStroke?.add(change.position)
                            },
                            onDragEnd = {
                                currentStroke?.let { pts ->
                                    if (pts.size >= 2) strokes.add(CanvasStroke(pts.toList(), drawColor, strokeWidth))
                                }
                                currentStroke = null
                            }
                        )
                    }
            ) {
                DataUrlImage(
                    dataUrl = backgroundImageDataUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size.minDimension
                    val allStrokes = strokes + (currentStroke?.takeIf { it.size >= 2 }?.let { listOf(CanvasStroke(it, drawColor, strokeWidth)) } ?: emptyList())
                    allStrokes.forEach { stroke ->
                        if (stroke.points.size < 2) return@forEach
                        val path = Path().apply {
                            moveTo(stroke.points[0].x, stroke.points[0].y)
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = stroke.color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = stroke.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GenCanvasModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    onConfirm: (String) -> Unit,
    shopLightChrome: Boolean = false
) {
    if (!visible) return
    val c = genModalChrome(shopLightChrome)
    var drawColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableStateOf(8f) }
    val strokes = remember { mutableStateListOf<CanvasStroke>() }
    var currentStroke by remember { mutableStateOf<MutableList<Offset>?>(null) }
    var canvasSize by remember { mutableStateOf(400f) }
    val scope = rememberCoroutineScope()

    fun exportToDataUrl() {
        val strokesSnapshot = strokes.toList()
        val currentSnapshot = currentStroke?.takeIf { it.size >= 2 }?.let { CanvasStroke(it, drawColor, strokeWidth) }
        val allStrokesSnapshot = strokesSnapshot + (currentSnapshot?.let { listOf(it) } ?: emptyList())
        val sizeSnapshot = if (canvasSize < 10f) 400f else canvasSize
        scope.launch {
            val dataUrl = withContext(Dispatchers.Default) {
                val outSize = 512
                val bitmap = android.graphics.Bitmap.createBitmap(outSize, outSize, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                val paint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val scale = outSize.toFloat() / sizeSnapshot
                allStrokesSnapshot.forEach { stroke ->
                    if (stroke.points.size < 2) return@forEach
                    paint.color = stroke.color.toArgb()
                    paint.strokeWidth = stroke.width * scale
                    val path = android.graphics.Path()
                    path.moveTo(stroke.points[0].x * scale, stroke.points[0].y * scale)
                    stroke.points.drop(1).forEach { path.lineTo(it.x * scale, it.y * scale) }
                    canvas.drawPath(path, paint)
                }
                val out = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                "data:image/png;base64,${android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)}"
            }
            onConfirm(dataUrl)
        }
    }

    GenModalBase(
        title = translationStore.t("creator.canvas.title", "Canvas Sketch"),
        onDismiss = onDismiss,
        showApply = true,
        applyLabel = translationStore.t("creator.canvas.use_drawing", "Use drawing"),
        onApply = { exportToDataUrl() },
        shopLightChrome = shopLightChrome
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translationStore.t("creator.canvas.color", "Color"),
                        fontSize = 12.sp,
                        color = c.muted
                    )
                    CANVAS_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(color)
                                .border(
                                    width = if (drawColor == color) 2.dp else 1.dp,
                                    color = if (drawColor == color) EazColors.Orange else c.cardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { drawColor = color }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translationStore.t("creator.canvas.brush_size", "Size"),
                        fontSize = 12.sp,
                        color = c.muted
                    )
                    Slider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 2f..40f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = EazColors.Orange,
                            activeTrackColor = EazColors.Orange
                        )
                    )
                    TextButton(onClick = {
                        strokes.clear()
                        currentStroke = null
                    }) {
                        Text(
                            translationStore.t("creator.common.clear", "Clear"),
                            color = c.text
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(12.dp))
                    .onSizeChanged { canvasSize = minOf(it.width, it.height).toFloat() }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = mutableListOf(offset)
                            },
                            onDrag = { change, _ ->
                                currentStroke?.add(change.position)
                            },
                            onDragEnd = {
                                currentStroke?.let { pts ->
                                    if (pts.size >= 2) strokes.add(CanvasStroke(pts.toList(), drawColor, strokeWidth))
                                }
                                currentStroke = null
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size.minDimension
                    val allStrokes = strokes + (currentStroke?.takeIf { it.size >= 2 }?.let { listOf(CanvasStroke(it, drawColor, strokeWidth)) } ?: emptyList())
                    allStrokes.forEach { stroke ->
                        if (stroke.points.size < 2) return@forEach
                        val path = Path().apply {
                            moveTo(stroke.points[0].x, stroke.points[0].y)
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = stroke.color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = stroke.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenModalBase(
    title: String,
    onDismiss: () -> Unit,
    showApply: Boolean = false,
    applyLabel: String = "Apply",
    onApply: () -> Unit = {},
    shopLightChrome: Boolean = false,
    content: @Composable () -> Unit
) {
    val c = genModalChrome(shopLightChrome)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.overlay)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.modalBg)
                    .border(1.dp, c.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.headerBg)
                        .padding(12.dp, 18.dp, 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (shopLightChrome) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f))
                            .border(1.dp, c.cardBorder, RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = c.text, modifier = Modifier.size(14.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(c.bodyBg)
                ) {
                    content()
                }
                if (showApply) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (shopLightChrome) Color(0xFFF9FAFB) else Color.Black.copy(alpha = 0.2f))
                            .padding(14.dp, 18.dp, 18.dp)
                    ) {
                        TextButton(
                            onClick = {
                                onApply()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (shopLightChrome) EazColors.Orange.copy(alpha = 0.15f)
                                    else EazColors.Orange.copy(alpha = 0.2f)
                                )
                                .border(1.dp, EazColors.Orange, RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = applyLabel,
                                color = if (shopLightChrome) ShopLightText else GenText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

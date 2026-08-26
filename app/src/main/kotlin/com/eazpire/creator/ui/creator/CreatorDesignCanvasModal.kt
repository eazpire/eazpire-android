package com.eazpire.creator.ui.creator

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazBottomSheet
import com.eazpire.creator.ui.modal.EazModalStickyFooter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorDesignCanvasModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    onUseDesign: (Uri) -> Unit,
    translationStore: TranslationStore,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)

    var text by remember { mutableStateOf("") }
    var font by remember { mutableStateOf(DesignCanvasFont.IMPACT) }
    var sizeKey by remember { mutableStateOf(88) }
    var align by remember { mutableStateOf(DesignCanvasAlign.CENTER) }
    var color by remember { mutableStateOf(DesignCanvasColor.WHITE) }
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var stageWidthPx by remember { mutableStateOf(0f) }

    val canUse = text.trim().isNotEmpty() && !exporting

    fun exportAndUse() {
        if (!canUse) return
        exporting = true
        exportError = null
        val snapshot = text
        val fontSnap = font
        val sizeSnap = sizeKey
        val colorSnap = color
        val alignSnap = align
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = DesignCanvasExport.renderPng(
                        text = snapshot,
                        font = fontSnap,
                        sizeKey = sizeSnap,
                        colorArgb = Color(colorSnap.argb).toArgb(),
                        align = alignSnap,
                    )
                    val dir = File(context.cacheDir, "design-canvas").apply { mkdirs() }
                    val file = File(dir, "canvas-design-${System.currentTimeMillis()}.png")
                    file.writeBytes(bytes)
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
            }
            exporting = false
            result.onSuccess(onUseDesign).onFailure {
                exportError = t(
                    "creator.my_creations.canvas_export_failed",
                    "Could not create the design image. Please try again.",
                )
            }
        }
    }

    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        dragHandle = null,
        fullscreen = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(t("creator.my_creations.canvas_title", "Canvas"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        t("creator.my_creations.canvas_size_meta", "4500 × 4500 px · transparent PNG"),
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("creator.my_creations.canvas_close", "Close"), tint = Color.White)
                }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val compact = maxWidth < 700.dp
                if (compact) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ToolRail(translationStore, compact = true)
                        ViewerPane(
                            text = text,
                            font = font,
                            sizeKey = sizeKey,
                            align = align,
                            color = color,
                            stageWidthPx = stageWidthPx,
                            onStageWidth = { stageWidthPx = it },
                            translationStore = translationStore,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        InspectorPane(
                            text = text,
                            onText = { text = it },
                            font = font,
                            onFont = { font = it },
                            sizeKey = sizeKey,
                            onSize = { sizeKey = it },
                            align = align,
                            onAlign = { align = it },
                            color = color,
                            onColor = { color = it },
                            translationStore = translationStore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .imePadding(),
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        ToolRail(translationStore, compact = false)
                        ViewerPane(
                            text = text,
                            font = font,
                            sizeKey = sizeKey,
                            align = align,
                            color = color,
                            stageWidthPx = stageWidthPx,
                            onStageWidth = { stageWidthPx = it },
                            translationStore = translationStore,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        InspectorPane(
                            text = text,
                            onText = { text = it },
                            font = font,
                            onFont = { font = it },
                            sizeKey = sizeKey,
                            onSize = { sizeKey = it },
                            align = align,
                            onAlign = { align = it },
                            color = color,
                            onColor = { color = it },
                            translationStore = translationStore,
                            modifier = Modifier
                                .width(260.dp)
                                .fillMaxHeight()
                                .imePadding(),
                        )
                    }
                }
            }

            if (exportError != null) {
                Text(
                    exportError ?: "",
                    color = Color(0xFFFCA5A5),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            EazModalStickyFooter(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(t("creator.my_creations.canvas_cancel", "Cancel"), color = Color(0xFFE5E7EB))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { exportAndUse() },
                    enabled = canUse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EazColors.Orange,
                        contentColor = Color(0xFF111827),
                        disabledContainerColor = EazColors.Orange.copy(alpha = 0.35f),
                    ),
                ) {
                    Text(t("creator.my_creations.canvas_use", "Use as design"))
                }
            }
        }
    }
}

@Composable
private fun ToolRail(translationStore: TranslationStore, compact: Boolean) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val modifier = if (compact) {
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF020617))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    } else {
        Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(Color(0xFF020617))
            .padding(vertical = 12.dp)
    }
    val arrangement = if (compact) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(8.dp)
    if (compact) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = arrangement) {
            ActiveTextTool()
            Text(t("creator.my_creations.canvas_tool_text", "Text"), color = Color(0xFF9CA3AF), fontSize = 11.sp)
            MutedTool(t("creator.my_creations.canvas_tool_draw", "Draw"))
            MutedTool(t("creator.my_creations.canvas_tool_shape", "Shape"))
            MutedTool(t("creator.my_creations.canvas_tool_image", "Image"))
        }
    } else {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = arrangement) {
            ActiveTextTool()
            Text(t("creator.my_creations.canvas_tool_text", "Text"), color = Color(0xFF9CA3AF), fontSize = 11.sp)
            Box(Modifier.size(width = 28.dp, height = 1.dp).background(Color.White.copy(alpha = 0.12f)))
            MutedTool(t("creator.my_creations.canvas_tool_draw", "Draw"))
            MutedTool(t("creator.my_creations.canvas_tool_shape", "Shape"))
            MutedTool(t("creator.my_creations.canvas_tool_image", "Image"))
        }
    }
}

@Composable
private fun ActiveTextTool() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x2EF59E0B))
            .border(1.dp, EazColors.Orange, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("T", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun MutedTool(label: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFF6B7280), fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 11.sp)
    }
}

@Composable
private fun ViewerPane(
    text: String,
    font: DesignCanvasFont,
    sizeKey: Int,
    align: DesignCanvasAlign,
    color: DesignCanvasColor,
    stageWidthPx: Float,
    onStageWidth: (Float) -> Unit,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val display = text.trim().ifEmpty {
        translationStore.t("creator.my_creations.canvas_text_placeholder", "Your text")
    }
    val placeholder = text.trim().isEmpty()
    val fontPx = DesignCanvasTextLayout.viewerFontPx(stageWidthPx, sizeKey)
    val fontSp = with(density) { fontPx.toSp() }
    val textAlign = when (align) {
        DesignCanvasAlign.LEFT -> TextAlign.Left
        DesignCanvasAlign.RIGHT -> TextAlign.Right
        DesignCanvasAlign.CENTER -> TextAlign.Center
    }
    val rowAlign = when (align) {
        DesignCanvasAlign.LEFT -> Alignment.CenterStart
        DesignCanvasAlign.RIGHT -> Alignment.CenterEnd
        DesignCanvasAlign.CENTER -> Alignment.Center
    }
    Box(
        modifier = modifier
            .background(Color(0xFF0B1220))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                .onSizeChanged { onStageWidth(it.width.toFloat()) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = 20.dp.toPx()
                val cols = (size.width / cell).toInt() + 1
                val rows = (size.height / cell).toInt() + 1
                for (y in 0 until rows) {
                    for (x in 0 until cols) {
                        drawRect(
                            color = if ((x + y) % 2 == 0) Color(0xFF4B5563) else Color(0xFF6B7280),
                            topLeft = Offset(x * cell, y * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .percentPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(0.dp)),
                )
            }
            Box(
                modifier = Modifier.fillMaxSize().percentPadding(),
                contentAlignment = rowAlign,
            ) {
                Text(
                    text = display,
                    color = Color(color.argb).copy(alpha = if (placeholder) 0.45f else 1f),
                    fontSize = fontSp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamilyFor(font),
                    textAlign = textAlign,
                    lineHeight = fontSp * 1.05f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, EazColors.Orange)
                        .padding(8.dp),
                )
            }
            Text(
                translationStore.t("creator.my_creations.canvas_print_area", "Print area"),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp),
            )
        }
    }
}

private fun Modifier.percentPadding(): Modifier = this.padding(28.dp)

private fun fontFamilyFor(font: DesignCanvasFont): FontFamily = when (font) {
    DesignCanvasFont.COURIER -> FontFamily.Monospace
    DesignCanvasFont.GEORGIA, DesignCanvasFont.TIMES, DesignCanvasFont.PALATINO -> FontFamily.Serif
    else -> FontFamily.SansSerif
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectorPane(
    text: String,
    onText: (String) -> Unit,
    font: DesignCanvasFont,
    onFont: (DesignCanvasFont) -> Unit,
    sizeKey: Int,
    onSize: (Int) -> Unit,
    align: DesignCanvasAlign,
    onAlign: (DesignCanvasAlign) -> Unit,
    color: DesignCanvasColor,
    onColor: (DesignCanvasColor) -> Unit,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = EazColors.Orange,
        unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
        focusedLabelColor = Color(0xFF9CA3AF),
        unfocusedLabelColor = Color(0xFF9CA3AF),
        cursorColor = EazColors.Orange,
        focusedContainerColor = Color(0xFF0B1220),
        unfocusedContainerColor = Color(0xFF0B1220),
    )
    var fontOpen by remember { mutableStateOf(false) }
    var sizeOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(Color(0xFF111827))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("creator.my_creations.canvas_add_text", "Add Text"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        OutlinedTextField(
            value = text,
            onValueChange = { onText(it.take(180)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(t("creator.my_creations.canvas_text_label", "Text")) },
            placeholder = { Text(t("creator.my_creations.canvas_text_placeholder", "Your text")) },
            colors = fieldColors,
        )
        ExposedDropdownMenuBox(expanded = fontOpen, onExpandedChange = { fontOpen = it }) {
            OutlinedTextField(
                value = font.label,
                onValueChange = {},
                readOnly = true,
                label = { Text(t("creator.my_creations.canvas_font", "Font")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = fieldColors,
            )
            ExposedDropdownMenu(expanded = fontOpen, onDismissRequest = { fontOpen = false }) {
                DesignCanvasFont.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onFont(option)
                            fontOpen = false
                        },
                    )
                }
            }
        }
        ExposedDropdownMenuBox(expanded = sizeOpen, onExpandedChange = { sizeOpen = it }) {
            OutlinedTextField(
                value = t("creator.my_creations.canvas_size_$sizeKey", sizeKey.toString()),
                onValueChange = {},
                readOnly = true,
                label = { Text(t("creator.my_creations.canvas_size", "Size")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sizeOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = fieldColors,
            )
            ExposedDropdownMenu(expanded = sizeOpen, onDismissRequest = { sizeOpen = false }) {
                DESIGN_CANVAS_SIZE_KEYS.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(t("creator.my_creations.canvas_size_$key", key.toString())) },
                        onClick = {
                            onSize(key)
                            sizeOpen = false
                        },
                    )
                }
            }
        }
        Text(t("creator.my_creations.canvas_align", "Align"), color = Color(0xFF9CA3AF), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            AlignChip(t("creator.my_creations.canvas_align_left", "Left"), align == DesignCanvasAlign.LEFT) { onAlign(DesignCanvasAlign.LEFT) }
            AlignChip(t("creator.my_creations.canvas_align_center", "Center"), align == DesignCanvasAlign.CENTER) { onAlign(DesignCanvasAlign.CENTER) }
            AlignChip(t("creator.my_creations.canvas_align_right", "Right"), align == DesignCanvasAlign.RIGHT) { onAlign(DesignCanvasAlign.RIGHT) }
        }
        Text(t("creator.my_creations.canvas_color", "Color"), color = Color(0xFF9CA3AF), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DesignCanvasColor.entries.take(4).forEach { swatch ->
                ColorSwatch(swatch, selected = swatch == color) { onColor(swatch) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DesignCanvasColor.entries.drop(4).forEach { swatch ->
                ColorSwatch(swatch, selected = swatch == color) { onColor(swatch) }
            }
        }
        Text(t(color.labelKey, color.fallback), color = Color(0xFF9CA3AF), fontSize = 12.sp)
        Text(
            t(
                "creator.my_creations.canvas_later_tools",
                "Later: drag to move, more lines, outline, curve. Not in this first version.",
            ),
            color = Color(0xFF6B7280),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AlignChip(label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) Color.White else Color.Transparent
    val fg = if (active) Color(0xFF111827) else Color(0xFFE5E7EB)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, if (active) Color.White else Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = fg, fontSize = 12.sp)
    }
}

@Composable
private fun ColorSwatch(swatch: DesignCanvasColor, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(swatch.argb))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.28f),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick),
    )
}

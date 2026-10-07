package com.orihami.nagareyomi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orihami.nagareyomi.MainViewModel
import com.orihami.nagareyomi.ReaderMode
import com.orihami.nagareyomi.ReaderSession
import com.orihami.nagareyomi.core.BlockKind
import com.orihami.nagareyomi.core.ChunkSize
import com.orihami.nagareyomi.core.Pacing
import com.orihami.nagareyomi.data.ReaderSettings
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: MainViewModel) {
    val session = vm.session ?: return
    var showSettings by remember { mutableStateOf(false) }

    // The flow itself: show the current chunk for its time, then move on.
    LaunchedEffect(vm.playing, vm.index, session) {
        if (vm.playing) {
            delay(vm.currentDurationMs())
            vm.advance()
        }
    }
    val view = LocalView.current
    DisposableEffect(vm.playing) {
        view.keepScreenOn = vm.playing
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(session.meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                },
                navigationIcon = {
                    IconButton(onClick = vm::closeReader) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "一覧へ戻る") }
                },
                actions = {
                    ModeSwitch(vm.mode, vm::showMode)
                    IconButton(onClick = {
                        vm.pause()
                        showSettings = true
                    }) { Icon(Icons.Default.Settings, contentDescription = "表示の設定") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LocationBar(session, vm.index, vm.settings.cpm)
            when (vm.mode) {
                ReaderMode.FLOW -> FlowView(vm, session)
                ReaderMode.TEXT -> TextView(vm, session)
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(vm.settings, vm::updateSettings)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(mode: ReaderMode, onMode: (ReaderMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.width(150.dp)) {
        SegmentedButton(
            selected = mode == ReaderMode.FLOW,
            onClick = { onMode(ReaderMode.FLOW) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            icon = {},
            modifier = Modifier.testTag(TestTags.MODE_FLOW),
        ) { Text("流す") }
        SegmentedButton(
            selected = mode == ReaderMode.TEXT,
            onClick = { onMode(ReaderMode.TEXT) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            icon = {},
            modifier = Modifier.testTag(TestTags.MODE_TEXT),
        ) { Text("本文") }
    }
}

/** "Where am I": current heading, progress and remaining time. */
@Composable
private fun LocationBar(session: ReaderSession, index: Int, cpm: Int) {
    val doc = session.doc
    val heading = doc.headingFor(index)?.let { doc.chunks[it.firstChunk].text }
    // Recomputing the remaining time on every chunk is wasteful for long texts.
    val remainingMin = remember(doc, index / 20, cpm) {
        (Pacing.remainingMs(doc, index, cpm) / 60_000.0).let { if (it < 1) "1分未満" else "約${Math.round(it)}分" }
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                heading ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "残り$remainingMin",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { session.nav.progress(index) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

@Composable
private fun FlowView(vm: MainViewModel, session: ReaderSession) {
    val doc = session.doc
    val chunk = doc.chunks.getOrNull(vm.index)
    val settings = vm.settings
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(TestTags.FLOW_AREA)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (chunk?.isStop != true) vm.togglePlay()
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                chunk == null -> Text("文章がありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.finished -> EndPanel(onRestart = vm::restart, onText = { vm.showMode(ReaderMode.TEXT) })
                chunk.isStop -> StopBlock(
                    text = chunk.text,
                    isCode = doc.blocks[chunk.block].kind == BlockKind.CODE,
                    onContinue = vm::continueAfterStop,
                )
                else -> ChunkDisplay(
                    text = chunk.text,
                    isHeading = doc.blocks[chunk.block].kind == BlockKind.HEADING,
                    fontSizeSp = settings.fontSizeSp,
                )
            }
        }
        val contextVisible = !vm.playing || settings.contextWhilePlaying
        ContextPanel(
            session = session,
            index = vm.index,
            modifier = Modifier.alpha(
                when {
                    !contextVisible -> 0f
                    vm.playing -> 0.45f
                    else -> 1f
                },
            ),
            enabled = !vm.playing,
            onJump = vm::jumpToChunk,
        )
        Controls(vm)
    }
}

@Composable
private fun ChunkDisplay(text: String, isHeading: Boolean, fontSizeSp: Int) {
    val guide = MaterialTheme.colorScheme.outlineVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp)) {
        // Small fixation marks keep the eye on one spot.
        Box(Modifier.size(width = 2.dp, height = 14.dp).background(guide))
        Spacer(Modifier.height(18.dp))
        Text(
            text = text,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.35).sp,
            fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Medium,
            color = if (isHeading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .heightIn(min = (fontSizeSp * 1.4).dp)
                .testTag(TestTags.CHUNK_TEXT),
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.size(width = 2.dp, height = 14.dp).background(guide))
    }
}

@Composable
private fun StopBlock(text: String, isCode: Boolean, onContinue: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .testTag(TestTags.STOP_BLOCK),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (isCode) "コード — 止まって確認" else "数式・表 — 止まって確認",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Equations taken from a PDF often arrive scattered over many short lines.
        if (!isCode && text.count { it == '\n' } >= 3) {
            Text(
                "PDFの数式や表は、崩れて取り出されることがあります。元の資料と見比べてください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
            Text(
                text,
                fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Serif,
                fontSize = if (isCode) 14.sp else 22.sp,
                lineHeight = if (isCode) 20.sp else 32.sp,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
        Button(onClick = onContinue, modifier = Modifier.testTag(TestTags.STOP_CONTINUE)) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("続きを流す")
        }
    }
}

@Composable
private fun EndPanel(onRestart: () -> Unit, onText: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag(TestTags.END_PANEL),
    ) {
        Text("おわり", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRestart) { Text("最初から") }
            OutlinedButton(onClick = onText) { Text("本文で見直す") }
        }
    }
}

/** The surrounding text, with the current chunk highlighted. Tap a phrase to jump there. */
@Composable
private fun ContextPanel(
    session: ReaderSession,
    index: Int,
    modifier: Modifier,
    enabled: Boolean,
    onJump: (Int) -> Unit,
) {
    val doc = session.doc
    val chunk = doc.chunks.getOrNull(index)
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .padding(horizontal = 20.dp)
            .testTag(TestTags.CONTEXT_PANEL),
    ) {
        if (chunk == null || chunk.isStop) return@Column
        val sentence = doc.sentences[chunk.sentence]
        val prev = doc.sentences.getOrNull(chunk.sentence - 1)?.takeIf { !doc.chunks[it.firstChunk].isStop }
        if (prev != null) {
            Text(
                doc.sentenceText(prev).replace('\n', ' '),
                style = MaterialTheme.typography.bodyMedium,
                color = muted.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }
        val text = doc.sentenceText(sentence).replace('\n', ' ')
        val annotated = buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(background = highlight, fontWeight = FontWeight.SemiBold),
                (chunk.start - sentence.start).coerceIn(0, text.length),
                (chunk.end - sentence.start).coerceIn(0, text.length),
            )
        }
        TappableText(
            annotated,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            maxLines = 5,
            enabled = enabled,
        ) { offset -> onJump(doc.chunkAt(sentence.start + offset)) }
    }
}

@Composable
private fun TappableText(
    text: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    onTap: (Int) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(enabled, text) {
            if (enabled) {
                detectTapGestures { pos -> layout?.let { onTap(it.getOffsetForPosition(pos)) } }
            }
        },
    )
}

@Composable
private fun Controls(vm: MainViewModel) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = vm::previousSentence, modifier = Modifier.testTag(TestTags.PREV_SENTENCE)) {
                Icon(ReaderIcons.SkipPrevious, contentDescription = "文の先頭・前の文へ")
            }
            IconButton(onClick = { vm.stepChunk(-1) }, modifier = Modifier.testTag(TestTags.PREV_CHUNK)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "1つ戻る")
            }
            FilledIconButton(
                onClick = vm::togglePlay,
                modifier = Modifier
                    .size(64.dp)
                    .testTag(TestTags.PLAY_PAUSE),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    if (vm.playing) ReaderIcons.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (vm.playing) "止める" else "流す",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = { vm.stepChunk(1) }, modifier = Modifier.testTag(TestTags.NEXT_CHUNK)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "1つ進む")
            }
            IconButton(onClick = vm::nextSentence, modifier = Modifier.testTag(TestTags.NEXT_SENTENCE)) {
                Icon(ReaderIcons.SkipNext, contentDescription = "次の文へ")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.changeSpeed(-1) }, modifier = Modifier.testTag(TestTags.SPEED_DOWN)) { Text("遅く", fontSize = 13.sp) }
            Text(
                "${vm.settings.cpm}字/分",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(84.dp)
                    .testTag(TestTags.SPEED_LABEL),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { vm.changeSpeed(1) }, modifier = Modifier.testTag(TestTags.SPEED_UP)) { Text("速く", fontSize = 13.sp) }
        }
    }
}

/** Normal reading: the whole text, current sentence highlighted. Tap a sentence, then "ここから流す". */
@Composable
private fun TextView(vm: MainViewModel, session: ReaderSession) {
    val doc = session.doc
    val current = doc.chunks.getOrNull(vm.index)
    val currentSentence = current?.let { doc.sentences[it.sentence] }
    val listState = rememberLazyListState()
    LaunchedEffect(session) {
        current?.let { listState.scrollToItem((it.block - 1).coerceAtLeast(0)) }
    }
    val highlight = MaterialTheme.colorScheme.primaryContainer
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(TestTags.TEXT_LIST),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(doc.blocks, key = { i, _ -> i }) { i, block ->
                val raw = doc.blockText(block)
                val tagged = Modifier.testTag(TestTags.block(i))
                when (block.kind) {
                    BlockKind.FORMULA, BlockKind.CODE -> Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = if (currentSentence?.block == i) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = tagged
                            .fillMaxWidth()
                            .clickable { vm.selectOffset(block.start) },
                    ) {
                        Text(
                            raw,
                            fontFamily = if (block.kind == BlockKind.CODE) FontFamily.Monospace else FontFamily.Serif,
                            fontSize = if (block.kind == BlockKind.CODE) 13.sp else 18.sp,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                    else -> {
                        val lead = if (block.kind == BlockKind.HEADING) raw.length - raw.trimStart('#', ' ').length else 0
                        val shown = raw.substring(lead)
                        val annotated = buildAnnotatedString {
                            append(shown)
                            if (currentSentence != null && currentSentence.block == i) {
                                addStyle(
                                    SpanStyle(background = highlight),
                                    (currentSentence.start - block.start - lead).coerceIn(0, shown.length),
                                    (currentSentence.end - block.start - lead).coerceIn(0, shown.length),
                                )
                            }
                        }
                        val style = when (block.kind) {
                            BlockKind.HEADING -> MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary)
                            else -> MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)
                        }
                        TappableText(
                            annotated,
                            style = style,
                            modifier = tagged
                                .fillMaxWidth()
                                .padding(top = if (block.kind == BlockKind.HEADING) 10.dp else 0.dp),
                        ) { offset -> vm.selectOffset(block.start + lead + offset) }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = vm::flowFromHere,
            icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            text = { Text("ここから流す") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag(TestTags.READ_FROM_HERE),
        )
    }
}

@Composable
private fun SettingsSheet(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("速さ  ${settings.cpm}字/分", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = settings.cpm.toFloat(),
            onValueChange = { onChange(settings.copy(cpm = (Math.round(it / Pacing.CPM_STEP) * Pacing.CPM_STEP))) },
            valueRange = Pacing.MIN_CPM.toFloat()..Pacing.MAX_CPM.toFloat(),
        )
        Text("まとまりの長さ", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ChunkSize.SHORT to "短め", ChunkSize.NORMAL to "標準", ChunkSize.LONG to "長め").forEach { (size, label) ->
                FilterChip(
                    selected = settings.chunkSize == size,
                    onClick = { onChange(settings.copy(chunkSize = size)) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("文字の大きさ", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = settings.fontSizeSp.toFloat(),
            onValueChange = { onChange(settings.copy(fontSizeSp = it.toInt())) },
            valueRange = ReaderSettings.MIN_FONT_SP.toFloat()..ReaderSettings.MAX_FONT_SP.toFloat(),
        )
        Text(
            "あ  電磁波は",
            fontSize = settings.fontSizeSp.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "流している間も、前後の文を薄く表示する",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = settings.contextWhilePlaying, onCheckedChange = { onChange(settings.copy(contextWhilePlaying = it)) })
        }
        Text(
            "止めているときは、いつでも下に前後の文が出ます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

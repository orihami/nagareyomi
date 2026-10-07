package com.orihami.nagareyomi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orihami.nagareyomi.core.DocMeta

/** What the "読み込む" row can do. */
class ImportActions(
    val paste: () -> Unit,
    val file: () -> Unit,
    val image: () -> Unit,
    val camera: () -> Unit,
    val type: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    docs: List<DocMeta>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    actions: ImportActions,
) {
    var confirmDelete by remember { mutableStateOf<DocMeta?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ながれよみ") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(TestTags.LIBRARY_LIST),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ImportCard(actions) }
            if (docs.isEmpty()) {
                item {
                    Text(
                        "まだ資料がありません。上のボタンから、PDF・画像・文章を読み込んでください。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                item {
                    Text(
                        "資料",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    )
                }
            }
            items(docs, key = { it.id }) { doc ->
                DocCard(doc, onOpen = { onOpen(doc.id) }, onDelete = { confirmDelete = doc })
            }
        }
    }
    confirmDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("削除しますか？") },
            text = { Text("「${doc.title}」をこの端末から削除します。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(doc.id)
                    confirmDelete = null
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("やめる") } },
        )
    }
}

@Composable
private fun ImportCard(actions: ImportActions) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("読み込む", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ImportButton("貼り付け", TestTags.IMPORT_PASTE, actions.paste, Modifier.weight(1f))
                ImportButton("PDF・ファイル", TestTags.IMPORT_FILE, actions.file, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ImportButton("画像から", TestTags.IMPORT_IMAGE, actions.image, Modifier.weight(1f))
                ImportButton("撮影して", TestTags.IMPORT_CAMERA, actions.camera, Modifier.weight(1f))
                ImportButton("入力", TestTags.IMPORT_TYPE, actions.type, Modifier.weight(1f))
            }
            Text(
                "ほかのアプリで文章を選んで「共有」や「ながれよみ」を選んでも読めます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportButton(label: String, tag: String, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag(tag),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DocCard(doc: DocMeta, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.doc(doc.id))
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { doc.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp),
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        progressLabel(doc),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "メニュー") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("削除") }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
    }
}

private fun progressLabel(doc: DocMeta): String {
    val pct = (doc.progress * 100).toInt()
    val chars = if (doc.length >= 10_000) "${doc.length / 1000}千字" else "${doc.length}字"
    return when {
        pct >= 99 -> "読了・$chars"
        pct == 0 -> chars
        else -> "$pct%・$chars"
    }
}

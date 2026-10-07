package com.orihami.nagareyomi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orihami.nagareyomi.ImportDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    draft: ImportDraft,
    onBack: () -> Unit,
    onChange: (title: String, text: String) -> Unit,
    onExtractPages: (from: Int, to: Int) -> Unit,
    onOcrPdf: () -> Unit,
    onRead: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("読み込み") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (draft.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            draft.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (draft.pdfUri != null && draft.pageCount > 0) {
                PageRange(draft, onExtractPages, onOcrPdf)
            }
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onChange(it, draft.text) },
                label = { Text("タイトル（空欄なら1行目）") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.DRAFT_TITLE),
            )
            OutlinedTextField(
                value = draft.text,
                onValueChange = { onChange(draft.title, it) },
                label = { Text("読む文章") },
                placeholder = { Text("ここに文章を貼り付けるか入力してください。不要な部分は消してから読めます。") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(TestTags.DRAFT_TEXT),
            )
            Button(
                onClick = onRead,
                enabled = draft.text.isNotBlank() && !draft.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag(TestTags.DRAFT_READ),
            ) {
                Text("流し読みを始める")
            }
        }
    }
}

@Composable
private fun PageRange(draft: ImportDraft, onExtract: (Int, Int) -> Unit, onOcr: () -> Unit) {
    var from by remember(draft.pdfUri) { mutableStateOf(draft.pageFrom.toString()) }
    var to by remember(draft.pdfUri) { mutableStateOf(draft.pageTo.toString()) }
    LaunchedEffect(draft.pageFrom, draft.pageTo) {
        from = draft.pageFrom.toString()
        to = draft.pageTo.toString()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = from,
                onValueChange = { from = it.filter(Char::isDigit).take(4) },
                label = { Text("ページ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp),
            )
            Text("〜")
            OutlinedTextField(
                value = to,
                onValueChange = { to = it.filter(Char::isDigit).take(4) },
                label = { Text("/ ${draft.pageCount}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp),
            )
            OutlinedButton(
                onClick = { onExtract(from.toIntOrNull() ?: 1, to.toIntOrNull() ?: draft.pageCount) },
                enabled = !draft.busy,
            ) { Text("取り出す") }
        }
        if (draft.pdfNeedsOcr) {
            Button(onClick = onOcr, enabled = !draft.busy) { Text("ページを画像として文字認識する") }
        }
    }
}

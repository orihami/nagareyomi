package com.orihami.nagareyomi

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.FileProvider
import com.orihami.nagareyomi.ui.ImportActions
import com.orihami.nagareyomi.ui.ImportScreen
import com.orihami.nagareyomi.ui.LibraryScreen
import com.orihami.nagareyomi.ui.ReaderScreen
import com.orihami.nagareyomi.ui.theme.NagareTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            NagareTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        vm.pause()
    }

    /** Text or files handed over by other apps (share, "select text → ながれよみ", open with). */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!text.isNullOrBlank()) vm.readTextNow(text, source = "selection")
            }
            Intent.ACTION_SEND -> {
                val stream = streamOf(intent)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                when {
                    stream != null -> vm.importUri(stream, intent.type)
                    !text.isNullOrBlank() -> vm.readTextNow(text, title = subject, source = "share")
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { vm.importUri(it, intent.type) }
        }
    }

    @Suppress("DEPRECATION")
    private fun streamOf(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
}

@Composable
fun App(vm: MainViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importUri(it) }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.importUri(it, "image/*") }
    }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) vm.importUri(uri, "image/jpeg")
    }

    when (vm.screen) {
        Screen.Library -> LibraryScreen(
            docs = vm.library,
            onOpen = vm::open,
            onDelete = vm::delete,
            actions = ImportActions(
                paste = {
                    val text = clipboard.getText()?.text
                    if (text.isNullOrBlank()) vm.startDraft() else vm.startDraft(text = text, source = "clipboard")
                },
                file = { pickFile.launch(arrayOf("application/pdf", "text/*")) },
                image = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                camera = {
                    val dir = File(context.cacheDir, "images").apply { mkdirs() }
                    val file = File(dir, "capture.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    photoUri = uri
                    takePhoto.launch(uri)
                },
                type = { vm.startDraft() },
            ),
        )
        Screen.Import -> {
            BackHandler { vm.cancelDraft() }
            ImportScreen(
                draft = vm.draft,
                onBack = vm::cancelDraft,
                onChange = { title, text -> vm.updateDraft(title, text) },
                onExtractPages = vm::extractPdf,
                onOcrPdf = vm::ocrPdf,
                onRead = vm::saveDraftAndRead,
            )
        }
        Screen.Reader -> {
            BackHandler { vm.closeReader() }
            ReaderScreen(vm)
        }
    }
}

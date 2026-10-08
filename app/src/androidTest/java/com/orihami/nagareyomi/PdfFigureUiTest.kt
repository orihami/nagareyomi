package com.orihami.nagareyomi

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.orihami.nagareyomi.data.DocumentStore
import com.orihami.nagareyomi.data.Importers
import com.orihami.nagareyomi.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File
import java.io.FileOutputStream

/** A document read from a PDF shows its equation as an image of the original page. */
class PdfFigureUiTest {

    @get:Rule(order = 0)
    val data = object : ExternalResource() {
        override fun before() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            File(ctx.filesDir, "docs").deleteRecursively()
            ctx.getSharedPreferences("store", 0).edit().clear().commit()
            ctx.getSharedPreferences("settings", 0).edit().clear().commit()
            val pdf = File(ctx.cacheDir, "figure-ui.pdf")
            PdfLayoutTest.makePdf(pdf)
            val r = runBlocking { Importers(ctx).pdfExtract(android.net.Uri.fromFile(pdf), 1, 1) {} }
            val store = DocumentStore(ctx)
            val meta = store.add("図のある資料", r.text, "pdf")
            pdf.inputStream().use { store.savePdf(meta.id, it, r.regions) }
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun equationIsShownAsAnImageOfThePage() {
        rule.onNodeWithText("図のある資料").performClick()
        rule.onNodeWithTag(TestTags.NEXT_SENTENCE).performClick() // → the equation
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasContentDescription("〔式 1-1〕"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        saveScreenshot("07_equation_image.png")
        rule.onNodeWithText("数式 — 止まって確認（タップで拡大）", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(TestTags.STOP_CONTINUE).assertIsDisplayed().performClick()
        // The next part is the picture: the reader stops on it too and shows it.
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("図 — 止まって確認（タップで拡大）"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasContentDescription("〔図 1-1〕"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        saveScreenshot("08_figure_image.png")
        rule.onNodeWithTag(TestTags.MODE_TEXT).performClick()
        rule.waitForIdle()
        saveScreenshot("09_text_view_with_images.png")
    }

    private fun saveScreenshot(fileName: String) {
        rule.waitForIdle()
        val bitmap: Bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "ui-test-screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, fileName)).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    }
}

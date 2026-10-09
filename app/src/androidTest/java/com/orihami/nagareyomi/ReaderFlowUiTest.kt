package com.orihami.nagareyomi

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.test.platform.app.InstrumentationRegistry
import com.orihami.nagareyomi.core.DocumentParser
import com.orihami.nagareyomi.core.SampleText
import com.orihami.nagareyomi.core.TextNormalizer
import com.orihami.nagareyomi.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-end checks of the reading flow on an emulator:
 * library → RSVP → step / play → normal text view → back to RSVP,
 * and typing in a text that contains an equation (the reader must stop on it).
 * Screenshots of each state are saved for the CI artifact.
 */
class ReaderFlowUiTest {

    /** Start every test from a fresh install state (only the sample document). */
    @get:Rule(order = 0)
    val cleanData = object : ExternalResource() {
        override fun before() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            File(ctx.filesDir, "docs").deleteRecursively()
            ctx.getSharedPreferences("store", 0).edit().clear().commit()
            ctx.getSharedPreferences("settings", 0).edit().clear().commit()
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val sample = DocumentParser.parse(TextNormalizer.normalize(SampleText.TEXT))

    private fun chunkText(): String =
        // The flow area is clickable, which merges its children; look at the unmerged tree.
        rule.onNodeWithTag(TestTags.CHUNK_TEXT, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun openSample() {
        rule.onNodeWithText(SampleText.TITLE).performClick()
        rule.waitForIdle()
    }

    @Test
    fun library_showsSampleAndImportButtons() {
        rule.onNodeWithText(SampleText.TITLE).assertIsDisplayed()
        rule.onNodeWithTag(TestTags.IMPORT_PASTE).assertIsDisplayed()
        rule.onNodeWithTag(TestTags.IMPORT_FILE).assertIsDisplayed()
        saveScreenshot("01_library.png")
    }

    @Test
    fun openingADocument_showsItsFirstPhraseAndStepsThroughPhrases() {
        openSample()
        assertEquals(sample.chunks[0].text, chunkText())
        rule.onNodeWithTag(TestTags.CONTEXT_PANEL).assertIsDisplayed()

        rule.onNodeWithTag(TestTags.NEXT_CHUNK).performClick()
        rule.onNodeWithTag(TestTags.NEXT_CHUNK).performClick()
        assertEquals(sample.chunks[2].text, chunkText())
        saveScreenshot("02_flow_paused.png")

        // "前の文": back to the start of the current sentence.
        rule.onNodeWithTag(TestTags.PREV_SENTENCE).performClick()
        val s = sample.sentences[sample.chunks[2].sentence]
        assertEquals(sample.chunks[s.firstChunk].text, chunkText())
    }

    @Test
    fun playing_advancesByItselfAndPauses() {
        openSample()
        val first = chunkText()
        rule.onNodeWithTag(TestTags.PLAY_PAUSE).performClick()
        rule.waitUntil(timeoutMillis = 10_000) { chunkText() != first }
        saveScreenshot("03_flow_playing.png")
        rule.onNodeWithTag(TestTags.FLOW_AREA).performClick() // tap the centre = pause
        val paused = chunkText()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        assertEquals(paused, chunkText())
        assertNotEquals(first, paused)
    }

    @Test
    fun textView_tapASentence_thenFlowFromThere() {
        openSample()
        rule.onNodeWithTag(TestTags.MODE_TEXT).performClick()
        rule.onNodeWithTag(TestTags.TEXT_LIST).assertIsDisplayed()
        saveScreenshot("04_text_view.png")

        // Tap the paragraph of the "回路の例" section and read from there.
        val block = sample.blocks.indexOfFirst { sample.blockText(it).startsWith("抵抗R") }
        rule.onNodeWithTag(TestTags.TEXT_LIST).performScrollToNode(hasTestTag(TestTags.block(block)))
        rule.onNodeWithTag(TestTags.block(block)).performClick()
        rule.onNodeWithTag(TestTags.READ_FROM_HERE).performClick()
        rule.onNodeWithTag(TestTags.PLAY_PAUSE).performClick() // pause right away to inspect
        rule.waitForIdle()

        val shown = chunkText()
        val b = sample.blocks[block]
        val inBlock = (b.firstChunk..b.lastChunk).map { sample.chunks[it].text }
        assertTrue("[$shown] is not in the tapped paragraph", shown in inBlock)
        saveScreenshot("05_back_to_flow.png")
    }

    @Test
    fun typedText_withEquation_stopsOnTheEquation() {
        rule.onNodeWithTag(TestTags.IMPORT_TYPE).performClick()
        rule.onNodeWithTag(TestTags.DRAFT_TEXT).performTextInput("オームの法則は次の式で表される。\nV = IR\n電圧は電流に比例する。")
        rule.onNodeWithTag(TestTags.DRAFT_READ).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(TestTags.PLAY_PAUSE).performClick()
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithTagExists(TestTags.STOP_BLOCK)
        }
        rule.onNodeWithText("V = IR", useUnmergedTree = true).assertIsDisplayed()
        saveScreenshot("06_equation_stop.png")

        rule.onNodeWithTag(TestTags.STOP_CONTINUE).performClick()
        rule.onNodeWithTag(TestTags.PLAY_PAUSE).performClick()
        rule.waitForIdle()
        assertTrue(chunkText().isNotBlank())
    }

    @Test
    fun speedButtons_changeTheSpeed() {
        openSample()
        rule.onNodeWithText("600字/分").assertIsDisplayed()
        rule.onNodeWithTag(TestTags.SPEED_UP).performClick()
        rule.onNodeWithText("700字/分").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagExists(tag: String): Boolean =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Saved to the app's internal files dir; CI pulls them with `run-as`. */
    private fun saveScreenshot(fileName: String) {
        rule.waitForIdle()
        val bitmap: Bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "ui-test-screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, fileName)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}

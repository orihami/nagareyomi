package com.orihami.nagareyomi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {
    private fun split(s: String) = SentenceSplitter.split(s).map { (a, b) -> s.substring(a, b) }

    @Test
    fun japanesePeriods() {
        assertEquals(listOf("電場がある。", "磁場もある。"), split("電場がある。磁場もある。"))
    }

    @Test
    fun academicFullWidthPeriodAndQuotes() {
        assertEquals(listOf("これは「法則」である．", "次に進む．"), split("これは「法則」である．次に進む．"))
        assertEquals(listOf("彼は「本当か？」と言った。"), split("彼は「本当か？」と言った。"))
    }

    @Test
    fun englishWithAbbreviationsAndDecimals() {
        assertEquals(
            listOf("See Fig. 3 for details.", "The value is 3.14 exactly.", "Next, e.g. this one."),
            split("See Fig. 3 for details. The value is 3.14 exactly. Next, e.g. this one."),
        )
    }

    @Test
    fun initialsAreNotSentenceEnds() {
        assertEquals(1, split("J. C. Maxwell unified electricity and magnetism.").size)
    }
}

class TextNormalizerTest {

    @Test
    fun pdfHardWrappedJapaneseLinesAreJoinedWithoutSpaces() {
        val raw = "電磁波は電場と磁場が相互に\n変化しながら空間を伝わる\n現象である。\n\n次の段落。"
        assertEquals("電磁波は電場と磁場が相互に変化しながら空間を伝わる現象である。\n\n次の段落。", TextNormalizer.normalize(raw))
    }

    @Test
    fun englishWrappedLinesAndHyphenation() {
        val raw = "The elec-\ntromagnetic wave travels\nthrough space."
        assertEquals("The electromagnetic wave travels through space.", TextNormalizer.normalize(raw))
    }

    @Test
    fun pageNumbersAndSpacesBetweenKanjiAreRemoved() {
        val raw = "電 磁 気 学 の 基 礎\n\n- 12 -\n\n本文です。"
        assertEquals("電磁気学の基礎\n\n本文です。", TextNormalizer.normalize(raw))
    }

    @Test
    fun fullWidthAlnumBecomesHalfWidth() {
        assertEquals("電圧V=10V（直流）", TextNormalizer.normalize("電圧Ｖ=１０Ｖ（直流）"))
    }

    @Test
    fun equationsAndHeadingsStayOnTheirOwnLines() {
        val raw = "波長と周波数の関係は\nc = fλ\nとなる。\n第2章 回路\n本文"
        val lines = TextNormalizer.normalize(raw).lines()
        assertTrue(lines.toString(), "c = fλ" in lines)
        assertTrue(lines.toString(), "第2章 回路" in lines)
    }
}

class LineClassifierTest {
    @Test
    fun formulas() {
        assertTrue(LineClassifier.looksLikeFormula("c = fλ"))
        assertTrue(LineClassifier.looksLikeFormula("V = IR"))
        assertTrue(LineClassifier.looksLikeFormula("∇ × E = −∂B/∂t"))
        assertTrue(LineClassifier.looksLikeFormula("\\frac{1}{2} m v^2"))
        assertFalse(LineClassifier.looksLikeFormula("両端の電圧VはV = IRで表される。"))
        assertFalse(LineClassifier.looksLikeFormula("The answer is that x = 3 in this problem."))
    }

    @Test
    fun headingsCodeAndLists() {
        assertEquals(LineKind.HEADING, LineClassifier.classify("# はじめに"))
        assertEquals(LineKind.HEADING, LineClassifier.classify("第3章 電磁誘導"))
        assertEquals(LineKind.HEADING, LineClassifier.classify("2.1 ガウスの法則"))
        assertEquals(LineKind.CODE, LineClassifier.classify("int main() {"))
        assertEquals(LineKind.CODE, LineClassifier.classify("    x = foo(y);"))
        assertEquals(LineKind.LIST_ITEM, LineClassifier.classify("・電場の定義"))
        assertEquals(LineKind.TEXT, LineClassifier.classify("電磁波は空間を伝わる。"))
    }
}

class DocumentParserTest {

    private val doc = DocumentParser.parse(SampleText.TEXT)

    @Test
    fun chunksCoverTextInOrderAndPointIntoIt() {
        var last = -1
        for (c in doc.chunks) {
            assertTrue(c.start >= last)
            assertTrue(c.end > c.start)
            last = c.end
            if (!c.isStop) {
                val source = doc.text.substring(c.start, c.end).replace('\n', ' ').trimStart('#', ' ')
                assertEquals(source, c.text)
            }
        }
    }

    @Test
    fun headingsFormulasAndParagraphsAreRecognized() {
        val kinds = doc.blocks.map { it.kind }
        assertEquals(BlockKind.HEADING, kinds.first())
        assertTrue(BlockKind.FORMULA in kinds)
        val formula = doc.blocks.first { it.kind == BlockKind.FORMULA }
        assertEquals("c = fλ", doc.blockText(formula))
        val stop = doc.chunks.single { it.isStop }
        assertEquals("c = fλ", stop.text)
    }

    @Test
    fun pausesGrowAtSentenceParagraphAndHeadingEnds() {
        val heading = doc.blocks.first()
        assertEquals(Pause.HEADING, doc.chunks[heading.lastChunk].pause)
        assertEquals("はじめに", doc.chunks[heading.firstChunk].text)
        for (s in doc.sentences) assertTrue(doc.chunks[s.lastChunk].pause >= Pause.SENTENCE)
        val para = doc.blocks.first { it.kind == BlockKind.PARAGRAPH }
        assertTrue(doc.chunks[para.lastChunk].pause >= Pause.PARAGRAPH)
    }

    @Test
    fun chunkAtAndHeadingFor() {
        val target = doc.chunks[10]
        assertEquals(10, doc.chunkAt(target.start))
        assertEquals(10, doc.chunkAt(target.start + 1))
        assertEquals(0, doc.chunkAt(0))
        val circuit = doc.chunks.first { it.text.startsWith("抵抗R") }
        assertEquals("# 回路の例", doc.blockText(assertNotNullAndGet(doc.headingFor(circuit.index))))
    }

    private fun <T> assertNotNullAndGet(v: T?): T {
        assertNotNull(v)
        return v!!
    }

    @Test
    fun codeFenceBecomesOneStopBlock() {
        val d = DocumentParser.parse("説明します。\n\n```\nfor (i in 0..3) {\n  println(i)\n}\n```\n\n終わり。")
        val code = d.blocks.single { it.kind == BlockKind.CODE }
        assertTrue(d.blockText(code).contains("println(i)"))
        assertEquals(3, d.blocks.size)
    }

    @Test
    fun emptyTextParses() {
        val d = DocumentParser.parse("")
        assertTrue(d.chunks.isEmpty())
        assertEquals(0, d.chunkAt(5))
    }
}

class PacingAndNavigationTest {
    private val doc = DocumentParser.parse(SampleText.TEXT)
    private val nav = ReaderNavigator(doc)

    private fun chunk(text: String, pause: Pause) = Chunk(0, text, 0, text.length, 0, 0, pause, false)

    @Test
    fun pausesMakeChunksLonger() {
        val cpm = 500
        val none = Pacing.durationMs(chunk("電場と磁場が", Pause.NONE), cpm)
        val comma = Pacing.durationMs(chunk("電場と磁場が", Pause.COMMA), cpm)
        val sentence = Pacing.durationMs(chunk("電場と磁場が", Pause.SENTENCE), cpm)
        val paragraph = Pacing.durationMs(chunk("電場と磁場が", Pause.PARAGRAPH), cpm)
        assertTrue(none < comma && comma < sentence && sentence < paragraph)
    }

    @Test
    fun longerAndTechnicalChunksTakeLonger() {
        assertTrue(Pacing.durationMs(chunk("電磁波は", Pause.NONE), 500) < Pacing.durationMs(chunk("相互に変化しながら", Pause.NONE), 500))
        assertTrue(Pacing.durationMs(chunk("約3.0×10^8 m/s", Pause.NONE), 500) > Pacing.durationMs(chunk("約さんてんぜろ", Pause.NONE), 500))
    }

    @Test
    fun fasterSpeedIsShorterAndRampSlowsTheStart() {
        val c = chunk("電場と磁場が", Pause.NONE)
        assertTrue(Pacing.durationMs(c, 1000) < Pacing.durationMs(c, 400))
        assertTrue(Pacing.durationMs(c, 500, rampStep = 0) > Pacing.durationMs(c, 500, rampStep = 2))
        assertTrue(Pacing.durationMs(c, 500, rampStep = 2) > Pacing.durationMs(c, 500))
    }

    @Test
    fun previousSentenceBehavesLikeAPlayerButton() {
        val s1 = doc.sentences[1]
        val mid = s1.firstChunk + 1
        assertEquals(s1.firstChunk, nav.previousSentence(mid))
        assertEquals(doc.sentences[0].firstChunk, nav.previousSentence(s1.firstChunk))
        assertEquals(0, nav.previousSentence(0))
    }

    @Test
    fun nextSentenceAndResumePoint() {
        val s1 = doc.sentences[1]
        assertEquals(doc.sentences[2].firstChunk, nav.nextSentence(s1.firstChunk))
        assertEquals(s1.firstChunk, nav.resumePoint(s1.lastChunk))
        assertEquals(nav.lastIndex, nav.nextSentence(nav.lastIndex))
    }

    @Test
    fun progressAndOffsets() {
        assertEquals(0f, nav.progress(0))
        assertEquals(1f, nav.progress(nav.lastIndex))
        val i = 15
        assertEquals(i, doc.chunkAt(nav.offsetOf(i)))
        assertTrue(Pacing.remainingMs(doc, 0, 500) > Pacing.remainingMs(doc, 20, 500))
    }

    @Test
    fun positionSurvivesChangingChunkSize() {
        val i = 20
        val offset = nav.offsetOf(i)
        val other = DocumentParser.parse(SampleText.TEXT, ChunkSize.SHORT)
        val j = other.chunkAt(offset)
        assertEquals(doc.chunks[i].sentence, other.chunks[j].sentence)
    }
}

class DocMetaTest {
    @Test
    fun roundTripWithAwkwardCharacters() {
        val m = DocMeta("abc", "タイトル\n2行目 \\ =値", "pdf", 1L, 2L, 30, 100)
        assertEquals(m, DocMeta.decode(DocMeta.encode(m)))
        assertEquals(0.3f, m.progress, 0.001f)
    }

    @Test
    fun titleFromFirstLine() {
        assertEquals("はじめに", DocMeta.titleFrom("\n# はじめに\n本文"))
        assertEquals("1234567890…", DocMeta.titleFrom("12345678901234", max = 10))
        assertEquals("無題", DocMeta.titleFrom("  \n "))
    }
}

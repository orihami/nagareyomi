package com.orihami.nagareyomi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutAssemblerTest {

    private fun assemble(block: LayoutAssembler.() -> Unit) = LayoutAssembler().apply(block)

    @Test
    fun equationLinesBecomeOneMarkerCoveringTheirArea() {
        val a = assemble {
            startPage(5, emptyList(), 595f, 842f)
            line("このような場合，平均値 y は次のように表される。", 60f, 100f, 500f, 112f)
            line("2 31", 90f, 120f, 150f, 130f)
            line("1 n", 95f, 132f, 120f, 140f)
            line("y y y y", 90f, 142f, 260f, 156f)
            line(" ", 92f, 150f, 120f, 160f)
            line("（検 証）", 60f, 180f, 120f, 192f)
            endPage()
        }
        val lines = a.text().lines().filter { it.isNotBlank() }
        assertEquals(listOf("このような場合，平均値 y は次のように表される。", "〔式 5-1〕", "（検 証）"), lines)
        val r = a.regions.getValue("〔式 5-1〕")
        assertEquals(5, r.page)
        assertTrue(r.x <= 90f && r.x + r.w >= 260f && r.y <= 120f && r.y + r.h >= 160f)
    }

    @Test
    fun sideBySidePiecesOfOneEquationAreMerged() {
        val a = assemble {
            startPage(7, emptyList(), 595f, 842f)
            line("xy x y", 87f, 80f, 230f, 115f)
            line("x y xy x", 228f, 80f, 335f, 116f)
            line("以上より、関係式は", 60f, 130f, 300f, 142f)
            endPage()
        }
        assertEquals(1, a.regions.size)
        assertEquals("〔式 7-1〕", a.text().lines().first { it.isNotBlank() })
    }

    @Test
    fun picturesGoWhereTheyAreOnThePage() {
        val fig = PageRect(0, 300f, 400f, 230f, 130f)
        val a = assemble {
            startPage(5, listOf(fig), 595f, 842f)
            line("5", 290f, 800f, 300f, 810f) // page number drawn first, at the bottom
            line("上の段落です。", 60f, 100f, 300f, 112f)
            line("下の段落です。", 60f, 600f, 300f, 612f)
            endPage()
        }
        val lines = a.text().lines().filter { it.isNotBlank() }
        assertEquals(listOf("5", "上の段落です。", "〔図 5-1〕", "下の段落です。"), lines)
        assertEquals(5, a.regions.getValue("〔図 5-1〕").page)
    }

    @Test
    fun picturePagesAreSparseAndCanBeReplacedByOcr() {
        val scan = PageRect(0, 0f, 0f, 595f, 842f)
        val small = PageRect(0, 50f, 500f, 200f, 150f)
        val a = assemble {
            startPage(1, emptyList(), 595f, 842f)
            line("本文のあるページです。十分な長さの文章がここにあります。これで三十字を超えます。", 60f, 100f, 500f, 112f)
            endPage()
            startPage(2, listOf(scan), 595f, 842f)
            endPage()
            startPage(3, listOf(small), 595f, 842f)
            line("図1", 60f, 660f, 80f, 672f)
            endPage()
        }
        assertEquals(listOf(2, 3), a.sparsePages)
        val replaced = mapOf(2 to "スキャンの文字", 3 to "図の説明")
        val text = a.text(replaced)
        assertTrue(text.contains("スキャンの文字"))
        assertFalse("a full-page scan is not kept as a figure", text.contains("〔図 2-1〕"))
        assertTrue("a real figure is kept", text.contains("〔図 3-1〕"))
        assertEquals(setOf("〔図 3-1〕"), a.regions(replaced).keys)
    }

    @Test
    fun markersRoundTripAndParse() {
        val regions = mapOf("〔式 3-1〕" to PageRect(3, 1f, 2.5f, 30f, 40f), "〔図 8-2〕" to PageRect(8, 0f, 0f, 1f, 1f))
        assertEquals(regions, Markers.decodeRegions(Markers.encodeRegions(regions)))
        assertEquals(Markers.Kind.FIGURE, Markers.kindOf(" 〔図 8-2〕 "))
        assertEquals(null, Markers.kindOf("〔式〕を見る"))
    }
}

class MarkerParsingTest {
    @Test
    fun markersBecomeStopBlocksThatSurviveNormalization() {
        val text = TextNormalizer.normalize("平均値は次の式で表される。\n〔式 5-1〕\n〔図 5-1〕\n（検 証）\n真値に最も近い値を a とする。")
        val doc = DocumentParser.parse(text)
        val stops = doc.chunks.filter { it.isStop }.map { it.text }
        assertEquals(listOf("〔式 5-1〕", "〔図 5-1〕"), stops)
        assertEquals(BlockKind.FIGURE, doc.blocks.first { doc.blockText(it) == "〔図 5-1〕" }.kind)
    }

    @Test
    fun dotLeadersAndLongBracketLinesAreNotHeadingsOrBullets() {
        assertEquals(LineKind.FORMULA, LineClassifier.classify("・ ・ ・ ・ ・"))
        assertEquals(LineKind.TEXT, LineClassifier.classify("【演習 2】 ばねの一端を固定し，もう一端に力 x [N]を加えてばねの伸び y"))
        assertEquals(LineKind.HEADING, LineClassifier.classify("【参考資料】"))
    }
}

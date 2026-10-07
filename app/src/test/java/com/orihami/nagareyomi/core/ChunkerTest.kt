package com.orihami.nagareyomi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {

    private fun chunks(s: String, size: ChunkSize = ChunkSize.NORMAL) =
        Chunker.chunk(s, size).map { s.substring(it.start, it.end) }

    @Test
    fun japaneseSentence_isCutIntoMeaningfulPhrases() {
        assertEquals(
            listOf("電磁波は", "電場と磁場が", "相互に変化しながら", "空間を伝わる現象である。"),
            chunks("電磁波は電場と磁場が相互に変化しながら空間を伝わる現象である。"),
        )
    }

    @Test
    fun chunksCoverTheWholeSentenceInOrder() {
        val s = "マクスウェル方程式によれば、時間変化する磁場は電場を生み、時間変化する電場は磁場を生む。"
        val spans = Chunker.chunk(s)
        assertEquals(s.replace(" ", ""), spans.joinToString("") { s.substring(it.start, it.end) })
        for (k in 1 until spans.size) assertTrue(spans[k].start >= spans[k - 1].end)
    }

    @Test
    fun commaEndsAChunkWithACommaPause() {
        val spans = Chunker.chunk("マクスウェル方程式によれば、磁場は電場を生む。")
        val first = spans.first()
        assertEquals("マクスウェル方程式によれば、", "マクスウェル方程式によれば、磁場は電場を生む。".substring(first.start, first.end))
        assertEquals(Pause.COMMA, first.pause)
        assertEquals(Pause.SENTENCE, spans.last().pause)
    }

    @Test
    fun numberStaysWithItsUnit() {
        for (c in chunks("周波数2.4 GHzの電波の波長は約12.5 cmになる。")) {
            assertFalse("number cut from unit in [$c]", c.trimEnd().endsWith("2.4") || c.trimEnd().endsWith("12.5"))
        }
        assertTrue(chunks("周波数2.4 GHzの電波の波長は約12.5 cmになる。").any { it.contains("2.4 GHz") })
    }

    @Test
    fun inlineEquationIsNotSplitAtTheOperator() {
        val c = chunks("両端の電圧VはV = IRで表される。")
        assertTrue(c.toString(), c.any { it.contains("V = IR") })
    }

    @Test
    fun scientificNotationStaysTogether() {
        val c = chunks("真空中での伝搬速度は約3.0×10^8 m/sであり、これを光速と呼ぶ。")
        assertTrue(c.toString(), c.any { it.contains("3.0×10^8 m/s") })
    }

    @Test
    fun englishIsShownAFewWordsAtATime() {
        val c = chunks("In English text, words are shown two or three at a time, so that each phrase can be read at a glance.")
        for (chunk in c) {
            val words = chunk.split(' ').size
            assertTrue("too many words in [$chunk]", words in 1..5)
        }
        // A chunk never ends on an article.
        assertTrue(c.toString(), c.none { it.endsWith(" a") || it.endsWith(" the") })
    }

    @Test
    fun okuriganaIsNotCutOff() {
        // 考え|方 must not become two chunks just because the length target was reached.
        assertTrue(chunks("電磁気学における基本的な考え方を説明する。").none { it.endsWith("考え") })
    }

    @Test
    fun quotedTermStaysWithItsParticle() {
        assertTrue(chunks("これを「オームの法則」という。").any { it.contains("」と") || it.endsWith("」という。") })
    }

    @Test
    fun shortSizeMakesMoreChunksThanLong() {
        val s = "時間変化する磁場は電場を生み、時間変化する電場は磁場を生む。この連鎖が、波として遠くまで伝わっていく。"
        assertTrue(Chunker.chunk(s, ChunkSize.SHORT).size > Chunker.chunk(s, ChunkSize.LONG).size)
    }

    @Test
    fun veryLongHiraganaRunIsSplit() {
        val s = "ことができるようになるということがわかっているのであるがそうではないかもしれないとおもわれる"
        val c = chunks(s)
        assertTrue(c.size >= 2)
        assertEquals(s, c.joinToString(""))
    }
}

class ChunkerOkuriganaTest {
    @Test
    fun compoundVerbIsNotSplit() {
        val s = "上の「本文」に切り替えてください。"
        val c = Chunker.chunk(s).map { s.substring(it.start, it.end) }
        assertTrue(c.toString(), c.none { it.endsWith("切り") })
    }
}

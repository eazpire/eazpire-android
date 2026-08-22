package com.eazpire.creator.ui.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoClipperCaptionsTest {
    private fun words() = listOf(
        ClipperWord("Hello", 1.0, 1.2),
        ClipperWord("friends", 1.2, 1.5),
        ClipperWord("today", 1.5, 1.8),
        ClipperWord("we", 1.8, 2.0),
        ClipperWord("talk", 2.0, 2.3),
        ClipperWord("health", 2.3, 2.7),
        ClipperWord("later", 10.0, 10.4),
    )

    @Test
    fun wrapsWordsIntoTimedBlocks() {
        val blocks = VideoClipperCaptions.buildBlocks(words(), 4, 2, 0.0, 5.0)
        assertEquals(2, blocks.size)
        assertEquals(2, blocks[0].lines.size)
        assertTrue(blocks[0].lines[0].contains("Hello"))
        assertTrue(VideoClipperCaptions.atTime(blocks, 1.3)?.lines?.joinToString(" ")?.contains("friends") == true)
        assertNull(VideoClipperCaptions.atTime(blocks, 9.0))
    }

    @Test
    fun typewriterRevealsText() {
        val block = ClipperCaptionBlock(1.0, 2.0, listOf("Hello world"))
        val mid = VideoClipperCaptions.visibleText(block, 1.05, "typewriter")
        assertTrue(mid.length < "Hello world".length)
        assertEquals("Hello world", VideoClipperCaptions.visibleText(block, 1.4, "typewriter"))
    }
}

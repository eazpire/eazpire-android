package com.eazpire.creator.ui.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchT2iPromptTest {
    private fun handoff(
        prompt: String = "gray cat with blue hat",
        topic: String = "pets",
        subtopic: String = "",
        tags: List<String> = listOf("cat"),
        designType: String? = "design_text",
        language: String? = "en",
    ) = ResearchGeneratorHandoff(
        imageUrl = "https://example.com/a.jpg",
        prompt = prompt,
        topic = topic,
        subtopic = subtopic,
        tags = tags,
        designType = designType,
        language = language,
        asin = "B0TEST",
        marketplace = "amazon.de",
    )

    @Test
    fun analysisTextDoesNotInventSales() {
        val text = researchT2iPrompt(handoff())
        assertTrue(text.contains("gray cat with blue hat"))
        assertTrue(text.contains("Topic: pets"))
        assertTrue(text.contains("Tags: cat"))
        assertTrue(!text.contains("sold", ignoreCase = true))
        assertTrue(!text.contains("BSR", ignoreCase = true))
    }

    @Test
    fun t2iEntriesKeepSlotLabelsABC() {
        val a = t2iDesignEntryOrNull(0, true, handoff(prompt = "take the text", topic = "", tags = emptyList(), designType = null, language = null))
        val b = t2iDesignEntryOrNull(1, true, handoff(prompt = "take the style", topic = "", tags = emptyList(), designType = null, language = null))
        val cSkip = t2iDesignEntryOrNull(2, false, handoff(prompt = "i2i image", topic = "", tags = emptyList(), designType = null, language = null))
        val c = t2iDesignEntryOrNull(2, true, handoff(prompt = "take elements", topic = "", tags = emptyList(), designType = null, language = null))
        assertEquals("A", a!!.label)
        assertEquals("take the text", a.text)
        assertEquals("B", b!!.label)
        assertNull(cSkip)
        assertEquals("C", c!!.label)
        assertEquals("take elements", c.text)
    }

    @Test
    fun emptyAnalysisIsOmittedFromHiddenPayload() {
        assertNull(
            t2iDesignEntryOrNull(
                0,
                true,
                handoff(prompt = "", topic = "", tags = emptyList(), designType = null, language = null),
            )
        )
    }
}

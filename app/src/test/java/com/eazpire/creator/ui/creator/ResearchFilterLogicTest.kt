package com.eazpire.creator.ui.creator

import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchFilterLogicTest {
    @Test
    fun topicAndSearchTextCombineWithAnd() {
        val rows = listOf(
            ResearchProductLike(asin = "1", title = "Christmas cat tee", topic = "cat", nicheKey = "cat"),
            ResearchProductLike(asin = "2", title = "Summer cat mug", topic = "cat", nicheKey = "cat"),
            ResearchProductLike(asin = "3", title = "Christmas dog tee", topic = "dog", nicheKey = "dog"),
        )
        val out = ResearchFilterLogic.andFilter(
            rows,
            ResearchFilterSnapshot(query = "Christmas", niches = setOf("cat")),
        )
        assertEquals(listOf("Christmas cat tee"), out.map { it.title })
    }

    @Test
    fun analyzeResultsKeepTopicFilter() {
        val rows = listOf(
            ResearchProductLike(asin = "a", title = "Cat moon", topic = "cat", searchIngestedAt = 2),
            ResearchProductLike(asin = "b", title = "Dog sun", topic = "dog", searchIngestedAt = 3),
        )
        val out = ResearchFilterLogic.andFilter(
            rows,
            ResearchFilterSnapshot(niches = setOf("cat")),
        )
        assertEquals(listOf("Cat moon"), out.map { it.title })
    }

    @Test
    fun newestAnalyzeHitsSortFirst() {
        val rows = listOf(
            ResearchProductLike(asin = "old", title = "Older", topic = "cat", searchIngestedAt = 1),
            ResearchProductLike(asin = "new", title = "Newer", topic = "cat", searchIngestedAt = 9),
        )
        val out = ResearchFilterLogic.andFilter(rows, ResearchFilterSnapshot())
        assertEquals(listOf("Newer", "Older"), out.map { it.title })
    }
}

package com.eazpire.creator.util

private val SAMPLE_HANDLE = Regex("^sample-(\\d+)$", RegexOption.IGNORE_CASE)

fun parseSamplePublishedId(handleOrId: String?): Long? {
    val m = SAMPLE_HANDLE.matchEntire(handleOrId?.trim().orEmpty()) ?: return null
    return m.groupValues[1].toLongOrNull()?.takeIf { it > 0L }
}

fun isSampleProductId(handleOrId: String?): Boolean = parseSamplePublishedId(handleOrId) != null

fun sampleHandle(publishedId: Long): String = "sample-$publishedId"

package com.eazpire.creator.ui.creator

data class PublishedListingRow(
    val productKey: String,
    val publishIntent: String = "",
    val completionStatus: String = "",
)

enum class ListingCardStatus { Active, Queue, Hidden }

enum class ListingCardKind { Sample, Product }

fun isSampleListing(row: PublishedListingRow?): Boolean {
    if (row == null) return false
    return row.publishIntent == "sample_publish" || row.completionStatus == "sample_publish"
}

fun isOnlineListing(row: PublishedListingRow?, inProgress: Boolean = false): Boolean {
    if (inProgress) return false
    if (row == null) return false
    if (row.completionStatus == "failed") return false
    if (
        row.completionStatus == "complete" ||
        row.completionStatus == "sample_publish" ||
        row.completionStatus == "draft_publish"
    ) {
        return true
    }
    return row.publishIntent == "sample_publish"
}

fun listingKind(row: PublishedListingRow?, sampleMode: Boolean = false): ListingCardKind {
    return if (isSampleListing(row) || sampleMode) ListingCardKind.Sample else ListingCardKind.Product
}

fun listingCardStatus(checked: Boolean, row: PublishedListingRow?, inProgress: Boolean = false): ListingCardStatus {
    return when {
        isOnlineListing(row, inProgress) -> ListingCardStatus.Active
        checked || row != null || inProgress -> ListingCardStatus.Queue
        else -> ListingCardStatus.Hidden
    }
}

package com.eazpire.creator.ui.askteam

data class AskTeamCampaignModes(
    val askProduct: Boolean,
    val collectTeamData: Boolean,
    val startVoting: Boolean,
)

/**
 * My Products picker → campaign modes (parity with askTeamLib.resolveCampaignModes).
 * 1 product: collect only. 2+: at least one of collect / vote stays on.
 */
fun resolveCampaignModes(
    productCount: Int,
    collectTeamData: Boolean = true,
    startVoting: Boolean = true,
): AskTeamCampaignModes {
    if (productCount <= 1) {
        return AskTeamCampaignModes(
            askProduct = false,
            collectTeamData = true,
            startVoting = false,
        )
    }
    var collect = collectTeamData
    var vote = startVoting
    if (!collect && !vote) collect = true
    return AskTeamCampaignModes(
        askProduct = vote,
        collectTeamData = collect,
        startVoting = vote,
    )
}

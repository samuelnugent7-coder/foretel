package com.marketmc.model

import java.util.UUID

data class MarketingCampaign(
    val id: UUID,
    val companyId: UUID,
    val sponsorId: UUID,
    val spend: Double,
    val boost: Double,
    val createdAt: Long,
    val expiresAt: Long
)

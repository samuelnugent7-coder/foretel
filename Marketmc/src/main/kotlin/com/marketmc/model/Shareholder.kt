package com.marketmc.model

import java.util.UUID

class Shareholder(
    val playerId: UUID,
    val companyId: UUID,
    var sharesOwned: Long,
    var avgBuyPrice: Double,
    var lastTradeAt: Long
)

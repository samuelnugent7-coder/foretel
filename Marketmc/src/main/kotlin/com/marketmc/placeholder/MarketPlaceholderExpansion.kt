package com.marketmc.placeholder

import com.marketmc.MarketMCPlugin
import com.marketmc.data.DataRepository
import com.marketmc.economy.VaultHook
import com.marketmc.model.Company
import com.marketmc.model.Shareholder
import com.marketmc.service.CompanyService
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import java.util.Locale
import java.util.UUID

class MarketPlaceholderExpansion(
    private val plugin: MarketMCPlugin,
    private val companyService: CompanyService,
    private val dataRepository: DataRepository,
    private val vaultHook: VaultHook
) : PlaceholderExpansion() {

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun getIdentifier(): String = "marketmc"

    override fun getAuthor(): String = plugin.description.authors.joinToString(", ")

    override fun getVersion(): String = plugin.description.version

    override fun onPlaceholderRequest(player: Player?, identifier: String): String? {
        if (player == null) {
            return ""
        }
        val key = identifier.lowercase(Locale.ROOT)
        return when {
            key == "balance" -> format(vaultHook.getBalance(player))
            key == "portfolio_value" -> format(calculatePortfolioValue(player.uniqueId))
            key.startsWith("company_price_") -> {
                val companyName = unwrapName(identifier, "company_price_")
                companyService.getCompanyByName(companyName)?.let { format(it.sharePrice) } ?: "N/A"
            }
            key.startsWith("company_cap_") -> {
                val companyName = unwrapName(identifier, "company_cap_")
                companyService.getCompanyByName(companyName)?.let { format(it.marketCap) } ?: "N/A"
            }
            key.startsWith("company_shares_") -> {
                val companyName = unwrapName(identifier, "company_shares_")
                val company = companyService.getCompanyByName(companyName)
                if (company == null) {
                    "0"
                } else {
                    dataRepository.fetchShareholder(player.uniqueId, company.id)
                        ?.sharesOwned?.toString() ?: "0"
                }
            }
            else -> null
        }
    }

    private fun unwrapName(identifier: String, prefix: String): String {
        val raw = identifier.substring(prefix.length)
        return raw.replace('_', ' ')
    }

    private fun calculatePortfolioValue(playerId: UUID): Double {
        val holdings = dataRepository.fetchShareholders(playerId)
        return holdings.sumOf { holder ->
            val company = companyService.getCompany(holder.companyId)
            if (company != null) {
                company.sharePrice * holder.sharesOwned
            } else {
                0.0
            }
        }
    }

    private fun format(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }
}

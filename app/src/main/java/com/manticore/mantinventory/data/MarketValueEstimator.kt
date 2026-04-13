package com.manticore.mantinventory.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Currency
import java.util.Locale

data class MarketValueEstimate(
    val source: String,
    val currencyCode: String,
    val sampleCount: Int,
    val minPrice: Double,
    val medianPrice: Double,
    val maxPrice: Double
)

class MarketValueEstimator {
    suspend fun estimateMarketValue(query: String): MarketValueEstimate? = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 3) return@withContext null

        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        val url = "https://www.ebay.com/sch/i.html?_nkw=$encoded&LH_Sold=1&LH_Complete=1"
        val document = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Android) Mantinventory/1.0")
            .timeout(8_000)
            .get()

        val prices = document.select(".s-item__price")
            .mapNotNull { parsePrice(it.text()) }
            .filter { it > 0.0 }
            .take(20)

        if (prices.size < 3) return@withContext null

        val sorted = prices.sorted()
        val midpoint = sorted.size / 2
        val median = if (sorted.size % 2 == 0) {
            (sorted[midpoint - 1] + sorted[midpoint]) / 2.0
        } else {
            sorted[midpoint]
        }

        val currencyCode = detectCurrencyCode(document.selectFirst(".s-item__price")?.text().orEmpty())
        MarketValueEstimate(
            source = "eBay sold listings",
            currencyCode = currencyCode,
            sampleCount = sorted.size,
            minPrice = sorted.first(),
            medianPrice = median,
            maxPrice = sorted.last()
        )
    }

    private fun parsePrice(raw: String): Double? {
        val cleaned = raw
            .replace(Regex("""US\s*[$]"""), "$")
            .replace(Regex("""[^\d.,\-$]"""), " ")
            .trim()

        // Pick the first plausible money token from ranges like "$14.99 to $27.50".
        val token = Regex("""-?\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?|-?\d+(?:[.,]\d{1,2})?""")
            .find(cleaned)
            ?.value
            ?.replace(",", "")
            ?.replace(" ", "")
            ?: return null

        return token.toDoubleOrNull()
    }

    private fun detectCurrencyCode(raw: String): String {
        return when {
            raw.contains("$") -> "USD"
            raw.contains("£") -> "GBP"
            raw.contains("€") -> "EUR"
            else -> Currency.getInstance(Locale.US).currencyCode
        }
    }
}

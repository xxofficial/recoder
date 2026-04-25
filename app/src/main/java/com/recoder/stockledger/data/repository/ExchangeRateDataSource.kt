package com.recoder.stockledger.data.repository

import android.content.Context
import com.recoder.stockledger.data.DisplayCurrency
import com.recoder.stockledger.data.ExchangeRateOrigin
import com.recoder.stockledger.data.ExchangeRateRefreshResult
import com.recoder.stockledger.data.ExchangeRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class FrankfurterExchangeRateDataSource(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun currentRates(): ExchangeRates = loadCachedRates() ?: ExchangeRates()

    suspend fun refreshRates(): ExchangeRateRefreshResult = withContext(Dispatchers.IO) {
        runCatching {
            val rates = ExchangeRates(
                usdToCny = fetchPairRate(base = "USD", quote = "CNY"),
                hkdToCny = fetchPairRate(base = "HKD", quote = "CNY"),
                updatedAtMillis = System.currentTimeMillis(),
            )
            saveRates(rates)
            ExchangeRateRefreshResult(
                rates = rates,
                origin = ExchangeRateOrigin.NETWORK,
            )
        }.getOrElse {
            val cached = loadCachedRates()
            if (cached != null) {
                ExchangeRateRefreshResult(
                    rates = cached,
                    origin = ExchangeRateOrigin.CACHE,
                )
            } else {
                ExchangeRateRefreshResult(
                    rates = ExchangeRates(),
                    origin = ExchangeRateOrigin.DEFAULT,
                )
            }
        }
    }

    private fun fetchPairRate(base: String, quote: String): Double {
        val url = URL("$API_BASE/v2/rate/$base/$quote")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_500
            readTimeout = 3_500
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                throw IOException("HTTP $statusCode for $url: ${body.take(120)}")
            }

            JSONObject(body).optDouble("rate").takeIf { it > 0.0 }
                ?: throw IOException("Missing exchange rate for $base/$quote")
        } finally {
            connection.disconnect()
        }
    }

    private fun loadCachedRates(): ExchangeRates? {
        val usdToCny = preferences.getFloat(KEY_USD_TO_CNY, 0f).toDouble()
        val hkdToCny = preferences.getFloat(KEY_HKD_TO_CNY, 0f).toDouble()
        if (usdToCny <= 0.0 || hkdToCny <= 0.0) return null

        val updatedAtMillis = preferences.getLong(KEY_UPDATED_AT, 0L)
            .takeIf { it > 0L }

        return ExchangeRates(
            usdToCny = usdToCny,
            hkdToCny = hkdToCny,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun saveRates(rates: ExchangeRates) {
        preferences.edit()
            .putFloat(KEY_USD_TO_CNY, rates.usdToCny.toFloat())
            .putFloat(KEY_HKD_TO_CNY, rates.hkdToCny.toFloat())
            .putLong(KEY_UPDATED_AT, rates.updatedAtMillis ?: 0L)
            .apply()
    }

    private companion object {
        const val API_BASE = "https://api.frankfurter.dev"
        const val PREFERENCES_NAME = "exchange_rates"
        const val KEY_USD_TO_CNY = "usd_to_cny"
        const val KEY_HKD_TO_CNY = "hkd_to_cny"
        const val KEY_UPDATED_AT = "updated_at"
    }
}

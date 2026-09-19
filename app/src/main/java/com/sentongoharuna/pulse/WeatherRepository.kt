package com.sentongoharuna.pulse

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class WeatherSnapshot(
    val temperatureC: Double? = null,
    val condition: String = "Weather unavailable",
    val humidityPct: Int? = null,
    val windKmh: Double? = null,
    val windDirectionDeg: Double? = null,
    val updatedAtMs: Long = 0
)

class WeatherRepository {
    @Volatile var latest = WeatherSnapshot()
        private set
    private val executor = Executors.newSingleThreadExecutor()

    fun refresh(lat: Double, lon: Double, onDone: (() -> Unit)? = null) {
        if (System.currentTimeMillis() - latest.updatedAtMs < 10 * 60_000L) {
            onDone?.invoke()
            return
        }
        executor.execute {
            try {
                val u = URL(
                    "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m"
                )
                val c = (u.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val text = c.inputStream.bufferedReader().use { it.readText() }
                val cur = JSONObject(text).getJSONObject("current")
                latest = WeatherSnapshot(
                    temperatureC = cur.optDouble("temperature_2m"),
                    condition = weatherCode(cur.optInt("weather_code")),
                    humidityPct = cur.optInt("relative_humidity_2m"),
                    windKmh = cur.optDouble("wind_speed_10m"),
                    windDirectionDeg = cur.optDouble("wind_direction_10m"),
                    updatedAtMs = System.currentTimeMillis()
                )
            } catch (_: Exception) {
                // Keep cached values if the network drops.
            }
            onDone?.invoke()
        }
    }

    private fun weatherCode(code: Int): String = when (code) {
        0 -> "Clear"
        1,2,3 -> "Cloudy"
        45,48 -> "Fog"
        51,53,55,56,57 -> "Drizzle"
        61,63,65,66,67 -> "Rain"
        71,73,75,77 -> "Snow"
        80,81,82 -> "Showers"
        95,96,99 -> "Thunderstorm"
        else -> "Weather"
    }
}

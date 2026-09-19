package com.sentongoharuna.pulse

data class TelemetrySample(
    val elapsedMs: Long,
    val timestampIso: String,
    val timezone: String,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeM: Double?,
    val accuracyM: Float?,
    val headingDeg: Float?,
    val speedKmh: Float?,
    val placeName: String,
    val temperatureC: Double?,
    val condition: String,
    val humidityPct: Int?,
    val windKmh: Double?,
    val windDirectionDeg: Double?,
    val networkType: String,
    val estimatedUploadKbps: Int?,
    val droppedFrames: Long,
    val batteryPct: Int?,
    val freeStorageGb: Long?
)

package com.sentongoharuna.pulse

data class ReporterOverlayState(
    var reporter: String = "PULSE REPORTER",
    var headline: String = "LIVE FIELD REPORT",
    var station: String = "PULSE NEWSROOM",
    var liveMode: String = "BREAKING",
    var opacity: Float = 0.78f,
    var lowerStripPercent: Float = 0.76f,
    var showCoordinates: Boolean = true,
    var showAltitude: Boolean = true,
    var showAccuracy: Boolean = true,
    var showHeading: Boolean = true,
    var showSpeed: Boolean = true,
    var showWeather: Boolean = true,
    var showDateTime: Boolean = true,
    var showTimecode: Boolean = true,
    var showNetwork: Boolean = true,
    var showBattery: Boolean = true,
    var showStorage: Boolean = true,
    var showAudio: Boolean = true
)

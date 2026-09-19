package com.sentongoharuna.pulse

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class TelemetryRecorder(private val context: Context) {
    private val samples = mutableListOf<TelemetrySample>()

    @Synchronized fun add(sample: TelemetrySample) {
        samples.add(sample)
    }

    @Synchronized fun snapshot(): List<TelemetrySample> = samples.toList()

    /**
     * Writes a synchronized JSONL sidecar into Documents/PULSE.
     * V172 also burns selected fields into the video itself.
     * A future remux stage can copy these 1-second samples into a timed MP4
     * metadata track using Media3 Mp4Muxer without losing the sidecar.
     */
    @Synchronized fun exportSidecar(baseName: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.telemetry.jsonl")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-ndjson")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/PULSE")
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri("external"),
            values
        ) ?: return

        context.contentResolver.openOutputStream(uri)?.use { os ->
            BufferedWriter(OutputStreamWriter(os)).use { w ->
                for (s in samples) {
                    val o = JSONObject()
                    o.put("elapsedMs", s.elapsedMs)
                    o.put("timestampIso", s.timestampIso)
                    o.put("timezone", s.timezone)
                    o.put("latitude", s.latitude)
                    o.put("longitude", s.longitude)
                    o.put("altitudeM", s.altitudeM)
                    o.put("accuracyM", s.accuracyM)
                    o.put("headingDeg", s.headingDeg)
                    o.put("speedKmh", s.speedKmh)
                    o.put("placeName", s.placeName)
                    o.put("temperatureC", s.temperatureC)
                    o.put("condition", s.condition)
                    o.put("humidityPct", s.humidityPct)
                    o.put("windKmh", s.windKmh)
                    o.put("windDirectionDeg", s.windDirectionDeg)
                    o.put("networkType", s.networkType)
                    o.put("estimatedUploadKbps", s.estimatedUploadKbps)
                    o.put("droppedFrames", s.droppedFrames)
                    o.put("batteryPct", s.batteryPct)
                    o.put("freeStorageGb", s.freeStorageGb)
                    w.write(o.toString())
                    w.newLine()
                }
            }
        }
    }
}

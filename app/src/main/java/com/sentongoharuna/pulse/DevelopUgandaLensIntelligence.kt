package com.sentongoharuna.pulse

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.Locale

@OptIn(ExperimentalCamera2Interop::class)
object DevelopUgandaLensIntelligence {

    data class LensDevice(
        val cameraId: String,
        val facing: String,
        val focalLengthsMm: List<Float>,
        val sensorOrientation: Int?,
        val hardwareLevel: Int?
    ) {
        fun label(): String {
            val focal =
                if (
                    focalLengthsMm.isEmpty()
                ) {
                    "focal --"
                } else {
                    "f " +
                        focalLengthsMm.joinToString(
                            "/"
                        ) {
                            String.format(
                                Locale.US,
                                "%.1fmm",
                                it
                            )
                        }
                }

            return "$facing • ID $cameraId • $focal"
        }

        fun shortLabel(): String =
            "$facing ID $cameraId"
    }

    fun devices(
        provider: ProcessCameraProvider
    ): List<LensDevice> {
        return provider.availableCameraInfos
            .mapNotNull {
                    info ->
                descriptor(
                    info
                )
            }
            .sortedWith(
                compareBy<LensDevice> {
                    when (
                        it.facing
                    ) {
                        "BACK" ->
                            0

                        "FRONT" ->
                            1

                        else ->
                            2
                    }
                }
                    .thenBy {
                        it.focalLengthsMm
                            .firstOrNull()
                            ?: Float.MAX_VALUE
                    }
                    .thenBy {
                        it.cameraId
                    }
            )
    }

    fun selectorFor(
        provider: ProcessCameraProvider,
        cameraId: String?,
        useFront: Boolean
    ): CameraSelector {
        if (
            !cameraId.isNullOrBlank()
        ) {
            provider.availableCameraInfos
                .firstOrNull {
                    cameraIdOf(
                        it
                    ) ==
                        cameraId
                }
                ?.let {
                    return it.cameraSelector
                }
        }

        return if (
            useFront
        ) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun hasCamera(
        provider: ProcessCameraProvider?,
        cameraId: String?
    ): Boolean {
        if (
            provider ==
                null ||
            cameraId.isNullOrBlank()
        ) {
            return false
        }

        return provider.availableCameraInfos
            .any {
                cameraIdOf(
                    it
                ) ==
                    cameraId
            }
    }

    fun cameraIdOf(
        info: CameraInfo
    ): String? {
        return try {
            Camera2CameraInfo.from(
                info
            )
                .cameraId
        } catch (_: Exception) {
            null
        }
    }

    private fun descriptor(
        info: CameraInfo
    ): LensDevice? {
        return try {
            val camera2 =
                Camera2CameraInfo.from(
                    info
                )

            val facingValue =
                camera2.getCameraCharacteristic(
                    CameraCharacteristics.LENS_FACING
                )

            val facing =
                when (
                    facingValue
                ) {
                    CameraCharacteristics.LENS_FACING_BACK ->
                        "BACK"

                    CameraCharacteristics.LENS_FACING_FRONT ->
                        "FRONT"

                    CameraCharacteristics.LENS_FACING_EXTERNAL ->
                        "EXTERNAL"

                    else ->
                        "UNKNOWN"
                }

            LensDevice(
                cameraId =
                    camera2.cameraId,
                facing =
                    facing,
                focalLengthsMm =
                    camera2
                        .getCameraCharacteristic(
                            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                        )
                        ?.toList()
                        ?: emptyList(),
                sensorOrientation =
                    camera2.getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_ORIENTATION
                    ),
                hardwareLevel =
                    camera2.getCameraCharacteristic(
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
                    )
            )
        } catch (_: Exception) {
            null
        }
    }
}

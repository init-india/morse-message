package com.morsemessage.output

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Camera flash as a light source. No CAMERA permission is needed for torch mode. */
class Torch(context: Context) {
    private val cameraManager: CameraManager? = context.getSystemService(CameraManager::class.java)

    private val cameraId: String? = runCatching {
        val cm = cameraManager ?: return@runCatching null
        val withFlash = cm.cameraIdList.filter {
            cm.getCameraCharacteristics(it)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull {
            cm.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }.getOrNull()

    val available: Boolean get() = cameraId != null

    fun set(on: Boolean) {
        val id = cameraId ?: return
        try {
            cameraManager?.setTorchMode(id, on)
        } catch (_: CameraAccessException) {
            // Camera in use by another app: skip light output silently.
        } catch (_: IllegalArgumentException) {
        }
    }
}

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    val available: Boolean get() = vibrator?.hasVibrator() == true

    /** Plays a whole message as one waveform, so timing is handled by the vibrator itself. */
    fun playPattern(onOffMs: LongArray) {
        if (onOffMs.isEmpty()) return
        vibrator?.vibrate(VibrationEffect.createWaveform(onOffMs, -1))
    }

    fun pulse(ms: Long) {
        vibrator?.vibrate(VibrationEffect.createOneShot(ms.coerceAtLeast(1), VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun tick() {
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    fun cancel() {
        vibrator?.cancel()
    }
}

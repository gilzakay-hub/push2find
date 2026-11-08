package com.push2find.app.alarm

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Blinks the camera flash while the phone rings, so it can be found in the dark
 * or under a pillow. setTorchMode needs no camera permission.
 * Main thread only.
 */
object Torch {
    private val handler = Handler(Looper.getMainLooper())
    private var camera: CameraManager? = null
    private var cameraId: String? = null
    private var lit = false
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            lit = !lit
            set(lit)
            handler.postDelayed(this, if (lit) ON_MS else OFF_MS)
        }
    }

    /** True if this phone has a flash we can drive. */
    fun isAvailable(context: Context): Boolean = findFlash(context) != null

    fun start(context: Context) {
        stop()
        val cm = context.getSystemService(CameraManager::class.java) ?: return
        val id = findFlash(context) ?: return
        camera = cm
        cameraId = id
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        if (lit) set(false)
        lit = false
    }

    private fun set(on: Boolean) {
        val cm = camera ?: return
        val id = cameraId ?: return
        try {
            cm.setTorchMode(id, on)
        } catch (e: Exception) {
            // Camera in use by another app, or torch unavailable – the alarm still rings.
            Log.w("P2F-Torch", "setTorchMode failed", e)
            running = false
            handler.removeCallbacks(tick)
        }
    }

    private fun findFlash(context: Context): String? {
        val cm = context.getSystemService(CameraManager::class.java) ?: return null
        return try {
            val withFlash = cm.cameraIdList.filter {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            withFlash.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: withFlash.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private const val ON_MS = 250L
    private const val OFF_MS = 350L
}

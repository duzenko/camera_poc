package com.example.camera_poc

import android.app.Activity
import android.Manifest;
import android.Manifest.permission;
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

typealias RequestCallback = (errorCode: String?, errorDescription: String?) -> Unit

typealias PermissionRegistry = (handler: RequestPermissionsResultListener) -> Unit

class CameraPermission {
    private var ongoing = false

    companion object CameraRequest {
        val id: Int
            get() = 9796
    }

    fun requestPermissions(
            activity: Activity,
            permissionRegistry: PermissionRegistry,
            enableAudio: Boolean,
            callback: RequestCallback
    ) {
        if (ongoing) {
            callback("cameraPermission", "Camera permission request ongoing")
        }

        if (!hasCameraPermission(activity) || (enableAudio && !hasAudioPermission(activity))) {
            permissionRegistry(
                    CameraRequestPermissionsListener { errorCode, errorDescription ->
                        run {
                            ongoing = false
                            callback(errorCode, errorDescription)
                        }
                    }
            )
            ongoing = true

            var permissions: Array<String> = if (enableAudio) {
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            } else {
                arrayOf(Manifest.permission.CAMERA)
            }

            ActivityCompat.requestPermissions(
                    activity,
                    permissions,
                    CameraRequest.id
            )
        } else {
            callback(null, null)
        }
    }

    private fun hasCameraPermission(activity: Activity): Boolean =
            ContextCompat.checkSelfPermission(activity, permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hasAudioPermission(activity: Activity): Boolean =
            ContextCompat.checkSelfPermission(activity, permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @VisibleForTesting
    class CameraRequestPermissionsListener(private val callback: RequestCallback) : RequestPermissionsResultListener {
        private var alreadyCalled = false

        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?): Boolean {
            if (alreadyCalled || requestCode != CameraRequest.id) {
                return false
            }

            alreadyCalled = true

            if (grantResults?.elementAt(0) == PackageManager.PERMISSION_GRANTED) {
                callback("cameraPermission", "MediaRecorderCamera permission not granted")
            } else if (grantResults?.size!! > 1 && grantResults?.elementAt(1) != PackageManager.PERMISSION_GRANTED) {
                callback("cameraPermission", "MediaRecorderAudio permission not granted")
            } else {
                callback(null, null)
            }
            return true;
        }

    }
}
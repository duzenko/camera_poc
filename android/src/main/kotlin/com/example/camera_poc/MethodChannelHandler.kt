package com.example.camera_poc

import ai.deepar.ar.AREventListener
import ai.deepar.ar.DeepAR
import android.app.Activity
import android.graphics.Bitmap
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.TextureRegistry

class MethodChannelHandler(
        private val activity: Activity,
        private val messenger: BinaryMessenger?,
        private val cameraPermission: CameraPermission,
        private val permissionRegistry: PermissionRegistry,
        private val textureRegistry: TextureRegistry?
) : MethodChannel.MethodCallHandler {
    private val channelName = "com.example.camera_poc/camera"
    private var methodChannel: MethodChannel = MethodChannel(messenger, channelName)
    private lateinit var deepAR: DeepAR
    private lateinit var camera: com.example.camera_poc.Camera
    var isAviatorsOn: Boolean = false;
    var isEffectOn: Boolean = false;

    init {
        methodChannel.setMethodCallHandler(this);
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d("Method", call.method)
        when (call.method) {
            "initialize" -> {
                var reply = HashMap<String, String?>();

                cameraPermission.requestPermissions(activity, permissionRegistry, false, { errorCode, errorDescription ->
                    run {
                        if (errorCode == null) {
                            try {


                                // startPreview();
                                // setImageStreamImageAvailableListener();
                                deepAR = DeepAR(activity)
                                deepAR.setLicenseKey("be843a53f7b440f022beafc45cd389cee0555f3caed8d45dad5d3f88764b9aa97e302286d52f9976")
                                deepAR.initialize(activity, object : AREventListener {
                                    override fun screenshotTaken(bitmap: Bitmap) {}
                                    override fun videoRecordingStarted() {}
                                    override fun videoRecordingFinished() {}
                                    override fun videoRecordingFailed() {}
                                    override fun videoRecordingPrepared() {}
                                    override fun shutdownFinished() {}
                                    override fun initialized() {}
                                    override fun faceVisibilityChanged(b: Boolean) {}
                                    override fun imageVisibilityChanged(s: String, b: Boolean) {}
                                    override fun error(s: String) {}
                                    override fun effectSwitched(s: String) {}
                                })


                                var flutterSurfaceTexture: TextureRegistry.SurfaceTextureEntry = textureRegistry?.createSurfaceTexture()!!

                                var arSurfaceTexture: TextureRegistry.SurfaceTextureEntry = textureRegistry?.createSurfaceTexture()

                                camera = Camera(activity, flutterSurfaceTexture, "0", "high", false, deepAR, arSurfaceTexture)

                                camera.open(result)
                            } catch (e: Exception) {
                                print(e)
                            }


                        } else {
                            result.error(errorCode, errorDescription, null)
                        }
                    }
                })
            }
            "putAviators" -> {
                var path: String? = if (isAviatorsOn) {
                    null
                } else {
                    "file:///android_asset/aviators"
                }
                deepAR.switchEffect("mask", path)
                isAviatorsOn = !isAviatorsOn
                result.success(null);
            }
            "toggleEffect" -> {
                var path: String? = if (isEffectOn) {
                    null
                } else {
                    "file:///android_asset/sepia"
                }
                deepAR.switchEffect("filter", path)
                isEffectOn = !isEffectOn
                result.success(null);
            }
            else -> result.notImplemented()
        }
    }

    fun stopListening() {
        methodChannel.setMethodCallHandler(null);
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("ConstantConditions")
    fun handleExceptions(exception: Exception, result: MethodChannel.Result) {
        if (exception is CameraAccessException) {
            result.error("CameraAccess", exception.message, null)
        }

        throw exception
    }
}
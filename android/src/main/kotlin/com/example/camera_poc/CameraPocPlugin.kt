package com.example.camera_poc

import android.app.Activity
import android.os.Build
import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.view.TextureRegistry
import org.jetbrains.annotations.NotNull

/** CameraPocPlugin */
public class CameraPocPlugin : FlutterPlugin, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private var channelHandler: MethodChannelHandler? = null
    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginBinding = binding
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            var cameraPocPlugin = CameraPocPlugin();
            cameraPocPlugin.maybeStartListening(
                    registrar.activity(),
                    registrar.messenger(),
                    { handler -> registrar.addRequestPermissionsResultListener(handler) },
                    registrar.view()
            )
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginBinding = null;
    }

    override fun onDetachedFromActivity() {
        if (channelHandler == null) {
            return;
        }

        channelHandler?.stopListening()
        channelHandler = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding);
    }

    override fun onAttachedToActivity(@NotNull binding: ActivityPluginBinding) {

        maybeStartListening(
                binding.activity,
                flutterPluginBinding?.binaryMessenger,
                { handler -> binding.addRequestPermissionsResultListener(handler) },
                flutterPluginBinding?.textureRegistry
        )
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    private fun maybeStartListening(activity: Activity, messenger: BinaryMessenger?, permissionRegistry: PermissionRegistry, textureRegistry: TextureRegistry?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        channelHandler = MethodChannelHandler(activity, messenger, CameraPermission(), permissionRegistry, textureRegistry)
    }
}

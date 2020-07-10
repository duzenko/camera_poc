import 'package:camera_poc/camera-controller.dart';
import 'package:flutter/widgets.dart';

class CameraPreview extends StatelessWidget {
  const CameraPreview(this.controller);

  final CameraController controller;

  @override
  Widget build(BuildContext context) {
    return controller != null && controller.value.isInitialized
        ? Texture(textureId: controller.textureId)
        : Container();
  }
}

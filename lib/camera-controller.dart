import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'dart:async';

class CameraValue {
  const CameraValue({
    this.isInitialized,
  });

  final bool isInitialized;

  const CameraValue.uninitialized()
      : this(
          isInitialized: false,
        );

  CameraValue copyWith({
    bool isInitialized,
  }) {
    return CameraValue(
      isInitialized: isInitialized ?? this.isInitialized,
    );
  }
}

class CameraController extends ValueNotifier<CameraValue> {
  CameraController({
    this.enableAudio = true,
  }) : super(
          const CameraValue.uninitialized(),
        );

  static const CHANNEL_NAME = 'com.example.camera_poc/camera';
  final _channel = MethodChannel(CameraController.CHANNEL_NAME);

  final bool enableAudio;

  int _textureId;
  get textureId => _textureId;

  bool _isDisposed = false;

  Completer<void> _completer;

  Future<void> initialize() async {
    if (_isDisposed) {
      return Future<void>.value();
    }

    try {
      _completer = Completer<void>();

      final Map<String, dynamic> reply =
          await _channel.invokeMapMethod('initialize');

      print(reply);

      _textureId = reply['textureId'];

      value = value.copyWith(
        isInitialized: true,
      );
    } on PlatformException catch (e) {
      throw e;
    }

    _completer.complete();

    return _completer.future;
  }

  Future<void> putAviators() async {
    if (_isDisposed) {
      return Future<void>.value();
    }

    try {
      _completer = Completer<void>();

      await _channel.invokeMethod('putAviators');

      _completer.complete();

      return _completer.future;
    } on PlatformException catch (e) {
      throw e;
    }
  }

  Future<void> toggleEffect() async {
    if (_isDisposed) {
      return Future<void>.value();
    }

    try {
      _completer = Completer<void>();

      await _channel.invokeMethod('toggleEffect');

      _completer.complete();

      return _completer.future;
    } on PlatformException catch (e) {
      throw e;
    }
  }
}

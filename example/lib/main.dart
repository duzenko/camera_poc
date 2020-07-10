import 'package:flare_flutter/flare_actor.dart';
import 'package:flutter/material.dart';
import 'dart:async';
import 'package:camera_poc/camera_poc.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  CameraController controller;
  bool isLikeVisible = false;
  bool isAviatorsOn = false;
  bool isEffectOn = false;

  @override
  void initState() {
    super.initState();

    initPlatformState();
  }

  Future<void> initPlatformState() async {
    controller = CameraController(
      enableAudio: true,
    );

    await controller.initialize();

    setState(() {});
  }

  void like() {
    setState(() {
      isLikeVisible = true;
    });
  }

  void likeFinished() {
    setState(() {
      isLikeVisible = false;
    });
  }

  Future<void> toggleGlasses() async {
    await controller.putAviators();

    setState(() {
      isAviatorsOn = !isAviatorsOn;
    });
  }

  Future<void> toggleEffect() async {
    await controller.toggleEffect();

    setState(() {
      isEffectOn = !isEffectOn;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData(accentColor: Color.fromRGBO(255, 88, 56, 1)),
      home: Scaffold(
        body: Transform.translate(
          offset: Offset(0, -42),
          child: Stack(
            children: <Widget>[
              Transform.rotate(
                angle: 0, //90 * pi / 180,
                child: AspectRatio(
                  aspectRatio: 3 / 4,
                  child: CameraPreview(controller),
                ),
              ),
              isLikeVisible
                  ? Transform.translate(
                      offset: Offset(0, -100),
                      child: Center(
                        child: SizedBox(
                          child: FlareActor(
                            'assets/Like.flr',
                            animation: 'Like heart',
                            callback: (animantion) {
                              likeFinished();
                            },
                          ),
                          height: 180,
                          width: 180,
                        ),
                      ),
                    )
                  : Container(),
              Positioned(
                  bottom: 50,
                  left: 50,
                  right: 50,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: <Widget>[
                      FloatingActionButton(
                        onPressed: () {
                          toggleGlasses();
                        },
                        child: FaIcon(isAviatorsOn
                            ? FontAwesomeIcons.smile
                            : FontAwesomeIcons.glasses),
                      ),
                      FloatingActionButton(
                        onPressed: () {
                          toggleEffect();
                        },
                        child: FaIcon(isEffectOn
                            ? FontAwesomeIcons.frown
                            : FontAwesomeIcons.magic),
                      ),
                      FloatingActionButton(
                        onPressed: () {
                          like();
                        },
                        child: FaIcon(FontAwesomeIcons.heart),
                      ),
                    ],
                  ))
            ],
          ),
        ),
      ),
    );
  }
}

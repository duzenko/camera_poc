package com.example.camera_poc;

import static android.graphics.PixelFormat.RGBA_8888;
import static android.view.OrientationEventListener.ORIENTATION_UNKNOWN;
import static com.example.camera_poc.CameraUtils.computeBestPreviewSize;
import static com.example.camera_poc.CameraUtils.convertYUV420ToNV21;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import ai.deepar.ar.DeepAR;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.view.TextureRegistry.SurfaceTextureEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Camera {
    private final SurfaceTextureEntry flutterTexture;
    private final CameraManager cameraManager;
    private final OrientationEventListener orientationEventListener;
    private final boolean isFrontFacing;
    private final int sensorOrientation;
    private final String cameraName;
    private final Size captureSize;
    private final Size previewSize;
    private final boolean enableAudio;
    private final DeepAR deepAR;
    private final SurfaceTextureEntry arFlutterTexture;

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private ImageReader pictureImageReader;
    private ImageReader imageStreamReader;
    private ImageReader eImageStreamReader;
    private ImageWriter imageWriter;
    private CaptureRequest.Builder captureRequestBuilder;
    private MediaRecorder mediaRecorder;
    private boolean recordingVideo;
    private CamcorderProfile recordingProfile;
    private int currentOrientation = ORIENTATION_UNKNOWN;
    private ByteBuffer[] buffers;
    private short numberOfBuffers = 2;
    private int currentBuffer = 0;
    private ArrayList<String> masks;
    private ArrayList<String> effects;

    // Mirrors camera.dart
    public enum ResolutionPreset {
        low,
        medium,
        high,
        veryHigh,
        ultraHigh,
        max,
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Camera(
            final Activity activity,
            final SurfaceTextureEntry flutterTexture,
            final String cameraName,
            final String resolutionPreset,
            final boolean enableAudio,
            final DeepAR deepAR,
            final SurfaceTextureEntry arFlutterTexture
    )
            throws CameraAccessException {
        if (activity == null) {
            throw new IllegalStateException("No activity available!");
        }

        this.deepAR = deepAR;
        this.cameraName = cameraName;
        this.enableAudio = enableAudio;
        this.flutterTexture = flutterTexture;
        this.arFlutterTexture = arFlutterTexture;
        this.cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        orientationEventListener =
                new OrientationEventListener(activity.getApplicationContext()) {
                    @Override
                    public void onOrientationChanged(int i) {
                        if (i == ORIENTATION_UNKNOWN) {
                            return;
                        }
                        // Convert the raw deg angle to the nearest multiple of 90.
                        currentOrientation = (int) Math.round(i / 90.0) * 90;
                    }
                };
        orientationEventListener.enable();

        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraName);
        StreamConfigurationMap streamConfigurationMap =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        //noinspection ConstantConditions
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        //noinspection ConstantConditions
        isFrontFacing =
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT;
        ResolutionPreset preset = ResolutionPreset.valueOf(resolutionPreset);
        recordingProfile =
                CameraUtils.getBestAvailableCamcorderProfileForResolutionPreset(cameraName, preset);
        captureSize = new Size(recordingProfile.videoFrameWidth, recordingProfile.videoFrameHeight);
        previewSize = computeBestPreviewSize(cameraName, preset);

        buffers = new ByteBuffer[numberOfBuffers];

        for (int i = 0; i < numberOfBuffers; i++) {
            buffers[i] = ByteBuffer.allocateDirect(previewSize.getWidth() * previewSize.getHeight() * 3 / 2);
            buffers[i].order(ByteOrder.nativeOrder());
            buffers[i].position(0);
        }

        masks = new ArrayList<>();
        masks.add("aviators");

        effects = new ArrayList<>();
        effects.add("blizzard");
    }

    private String getFilterPath(String filterName) {
        if (filterName.equals("none")) {
            return null;
        }
        return "file:///android_asset/" + filterName;
    }

    private void prepareMediaRecorder(String outputFilePath) throws IOException {
        if (mediaRecorder != null) {
            mediaRecorder.release();
        }
        mediaRecorder = new MediaRecorder();

        // There's a specific order that mediaRecorder expects. Do not change the order
        // of these function calls.
        if (enableAudio) mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(recordingProfile.fileFormat);
        if (enableAudio) mediaRecorder.setAudioEncoder(recordingProfile.audioCodec);
        mediaRecorder.setVideoEncoder(recordingProfile.videoCodec);
        mediaRecorder.setVideoEncodingBitRate(recordingProfile.videoBitRate);
        if (enableAudio) mediaRecorder.setAudioSamplingRate(recordingProfile.audioSampleRate);
        mediaRecorder.setVideoFrameRate(recordingProfile.videoFrameRate);
        mediaRecorder.setVideoSize(recordingProfile.videoFrameWidth, recordingProfile.videoFrameHeight);
        mediaRecorder.setOutputFile(outputFilePath);
        mediaRecorder.setOrientationHint(getMediaOrientation());

        mediaRecorder.prepare();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("MissingPermission")
    public void open(@NonNull final Result result) throws CameraAccessException {
        pictureImageReader =
                ImageReader.newInstance(
                        captureSize.getWidth(), captureSize.getHeight(), ImageFormat.JPEG, 2);

        // Used to steam image byte data to dart side.
        imageStreamReader =
                ImageReader.newInstance(
                        previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);


        eImageStreamReader =
                ImageReader.newInstance(
                        previewSize.getWidth(), previewSize.getHeight(), RGBA_8888, 2);

        SurfaceTexture texture = arFlutterTexture.surfaceTexture();
        Surface surface = new Surface(texture);

        imageWriter = ImageWriter.newInstance(surface, 2);

        cameraManager.openCamera(
                cameraName,
                new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice device) {
                        try {
                            cameraDevice = device;

                            Surface eSurface = eImageStreamReader.getSurface();

                            deepAR.setRenderSurface(eSurface
                                    , previewSize.getWidth(), previewSize.getWidth());

                            eImageStreamReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                                @Override
                                public void onImageAvailable(ImageReader imageReader) {
                                    Image img = imageReader.acquireLatestImage();

                                    imageWriter.queueInputImage(img);

                                    img.close();
                                }
                            }, null);

                            deepAR.setFaceDetectionSensitivity(3);
                            imageStreamReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                                @Override
                                public void onImageAvailable(ImageReader imageReader) {
                                    Image img = imageReader.acquireLatestImage();
                                    if (img == null) return;

                                    byte[] data = convertYUV420ToNV21(img);

                                    ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
                                    buffer.order(ByteOrder.nativeOrder());

                                    buffer.put(data);
                                    buffer.position(0);

                                    deepAR.receiveFrame(buffer, previewSize.getWidth(), previewSize.getHeight(), 0, true);

                                    img.close();
                                }
                            }, null);

                            Surface mImageSurface = imageStreamReader.getSurface();

                            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            builder.addTarget(mImageSurface);

                            CameraCaptureSession.StateCallback callback =
                                    new CameraCaptureSession.StateCallback() {
                                        @Override
                                        public void onConfigured(@NonNull CameraCaptureSession session) {
                                            try {
                                                if (cameraDevice == null) {
                                                    return;
                                                }
                                                builder.set(
                                                        CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                                session.setRepeatingRequest(builder.build(), null, null);
                                            } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
                                            }
                                        }

                                        @Override
                                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                        }
                                    };

                            List<Surface> surfaceList = new ArrayList<>();
                            surfaceList.add(mImageSurface);
                            cameraDevice.createCaptureSession(surfaceList, callback, null);



                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }

                        Map<String, Object> reply = new HashMap<>();
                        reply.put("textureId", arFlutterTexture.id());
                        reply.put("previewWidth", previewSize.getWidth());
                        reply.put("previewHeight", previewSize.getHeight());
                        result.success(reply);
                    }

                    @Override
                    public void onClosed(@NonNull CameraDevice camera) {
                        super.onClosed(camera);
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                        close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice cameraDevice, int errorCode) {
                        close();
                        String errorDescription;
                        switch (errorCode) {
                            case ERROR_CAMERA_IN_USE:
                                errorDescription = "The camera device is in use already.";
                                break;
                            case ERROR_MAX_CAMERAS_IN_USE:
                                errorDescription = "Max cameras in use";
                                break;
                            case ERROR_CAMERA_DISABLED:
                                errorDescription = "The camera device could not be opened due to a device policy.";
                                break;
                            case ERROR_CAMERA_DEVICE:
                                errorDescription = "The camera device has encountered a fatal error";
                                break;
                            case ERROR_CAMERA_SERVICE:
                                errorDescription = "The camera service has encountered a fatal error.";
                                break;
                            default:
                                errorDescription = "Unknown camera error";
                        }
                    }
                },
                null);
    }

    private void writeToFile(ByteBuffer buffer, File file) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            while (0 < buffer.remaining()) {
                outputStream.getChannel().write(buffer);
            }
        }
    }

    SurfaceTextureEntry getFlutterTexture() {
        return flutterTexture;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void takePicture(String filePath, @NonNull final Result result) {
        final File file = new File(filePath);

        if (file.exists()) {
            result.error(
                    "fileExists", "File at path '" + filePath + "' already exists. Cannot overwrite.", null);
            return;
        }

        pictureImageReader.setOnImageAvailableListener(reader -> {
                    try (Image image = reader.acquireLatestImage()) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        writeToFile(buffer, file);
                        result.success(null);
                    } catch (IOException e) {
                        result.error("IOError", "Failed saving image", null);
                    }
                },
                null);

        try {
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(pictureImageReader.getSurface());
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getMediaOrientation());

            cameraCaptureSession.capture(
                    captureBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureFailed(
                                @NonNull CameraCaptureSession session,
                                @NonNull CaptureRequest request,
                                @NonNull CaptureFailure failure) {
                            String reason;
                            switch (failure.getReason()) {
                                case CaptureFailure.REASON_ERROR:
                                    reason = "An error happened in the framework";
                                    break;
                                case CaptureFailure.REASON_FLUSHED:
                                    reason = "The capture has failed due to an abortCaptures() call";
                                    break;
                                default:
                                    reason = "Unknown reason";
                            }
                            result.error("captureFailure", reason, null);
                        }
                    },
                    null);
        } catch (CameraAccessException e) {
            result.error("cameraAccess", e.getMessage(), null);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCaptureSession(int templateType, Surface... surfaces)
            throws CameraAccessException {
        createCaptureSession(templateType, null, surfaces);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCaptureSession(
            int templateType, final Runnable onSuccessCallback, Surface... surfaces)
            throws CameraAccessException {
        // Close any existing capture session.
        closeCaptureSession();

        // Create a new capture builder.
        captureRequestBuilder = cameraDevice.createCaptureRequest(templateType);

        // Build Flutter surface to render to
        SurfaceTexture surfaceTexture = flutterTexture.surfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface flutterSurface = new Surface(surfaceTexture);
        captureRequestBuilder.addTarget(flutterSurface);

        List<Surface> remainingSurfaces = Arrays.asList(surfaces);
        if (templateType != CameraDevice.TEMPLATE_PREVIEW) {
            // If it is not preview mode, add all surfaces as targets.
            for (Surface surface : remainingSurfaces) {
                captureRequestBuilder.addTarget(surface);
            }
        }

        // Prepare the callback
        CameraCaptureSession.StateCallback callback =
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            captureRequestBuilder.set(
                                    CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                            if (onSuccessCallback != null) {
                                onSuccessCallback.run();
                            }
                        } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    }
                };

        // Collect all surfaces we want to render to.
        List<Surface> surfaceList = new ArrayList<>();
        surfaceList.add(flutterSurface);
        surfaceList.addAll(remainingSurfaces);
        // Start the session
        cameraDevice.createCaptureSession(surfaceList, callback, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startVideoRecording(String filePath, Result result) {
        if (new File(filePath).exists()) {
            result.error("fileExists", "File at path '" + filePath + "' already exists.", null);
            return;
        }
        try {
            prepareMediaRecorder(filePath);
            recordingVideo = true;
            createCaptureSession(
                    CameraDevice.TEMPLATE_RECORD, () -> mediaRecorder.start(), mediaRecorder.getSurface());
            result.success(null);
        } catch (CameraAccessException | IOException e) {
            result.error("videoRecordingFailed", e.getMessage(), null);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stopVideoRecording(@NonNull final Result result) {
        if (!recordingVideo) {
            result.success(null);
            return;
        }

        try {
            recordingVideo = false;
            mediaRecorder.stop();
            mediaRecorder.reset();
            startPreview();
            result.success(null);
        } catch (CameraAccessException | IllegalStateException e) {
            result.error("videoRecordingFailed", e.getMessage(), null);
        }
    }

    public void pauseVideoRecording(@NonNull final Result result) {
        if (!recordingVideo) {
            result.success(null);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder.pause();
            } else {
                result.error("videoRecordingFailed", "pauseVideoRecording requires Android API +24.", null);
                return;
            }
        } catch (IllegalStateException e) {
            result.error("videoRecordingFailed", e.getMessage(), null);
            return;
        }

        result.success(null);
    }

    public void resumeVideoRecording(@NonNull final Result result) {
        if (!recordingVideo) {
            result.success(null);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder.resume();
            } else {
                result.error(
                        "videoRecordingFailed", "resumeVideoRecording requires Android API +24.", null);
                return;
            }
        } catch (IllegalStateException e) {
            result.error("videoRecordingFailed", e.getMessage(), null);
            return;
        }

        result.success(null);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startPreview() throws CameraAccessException {
        createCaptureSession(CameraDevice.TEMPLATE_PREVIEW, pictureImageReader.getSurface());
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startPreviewWithImageStream(EventChannel imageStreamChannel)
            throws CameraAccessException {
        createCaptureSession(CameraDevice.TEMPLATE_RECORD, imageStreamReader.getSurface());

        imageStreamChannel.setStreamHandler(
                new EventChannel.StreamHandler() {
                    @Override
                    public void onListen(Object o, EventChannel.EventSink imageStreamSink) {
                        setImageStreamImageAvailableListener();
                    }

                    @Override
                    public void onCancel(Object o) {
                        imageStreamReader.setOnImageAvailableListener(null, null);
                    }
                });
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setImageStreamImageAvailableListener() {
        imageStreamReader.setOnImageAvailableListener(
                reader -> {
                    Image img = reader.acquireLatestImage();
                    if (img == null) return;

                    buffers[currentBuffer].put(convertYUV420ToNV21(img));
                    buffers[currentBuffer].position(0);

                    deepAR.receiveFrame(buffers[currentBuffer], img.getWidth(), img.getHeight(), sensorOrientation, isFrontFacing);

                    currentBuffer = (currentBuffer + 1) % numberOfBuffers;
                    img.close();
                },
                null);
    }

    private void closeCaptureSession() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
    }

    public void close() {
        closeCaptureSession();

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (pictureImageReader != null) {
            pictureImageReader.close();
            pictureImageReader = null;
        }
        if (imageStreamReader != null) {
            imageStreamReader.close();
            imageStreamReader = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    public void dispose() {
        close();
        flutterTexture.release();
        orientationEventListener.disable();
    }

    private int getMediaOrientation() {
        final int sensorOrientationOffset =
                (currentOrientation == ORIENTATION_UNKNOWN)
                        ? 0
                        : (isFrontFacing) ? -currentOrientation : currentOrientation;
        return (sensorOrientationOffset + sensorOrientation + 360) % 360;
    }
}

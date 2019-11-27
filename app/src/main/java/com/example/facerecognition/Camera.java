package com.example.facerecognition;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import org.w3c.dom.Text;

import java.util.Collections;

public class Camera extends AppCompatActivity {
    private static final int CAMERA_REQUEST_CODE = 1;
    private CameraManager cameraManager;
    private int cameraFacing;
    private String cameraId;
    private TextureView.SurfaceTextureListener surfaceTextureListener;
    private Size previewSize;
    private CameraDevice.StateCallback stateCallback;
    private Handler backgroundHandler;
    private CameraDevice cameraDevice;
    private HandlerThread backgroundThread;
    private TextureView textureView;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest captureRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        textureView = (TextureView) findViewById(R.id.texture_view);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        cameraFacing = CameraCharacteristics.LENS_FACING_BACK;

        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setUpCamera();
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        };

        stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                cameraDevice = camera;
                createPreviewSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                cameraDevice.close();
                cameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                cameraDevice.close();
                cameraDevice = null;
            }
        };

    }

    @Override
    protected void onResume() {
        super.onResume();
        openBackgroundThread();
        if (textureView.isAvailable()) {
            setUpCamera();
            openCamera();
        }
        else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCamera();
        closeBackgroundThread();
    }

    private void setUpCamera() {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == cameraFacing) {
                    StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];
                    this.cameraId = cameraId;
                }
            }
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
    }

    private void openBackgroundThread() {
        backgroundThread = new HandlerThread("bg_camera");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void closeBackgroundThread() {
        if (backgroundHandler != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    private void createPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }

                            try {
                                captureRequest = captureRequestBuilder.build();
                                cameraCaptureSession = session;
                                cameraCaptureSession.setRepeatingRequest(captureRequest, null, backgroundHandler);
                            }
                            catch (CameraAccessException cae) {
                                cae.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }
                    }, backgroundHandler);
        }
        catch (CameraAccessException cae) {
            cae.printStackTrace();
        }
    }
}

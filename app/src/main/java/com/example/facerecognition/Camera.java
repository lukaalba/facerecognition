package com.example.facerecognition;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private File galleryDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        textureView = (TextureView) findViewById(R.id.texture_view);
        FloatingActionButton fab = findViewById(R.id.take_photo_button);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_REQUEST_CODE);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        cameraFacing = CameraCharacteristics.LENS_FACING_BACK;

        surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setUpCamera();
                openCamera();
                createImageDirectory();
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
        catch (NullPointerException npe) {
            Toast camera_ava = Toast.makeText(getApplicationContext(), "No Cameras available", Toast.LENGTH_SHORT);
            camera_ava.show();
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
                            } catch (CameraAccessException cae) {
                                cae.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }
                    }, backgroundHandler);
        } catch (CameraAccessException cae) {
            cae.printStackTrace();
        }
    }

    private void createImageDirectory() {
        Toast cImageDir;
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            File storeDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            galleryDir = new File(storeDir, getResources().getString(R.string.app_name));

            if (!galleryDir.exists()) {
                boolean dirExists = galleryDir.mkdirs();
                if (!dirExists) {
                    cImageDir = Toast.makeText(getApplicationContext(),"Can not create directory on data storage. Images can not be saved", Toast.LENGTH_LONG);
                    cImageDir.show();
                }
            }
        }
        else {
            cImageDir = Toast.makeText(getApplicationContext(),"No permissions to write to data storage. Images can not be saved", Toast.LENGTH_LONG);
            cImageDir.show();
        }
    }

    private File createImageFile(File imgDir) throws IOException {
        String imageFileName = "frc_img";
        return File.createTempFile(imageFileName, ".png", imgDir);
    }

    private void lock_preview() {
        try {
            cameraCaptureSession.capture(captureRequestBuilder.build(), null, backgroundHandler);

        }
        catch (CameraAccessException cae) {
            cae.printStackTrace();
        }
    }

    private void unlock_preview() {
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException cae) {
            cae.printStackTrace();
        }
    }

    public void takePhoto() {
        FileOutputStream photo_output = null;
        lock_preview();
        try {
            photo_output = new FileOutputStream(createImageFile(galleryDir));
            textureView.getBitmap().compress(Bitmap.CompressFormat.PNG, 100, photo_output);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        finally {
            unlock_preview();
            try {
                if (photo_output != null) {
                    photo_output.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}

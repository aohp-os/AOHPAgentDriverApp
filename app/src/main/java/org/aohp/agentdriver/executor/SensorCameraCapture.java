package org.aohp.agentdriver.executor;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hardware still capture via Camera2, saved under the standard DCIM/Camera path.
 */
public final class SensorCameraCapture {
    private static final String TAG = "SensorCameraCapture";
    public static final String DEFAULT_DIR = "/sdcard/DCIM/Camera";
    private static final long CAPTURE_TIMEOUT_MS = 20_000L;

    private SensorCameraCapture() {
    }

    public static String defaultOutputPath() {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return DEFAULT_DIR + "/IMG_" + stamp + ".jpg";
    }

    public static ShellExecutor.CommandResult captureStill(
            Context context,
            String outputPath,
            int lensFacing,
            int jpegQuality) {
        ShellExecutor.CommandResult result = new ShellExecutor.CommandResult();
        result.exitCode = 1;

        String path = (outputPath == null || outputPath.trim().isEmpty())
                ? defaultOutputPath()
                : outputPath.trim();
        File outFile = new File(path);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            result.success = false;
            result.error = "Failed to create directory: " + parent.getAbsolutePath();
            return result;
        }

        HandlerThread thread = new HandlerThread("aohp-sensor-camera");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<CameraDevice> deviceRef = new AtomicReference<>();
        AtomicReference<CameraCaptureSession> sessionRef = new AtomicReference<>();
        ImageReader reader = null;

        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                result.success = false;
                result.error = "CameraManager unavailable";
                return result;
            }

            String cameraId = pickCameraId(manager, lensFacing);
            if (cameraId == null) {
                result.success = false;
                result.error = "No camera for facing=" + lensFacing;
                return result;
            }

            Size size = pickJpegSize(manager, cameraId);
            reader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
            final ImageReader imageReader = reader;
            final String finalPath = path;

            reader.setOnImageAvailableListener(ir -> {
                Image image = null;
                try {
                    image = ir.acquireLatestImage();
                    if (image == null) {
                        error.compareAndSet(null, "Camera returned no image");
                        return;
                    }
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    try (FileOutputStream fos = new FileOutputStream(finalPath)) {
                        fos.write(bytes);
                    }
                    MediaScannerConnection.scanFile(
                            context,
                            new String[]{finalPath},
                            new String[]{"image/jpeg"},
                            null);
                    result.success = true;
                    result.exitCode = 0;
                    result.output = finalPath;
                } catch (Exception e) {
                    error.compareAndSet(null, e.getMessage());
                } finally {
                    if (image != null) {
                        image.close();
                    }
                    done.countDown();
                }
            }, handler);

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    deviceRef.set(camera);
                    try {
                        CaptureRequest.Builder builder =
                                camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        builder.addTarget(imageReader.getSurface());
                        Integer sensorOrientation = null;
                        try {
                            CameraCharacteristics chars =
                                    manager.getCameraCharacteristics(cameraId);
                            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
                        } catch (CameraAccessException ignored) {
                        }
                        if (sensorOrientation != null) {
                            builder.set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation);
                        }
                        int quality = Math.max(1, Math.min(100, jpegQuality));
                        builder.set(CaptureRequest.JPEG_QUALITY, (byte) quality);

                        camera.createCaptureSession(
                                Collections.singletonList(imageReader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(CameraCaptureSession session) {
                                        sessionRef.set(session);
                                        try {
                                            session.capture(
                                                    builder.build(),
                                                    new CameraCaptureSession.CaptureCallback() {
                                                        @Override
                                                        public void onCaptureFailed(
                                                                CameraCaptureSession s,
                                                                CaptureRequest request,
                                                                CaptureFailure failure) {
                                                            error.compareAndSet(
                                                                    null,
                                                                    "Capture failed: reason="
                                                                            + failure.getReason());
                                                            done.countDown();
                                                        }
                                                    },
                                                    handler);
                                        } catch (Exception e) {
                                            error.compareAndSet(null, e.getMessage());
                                            done.countDown();
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(CameraCaptureSession session) {
                                        error.compareAndSet(null, "Camera session configure failed");
                                        done.countDown();
                                    }
                                },
                                handler);
                    } catch (Exception e) {
                        error.compareAndSet(null, e.getMessage());
                        done.countDown();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    error.compareAndSet(null, "Camera disconnected");
                    done.countDown();
                    closeQuietly(camera);
                }

                @Override
                public void onError(CameraDevice camera, int errorCode) {
                    error.compareAndSet(null, "Camera error code=" + errorCode);
                    done.countDown();
                    closeQuietly(camera);
                }
            }, handler);

            if (!done.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                result.success = false;
                result.error = "Camera capture timed out";
                return result;
            }
            if (error.get() != null) {
                result.success = false;
                result.error = error.get();
                return result;
            }
            if (!result.success) {
                result.error = "Camera capture failed";
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "captureStill failed", e);
            result.success = false;
            result.error = e.getMessage();
            return result;
        } finally {
            CameraCaptureSession session = sessionRef.get();
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
            CameraDevice device = deviceRef.get();
            if (device != null) {
                closeQuietly(device);
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
            thread.quitSafely();
        }
    }

    private static void closeQuietly(CameraDevice camera) {
        try {
            camera.close();
        } catch (Exception ignored) {
        }
    }

    private static String pickCameraId(CameraManager manager, int lensFacing)
            throws CameraAccessException {
        int desired = lensFacing == 1
                ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK;
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == desired) {
                return id;
            }
            if (fallback == null) {
                fallback = id;
            }
        }
        return fallback;
    }

    private static Size pickJpegSize(CameraManager manager, String cameraId)
            throws CameraAccessException {
        CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
        Size[] sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getOutputSizes(ImageFormat.JPEG);
        if (sizes == null || sizes.length == 0) {
            return new Size(1920, 1080);
        }
        return Collections.max(
                Arrays.asList(sizes),
                Comparator.comparingInt((Size s) -> s.getWidth() * s.getHeight()));
    }
}

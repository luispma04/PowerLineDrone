package com.dji.sdk.sample.internal.view;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.controller.MainActivity;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.utils.VideoFeedView;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.LEDsSettings;
import dji.common.flightcontroller.ObstacleDetectionSector;
import dji.common.flightcontroller.VisionSensorPosition;
import dji.common.flightcontroller.FlightControllerState;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.flightcontroller.FlightAssistant;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.TensorFlowLite;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class FullScreenVideoViewZPI extends LinearLayout implements PresentableView {

    public static final int INVALID_READING_LIMIT_TIME = 2000;
    public static final int DISTANCE_UPDATE_DELAY_TIME = 170;
    public static final int SHOT_DELAY_TIME = 4000;
    private static final int INVALID_DISTANCE = 100;
    public static final int THRESHOLD_DISTANCE = 4;
    MainContent mainContent;
    private VideoFeedView videoFeedView;
    private VideoFeeder.VideoDataListener videoDataListener;
    private FlightController flightController;
    private Handler circlesHandler;
    private CrosshairViewZPI overlayView;
    private Runnable updateRunnable;
    private Handler distanceHandler;
    private Queue<Float> recentReadings = new LinkedList<>();
    private boolean isAimModeOn = false;
    private float noseObstacleDistance = 100;
    private long lastValidReadingTime = 0;
    private Button btn_fire;
    private Button btn_aim;
    private Button btn_detect;
    private TextView textOverlayView;
    private View greenTintOverlay;
    private TextView tvAltitude;
    private TextView tvSpeed;
    private ArtificialHorizonViewZPI artificialHorizonView;
    private boolean isObjectDetectionEnabled = false;
    private Handler objectDetectionHandler;
    private Runnable objectDetectionRunnable;
    private Interpreter tfliteInterpreter;
    private ObjectDetectionOverlayView detectionOverlayView;

    public FullScreenVideoViewZPI(Context context) {
        super(context);
        init(context);
    }

    public FullScreenVideoViewZPI(Context context, MainContent mainContent) {
        super(context);
        init(context);
        this.mainContent = mainContent;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DJISampleApplication.getEventBus().post(new MainActivity.RequestStartFullScreenEvent());
        enableFullscreenMode();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        DJISampleApplication.getEventBus().post(new MainActivity.RequestEndFullScreenEvent());
        disableFullscreenMode();
        circlesHandler.removeCallbacks(updateRunnable);
        distanceHandler.removeCallbacks(distanceChecker);
        distanceHandler.removeCallbacks(distanceTextUpdater);

        if (flightController != null) {
            flightController.setStateCallback(null);
        }
    }

    private void init(Context context) {
        try {
            // Log the model loading attempt
            Log.d("ObjectDetection", "Attempting to load TensorFlow Lite model...");
            tfliteInterpreter = new Interpreter(loadModelFile(context, "best_float32.tflite"));
            Log.d("ObjectDetection", "TensorFlow Lite model loaded successfully.");
            // Log model input and output shapes
            Log.d("ObjectDetection", "Input shape: " + Arrays.toString(tfliteInterpreter.getInputTensor(0).shape()));
            Log.d("ObjectDetection", "Output shape: " + Arrays.toString(tfliteInterpreter.getOutputTensor(0).shape()));

        } catch (IOException e) {
            e.printStackTrace();
            Log.e("ObjectDetection", "Failed to load TensorFlow Lite model!", e);
            ToastUtils.setResultToToast("Failed to load TensorFlow Lite model!");
        }

        LayoutInflater.from(context).inflate(R.layout.view_full_screen_video_zpi, this, true);
        videoFeedView = findViewById(R.id.video_feed_view);
        btn_fire = findViewById(R.id.btn_fire);
        btn_aim = findViewById(R.id.btn_aim);
        btn_detect = findViewById(R.id.btn_detect);
        overlayView = findViewById(R.id.overlay_view);
        textOverlayView = findViewById(R.id.text_overlay_view);
        greenTintOverlay = findViewById(R.id.green_tint_overlay);

        detectionOverlayView = findViewById(R.id.object_detection_overlay);

        tvAltitude = findViewById(R.id.tv_altitude);
        tvSpeed = findViewById(R.id.tv_speed);

        artificialHorizonView = findViewById(R.id.artificial_horizon_view);
        artificialHorizonView.setVisibility(View.GONE);

        if (VideoFeeder.getInstance() != null) {
            setupVideoFeedAndCamera();
        }

        initFlightController();
        setupFlightControllerStateCallback();

        setupButtons();
        setupObstacleDistanceDetection();
        setupCircleHandler();
        startDistanceHandler();

        Log.d("ObjectDetection", "Initialization complete.");
    }

    private void enableFullscreenMode() {
        View decorView = ((MainActivity) getContext()).getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void disableFullscreenMode() {
        if (mainContent != null) {
            mainContent.enableButton();
        }
        View decorView = ((MainActivity) getContext()).getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    private void setupCircleHandler() {
        circlesHandler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateOverlayCircles();
                circlesHandler.postDelayed(this, 50);
            }
        };
        circlesHandler.post(updateRunnable);
    }

    private void setupVideoFeedAndCamera() {
        VideoFeeder.VideoFeed videoFeed = VideoFeeder.getInstance().getPrimaryVideoFeed();
        videoDataListener = videoFeedView.registerLiveVideo(VideoFeeder.getInstance().getPrimaryVideoFeed(), true);
        videoFeed.addVideoDataListener(videoDataListener);
        videoFeedView.registerLiveVideo(videoFeed, true);
    }

    private void shoot() {
        turnOnLed();
        ToastUtils.setResultToToast("Fired!");
    }

    private void updateOverlayCircles() {
        // Check conditions for displaying crosshair
        boolean shouldShowCrosshair = isAimModeOn && noseObstacleDistance < THRESHOLD_DISTANCE;

        if (shouldShowCrosshair) {
            btn_fire.setEnabled(true);
            // Calculate the radii based on the error distance and the CEP multipliers
            float errorDistance = noseObstacleDistance * 10;

            float radius50 = errorDistance * 0.6745f;
            float radius93 = errorDistance * 2.0f;
            float radius99 = errorDistance * 2.576f;

            // Scale the radii to fit the view dimensions
            float scaleFactor = calculateScaleFactor();
            radius50 *= scaleFactor;
            radius93 *= scaleFactor;
            radius99 *= scaleFactor;

            overlayView.updateRadii(radius50, radius93, radius99);
            overlayView.showCrosshair(true);
        } else {
            btn_fire.setEnabled(false);
            // Hide the crosshair
            overlayView.showCrosshair(false);
        }
        // Always redraw the overlayView to show distance text
        overlayView.invalidate();
    }

    private float calculateScaleFactor() {
        // Calculate a scale factor based on the view size
        // For simplicity, let's assume the maximum errorDistance corresponds to half the smaller dimension
        float maxErrorDistance = 50f; // This should match the amplitude used in getErrorDistance()
        float minViewDimension = Math.min(overlayView.getWidth(), overlayView.getHeight());
        return (minViewDimension / 2f) / maxErrorDistance;
    }

    private Random random = new Random();
    private float currentErrorDistance = 1f;
    private float targetErrorDistance = 1f;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 50; // Update every 500 milliseconds
    private float interpolationSpeed = 0.05f; // Adjust for smoother transitions

    private float getErrorDistance() {
        long currentTime = System.currentTimeMillis();

        // Check if it's time to update the target error distance
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            lastUpdateTime = currentTime;

            // Generate a new random target error distance between 1 and 50
            targetErrorDistance = 1 + random.nextFloat() * 20; // Random value between 1 and 50
        }

        // Interpolate towards the target error distance
        currentErrorDistance += (targetErrorDistance - currentErrorDistance) * interpolationSpeed;

        return currentErrorDistance;
    }

    private void turnOnLed() {
        if (flightController == null && DJISDKManager.getInstance() != null) {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product instanceof Aircraft) {
                flightController = ((Aircraft) product).getFlightController();
            }
        }

        if (flightController != null) {
            LEDsSettings ledsSettingsOn = new LEDsSettings.Builder().frontLEDsOn(true).build();
            flightController.setLEDsEnabledSettings(ledsSettingsOn, null);

            new Handler().postDelayed(() -> {
                LEDsSettings ledsSettingsOff = new LEDsSettings.Builder().frontLEDsOn(false).build();
                flightController.setLEDsEnabledSettings(ledsSettingsOff, null);
            }, SHOT_DELAY_TIME);
        }
    }

    private void setupButtons() {
        btn_fire.setOnClickListener(v -> {
            shoot();
        });

        btn_aim.setOnClickListener(v -> {
            aim();
        });

        btn_detect.setOnClickListener(v -> {
            toggleObjectDetection();
        });

        btn_fire.setEnabled(false);
    }

    private void aim() {
        isAimModeOn = !isAimModeOn;
        overlayView.showCrosshair(isAimModeOn);
        greenTintOverlay.setVisibility(isAimModeOn ? View.VISIBLE : View.GONE);
        artificialHorizonView.setVisibility(isAimModeOn ? View.VISIBLE : View.GONE);
        displayAimModeToastMsg();
    }

    private void toggleObjectDetection() {
        if (isObjectDetectionEnabled) {
            Log.d("ObjectDetection", "Stopping object detection...");
            stopObjectDetection();
            btn_detect.setText("Start Detect");
            ToastUtils.setResultToToast("Object Detection Stopped");
        } else {
            Log.d("ObjectDetection", "Starting object detection...");
            startObjectDetection();
            btn_detect.setText("Stop Detect");
            ToastUtils.setResultToToast("Object Detection Started");
        }
    }

    private void displayAimModeToastMsg() {
        if (isAimModeOn) {
            ToastUtils.setResultToToast("Aim Mode turned ON");
        } else {
            ToastUtils.setResultToToast("Aim Mode turned OFF");
        }
    }

    @Override
    public int getDescription() {
        return R.string.component_fullscreen_video_view_zpi;
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    private void setupObstacleDistanceDetection() {
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            FlightAssistant intelligentFlightAssistant = ((Aircraft) DJISampleApplication
                    .getProductInstance())
                    .getFlightController()
                    .getFlightAssistant();

            if (intelligentFlightAssistant != null) {
                intelligentFlightAssistant.setVisionDetectionStateUpdatedCallback(visionDetectionState -> {

                    ObstacleDetectionSector[] visionDetectionSectorArray =
                            visionDetectionState.getDetectionSectors();

                    if (visionDetectionSectorArray == null
                            || visionDetectionState.getPosition() != VisionSensorPosition.NOSE) {
                        return;
                    }
                    float minReading = INVALID_DISTANCE;
                    for (ObstacleDetectionSector sector : visionDetectionSectorArray) {
                        float distance = sector.getObstacleDistanceInMeters();
                        if (distance >= 0 && distance != 100 && distance < minReading) { // ignore invalid readings of 100 meters
                            minReading = distance;
                            lastValidReadingTime = System.currentTimeMillis(); // update the last valid reading time
                        }
                    }
                    if (minReading < INVALID_DISTANCE) {
                        recentReadings.add(minReading);
                    }
                });
            }
        }
    }

    private void startDistanceHandler() {
        distanceHandler = new Handler();
        distanceHandler.post(distanceChecker);
        distanceHandler.post(distanceTextUpdater);
    }

    private final Runnable distanceChecker = new Runnable() {
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();

            if (!recentReadings.isEmpty()) {
                float sum = 0;
                for (Float reading : recentReadings) {
                    sum += reading;
                }
                noseObstacleDistance = sum / recentReadings.size();
            } else if (currentTime - lastValidReadingTime > INVALID_READING_LIMIT_TIME) {
                // if no valid readings, set distance to 100 meters
                noseObstacleDistance = INVALID_DISTANCE;
            }

            recentReadings.clear(); // clear readings for the next time window
            distanceHandler.postDelayed(this, DISTANCE_UPDATE_DELAY_TIME);
        }
    };

    private final Runnable distanceTextUpdater = new Runnable() {
        @Override
        public void run() {
            String distanceText = "Obstacle Distance: " + String.format("%.2f m", noseObstacleDistance);
            textOverlayView.setText(distanceText);

            distanceHandler.postDelayed(this, 500);
        }
    };

    private void initFlightController() {
        if (DJISDKManager.getInstance() != null) {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product instanceof Aircraft) {
                flightController = ((Aircraft) product).getFlightController();
            }
        }
    }

    private void setupFlightControllerStateCallback() {
        if (flightController != null) {
            flightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState state) {
                    updateDroneState(state);
                }
            });
        }
    }
    private void updateDroneState(FlightControllerState state) {
        // Update Altitude
        final double altitude = state.getAircraftLocation().getAltitude();

        // Update Velocity
        final float velocityX = state.getVelocityX();
        final float velocityY = state.getVelocityY();
        final float velocityZ = state.getVelocityZ();
        final double speed = Math.sqrt(
                velocityX * velocityX +
                        velocityY * velocityY +
                        velocityZ * velocityZ
        );

        // Update Gyroscope Data (Attitude)
        final Attitude attitude = state.getAttitude();
        final double pitch = attitude.pitch;
        final double roll = attitude.roll;

        // Update UI on the main thread
        post(() -> {
            tvAltitude.setText(String.format("Altitude: %.1f m", altitude));
            tvSpeed.setText(String.format("Speed: %.1f m/s", speed));

            if (artificialHorizonView != null) {
                artificialHorizonView.updateAttitude((float) pitch, (float) roll);
            }
        });
    }

    private void startObjectDetection() {
        isObjectDetectionEnabled = true;
        objectDetectionHandler = new Handler();
        objectDetectionRunnable = new Runnable() {
            @Override
            public void run() {
                if (isObjectDetectionEnabled) {
                    performObjectDetection();
                    objectDetectionHandler.postDelayed(this, 200);
                }
            }
        };
        objectDetectionHandler.post(objectDetectionRunnable);
    }

    private void stopObjectDetection() {
        isObjectDetectionEnabled = false;
        if (objectDetectionHandler != null) {
            objectDetectionHandler.removeCallbacks(objectDetectionRunnable);
        }
    }

    private void performObjectDetection() {
        Log.d("ObjectDetection", "Starting object detection process...");
        String tfliteVersion = TensorFlowLite.runtimeVersion();
        Log.d("TensorFlowLite", "TensorFlow Lite Runtime Version: " + tfliteVersion);

        if (videoFeedView.getBitmap() != null) {
            Bitmap frameBitmap = videoFeedView.getBitmap();

            Log.d("ObjectDetection", "Frame captured for processing: " +
                    frameBitmap.getWidth() + "x" + frameBitmap.getHeight());

            try {
                // Preprocess the frame
                float[] inputTensor = preprocessFrame(frameBitmap);
                Log.d("ObjectDetection", "Input tensor prepared. Shape: [1, 640, 640, 3]");
                Log.d("ObjectDetection", "Input tensor size: " + inputTensor.length);
                Log.d("ObjectDetection", "First 10 tensor values: " +
                        inputTensor[0] + ", " + inputTensor[1] + ", " + inputTensor[2] + "...");

                // Initialize output tensor matching the model's output shape
                float[][][] outputTensor = new float[1][5][8400];

                // Allocate tensors and run inference
                try {
                    tfliteInterpreter.allocateTensors(); // Ensure tensors are allocated
                    Log.d("ObjectDetection", "Tensors allocated successfully.");
                    tfliteInterpreter.run(inputTensor, outputTensor);
                    Log.d("ObjectDetection", "Inference completed.");
                } catch (Exception e) {
                    Log.e("ObjectDetection", "Error during inference: ", e);
                    return;
                }

                // Process the model output
                processOutput(outputTensor);

            } catch (Exception e) {
                Log.e("ObjectDetection", "Error during preprocessing or detection: ", e);
            }
        } else {
            Log.w("ObjectDetection", "No frame available for processing.");
        }
    }

    private void processOutput(float[][][] outputTensor) {
        Log.d("ObjectDetection", "Processing output tensor...");

        // Iterate through detections
        for (int i = 0; i < outputTensor[0][4].length; i++) {
            float confidence = outputTensor[0][4][i];

            if (confidence > 0.2) { // Adjust confidence threshold as needed
                float xCenter = outputTensor[0][0][i];
                float yCenter = outputTensor[0][1][i];
                float width = outputTensor[0][2][i];
                float height = outputTensor[0][3][i];

                Log.d("ObjectDetection", "Detection " + i + ": Confidence=" + confidence);

                // Convert YOLO format (x_center, y_center, width, height) to bounding box
                float left = xCenter - width / 2;
                float top = yCenter - height / 2;
                float right = xCenter + width / 2;
                float bottom = yCenter + height / 2;

                Log.d("ObjectDetection", "Bounding box - Left: " + left + ", Top: " + top +
                        ", Right: " + right + ", Bottom: " + bottom);

                updateDetectionOverlay(left, top, right, bottom, "Detected Object");
            }
        }
    }

    private float[] preprocessFrame(Bitmap frameBitmap) {
        int modelInputSize = 640;

        // Resize the frame to 640x640
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(frameBitmap, modelInputSize, modelInputSize, true);

        // Create a tensor for the input
        float[] inputTensor = new float[modelInputSize * modelInputSize * 3];
        int[] pixelValues = new int[modelInputSize * modelInputSize];
        resizedBitmap.getPixels(pixelValues, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize);

        for (int i = 0; i < pixelValues.length; i++) {
            int pixel = pixelValues[i];

            // Normalize RGB values to [0, 1]
            inputTensor[i * 3] = ((pixel >> 16) & 0xFF) / 255.0f; // Red
            inputTensor[i * 3 + 1] = ((pixel >> 8) & 0xFF) / 255.0f; // Green
            inputTensor[i * 3 + 2] = (pixel & 0xFF) / 255.0f; // Blue
        }
        return inputTensor;
    }

    private void updateDetectionOverlay(float left, float top, float right, float bottom, String label) {
        detectionOverlayView.setBoundingBox(left, top, right, bottom, label);
    }

    private void clearDetectionOverlay() {
        detectionOverlayView.clearBoundingBox();
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
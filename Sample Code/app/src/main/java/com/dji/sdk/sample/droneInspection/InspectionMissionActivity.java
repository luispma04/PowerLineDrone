package com.dji.sdk.sample.droneInspection;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.dji.sdk.sample.droneInspection.model.InspectionPoint;
import com.dji.sdk.sample.droneInspection.model.PhotoPosition;
import com.dji.sdk.sample.droneInspection.utils.CsvUtils;
import com.dji.sdk.sample.droneInspection.utils.ObstacleAvoidanceUtils;

import java.util.ArrayList;
import java.util.List;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.media.MediaFile;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class InspectionMissionActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = InspectionMissionActivity.class.getName();

    // UI Elements
    private Button btnLoadInspectionPoints;
    private Button btnLoadPhotoPositions;
    private Button btnStartMission;
    private Button btnPauseMission;
    private Button btnResumeMission;
    private Button btnStopMission;
    private Button btnAcceptPhoto;
    private Button btnRetakePhoto;
    private TextView tvStatus;
    private ImageView ivPreview;

    // Mission data
    private List<InspectionPoint> inspectionPoints = new ArrayList<>();
    private List<PhotoPosition> photoPositions = new ArrayList<>();
    private int currentInspectionPointIndex = 0;
    private int currentPhotoPositionIndex = 0;
    private boolean isWaitingForPhotoApproval = false;

    // DJI SDK components
    private WaypointMissionOperator waypointMissionOperator;
    private FlightController flightController;
    private Camera camera;
    private Gimbal gimbal;

    // Activity result launchers for file picking
    private ActivityResultLauncher<String> inspectionPointsLauncher;
    private ActivityResultLauncher<String> photoPositionsLauncher;

    // Broadcast receiver for product connection changes
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateDroneConnectionStatus();
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inspection_mission);

        // Initialize UI elements
        initUI();

        // Initialize file pickers
        initFilePickers();

        // Get mission operator
        if (DJISDKManager.getInstance() != null &&
                DJISDKManager.getInstance().getMissionControl() != null) {
            waypointMissionOperator = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();

            // Add mission operator listener
            if (waypointMissionOperator != null) {
                addMissionOperatorListener();
            }
        }

        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(DroneInspectionApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(receiver, filter);

        // Initial update of connection status
        updateDroneConnectionStatus();
    }

    private void initUI() {
        btnLoadInspectionPoints = findViewById(R.id.btn_load_inspection_points);
        btnLoadPhotoPositions = findViewById(R.id.btn_load_photo_positions);
        btnStartMission = findViewById(R.id.btn_start_mission);
        btnPauseMission = findViewById(R.id.btn_pause_mission);
        btnResumeMission = findViewById(R.id.btn_resume_mission);
        btnStopMission = findViewById(R.id.btn_stop_mission);
        btnAcceptPhoto = findViewById(R.id.btn_accept_photo);
        btnRetakePhoto = findViewById(R.id.btn_retake_photo);
        tvStatus = findViewById(R.id.tv_status);
        ivPreview = findViewById(R.id.iv_preview);

        // Configure os listeners
        if (btnLoadInspectionPoints != null) btnLoadInspectionPoints.setOnClickListener(this);
        if (btnLoadPhotoPositions != null) btnLoadPhotoPositions.setOnClickListener(this);
        if (btnStartMission != null) btnStartMission.setOnClickListener(this);
        if (btnPauseMission != null) btnPauseMission.setOnClickListener(this);
        if (btnResumeMission != null) btnResumeMission.setOnClickListener(this);
        if (btnStopMission != null) btnStopMission.setOnClickListener(this);
        if (btnAcceptPhoto != null) btnAcceptPhoto.setOnClickListener(this);
        if (btnRetakePhoto != null) btnRetakePhoto.setOnClickListener(this);

        // Initially disable mission control buttons until data is loaded
        setMissionButtonsEnabled(false);
        setPhotoApprovalButtonsEnabled(false);
    }

    private void initFilePickers() {
        inspectionPointsLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                result -> {
                    if (result != null) {
                        loadInspectionPoints(result);
                    }
                });

        photoPositionsLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                result -> {
                    if (result != null) {
                        loadPhotoPositions(result);
                    }
                });
    }

    private void addMissionOperatorListener() {
        waypointMissionOperator.addListener(new WaypointMissionOperatorListener() {
            @Override
            public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {
                // Not needed for our implementation
            }

            @Override
            public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {
                if (uploadEvent.getCurrentState() == WaypointMissionUploadEvent.UploadState.UPLOADING) {
                    updateStatus("Uploading waypoint mission: " +
                            uploadEvent.getProgress().uploadedWaypointIndex + " / " +
                            uploadEvent.getProgress().totalWaypointCount);
                } else if (uploadEvent.getCurrentState() == WaypointMissionUploadEvent.UploadState.UPLOAD_SUCCESSFUL) {
                    updateStatus("Mission upload successful");
                    runOnUiThread(() -> {
                        setMissionButtonsEnabled(true);
                    });
                }
            }

            @Override
            public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {
                if (executionEvent.getCurrentState() == WaypointMissionExecutionEvent.ExecutionState.EXECUTING) {
                    updateStatus("Executing waypoint mission: " +
                            executionEvent.getProgress().targetWaypointIndex + " / " +
                            executionEvent.getProgress().totalWaypointCount);
                }
            }

            @Override
            public void onExecutionStart() {
                updateStatus("Mission execution started");
            }

            @Override
            public void onExecutionFinish(DJIError error) {
                if (error == null) {
                    updateStatus("Mission execution finished successfully");
                    startPhotoSequence(); // Start taking photos after arriving at the inspection point
                } else {
                    updateStatus("Mission execution finished with error: " + error.getDescription());
                    resetMission();
                }
            }
        });
    }

    private void updateDroneConnectionStatus() {
        BaseProduct product = DroneInspectionApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                Aircraft aircraft = (Aircraft) product;

                // Get flight controller
                flightController = aircraft.getFlightController();
                if (flightController != null) {
                    flightController.setStateCallback(state -> {
                        // Check for obstacles using our utility
                        if (ObstacleAvoidanceUtils.isObstacleDetected(state)) {
                            // Handle obstacle detection
                            runOnUiThread(() -> {
                                showToast("Obstacle detected!");
                            });
                        }
                    });
                }

                // Get camera
                camera = DroneInspectionApplication.getCameraInstance();

                // Get gimbal
                if (aircraft.getGimbals() != null && !aircraft.getGimbals().isEmpty()) {
                    gimbal = aircraft.getGimbals().get(0);
                }

                updateStatus("Drone connected");
                runOnUiThread(() -> {
                    if (btnLoadInspectionPoints != null) btnLoadInspectionPoints.setEnabled(true);
                    if (btnLoadPhotoPositions != null) btnLoadPhotoPositions.setEnabled(true);
                });
            }
        } else {
            updateStatus("Drone not connected");
            runOnUiThread(() -> {
                if (btnLoadInspectionPoints != null) btnLoadInspectionPoints.setEnabled(false);
                if (btnLoadPhotoPositions != null) btnLoadPhotoPositions.setEnabled(false);
                setMissionButtonsEnabled(false);
            });
        }
    }

    private void loadInspectionPoints(Uri uri) {
        if (uri != null) {
            // Use CsvUtils to parse inspection points from CSV file
            inspectionPoints = CsvUtils.parseInspectionPointsCsv(this, uri);
            updateStatus("Loaded " + inspectionPoints.size() + " inspection points");
            updateMissionButtonStatus();
        } else {
            showToast("Error loading inspection points file");
        }
    }

    private void loadPhotoPositions(Uri uri) {
        if (uri != null) {
            // Use CsvUtils to parse photo positions from CSV file
            photoPositions = CsvUtils.parsePhotoPositionsCsv(this, uri);
            updateStatus("Loaded " + photoPositions.size() + " photo positions");
            updateMissionButtonStatus();
        } else {
            showToast("Error loading photo positions file");
        }
    }

    private void updateMissionButtonStatus() {
        boolean canStartMission = !inspectionPoints.isEmpty() && !photoPositions.isEmpty();
        setMissionButtonsEnabled(canStartMission);
    }

    private void setMissionButtonsEnabled(boolean enabled) {
        if (btnStartMission != null) btnStartMission.setEnabled(enabled);
        if (btnPauseMission != null) btnPauseMission.setEnabled(false);
        if (btnResumeMission != null) btnResumeMission.setEnabled(false);
        if (btnStopMission != null) btnStopMission.setEnabled(false);
    }

    private void setPhotoApprovalButtonsEnabled(boolean enabled) {
        if (btnAcceptPhoto != null) btnAcceptPhoto.setEnabled(enabled);
        if (btnRetakePhoto != null) btnRetakePhoto.setEnabled(enabled);
    }

    private void startInspectionMission() {
        if (inspectionPoints.isEmpty() || photoPositions.isEmpty()) {
            showToast("Please load inspection points and photo positions first");
            return;
        }

        if (waypointMissionOperator == null) {
            showToast("Waypoint mission operator is null");
            return;
        }

        // Reset mission state
        currentInspectionPointIndex = 0;
        currentPhotoPositionIndex = 0;

        // Navigate to the first inspection point
        navigateToInspectionPoint(currentInspectionPointIndex);
    }

    private void navigateToInspectionPoint(int index) {
        if (index >= inspectionPoints.size()) {
            // All inspection points completed
            showToast("Inspection mission completed");
            updateStatus("Inspection mission completed");
            return;
        }

        InspectionPoint point = inspectionPoints.get(index);
        updateStatus("Navigating to inspection point " + (index + 1) + "/" + inspectionPoints.size());

        // Get the drone's current position
        if (flightController != null && flightController.getState() != null) {
            FlightControllerState state = flightController.getState();

            double startLat = state.getAircraftLocation().getLatitude();
            double startLng = state.getAircraftLocation().getLongitude();
            float startAlt = state.getAircraftLocation().getAltitude();

            // Use ObstacleAvoidanceUtils to calculate waypoints with obstacle avoidance
            List<Waypoint> waypointsWithAvoidance = ObstacleAvoidanceUtils.calculateIntermediateWaypoints(
                    startLat, startLng, startAlt,
                    point.getLatitude(), point.getLongitude(), point.getSafeAltitude());

            // Create a waypoint mission with the calculated waypoints
            WaypointMission.Builder builder = new WaypointMission.Builder();

            // Add all waypoints to the mission
            for (Waypoint waypoint : waypointsWithAvoidance) {
                builder.addWaypoint(waypoint);
            }

            // Configure the mission
            builder.waypointCount(waypointsWithAvoidance.size())
                    .autoFlightSpeed(5f)
                    .maxFlightSpeed(10f)
                    .setExitMissionOnRCSignalLostEnabled(true)
                    .finishedAction(WaypointMissionFinishedAction.NO_ACTION)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                    .headingMode(WaypointMissionHeadingMode.AUTO);

            // Build the mission
            WaypointMission mission = builder.build();

            // Check if the mission is valid
            DJIError error = waypointMissionOperator.loadMission(mission);
            if (error == null) {
                waypointMissionOperator.uploadMission(djiError -> {
                    if (djiError == null) {
                        waypointMissionOperator.startMission(startError -> {
                            if (startError == null) {
                                // When mission completes, start taking photos
                                // This will be handled by the mission operator listener
                                // in the onExecutionFinish method

                                // Update UI
                                runOnUiThread(() -> {
                                    if (btnPauseMission != null) btnPauseMission.setEnabled(true);
                                    if (btnStopMission != null) btnStopMission.setEnabled(true);
                                    if (btnStartMission != null) btnStartMission.setEnabled(false);
                                    if (btnResumeMission != null) btnResumeMission.setEnabled(false);
                                });
                            } else {
                                showToast("Failed to start mission: " + startError.getDescription());
                            }
                        });
                    } else {
                        showToast("Failed to upload mission: " + djiError.getDescription());
                    }
                });
            } else {
                showToast("Invalid mission: " + error.getDescription());
            }
        } else {
            // Simplified version if flight controller state is not available
            // (useful for testing without a drone)
            WaypointMission.Builder builder = new WaypointMission.Builder();

            // Add waypoint at the inspection point, at a safe altitude
            Waypoint waypoint = new Waypoint(
                    point.getLatitude(),
                    point.getLongitude(),
                    (float) point.getSafeAltitude()
            );

            // Add the waypoint to the mission
            builder.addWaypoint(waypoint);

            // Configure the mission
            builder.waypointCount(1)
                    .autoFlightSpeed(5f)
                    .maxFlightSpeed(10f)
                    .setExitMissionOnRCSignalLostEnabled(true)
                    .finishedAction(WaypointMissionFinishedAction.NO_ACTION)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                    .headingMode(WaypointMissionHeadingMode.AUTO);

            // Build the mission
            WaypointMission mission = builder.build();

            // Check if the mission is valid
            DJIError error = waypointMissionOperator.loadMission(mission);
            if (error == null) {
                waypointMissionOperator.uploadMission(djiError -> {
                    if (djiError == null) {
                        waypointMissionOperator.startMission(startError -> {
                            if (startError == null) {
                                // Update UI
                                runOnUiThread(() -> {
                                    if (btnPauseMission != null) btnPauseMission.setEnabled(true);
                                    if (btnStopMission != null) btnStopMission.setEnabled(true);
                                    if (btnStartMission != null) btnStartMission.setEnabled(false);
                                    if (btnResumeMission != null) btnResumeMission.setEnabled(false);
                                });
                            } else {
                                showToast("Failed to start mission: " + startError.getDescription());
                            }
                        });
                    } else {
                        showToast("Failed to upload mission: " + djiError.getDescription());
                    }
                });
            } else {
                showToast("Invalid mission: " + error.getDescription());
            }
        }
    }

    private void startPhotoSequence() {
        currentPhotoPositionIndex = 0;
        takePhotoAtPosition(currentPhotoPositionIndex);
    }

    private void takePhotoAtPosition(int posIndex) {
        if (posIndex >= photoPositions.size()) {
            // All photos for this inspection point are completed
            completedInspectionPoint();
            return;
        }

        InspectionPoint inspectionPoint = inspectionPoints.get(currentInspectionPointIndex);
        PhotoPosition photoPosition = photoPositions.get(posIndex);

        updateStatus("Taking photo " + (posIndex + 1) + "/" + photoPositions.size() +
                " at inspection point " + (currentInspectionPointIndex + 1) + "/" +
                inspectionPoints.size());

        // Calculate absolute position from relative position
        double latitude = calculateLatitude(
                inspectionPoint.getLatitude(),
                photoPosition.getRelativeY()
        );

        double longitude = calculateLongitude(
                inspectionPoint.getLatitude(),
                inspectionPoint.getLongitude(),
                photoPosition.getRelativeX()
        );

        double altitude = inspectionPoint.getGroundAltitude() +
                inspectionPoint.getStructureHeight() +
                photoPosition.getRelativeZ();

        // Create a waypoint mission to navigate to the photo position
        WaypointMission.Builder builder = new WaypointMission.Builder();

        // Add waypoint at the photo position
        Waypoint waypoint = new Waypoint(
                latitude,
                longitude,
                (float) altitude
        );

        // Add waypoint action to rotate gimbal to the desired angle
        waypoint.addAction(new WaypointAction(
                WaypointActionType.GIMBAL_PITCH,
                (int) photoPosition.getCameraPitch()
        ));

        // Em vez de adicionar uma ação para tirar foto, vamos tirar a foto manualmente
        // para poder mostrar a pré-visualização e permitir a aprovação

        // Add the waypoint to the mission
        builder.addWaypoint(waypoint);

        // Configure the mission
        builder.waypointCount(1)
                .autoFlightSpeed(2f)  // Slower speed for precision
                .maxFlightSpeed(5f)
                .setExitMissionOnRCSignalLostEnabled(true)
                .finishedAction(WaypointMissionFinishedAction.NO_ACTION)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                .headingMode(WaypointMissionHeadingMode.USING_WAYPOINT_HEADING);

        // Build the mission
        WaypointMission mission = builder.build();

        // Check if the mission is valid
        DJIError error = waypointMissionOperator.loadMission(mission);
        if (error == null) {
            isWaitingForPhotoApproval = true;

            waypointMissionOperator.uploadMission(djiError -> {
                if (djiError == null) {
                    waypointMissionOperator.startMission(startError -> {
                        if (startError == null) {
                            // Após chegar à posição, tirar a foto manualmente
                            // Adicionamos um atraso pequeno para garantir que o drone está estável
                            new Handler().postDelayed(() -> capturePhoto(), 2000); // Atraso de 2 segundos
                        } else {
                            showToast("Failed to start photo mission: " + startError.getDescription());
                            isWaitingForPhotoApproval = false;
                        }
                    });
                } else {
                    showToast("Failed to upload photo mission: " + djiError.getDescription());
                    isWaitingForPhotoApproval = false;
                }
            });
        } else {
            showToast("Invalid photo mission: " + error.getDescription());
            isWaitingForPhotoApproval = false;
        }
    }

    /* Metodo para simular captura de foto para testes*/
    /**
     * Captura uma foto real usando a câmera do drone
     */
    private void capturePhoto() {
        // Verificar se a câmera está disponível
        if (camera == null) {
            showToast("Camera is not available");
            return;
        }

        // Garantir que a câmera está no modo de fotografia
        camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, error -> {
            if (error != null) {
                showToast("Failed to set camera mode: " + error.getDescription());
                return;
            }

            // Configurar o modo de fotografia (tiro único)
            camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.SINGLE, error1 -> {
                if (error1 != null) {
                    showToast("Failed to set shoot photo mode: " + error1.getDescription());
                    return;
                }

                // Capturar a foto
                camera.startShootPhoto(error2 -> {
                    if (error2 != null) {
                        showToast("Failed to shoot photo: " + error2.getDescription());
                        return;
                    }

                    showToast("Photo captured successfully");

                    // A câmera enviará uma notificação quando a foto for tirada
                    // A imagem será enviada via callback que foi configurado anteriormente
                    // no metodo updateDroneConnectionStatus(), se você implementou o callback.

                    // Se você não implementou o callback, pode adicionar aqui:
                    camera.setMediaFileCallback(mediaFile -> {
                        if (isWaitingForPhotoApproval) {
                            handlePhotoTaken(mediaFile);
                        }
                    });
                });
            });
        });
    }

    /**
     * Processa a foto capturada
     */
    private void handlePhotoTaken(MediaFile mediaFile) {
        if (mediaFile != null) {
            // Obter a miniatura da foto para exibição
            mediaFile.fetchThumbnail(mediaData -> {
                if (mediaData != null) {
                    runOnUiThread(() -> {
                        // Exibir a miniatura para aprovação do usuário
                        if (ivPreview != null) ivPreview.setImageBitmap(mediaData);
                        setPhotoApprovalButtonsEnabled(true);
                    });
                } else {
                    // Se não conseguir obter a miniatura, permite simplesmente aprovar/rejeitar
                    runOnUiThread(() -> {
                        showToast("Could not fetch photo thumbnail");
                        setPhotoApprovalButtonsEnabled(true);
                    });
                }
            });
        } else {
            runOnUiThread(() -> {
                showToast("No media file received");
                setPhotoApprovalButtonsEnabled(true);
            });
        }
    }

    private void acceptPhoto() {
        isWaitingForPhotoApproval = false;
        setPhotoApprovalButtonsEnabled(false);

        // Mark the current photo position as captured
        photoPositions.get(currentPhotoPositionIndex).setCaptured(true);

        // Move to the next photo position
        currentPhotoPositionIndex++;
        takePhotoAtPosition(currentPhotoPositionIndex);
    }

    private void retakePhoto() {
        isWaitingForPhotoApproval = false;
        setPhotoApprovalButtonsEnabled(false);

        // Retake the current photo
        takePhotoAtPosition(currentPhotoPositionIndex);
    }

    private void completedInspectionPoint() {
        // Mark the current inspection point as completed
        inspectionPoints.get(currentInspectionPointIndex).setCompleted(true);

        // Move to the next inspection point
        currentInspectionPointIndex++;

        if (currentInspectionPointIndex < inspectionPoints.size()) {
            // Navigate to the next inspection point
            navigateToInspectionPoint(currentInspectionPointIndex);
        } else {
            // All inspection points completed
            showToast("All inspection points completed");
            updateStatus("Inspection mission completed");

            // Reset mission state
            resetMission();
        }
    }

    private void resetMission() {
        runOnUiThread(() -> {
            if (btnStartMission != null) btnStartMission.setEnabled(true);
            if (btnPauseMission != null) btnPauseMission.setEnabled(false);
            if (btnResumeMission != null) btnResumeMission.setEnabled(false);
            if (btnStopMission != null) btnStopMission.setEnabled(false);
            setPhotoApprovalButtonsEnabled(false);
        });
    }

    private double calculateLatitude(double baseLatitude, double offsetMetersNorth) {
        // One degree of latitude is approximately 111,111 meters
        return baseLatitude + (offsetMetersNorth / 111111);
    }

    private double calculateLongitude(double baseLatitude, double baseLongitude, double offsetMetersEast) {
        // One degree of longitude varies with latitude
        double metersPerDegree = 111111 * Math.cos(Math.toRadians(baseLatitude));
        return baseLongitude + (offsetMetersEast / metersPerDegree);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();

        if (id == R.id.btn_load_inspection_points) {
            // Lança o seletor de ficheiros para o utilizador escolher o CSV
            inspectionPointsLauncher.launch("*/*");
        } else if (id == R.id.btn_load_photo_positions) {
            // Lança o seletor de ficheiros para o utilizador escolher o CSV
            photoPositionsLauncher.launch("*/*");
        } else if (id == R.id.btn_start_mission) {
            startInspectionMission();
        } else if (id == R.id.btn_pause_mission) {
            pauseMission();
        } else if (id == R.id.btn_resume_mission) {
            resumeMission();
        } else if (id == R.id.btn_stop_mission) {
            stopMission();
        } else if (id == R.id.btn_accept_photo) {
            acceptPhoto();
        } else if (id == R.id.btn_retake_photo) {
            retakePhoto();
        }
    }

    private void pauseMission() {
        if (waypointMissionOperator != null) {
            waypointMissionOperator.pauseMission(error -> {
                if (error == null) {
                    updateStatus("Mission paused");
                    runOnUiThread(() -> {
                        if (btnPauseMission != null) btnPauseMission.setEnabled(false);
                        if (btnResumeMission != null) btnResumeMission.setEnabled(true);
                    });
                } else {
                    showToast("Failed to pause mission: " + error.getDescription());
                }
            });
        }
    }

    private void resumeMission() {
        if (waypointMissionOperator != null) {
            waypointMissionOperator.resumeMission(error -> {
                if (error == null) {
                    updateStatus("Mission resumed");
                    runOnUiThread(() -> {
                        if (btnPauseMission != null) btnPauseMission.setEnabled(true);
                        if (btnResumeMission != null) btnResumeMission.setEnabled(false);
                    });
                } else {
                    showToast("Failed to resume mission: " + error.getDescription());
                }
            });
        }
    }

    private void stopMission() {
        if (waypointMissionOperator != null) {
            waypointMissionOperator.stopMission(error -> {
                if (error == null) {
                    updateStatus("Mission stopped");
                    isWaitingForPhotoApproval = false;
                    resetMission();
                } else {
                    showToast("Failed to stop mission: " + error.getDescription());
                }
            });
        }
    }

    private void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(InspectionMissionActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private void updateStatus(final String status) {
        runOnUiThread(() -> {
            if (tvStatus != null) tvStatus.setText(status);
        });
        Log.d(TAG, status);
    }

    @Override
    protected void onDestroy() {
        // Unregister broadcast receiver
        if (receiver != null) {
            unregisterReceiver(receiver);
        }

        // Remove mission operator listener
        if (waypointMissionOperator != null) {
            waypointMissionOperator.removeAllListeners();
        }

        super.onDestroy();
    }
}
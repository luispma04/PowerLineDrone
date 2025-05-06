package com.dji.sdk.sample.droneInspection;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import dji.sdk.base.BaseProduct;

/**
 * ConnectionActivity checks the drone connection status and allows users to open the app only when
 * the drone is connected properly.
 */
public class ConnectionActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = ConnectionActivity.class.getName();
    private static final int REQUEST_PERMISSION_CODE = 12345;

    private TextView tvConnectionStatus;
    private Button btnOpen;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        initUI();

        // Check and request permissions
        checkAndRequestPermissions();

        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(DroneInspectionApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onDestroy() {
        // Unregister broadcast receiver
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        super.onDestroy();
    }

    private void initUI() {
        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        btnOpen = findViewById(R.id.btn_open);

        btnOpen.setOnClickListener(this);
        btnOpen.setEnabled(false);
    }

    private void checkAndRequestPermissions() {
        List<String> missingPermissions = new ArrayList<>();

        // Check all required permissions
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        // Request missing permissions
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[missingPermissions.size()]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ permissions
            return new String[]{
                    Manifest.permission.VIBRATE,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE,
            };
        } else {
            // Android 9 and below permissions
            return new String[]{
                    Manifest.permission.VIBRATE,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE,
            };
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CODE) {
            boolean allPermissionsGranted = true;

            // Check if all permissions were granted
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (!allPermissionsGranted) {
                // At least one permission was not granted
                showToast("Please grant all permissions to use this app");
            }
        }
    }

    /**
     * Broadcast Receiver for receiving the product connection status
     */
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshSDKRelativeUI();
        }
    };

    private void refreshSDKRelativeUI() {
        BaseProduct product = DroneInspectionApplication.getProductInstance();

        if (product != null && product.isConnected()) {
            tvConnectionStatus.setText("Status: Connected to " + product.getModel());
            btnOpen.setEnabled(true);
        } else {
            tvConnectionStatus.setText("Status: Disconnected");
            btnOpen.setEnabled(false);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_open) {
            Intent intent = new Intent(this, InspectionMissionActivity.class);
            startActivity(intent);
        }
    }

    private void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(ConnectionActivity.this, message, Toast.LENGTH_SHORT).show());
    }
}
package com.dji.sdk.sample.droneInspection;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.products.Aircraft;
import dji.sdk.products.HandHeld;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

/**
 * Main application class for initializing DJI SDK
 */
public class DroneInspectionApplication extends Application {

    private static final String TAG = DroneInspectionApplication.class.getName();
    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private static BaseProduct mProduct;
    private Handler mHandler;

    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {
        @Override
        public void onRegister(DJIError error) {
            if (error == DJISDKError.REGISTRATION_SUCCESS) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> {
                    Toast.makeText(getApplicationContext(), "Registration Success", Toast.LENGTH_LONG).show();
                });
                DJISDKManager.getInstance().startConnectionToProduct();
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> {
                    Toast.makeText(getApplicationContext(), "Registration Failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                });
            }
            Log.i(TAG, error.getDescription());
        }

        @Override
        public void onProductDisconnect() {
            Log.d(TAG, "onProductDisconnect");
            notifyStatusChange();
        }

        @Override
        public void onProductConnect(BaseProduct baseProduct) {
            Log.d(TAG, "onProductConnect newProduct: " + baseProduct);
            notifyStatusChange();
        }

        @Override
        public void onProductChanged(BaseProduct baseProduct) {
            Log.d(TAG, "onProductChanged newProduct: " + baseProduct);
            notifyStatusChange();
        }

        @Override
        public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent, BaseComponent newComponent) {
            if (newComponent != null) {
                newComponent.setComponentListener(isConnected -> {
                    Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
                    notifyStatusChange();
                });
            }
        }

        @Override
        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {
            // Do nothing
        }

        @Override
        public void onDatabaseDownloadProgress(long current, long total) {
            // Do nothing
        }
    };

    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }

    private Runnable updateRunnable = () -> {
        Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
        sendBroadcast(intent);
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());

        // Initialize DJI SDK when application starts
        registerApp();
    }

    /**
     * Register the app to DJI SDK
     */
    private void registerApp() {
        DJISDKManager.getInstance().registerApp(this, mDJISDKManagerCallback);
    }

    public static synchronized BaseProduct getProductInstance() {
        if (null == mProduct) {
            mProduct = DJISDKManager.getInstance().getProduct();
        }
        return mProduct;
    }

    public static synchronized Camera getCameraInstance() {
        if (getProductInstance() == null) return null;

        if (getProductInstance() instanceof Aircraft) {
            return ((Aircraft) getProductInstance()).getCamera();
        } else if (getProductInstance() instanceof HandHeld) {
            return ((HandHeld) getProductInstance()).getCamera();
        } else {
            return null;
        }
    }
}


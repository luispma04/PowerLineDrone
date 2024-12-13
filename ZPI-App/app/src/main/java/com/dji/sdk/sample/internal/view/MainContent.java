package com.dji.sdk.sample.internal.view;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dji.sdk.sample.BuildConfig;
import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.controller.MainActivity;
import com.dji.sdk.sample.internal.model.ViewWrapper;
import com.dji.sdk.sample.internal.utils.DialogUtils;
import com.dji.sdk.sample.internal.utils.GeneralUtils;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.LEDsSettings;
import dji.common.realname.AppActivationState;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.keysdk.DJIKey;
import dji.keysdk.KeyManager;
import dji.keysdk.ProductKey;
import dji.keysdk.callback.KeyListener;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.realname.AppActivationManager;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LDMModule;
import dji.sdk.sdkmanager.LDMModuleType;
import dji.sdk.useraccount.UserAccountManager;

import org.opencv.android.OpenCVLoader;

/**
 * Created by dji on 15/12/18.
 */
public class MainContent extends RelativeLayout {

    private static final boolean DEBUG = BuildConfig.DEBUG_ZPI;

    public static final String TAG = MainContent.class.getName();
    private String[] permissionArrays;
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private int lastProcess = -1;
    private Handler mHander = new Handler();
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {

        @Override
        public void onConnectivityChange(boolean isConnected) {
            Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
            notifyStatusChange();
        }
    };
    private ProgressBar progressBar;
    // private static BluetoothProductConnector connector = null;
    private TextView mTextConnectionStatus;
    private TextView mTextProduct;
    private TextView mTextModelAvailable;
    private Button mBtnRegisterApp;
    private Button mBtnOpen;
    private ImageView imageView;
    private ViewWrapper componentList =
            new ViewWrapper(new DemoListView(getContext()), R.string.activity_component_list);
    private ViewWrapper bluetoothView;
    private Handler mHandler;
    private Handler mHandlerUI;
    private HandlerThread mHandlerThread = new HandlerThread("Bluetooth");

    private BaseProduct mProduct;
    private DJIKey firmwareKey;
    private KeyListener firmwareVersionUpdater;
    private boolean hasStartedFirmVersionListener = false;
    private AtomicBoolean hasAppActivationListenerStarted = new AtomicBoolean(false);
    private static final int MSG_UPDATE_BLUETOOTH_CONNECTOR = 0;
    private static final int MSG_INFORM_ACTIVATION = 1;
    private static final int ACTIVATION_DALAY_TIME = 3000;
    private AppActivationState.AppActivationStateListener appActivationStateListener;
    private boolean isregisterForLDM = false;
    private Context mContext;
    private FlightController flightController;

    public MainContent(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionArrays = new String[]{
                    Manifest.permission.VIBRATE, // Gimbal rotation
                    Manifest.permission.INTERNET, // API requests
                    Manifest.permission.ACCESS_WIFI_STATE, // WIFI connected products
                    Manifest.permission.ACCESS_COARSE_LOCATION, // Maps
                    Manifest.permission.ACCESS_NETWORK_STATE, // WIFI connected products
                    Manifest.permission.ACCESS_FINE_LOCATION, // Maps
                    Manifest.permission.CHANGE_WIFI_STATE, // Changing between WIFI and USB connection
                    Manifest.permission.BLUETOOTH, // Bluetooth connected products
                    Manifest.permission.BLUETOOTH_ADMIN, // Bluetooth connected products
                    Manifest.permission.READ_PHONE_STATE, // Device UUID accessed upon registration
                    Manifest.permission.RECORD_AUDIO,// Speaker accessory
            };
        } else {//兼容Android 12
            permissionArrays = new String[]{
                    Manifest.permission.VIBRATE, // Gimbal rotation
                    Manifest.permission.INTERNET, // API requests
                    Manifest.permission.ACCESS_WIFI_STATE, // WIFI connected products
                    Manifest.permission.ACCESS_COARSE_LOCATION, // Maps
                    Manifest.permission.ACCESS_NETWORK_STATE, // WIFI connected products
                    Manifest.permission.ACCESS_FINE_LOCATION, // Maps
                    Manifest.permission.CHANGE_WIFI_STATE, // Changing between WIFI and USB connection
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, // Log files
                    Manifest.permission.BLUETOOTH, // Bluetooth connected products
                    Manifest.permission.BLUETOOTH_ADMIN, // Bluetooth connected products
                    Manifest.permission.READ_EXTERNAL_STORAGE, // Log files
                    Manifest.permission.READ_PHONE_STATE, // Device UUID accessed upon registration
                    Manifest.permission.RECORD_AUDIO // Speaker accessory
            };
        }
    }

    public void enableButton(){
        mBtnRegisterApp.setEnabled(true);
        mBtnOpen.setEnabled(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (isInEditMode()) {
            return;
        }
        DJISampleApplication.getEventBus().register(this);
        initUI();
    }

    private void initUI() {
        Log.v(TAG, "initUI");

        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mTextConnectionStatus = (TextView) findViewById(R.id.text_connection_status);
        mTextModelAvailable = (TextView) findViewById(R.id.text_model_available);
        mTextProduct = (TextView) findViewById(R.id.text_product_info);
        mBtnRegisterApp = (Button) findViewById(R.id.btn_registerApp);
        mBtnOpen = (Button) findViewById(R.id.btn_open);
        imageView = findViewById(R.id.openListImageView);

        //mBtnBluetooth.setEnabled(false);


        mBtnRegisterApp.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                isregisterForLDM = false;
                checkAndRequestPermissions();
            }
        });
        mBtnOpen.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (GeneralUtils.isFastDoubleClick()) {
                    return;
                }
                // DJISampleApplication.getEventBus().post(componentList);
                ViewWrapper fullScreenVideoViewWrapper =
                        new ViewWrapper(new FullScreenVideoViewZPI(getContext(),MainContent.this), R.string.component_fullscreen_video_view_zpi);
                DJISampleApplication.getEventBus().post(fullScreenVideoViewWrapper);
                mBtnRegisterApp.setEnabled(false);
                mBtnOpen.setEnabled(false);
            }
        });
        ((TextView) findViewById(R.id.text_version)).setText(getResources().getString(R.string.sdk_version,
                DJISDKManager.getInstance().getRegistrationSDKVersion() /*
                        + " Debug:"
                        + GlobalConfig.DEBUG*/));

        if (DEBUG) {
            imageView.setVisibility(VISIBLE);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DJISampleApplication.getEventBus().post(new MainActivity.RequestPortrait());
                    DJISampleApplication.getEventBus().post(componentList);
                }
            });
        }

        mBtnRegisterApp.performClick();
    }

    @Override
    protected void onAttachedToWindow() {
        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed");
        } else {
            Log.d(TAG, "OpenCV initialization succeeded");
        }

        Log.d(TAG, "Comes into the onAttachedToWindow");
        if (!isInEditMode()) {
            refreshSDKRelativeUI();
            turnOffLed();
            mHandlerThread.start();
            final long currentTime = System.currentTimeMillis();
            mHandler = new Handler(mHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MSG_UPDATE_BLUETOOTH_CONNECTOR:
                            //connected = DJISampleApplication.getBluetoothConnectStatus();
                            /*connector = DJISampleApplication.getBluetoothProductConnector();

                            if (connector != null) {
                                mBtnBluetooth.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mBtnBluetooth.setEnabled(true);
                                    }
                                });
                                return;
                            } else */
                            if ((System.currentTimeMillis() - currentTime) >= 5000) {
                                DialogUtils.showDialog(getContext(),
                                        "Fetch Connector failed, reboot if you want to connect the Bluetooth");
                                return;
                            } else /*if (connector == null)*/ {
                                sendDelayMsg(0, MSG_UPDATE_BLUETOOTH_CONNECTOR);
                            }
                            break;
                        case MSG_INFORM_ACTIVATION:
                            loginToActivationIfNeeded();
                            break;
                    }
                }
            };
            mHandlerUI = new Handler(Looper.getMainLooper());
        }


        super.onAttachedToWindow();
    }


    private void turnOffLed() {
        if (DJISDKManager.getInstance() != null && flightController == null) {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product != null) {
                if (product instanceof Aircraft) {
                    flightController = ((Aircraft) product).getFlightController();
                }
            }
        }
        LEDsSettings ledsSettings = new LEDsSettings.Builder().frontLEDsOn(false).build();
        if (flightController != null) {
            flightController.setLEDsEnabledSettings(ledsSettings, null);
        }
    }

    private void sendDelayMsg(int msg, long delayMillis) {
        if (mHandler == null) {
            return;
        }

        if (!mHandler.hasMessages(msg)) {
            mHandler.sendEmptyMessageDelayed(msg, delayMillis);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            removeFirmwareVersionListener();
            removeAppActivationListenerIfNeeded();
            mHandler.removeCallbacksAndMessages(null);
            mHandlerUI.removeCallbacksAndMessages(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mHandlerThread.quitSafely();
            } else {
                mHandlerThread.quit();
            }
            mHandlerUI = null;
            mHandler = null;
        }
        super.onDetachedFromWindow();
    }

    private void updateVersion() {
        String version = null;
        if (mProduct != null) {
            version = mProduct.getFirmwarePackageVersion();
        }

        if (TextUtils.isEmpty(version)) {
            mTextModelAvailable.setText("Firmware version:N/A"); //Firmware version:
        } else {
            mTextModelAvailable.setText("Firmware version:" + version); //"Firmware version: " +
            removeFirmwareVersionListener();
        }
    }

    @Subscribe
    public void onConnectivityChange(MainActivity.ConnectivityChangeEvent event) {
        if (mHandlerUI != null) {
            mHandlerUI.post(new Runnable() {
                @Override
                public void run() {
                    refreshSDKRelativeUI();
                }
            });
        }
    }

    private void refreshSDKRelativeUI() {
        mProduct = DJISampleApplication.getProductInstance();
        mBtnRegisterApp.setEnabled(true);
        Log.d(TAG, "mProduct: " + (mProduct == null ? "null" : "unnull"));
        if (null != mProduct) {
            if (mProduct.isConnected()) {
                mBtnOpen.setEnabled(true);
                String str = mProduct instanceof Aircraft ? "DJIAircraft" : "DJIHandHeld";
                mTextConnectionStatus.setText("Status: " + str + " connected");
                tryUpdateFirmwareVersionWithListener();
                if (mProduct instanceof Aircraft) {
                    addAppActivationListenerIfNeeded();
                }

                if (null != mProduct.getModel()) {
                    mTextProduct.setText("" + mProduct.getModel().getDisplayName());
                } else {
                    mTextProduct.setText(R.string.product_information);
                }
            } else if (mProduct instanceof Aircraft) {
                Aircraft aircraft = (Aircraft) mProduct;
                if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                    mTextConnectionStatus.setText(R.string.connection_only_rc);
                    mTextProduct.setText(R.string.product_information);
                    mBtnOpen.setEnabled(false);
                    mTextModelAvailable.setText("Firmware version:N/A");
                }
            }
        } else {
            mBtnOpen.setEnabled(DEBUG);
            mTextProduct.setText(R.string.product_information);
            mTextConnectionStatus.setText(R.string.connection_loose);
            mTextModelAvailable.setText("Firmware version:N/A");
        }
    }

    private void tryUpdateFirmwareVersionWithListener() {
        if (!hasStartedFirmVersionListener) {
            firmwareVersionUpdater = new KeyListener() {
                @Override
                public void onValueChange(final Object o, final Object o1) {
                    mHandlerUI.post(new Runnable() {
                        @Override
                        public void run() {
                            updateVersion();
                        }
                    });
                }
            };
            firmwareKey = ProductKey.create(ProductKey.FIRMWARE_PACKAGE_VERSION);
            if (KeyManager.getInstance() != null) {
                KeyManager.getInstance().addListener(firmwareKey, firmwareVersionUpdater);
            }
            hasStartedFirmVersionListener = true;
        }
        updateVersion();
    }

    private void removeFirmwareVersionListener() {
        if (hasStartedFirmVersionListener) {
            if (KeyManager.getInstance() != null) {
                KeyManager.getInstance().removeListener(firmwareVersionUpdater);
            }
        }
        hasStartedFirmVersionListener = false;
    }

    private void addAppActivationListenerIfNeeded() {
        if (AppActivationManager.getInstance().getAppActivationState() != AppActivationState.ACTIVATED) {
            sendDelayMsg(MSG_INFORM_ACTIVATION, ACTIVATION_DALAY_TIME);
            if (hasAppActivationListenerStarted.compareAndSet(false, true)) {
                appActivationStateListener = new AppActivationState.AppActivationStateListener() {

                    @Override
                    public void onUpdate(AppActivationState appActivationState) {
                        if (mHandler != null && mHandler.hasMessages(MSG_INFORM_ACTIVATION)) {
                            mHandler.removeMessages(MSG_INFORM_ACTIVATION);
                        }
                        if (appActivationState != AppActivationState.ACTIVATED) {
                            sendDelayMsg(MSG_INFORM_ACTIVATION, ACTIVATION_DALAY_TIME);
                        }
                    }
                };
                AppActivationManager.getInstance().addAppActivationStateListener(appActivationStateListener);
            }
        }
    }

    private void removeAppActivationListenerIfNeeded() {
        if (hasAppActivationListenerStarted.compareAndSet(true, false)) {
            AppActivationManager.getInstance().removeAppActivationStateListener(appActivationStateListener);
        }
    }

    private void loginToActivationIfNeeded() {
        if (AppActivationManager.getInstance().getAppActivationState() == AppActivationState.LOGIN_REQUIRED) {
            UserAccountManager.getInstance()
                    .logIntoDJIUserAccount(getContext(),
                            new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                                @Override
                                public void onSuccess(UserAccountState userAccountState) {
                                    ToastUtils.setResultToToast("Login Successed!");
                                }

                                @Override
                                public void onFailure(DJIError djiError) {
                                    ToastUtils.setResultToToast("Login Failed!");
                                }
                            });
        }
    }

    //region Registration n' Permissions Helpers

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        List<String> missingPermission = new ArrayList<>();
        for (String eachPermission : permissionArrays) {
            if (ContextCompat.checkSelfPermission(mContext, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions((Activity) mContext,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    ToastUtils.setResultToToast(mContext.getString(R.string.sdk_registration_doing_message_zpi));
                    //if we hope the Firmware Upgrade module could access the network under LDM mode, we need call the setModuleNetworkServiceEnabled()
                    //method before the registerAppForLDM() method
                    /*if (mCheckboxFirmware.isChecked()) {
                        DJISDKManager.getInstance().getLDMManager().setModuleNetworkServiceEnabled(new LDMModule.Builder().moduleType(
                                LDMModuleType.FIRMWARE_UPGRADE).enabled(true).build());
                    } else {*/
                    DJISDKManager.getInstance().getLDMManager().setModuleNetworkServiceEnabled(new LDMModule.Builder().moduleType(
                            LDMModuleType.FIRMWARE_UPGRADE).enabled(false).build());
                    // }
                    if (isregisterForLDM) {
                        DJISDKManager.getInstance().registerAppForLDM(mContext.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                            @Override
                            public void onRegister(DJIError djiError) {
                                if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                    DJILog.e("App registration for LDM", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                    DJISDKManager.getInstance().startConnectionToProduct();
                                    ToastUtils.setResultToToast(mContext.getString(R.string.sdk_registration_success_message));
                                } else {
                                    ToastUtils.setResultToToast(mContext.getString(R.string.sdk_registration_message) + djiError.getDescription());
                                }
                                Log.v(TAG, djiError.getDescription());
                                hideProcess();
                            }

                            @Override
                            public void onProductDisconnect() {
                                Log.d(TAG, "onProductDisconnect");
                                notifyStatusChange();
                            }

                            @Override
                            public void onProductConnect(BaseProduct baseProduct) {
                                Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                                notifyStatusChange();
                            }

                            @Override
                            public void onProductChanged(BaseProduct baseProduct) {
                                notifyStatusChange();
                            }

                            @Override
                            public void onComponentChange(BaseProduct.ComponentKey componentKey,
                                                          BaseComponent oldComponent,
                                                          BaseComponent newComponent) {
                                if (newComponent != null) {
                                    newComponent.setComponentListener(mDJIComponentListener);

                                    /*if (componentKey == BaseProduct.ComponentKey.FLIGHT_CONTROLLER) {
                                        showDBVersion();
                                    }*/
                                }
                                Log.d(TAG,
                                        String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                                componentKey,
                                                oldComponent,
                                                newComponent));

                                notifyStatusChange();
                            }

                            @Override
                            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                            }

                            @Override
                            public void onDatabaseDownloadProgress(long current, long total) {
                                int process = (int) (100 * current / total);
                                if (process == lastProcess) {
                                    return;
                                }
                                lastProcess = process;
                                showProgress(process);
                                if (process % 25 == 0) {
                                    ToastUtils.setResultToToast("DB load process : " + process);
                                } else if (process == 0) {
                                    ToastUtils.setResultToToast("DB load begin");
                                }
                            }
                        });

                    } else {
                        DJISDKManager.getInstance().registerApp(mContext.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                            @Override
                            public void onRegister(DJIError djiError) {
                                if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                    DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                    DJISDKManager.getInstance().startConnectionToProduct();
                                    ToastUtils.setResultToToast(mContext.getString(R.string.sdk_registration_success_message));
                                } else {
                                    ToastUtils.setResultToToast(mContext.getString(R.string.sdk_registration_message) + djiError.getDescription());
                                }
                                Log.v(TAG, djiError.getDescription());
                                hideProcess();
                            }

                            @Override
                            public void onProductDisconnect() {
                                Log.d(TAG, "onProductDisconnect");
                                notifyStatusChange();
                            }

                            @Override
                            public void onProductConnect(BaseProduct baseProduct) {
                                Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                                notifyStatusChange();
                            }

                            @Override
                            public void onProductChanged(BaseProduct baseProduct) {
                                notifyStatusChange();
                            }

                            @Override
                            public void onComponentChange(BaseProduct.ComponentKey componentKey,
                                                          BaseComponent oldComponent,
                                                          BaseComponent newComponent) {
                                if (newComponent != null) {
                                    newComponent.setComponentListener(mDJIComponentListener);

                                   /* if (componentKey == BaseProduct.ComponentKey.FLIGHT_CONTROLLER) {
                                        showDBVersion();
                                    }*/
                                }
                                Log.d(TAG,
                                        String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                                componentKey,
                                                oldComponent,
                                                newComponent));

                                notifyStatusChange();
                            }

                            @Override
                            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                            }

                            @Override
                            public void onDatabaseDownloadProgress(long current, long total) {
                                int process = (int) (100 * current / total);
                                if (process == lastProcess) {
                                    return;
                                }
                                lastProcess = process;
                                showProgress(process);
                                if (process % 25 == 0) {
                                    ToastUtils.setResultToToast("DB load process : " + process);
                                } else if (process == 0) {
                                    ToastUtils.setResultToToast("DB load begin");
                                }
                            }
                        });

                    }
                }
            });
        }
    }

    private void showProgress(final int process) {
        mHander.post(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(process);
            }
        });
    }

    private void showDBVersion() {
        mHander.postDelayed(new Runnable() {
            @Override
            public void run() {
                DJISDKManager.getInstance().getFlyZoneManager().getPreciseDatabaseVersion(new CommonCallbacks.CompletionCallbackWith<String>() {
                    @Override
                    public void onSuccess(String s) {
                        ToastUtils.setResultToToast("db load success ! version : " + s);
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        ToastUtils.setResultToToast("db load failure ! get version error : " + djiError.getDescription());

                    }
                });
            }
        }, 3000);
    }

    private void hideProcess() {
        mHander.post(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void notifyStatusChange() {
        DJISampleApplication.getEventBus().post(new MainActivity.ConnectivityChangeEvent());
    }
    //endregion
}
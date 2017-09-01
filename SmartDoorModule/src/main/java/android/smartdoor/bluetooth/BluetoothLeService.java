package android.smartdoor.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BluetoothLeService extends Service {

    /**
     * Serwis z charakterystyką
     */
    private final static String SERVICE_UUID = "000018f1-0000-1000-8000-00805f9b34fb";

    /*
    * Charakterystyka do zapisu
    * */
    private final static String CHARACTERISTIC_UUID = "000000F1-0000-1000-8000-00805f9b34fb";

    /*
    * Bluetooth timeout
    * */
    private final static long CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(20);

    private final Handler logHandler = new Handler(Looper.getMainLooper());
    private final Handler timeoutHandler = new Handler();
    private final IBinder iBinder = new LocalBinder();

    private BluetoothListener bluetoothListener;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private String key;

    public boolean initialize() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }

        BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager == null) {
            return false;
        }

        bluetoothAdapter = mBluetoothManager.getAdapter();
        return !(bluetoothAdapter == null || !bluetoothAdapter.isEnabled());
    }

    public void onConnectGATT(final String address, String pKey) {
        createConnectionTimeout();
        log("Connecting to device: " + address);

        bluetoothListener.onConnectDevice();
        key = pKey;

        if (bluetoothGatt != null) {
            if (bluetoothGatt.connect()) {
                return;
            }
        }

        bluetoothGatt = getBleDevice(address).connectGatt(this, true, this.mBluetoothGattCallback);
    }

    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            super.onConnectionStateChange(bluetoothGatt, status, newState);
            log("onConnectionStateChange: " + getGattStatus(status));
            log("onConnectionStateChange: " + getNewStateName(newState));
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                onConnected();
                bluetoothGatt.discoverServices();
            } else {
                disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            super.onServicesDiscovered(bluetoothGatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Serwis wykryty: " + getGattStatus(status));

                BluetoothGattService mBluetoothGattService = bluetoothGatt.getService(UUID.fromString(SERVICE_UUID));
                BluetoothGattCharacteristic mCharacteristic = mBluetoothGattService.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                mCharacteristic.setValue(hexStringToByteArray(key));
                bluetoothGatt.writeCharacteristic(mCharacteristic);
            } else {
                log("Błąd wykrywania serwisów: " + getGattStatus(status));
                //  onError("Błąd wykrywania serwisów: " + getGattStatus(status));
                //TODO disconnect?
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(bluetoothGatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Klucz wysłany: " + getGattStatus(status));
                onKeySend();
            } else {
                log("Błąd wysyłania klucza: " + getGattStatus(status));
                //    onError("Błąd wysyłania klucza: " + getGattStatus(status));
            }
            disconnect();
        }
    };

    private void onConnected() {
        logHandler.post(new Runnable() {
            @Override
            public void run() {
                if (BluetoothLeService.this.bluetoothListener != null) {
                    BluetoothLeService.this.bluetoothListener.onConnectGATT();
                }
            }
        });
    }

    private void onKeySend() {
        timeoutHandler.removeCallbacksAndMessages(null);
        logHandler.post(new Runnable() {
            @Override
            public void run() {
                if (BluetoothLeService.this.bluetoothListener != null) {
                    BluetoothLeService.this.bluetoothListener.onSendKeySuccess();
                }
            }
        });
    }

    private void onError(final String pError) {
        logHandler.post(new Runnable() {
            @Override
            public void run() {
                if (BluetoothLeService.this.bluetoothListener != null) {
                    BluetoothLeService.this.bluetoothListener.onError(pError);
                }
            }
        });
        disconnect();
    }

    private void log(final String pMessage) {
        logHandler.post(new Runnable() {
            @Override
            public void run() {
                if (BluetoothLeService.this.bluetoothListener != null) {
                    BluetoothLeService.this.bluetoothListener.onLog(pMessage);
                }
            }
        });
    }

    private void createConnectionTimeout() {
        timeoutHandler.removeCallbacksAndMessages(null);
        timeoutHandler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT);
    }

    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            bluetoothListener.onForceDisconnect("FAILED\nTIMEOUT");
            disconnect();
        }
    };

    public void setBluetoothListener(BluetoothListener pBluetoothListener) {
        bluetoothListener = pBluetoothListener;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public BluetoothDevice getBleDevice(String address) {
        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
        getDeviceType(bluetoothDevice.getType());
        return bluetoothDevice;
    }

    private byte[] hexStringToByteArray(String value) {
        byte result[] = new byte[value.length() / 2];
        char enc[] = value.toCharArray();
        for (int i = 0; i < enc.length; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(String.valueOf(enc[i]) + enc[i + 1], 16);
        }
        return result;
    }

    public String getNewStateName(int newState) {
        String status;
        switch (newState) {
            case BluetoothProfile.STATE_CONNECTED:
                status = "STATE_CONNECTED";
                break;
            case BluetoothProfile.STATE_CONNECTING:
                status = "STATE_CONNECTING";
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                status = "STATE_DISCONNECTED";
                break;
            case BluetoothProfile.STATE_DISCONNECTING:
                status = "STATE_DISCONNECTING";
                break;
            default:
                status = "STATE_UNKNOWN";
                break;
        }
        return status + "(" + newState + ")";
    }

    /**
     * Bluetooth Basic Rate/Enhanced Data Rate (BR/EDR) is typically used for relatively short-range, continuous wireless connection such as streaming audio to headsets.
     * <p>
     * Bluetooth low energy (LE) is designed to use short bursts of longer-range radio connection,
     * making it ideal for Internet of Things (IoT) applications that don’t require continuous connection.
     * These apps can often run on just one coin cell and still have a relatively long battery life.
     *
     * @param type
     * @return
     */
    public String getDeviceType(int type) {
        switch (type) {
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                return "DEVICE_TYPE_DUAL";
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                return "DEVICE_TYPE_CLASSIC";
            case BluetoothDevice.DEVICE_TYPE_LE:
                return "DEVICE_TYPE_LE";
            default:
                return "DEVICE_TYPE_UNKNOWN";
        }
    }

    /**
     * https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.1_r13/stack/include/gatt_api.h
     *
     * @param gattStatus
     * @return
     */
    public String getGattStatus(int gattStatus) {
        String status;
        switch (gattStatus) {
            case BluetoothGatt.GATT_SUCCESS:
                status = "GATT_SUCCESS";
                break;
            case BluetoothGatt.GATT_FAILURE:
                /* A GATT operation failed*/
                status = "GATT_FAILURE";
                break;
            case 133:
                status = "GATT_ERROR";
                break;
            case 129:
                status = "GATT_INTERNAL_ERROR";
                break;
            case 19:
                status = "GATT_RSP_WRITE";
                break;
            default:
                status = "GATT STATUS";
                break;
        }
        return status + "(" + gattStatus + ")";
    }

    public void onForceDisconnect() {
        bluetoothListener.onForceDisconnect("FAILED\nCANCELLED");
        disconnect();
    }

    public void disconnect() {
        timeoutHandler.removeCallbacksAndMessages(null);
        bluetoothListener.onDisconnect();
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            return;
        }

        bluetoothGatt.disconnect();
    }

    public void close() {
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return iBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        return super.onUnbind(intent);
    }

    class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        close();
    }
}
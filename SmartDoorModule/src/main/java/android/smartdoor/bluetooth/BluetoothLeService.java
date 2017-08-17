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
import android.util.Log;

import java.util.UUID;

public class BluetoothLeService extends Service {

    /////////////////////////////////////////////////////////////////////////
    // GIST - https://gist.github.com/AndroidStudio/06c3438d38407251a305f99aab280ae2
    /////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    private static final String TAG = BluetoothLeService.class.getSimpleName();

    /////////////////////////////////////////////////////////////////////////
    // UUID serwisu (Serwis z charakterystyką klucza)
    /////////////////////////////////////////////////////////////////////////
    private final static String SERVICE_UUID = "000018f1-0000-1000-8000-00805f9b34fb";

    /////////////////////////////////////////////////////////////////////////
    // UUID charakterystyki (Charakterystyka do której będzie zapisywany klucz)
    /////////////////////////////////////////////////////////////////////////
    private final static String CHARACTERISTIC_UUID = "000000F1-0000-1000-8000-00805f9b34fb";

    /////////////////////////////////////////////////////////////////////////
    // Rozłączenie urządzenia jeśli zostanie przekroczony limit czasu (milisekundy)
    /////////////////////////////////////////////////////////////////////////
    private static final long TIME_OUT = 10000;

    private final Handler mLogHandler = new Handler(Looper.getMainLooper());
    private final Handler mTimeoutHandler = new Handler();
    private final IBinder mIBinder = new LocalBinder();

    private BluetoothListener mBluetoothListener = null;
    private BluetoothManager mBluetoothManager = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothGatt mBluetoothGatt = null;

    /////////////////////////////////////////////////////////////////////////
    // Czy serwis jest w trakcie wysyłania klucza
    /////////////////////////////////////////////////////////////////////////
    private boolean mServiceRunning = false;

    /////////////////////////////////////////////////////////////////////////
    // Adres urządznienia zewnętrznego do którego łączy sie telefon/tablet
    /////////////////////////////////////////////////////////////////////////
    private String mBluetoothDeviceAddress;

    /////////////////////////////////////////////////////////////////////////
    // Klucz wysyłany do serwera
    /////////////////////////////////////////////////////////////////////////
    private String mKey;

    /////////////////////////////////////////////////////////////////////////
    // Serwer GATT - Callbacks
    /////////////////////////////////////////////////////////////////////////
    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(final BluetoothGatt pBluetoothGatt, int pStatus, int pNewState) {
            super.onConnectionStateChange(pBluetoothGatt, pStatus, pNewState);
            BluetoothLeService.this.onConnectGattStageChange(pBluetoothGatt, pStatus, pNewState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt pBluetoothGatt, int pStatus) {
            super.onServicesDiscovered(pBluetoothGatt, pStatus);
            BluetoothLeService.this.onServicesDiscovered(pStatus);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt pBluetoothGatt, BluetoothGattCharacteristic pBluetoothGattCharacteristic, int pStatus) {
            super.onCharacteristicWrite(pBluetoothGatt, pBluetoothGattCharacteristic, pStatus);
            BluetoothLeService.this.onCharacteristicWrite(pStatus);
        }
    };

    /////////////////////////////////////////////////////////////////////////
    // Monitorowanie stanu połacząnia
    /////////////////////////////////////////////////////////////////////////
    private void onConnectGattStageChange(@SuppressWarnings("unused") BluetoothGatt pBluetoothGatt, int pStatus, int pNewState) {
        Log.w(TAG, "GATT STATUS:" + pStatus + (pStatus == BluetoothGatt.GATT_SUCCESS ? " (GATT_SUCCESS)" : ""));
        this.onLog("GATT STATUS:" + pStatus + (pStatus == BluetoothGatt.GATT_SUCCESS ? " (GATT_SUCCESS)" : ""));

        if (pStatus == BluetoothGatt.GATT_SUCCESS && pNewState == BluetoothProfile.STATE_CONNECTED) {
            this.onLog("onConnectionStateChange: (STATE_CONNECTED)");
            this.onConnectGATTSuccess();
            this.mBluetoothGatt.discoverServices();
        } else if (pNewState == BluetoothProfile.STATE_DISCONNECTED) {

            /*
            * Urządzenie rozłączone
            *
            * Jeśli klucz nie został wysłany i nie przekroczono limitu czasu następuje reconnect
            * */
            Log.w(TAG, "Rozłączono z serwerem GATT");
            this.onLog("onConnectionStateChange: (STATE_DISCONNECTED)");
        } else {
            Log.w(TAG, "NEW STATE: " + pNewState);
            this.onLog("onConnectionStateChange: (STATE: " + pNewState + ")");
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Callback - serwisy wykryte
    /////////////////////////////////////////////////////////////////////////
    private void onServicesDiscovered(int pStatus) {
        if (pStatus == BluetoothGatt.GATT_SUCCESS) {
            final UUID mServiceUuid = UUID.fromString(SERVICE_UUID);
            final BluetoothGattService mBluetoothGattService = this.mBluetoothGatt.getService(mServiceUuid);
            if (mBluetoothGattService != null) {
                this.onLog("Serwis wykryty: (" + mServiceUuid.toString() + ")");
                this.onLog("Wysyłanie klucza");

                final UUID mCharacteristicUuid = UUID.fromString(CHARACTERISTIC_UUID);
                final BluetoothGattCharacteristic mCharacteristic
                        = mBluetoothGattService.getCharacteristic(mCharacteristicUuid);
                final byte[] bytes = hexStringToByteArray(this.mKey);
                mCharacteristic.setValue(bytes);
                this.mBluetoothGatt.writeCharacteristic(mCharacteristic);
            }
        } else {
            /*
            * Obsługa błedów
            * */
            Log.w(TAG, "Błąd wykrywania serwisów: " + pStatus);
            this.onLog("Błąd wykrywania serwisów");
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Callback - jeśli "BluetoothGatt.GATT_SUCCESS" klucz został wysłany poprawnie
    /////////////////////////////////////////////////////////////////////////
    private void onCharacteristicWrite(int pStatus) {
        this.onLog("onCharacteristicWrite BluetoothGattState: " + pStatus + (pStatus == BluetoothGatt.GATT_SUCCESS ? " (GATT_SUCCESS)" : ""));
        if (pStatus == BluetoothGatt.GATT_SUCCESS) {
            this.onLog("Klucz został wysłany: (" + this.mKey + ")");
            this.mTimeoutHandler.removeCallbacksAndMessages(null);
            this.mLogHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (BluetoothLeService.this.mBluetoothListener != null) {
                        BluetoothLeService.this.mBluetoothListener.onSendKeySuccess();
                    }
                    BluetoothLeService.this.disconnect();
                }
            });
        } else {
            /*
            * Obsługa błedów
            * */
            Log.w(TAG, "Błąd wysyłania danych: " + pStatus);
            this.onLog("Błąd wysyłania klucza: (" + this.mKey + ")");
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Logi bluetooth
    /////////////////////////////////////////////////////////////////////////
    private void onLog(final String pMessage) {
        this.mLogHandler.post(new Runnable() {
            @Override
            public void run() {
                if (BluetoothLeService.this.mBluetoothListener != null) {
                    BluetoothLeService.this.mBluetoothListener.onLog(pMessage);
                }
            }
        });
    }

    /////////////////////////////////////////////////////////////////////////
    // Callback - error
    /////////////////////////////////////////////////////////////////////////
    private void onError(final String pError) {
        this.mLogHandler.post(new Runnable() {
            @Override
            public void run() {
                if (BluetoothLeService.this.mBluetoothListener != null) {
                    BluetoothLeService.this.mBluetoothListener.onError(pError);
                }
            }
        });
    }

    /////////////////////////////////////////////////////////////////////////
    // Callback - połączono z serwerem GATT
    /////////////////////////////////////////////////////////////////////////
    private void onConnectGATTSuccess() {
        this.mLogHandler.post(new Runnable() {
            @Override
            public void run() {
                if (BluetoothLeService.this.mBluetoothListener != null) {
                    BluetoothLeService.this.mBluetoothListener.onConnectGATT();
                }
            }
        });
    }

    /////////////////////////////////////////////////////////////////////////
    // Inicjalizacja - Bluetooth listener
    /////////////////////////////////////////////////////////////////////////
    public void setBluetoothListener(BluetoothListener pBluetoothListener) {
        this.mBluetoothListener = pBluetoothListener;
    }

    /////////////////////////////////////////////////////////////////////////
    // Zamiana HEX na tablice bajtów
    /////////////////////////////////////////////////////////////////////////
    private byte[] hexStringToByteArray(String value) {
        final byte result[] = new byte[value.length() / 2];
        final char enc[] = value.toCharArray();
        for (int i = 0; i < enc.length; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(String.valueOf(enc[i]) + enc[i + 1], 16);
        }
        return result;
    }

    /////////////////////////////////////////////////////////////////////////
    // Łączenie z serwerem GATT
    /////////////////////////////////////////////////////////////////////////
    public void onConnectGATT(final String pAddress, String pKey) {
        if (this.isServiceRunning()) {
            return;
        }

        this.mServiceRunning = true;
        this.mKey = pKey;

        this.onLog("Lączenie z urządzeniem: (" + pAddress + ")");

        if (this.mBluetoothListener != null) {
            this.mBluetoothListener.onConnectDevice();
        }

        if (this.mBluetoothAdapter == null) {
            this.onLog("Nie można połączyć z urządzeniem");
            this.onError("Nie można połączyć z urządzeniem");
            return;
        }

        /*
        * Wyszukiwanie urządzenia zewnętrznego
        * */
        BluetoothDevice mDevice;
        try {
            mDevice = this.mBluetoothAdapter.getRemoteDevice(pAddress);
            if (mDevice == null) {
                this.onLog("Nie odnaleziono urządzenia");
                this.onError("Nie odnaleziono urządzenia");
                return;
            }
        } catch (Exception e) {
            this.onLog("Nie można połączyć z urządzeniem");
            this.onError("Nie można połączyć z urządzeniem");
            e.printStackTrace();
            return;
        }

        /*
        * Inicjalizacja funkcji timeout
        * */
        this.onCreateTimeOutHandler();

        /*
        * Łączenie z urządzeniem zewnętrznym
        * */
        this.mBluetoothGatt = mDevice.connectGatt(this, true, this.mBluetoothGattCallback);
        this.mBluetoothDeviceAddress = pAddress;
    }

    /////////////////////////////////////////////////////////////////////////
    // Czy serwis dostępny
    /////////////////////////////////////////////////////////////////////////
    private boolean isServiceRunning() {
        return this.mServiceRunning;
    }

    /////////////////////////////////////////////////////////////////////////
    // Limit czasu na wysłanie klucza
    /////////////////////////////////////////////////////////////////////////
    private void onCreateTimeOutHandler() {
        this.mTimeoutHandler.removeCallbacksAndMessages(null);
        this.mTimeoutHandler.postDelayed(this.mTimeOutRunnable, TIME_OUT);
    }

    /////////////////////////////////////////////////////////////////////////
    // Timeout handler
    /////////////////////////////////////////////////////////////////////////
    private final Runnable mTimeOutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mBluetoothListener != null) {
                mBluetoothListener.onForceDisconnect("FAILED\nTIMEOUT");
            }
            BluetoothLeService.this.disconnect();
        }
    };

    /////////////////////////////////////////////////////////////////////////
    // Wymuszone rozłączenie z serwerem GATT
    /////////////////////////////////////////////////////////////////////////
    public void onForceDisconnect() {
        if (this.mBluetoothListener != null) {
            this.mBluetoothListener.onForceDisconnect("FAILED\nCANCELLED");
        }
        this.disconnect();
    }

    /////////////////////////////////////////////////////////////////////////
    // Rozłączenie z serwerem GATT
    /////////////////////////////////////////////////////////////////////////
    public void disconnect() {
        this.mTimeoutHandler.removeCallbacksAndMessages(null);
        if (this.mBluetoothListener != null) {
            this.mBluetoothListener.onDisconnect();
        }

        this.mLogHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                BluetoothLeService.this.mServiceRunning = false;
            }
        }, 2000);

        if (this.mBluetoothAdapter == null || this.mBluetoothGatt == null) {
            this.onLog("Wystąpił błąd podczas rozłączania");
            return;
        }

        this.mBluetoothGatt.disconnect();
        this.close();
    }

    /////////////////////////////////////////////////////////////////////////
    // Inicjalizacja adaptera bluetooth
    /////////////////////////////////////////////////////////////////////////
    public boolean initialize() {
        if (!this.isBluetoothLeSupported()) {
            return false;
        }

        /*
        * Bluetooth manager
        * */
        if (this.mBluetoothManager == null) {
            this.mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (this.mBluetoothManager == null) {
                return false;
            }
        }

        /*
        * Bluetooth adapter
        * */
        this.mBluetoothAdapter = this.mBluetoothManager.getAdapter();
        return !(this.mBluetoothAdapter == null || !this.mBluetoothAdapter.isEnabled());
    }

    /////////////////////////////////////////////////////////////////////////
    // Czy urządzenie użytkownika wspiera bluetooth
    /////////////////////////////////////////////////////////////////////////
    private boolean isBluetoothLeSupported() {
        return this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /////////////////////////////////////////////////////////////////////////
    // Rozłączenie z serwerem GATT oraz zwonienie zasobów serwera GATT
    /////////////////////////////////////////////////////////////////////////
    public void close() {
        if (this.mBluetoothGatt == null) {
            return;
        }
        this.mBluetoothGatt.close();
        this.mBluetoothGatt = null;
    }

    /////////////////////////////////////////////////////////////////////////
    // Bindowanie z serwisem
    /////////////////////////////////////////////////////////////////////////
    @Override
    public IBinder onBind(final Intent intent) {
        return this.mIBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        /*
        * Rozłączenie z serwisem, rozłączenie z bluetooth
        * */
        this.close();
        return super.onUnbind(intent);
    }

    class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }
}
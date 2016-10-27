package android.smartdoor.bluetooth;

interface BluetoothListener {

    void onConnectDevice();

    void onLog(String pMessage);

    void onConnectGATT();

    void onDisconnect();

    void onError(String pError);

    void onSendKeySuccess();

    void onForceDisconnect(String pMessage);
}

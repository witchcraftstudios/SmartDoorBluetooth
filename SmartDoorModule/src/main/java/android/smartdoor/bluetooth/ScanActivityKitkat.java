package android.smartdoor.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.annotation.Nullable;

public class ScanActivityKitkat extends SmartDoorBaseActivity implements BluetoothAdapter.LeScanCallback {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void startScan() {
        super.startScan();
        BluetoothAdapter bluetoothAdapter = bluetoothLeService.getBluetoothAdapter();
        bluetoothAdapter.startLeScan(this);
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        if (device.getAddress().equals(macAddress)) {
            //stopScan();
            deviceFound();
        }
    }

    @SuppressWarnings("deprecation")
    private void stopScan() {
        try {
            BluetoothAdapter bluetoothAdapter = bluetoothLeService.getBluetoothAdapter();
            if (bluetoothAdapter != null) {
                bluetoothAdapter.stopLeScan(this);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
    }
}

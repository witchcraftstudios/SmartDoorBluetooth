package android.smartdoor.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.support.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScanActivityLollipop extends SmartDoorBaseActivity {

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void startScan() {
        super.startScan();
        BluetoothAdapter bluetoothAdapter = bluetoothLeService.getBluetoothAdapter();
        if (bluetoothAdapter != null) {
            BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null) {
                bluetoothLeScanner.startScan(scanCallback);
            }
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (result.getDevice().getAddress().equals(macAddress)) {
                stopScan();
                deviceFound();
            }
        }
    };

    private void stopScan() {
        try {
            BluetoothAdapter bluetoothAdapter = bluetoothLeService.getBluetoothAdapter();
            if (bluetoothAdapter != null) {
                BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                if (bluetoothLeScanner != null) {
                    //bluetoothLeScanner.stopScan(scanCallback);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
    }
}

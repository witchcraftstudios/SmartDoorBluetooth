package android.smartdoor.bluetooth;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class SmartDoorBaseActivity extends AppCompatActivity {
    public BluetoothLeService bluetoothLeService;

    private ProgressDialog searchingDialog;

    private ImageButton keyImageButton;
    private TextView statusTextView;
    private TextView debugTextView;

    private String key;
    public String macAddress;

    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.onCreateStatusBarColor();
        this.setContentView(R.layout.bluetooth_smart_door);

        Bundle extras = getIntent().getExtras();
        macAddress = extras.getString(SettingsActivity.MAC);
        key = extras.getString(SettingsActivity.KEY);

        keyImageButton = (ImageButton) findViewById(R.id.keyImageButton);
        keyImageButton.setOnClickListener(this.mOnClickListener);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        startService(gattServiceIntent);
        bindService(gattServiceIntent, this.mServiceConnection, BIND_AUTO_CREATE);

        debugTextView = (TextView) findViewById(R.id.debugTextView);
        statusTextView = (TextView) findViewById(R.id.statusTextView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
    }

    private final BluetoothListener mBluetoothListener = new BluetoothListener() {

        private final StringBuilder mStringBuilder = new StringBuilder();

        @Override
        public void onConnectDevice() {
            mStringBuilder.setLength(0);
            debugTextView.setText("");
            statusTextView.setText("");
            progressBar.setVisibility(View.VISIBLE);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    keyImageButton.setEnabled(false);
                    keyImageButton.setAlpha(.9f);
                }
            });
        }

        @Override
        public void onLog(String pMessage) {
            mStringBuilder.append(String.valueOf(pMessage + "\n"));
            debugTextView.setText(this.mStringBuilder.toString());
        }

        @Override
        public void onConnectGATT() {

        }

        @Override
        public void onDisconnect() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBar.setVisibility(View.INVISIBLE);
                    keyImageButton.setEnabled(true);
                    keyImageButton.setAlpha(1f);
                }
            });
        }

        @Override
        public void onError(String pError) {
            Toast.makeText(SmartDoorBaseActivity.this, pError, Toast.LENGTH_LONG).show();
            statusTextView.setTextColor(Color.RED);
            statusTextView.setText("FAILED");
        }

        @Override
        public void onSendKeySuccess() {
            statusTextView.setTextColor(Color.GREEN);
            statusTextView.setText("SUCCESS");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    keyImageButton.setEnabled(true);
                    keyImageButton.setAlpha(1f);
                }
            });
        }

        @Override
        public void onForceDisconnect(String pMessage) {
            statusTextView.setTextColor(Color.RED);
            statusTextView.setText(pMessage);
        }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(final ComponentName componentName, final IBinder service) {
            bluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            bluetoothLeService.setBluetoothListener(mBluetoothListener);

            if (!bluetoothLeService.initialize()) {
                Toast.makeText(SmartDoorBaseActivity.this, "Bluetooth LE nie jest dostępny", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            startScan();
        }

        @Override
        public void onServiceDisconnected(final ComponentName componentName) {
            bluetoothLeService.setBluetoothListener(null);
            bluetoothLeService = null;
        }
    };

    public void deviceFound() {
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dismissSearchDialog();
                    keyImageButton.setVisibility(View.VISIBLE);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startScan() {
        searchingDialog = new ProgressDialog(SmartDoorBaseActivity.this);
        searchingDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    finish();
                }
                return true;
            }
        });
        searchingDialog.setCanceledOnTouchOutside(false);
        searchingDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        searchingDialog.setMessage("Wyszukiwanie urządzenia...");
        searchingDialog.show();
    }

    private void dismissSearchDialog() {
        try {
            if (searchingDialog != null && searchingDialog.isShowing()) {
                searchingDialog.dismiss();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        stopService(new Intent(this, BluetoothLeService.class));
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            checkPermissions();
        }
    };

    private void checkPermissions() {
        openSmartDoor();
    }

    private void openSmartDoor() {
        if (macAddress.length() != 17) {
            Toast.makeText(this, "Proszę podać poprawny adress mac", Toast.LENGTH_LONG).show();
            return;
        }

        if (key.length() < 1) {
            Toast.makeText(this, "Proszę podać poprawny kod", Toast.LENGTH_LONG).show();
            return;
        }

        bluetoothLeService.onConnectGATT(macAddress, key);
    }

    private void onCreateStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window mWindow = getWindow();
            mWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            mWindow.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            mWindow.setStatusBarColor(getResources().getColor(R.color.colorPrimary));
        }
    }
}

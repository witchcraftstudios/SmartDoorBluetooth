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
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class SmartDoorActivity extends AppCompatActivity {

    private BluetoothLeService mBluetoothLeService;
    private ProgressDialog mProgressDialog;
    private TextView mStatusTextView;
    private TextView mDebugTextView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.onCreateStatusBarColor();
        this.setContentView(R.layout.bluetooth_smart_door);

        final ImageButton mKeyImageButton = (ImageButton) findViewById(R.id.keyImageButton);
        mKeyImageButton.setOnClickListener(this.mOnClickListener);

        final Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        this.bindService(gattServiceIntent, this.mServiceConnection, BIND_AUTO_CREATE);

        this.mDebugTextView = (TextView) findViewById(R.id.debugTextView);
        this.mStatusTextView = (TextView) findViewById(R.id.statusTextView);
    }

    private final BluetoothListener mBluetoothListener = new BluetoothListener() {

        private final StringBuilder mStringBuilder = new StringBuilder();

        @Override
        public void onConnectDevice() {
            mStringBuilder.setLength(0);
            mDebugTextView.setText("");
            mStatusTextView.setText("");

            mProgressDialog = new ProgressDialog(SmartDoorActivity.this);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    mBluetoothLeService.onForceDisconnect();
                }
            });

            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setMessage("Łączenie z urządzeniem...");
            mProgressDialog.show();
        }

        @Override
        public void onLog(String pMessage) {
            mStringBuilder.append(String.valueOf(pMessage + "\n"));
            mDebugTextView.setText(this.mStringBuilder.toString());
        }

        @Override
        public void onConnectGATT() {
            if (mProgressDialog != null) {
                mProgressDialog.setMessage("Wysyłanie klucza...");
            }
        }

        @Override
        public void onDisconnect() {
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }
        }

        @Override
        public void onError(String pError) {
            Toast.makeText(SmartDoorActivity.this, pError, Toast.LENGTH_LONG).show();
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            mStatusTextView.setTextColor(Color.RED);
            mStatusTextView.setText("FAILED");
        }

        @Override
        public void onSendKeySuccess() {
            Toast.makeText(SmartDoorActivity.this, "Klucz został wysłany", Toast.LENGTH_LONG).show();
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
            }

            mStatusTextView.setTextColor(Color.GREEN);
            mStatusTextView.setText("SUCCESS");
        }

        @Override
        public void onForceDisconnect(String pMessage) {
            mStatusTextView.setTextColor(Color.RED);
            mStatusTextView.setText(pMessage);
        }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(final ComponentName componentName, final IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            mBluetoothLeService.setBluetoothListener(mBluetoothListener);
        }

        @Override
        public void onServiceDisconnected(final ComponentName componentName) {
            mBluetoothLeService.setBluetoothListener(null);
            mBluetoothLeService = null;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.mBluetoothLeService.disconnect();
        this.unbindService(this.mServiceConnection);
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            openSmartDoor();
        }
    };

    private void openSmartDoor() {
        final EditText mMacEditText = (EditText) findViewById(R.id.macEditText);
        final EditText mKeyEditText = (EditText) findViewById(R.id.keyEditText);

        final String mMac = mMacEditText.getText().toString();
        final String mKey = mKeyEditText.getText().toString();

        if (!this.mBluetoothLeService.initialize()) {
            Toast.makeText(this, "Bluetooth LE nie jest dostępny", Toast.LENGTH_LONG).show();
            return;
        }

        if (mMac.length() != 17) {
            Toast.makeText(this, "Proszę podać poprawny adress mac", Toast.LENGTH_LONG).show();
            return;
        }

        if (mKey.length() != 8) {
            Toast.makeText(this, "Proszę podać poprawny kod", Toast.LENGTH_LONG).show();
            return;
        }

        this.mBluetoothLeService.onConnectGATT(mMac, mKey);
    }

    private void onCreateStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Window mWindow = getWindow();
            mWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            mWindow.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            mWindow.setStatusBarColor(getResources().getColor(R.color.colorPrimary));
        }
    }
}

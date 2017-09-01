package android.smartdoor.bluetooth;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_LOCATION = 1001;

    public final static String MAC = "mac";
    public final static String KEY = "key";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_layout);
    }

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_LOCATION);
            return false;
        } else {
            return true;
        }
    }

    public void onConfirmClick(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (checkLocationPermission()) {
                startSmartDoorActivity();
            } else {
                Toast.makeText(this, "Aplikacja wymaga dostępu do informacji o likalizacji tego urządzenia.", Toast.LENGTH_LONG).show();
            }
        } else {
            startSmartDoorActivity();
        }
    }

    private void startSmartDoorActivity() {
        EditText macEditText = (EditText) findViewById(R.id.macEditText);
        EditText keyEditText = (EditText) findViewById(R.id.keyEditText);

        String mac = macEditText.getText().toString();
        if (TextUtils.isEmpty(mac)) {
            Toast.makeText(SettingsActivity.this, "Proszę podać adres mac", Toast.LENGTH_LONG).show();
            return;
        }

        String key = keyEditText.getText().toString();
        if (TextUtils.isEmpty(key)) {
            Toast.makeText(SettingsActivity.this, "Proszę podać klucz", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent = new Intent(this, ScanActivityLollipop.class);
        } else {
            intent = new Intent(this, ScanActivityKitkat.class);
        }
        intent.putExtra(MAC, mac);
        intent.putExtra(KEY, key);
        startActivity(intent);
    }
}

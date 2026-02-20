package com.example.citaqh10printer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText etDevicePath, etBaudRate, etPaperWidth;
    private Switch   swShare;
    private EditText etSharePort;

    private Switch   swShareUsb;
    private EditText etShareUsbPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etDevicePath = findViewById(R.id.etDevicePath);
        etBaudRate = findViewById(R.id.etBaudRate);
        etPaperWidth = findViewById(R.id.etPaperWidth);
        // New: sharing
        swShare     = findViewById(R.id.swShare);
        etSharePort = findViewById(R.id.etSharePort);

        swShareUsb     = findViewById(R.id.swShareUsb);
        etShareUsbPort = findViewById(R.id.etShareUsbPort);

        Button btnSave = findViewById(R.id.btnSave);

        final android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        etDevicePath.setText(prefs.getString("device_path", "/dev/ttyS1"));
        etBaudRate.setText(String.valueOf(prefs.getInt("baud_rate", 115200)));
        etPaperWidth.setText(String.valueOf(prefs.getInt("paper_width", 576)));

        // New: load sharing prefs
        swShare.setChecked(prefs.getBoolean("share_enabled", false));
        etSharePort.setText(String.valueOf(prefs.getInt("share_port", 9100)));

        swShareUsb.setChecked(prefs.getBoolean("share_usb_enabled", false));
        etShareUsbPort.setText(String.valueOf(prefs.getInt("share_usb_port", 9101)));

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String path = etDevicePath.getText().toString().trim();
                int baud; int width; int sharePort;
                try { baud = Integer.parseInt(etBaudRate.getText().toString().trim()); } catch (Exception e) { baud = 115200; }
                try { width = Integer.parseInt(etPaperWidth.getText().toString().trim()); } catch (Exception e) { width = 576; }
                try { sharePort = Integer.parseInt(etSharePort.getText().toString().trim()); } catch (Exception e) { sharePort = 9100; }

                boolean shareEnabled = swShare.isChecked();

                boolean shareUsb = swShareUsb.isChecked();
                int usbPort;
                try { usbPort = Integer.parseInt(etShareUsbPort.getText().toString().trim()); }
                catch (Exception e) { usbPort = 9101; }

                prefs.edit()
                        .putString("device_path", path.isEmpty()?"/dev/ttyS1":path)
                        .putInt("baud_rate", baud)
                        .putInt("paper_width", width)
                        .putBoolean("share_enabled", shareEnabled)
                        .putInt("share_port", sharePort)
                        .putBoolean("share_usb_enabled", shareUsb)
                        .putInt("share_usb_port", usbPort)
                        .apply();

                // start/stop the USB share service now
                Intent usbSvc = new Intent(SettingsActivity.this, Raw9101UsbShareService.class);
                if (shareUsb) startService(usbSvc); else stopService(usbSvc);

                Intent svc = new Intent(SettingsActivity.this, Raw9100ShareService.class);
                if (shareEnabled) startService(svc); else stopService(svc);

                Toast.makeText(SettingsActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}

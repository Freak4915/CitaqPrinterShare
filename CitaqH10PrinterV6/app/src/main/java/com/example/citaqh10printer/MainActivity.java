package com.example.citaqh10printer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private Button btnPrint;
    private Button btnSettings;

    private SharedPreferences prefs;

    private static final String DEFAULT_PATH = "/dev/ttyS1";
    private static final int DEFAULT_BAUD = 115200;
    private static final String URL = "http://www.ihavealongdomainnameandilikeitverymuchthankyou.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        tvStatus = findViewById(R.id.tvStatus);
        btnPrint = findViewById(R.id.btnPrint);
        btnSettings = findViewById(R.id.btnSettings);

        Button btnStatus = findViewById(R.id.btnStatus);
        btnStatus.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, StatusActivity.class)));

        btnPrint.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doPrint(); }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeStartOrStopShareServices();
    }

    private void maybeStartOrStopShareServices() {
        boolean serialEnabled = prefs.getBoolean("share_enabled", false);
        boolean usbEnabled    = prefs.getBoolean("share_usb_enabled", false);

        Intent serialSvc = new Intent(this, Raw9100ShareService.class);
        Intent usbSvc    = new Intent(this, Raw9101UsbShareService.class);

        if (serialEnabled) startService(serialSvc); else stopService(serialSvc);
        if (usbEnabled)    startService(usbSvc);    else stopService(usbSvc);
    }

    private void doPrint() {
        final String path = prefs.getString("device_path", DEFAULT_PATH);
        final int baud = prefs.getInt("baud_rate", DEFAULT_BAUD);

        tvStatus.setText(R.string.status_printing);
        btnPrint.setEnabled(false);

        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... voids) {
                SerialPort sp = null;
                try {
                    sp = new SerialPort(path, baud, 0);
                    OutputStream os = sp.getOutputStream();
                    PrinterEscPos.initialize(os);
                    PrinterEscPos.setAlignCenter(os);
                    PrinterEscPos.printQr(os, URL, 6, 51);
                    PrinterEscPos.feed(os, 3);
                    PrinterEscPos.cut(os);
                    os.flush();
                    return null;
                } catch (Throwable t) {
                    return t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "error" : t.getMessage());
                } finally {
                    if (sp != null) sp.close();
                }
            }

            @Override protected void onPostExecute(String error) {
                if (error == null) {
                    tvStatus.setText(R.string.status_done);
                    Toast.makeText(MainActivity.this, "Printed", Toast.LENGTH_SHORT).show();
                } else {
                    tvStatus.setText(R.string.status_disconnected);
                    Toast.makeText(MainActivity.this, "Failed: " + error, Toast.LENGTH_LONG).show();
                }
                btnPrint.setEnabled(true);
            }
        }.execute();
    }
}

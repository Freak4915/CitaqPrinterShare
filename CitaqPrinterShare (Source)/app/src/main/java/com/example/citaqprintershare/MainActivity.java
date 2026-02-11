package com.example.citaqprintershare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);

        Button start = findViewById(R.id.btn_start);
        Button stop = findViewById(R.id.btn_stop);

        start.setOnClickListener(v -> {
            Intent i = new Intent(this, PrinterService.class);
            startService(i);
        });

        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, PrinterService.class);
            stopService(i);
        });
    }
}

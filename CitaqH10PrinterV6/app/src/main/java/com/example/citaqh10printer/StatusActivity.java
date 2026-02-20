package com.example.citaqh10printer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class StatusActivity extends Activity implements StatusLog.Listener {

    private ArrayAdapter<String> adapter;
    private final ArrayList<String> data = new ArrayList<>();
    private TextView tvCount;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);

        ListView list = findViewById(R.id.list);
        tvCount = findViewById(R.id.tvCount);
        Button btnRefresh = findViewById(R.id.btnRefresh);
        Button btnClear = findViewById(R.id.btnClear);

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, data);
        list.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> reload());
        btnClear.setOnClickListener(v -> {
            StatusLog.get().clear();
            reload();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        StatusLog.get().register(this);
        reload();
    }

    @Override protected void onPause() {
        StatusLog.get().unregister(this);
        super.onPause();
    }

    private void reload() {
        data.clear();
        data.addAll(StatusLog.get().snapshot());
        adapter.notifyDataSetChanged();
        tvCount.setText(data.size() + " lines");
        // auto-scroll to last
        if (!data.isEmpty()) {
            ((ListView) findViewById(R.id.list))
                    .setSelection(data.size() - 1);
        }
    }

    // StatusLog.Listener
    @Override public void onLogAppended(final String line) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                data.add(line);
                adapter.notifyDataSetChanged();
                tvCount.setText(data.size() + " lines");
                ((ListView) findViewById(R.id.list))
                        .setSelection(data.size() - 1);
            }
        });
    }

    @Override public void onLogCleared() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                data.clear();
                adapter.notifyDataSetChanged();
                tvCount.setText("0 lines");
            }
        });
    }
}
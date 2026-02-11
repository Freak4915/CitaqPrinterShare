package com.example.citaqprintershare;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrinterService extends Service {
    private static final String TAG = "PrinterService";
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private SerialPort serialPort = new SerialPort();
    private int fd = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newCachedThreadPool();

        // open serial port
        fd = serialPort.open("/dev/ttyS1", 115200);
        if (fd < 0) {
            Log.e(TAG, "Failed to open serial port, fd=" + fd);
            stopSelf();
            return;
        }

        executor.submit(this::runServer);
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(9100);
            while (!serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                executor.submit(() -> handleClient(client));
            }
        } catch (Exception e) {
            Log.e(TAG, "Server error", e);
        }
    }

    private void handleClient(Socket client) {
        try (Socket s = client;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {

            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) {
                // forward to serial
                int written = serialPort.write(fd, buf, r);
                if (written < 0) {
                    Log.e(TAG, "Serial write error");
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Client handler error", e);
        }
    }

    @Override
    public void onDestroy() {
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (fd >= 0) serialPort.close(fd);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

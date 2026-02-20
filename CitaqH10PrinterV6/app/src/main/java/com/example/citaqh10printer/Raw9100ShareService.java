package com.example.citaqh10printer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class Raw9100ShareService extends Service {
    private static final String TAG = "Raw9100Share";
    private static final String DEFAULT_PATH = "/dev/ttyS1";
    private static final int DEFAULT_BAUD = 115200;

    private Thread serverThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // NSD / mDNS
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private WifiManager.MulticastLock multicastLock;

    @Override
    public void onCreate() {
        super.onCreate();

        final int port = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt("share_port", 9100);

        // Start server loop
        running.set(true);
        serverThread = new Thread(new ServerRunnable(port), "Raw9100-Server");
        serverThread.start();

        // Acquire MulticastLock (helps NSD/mDNS on many Wi‑Fi stacks)
        try {
            WifiManager wifi = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            multicastLock = wifi.createMulticastLock("escpos-mdns-lock");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Throwable t) {
            StatusLog.get().w(TAG, "MulticastLock not available: " + t.getMessage());
        }

        // Register Bonjour/NSD service as _pdl-datastream._tcp
        try {
            nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName("Citaq H10 Printer");
            info.setServiceType("_pdl-datastream._tcp.");
            info.setPort(port);

            registrationListener = new NsdManager.RegistrationListener() {
                @Override public void onRegistrationFailed(NsdServiceInfo s, int errorCode) {
                    StatusLog.get().w(TAG, "NSD registration failed: " + errorCode);
                }
                @Override public void onUnregistrationFailed(NsdServiceInfo s, int errorCode) {
                    StatusLog.get().w(TAG, "NSD unregistration failed: " + errorCode);
                }
                @Override public void onServiceRegistered(NsdServiceInfo s) {
                    StatusLog.get().i(TAG, "NSD registered: " + s.getServiceName() + " on " + s.getPort());
                }
                @Override public void onServiceUnregistered(NsdServiceInfo s) {
                    StatusLog.get().i(TAG, "NSD unregistered");
                }
            };

            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        } catch (Throwable t) {
            StatusLog.get().w(TAG, "NSD register failed: " + t.getMessage());
        }
    }

    /** Return a hex preview of the first up-to-`max` bytes from buffer. */
    private static String hexHead(byte[] b, int len, int max) {
        int n = Math.min(len, max);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02X ", b[i] & 0xFF));
        }
        return sb.toString().trim();
    }

    /** Return index of a pattern in b[0..len), or -1 if not found. */
    private static int indexOf(byte[] b, int len, byte[] pat) {
        outer: for (int i = 0; i <= len - pat.length; i++) {
            for (int j = 0; j < pat.length; j++) {
                if (b[i + j] != pat[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    /** Quick detector: GS v 0 vs ESC * vs QR GS ( k. */
    private static String detectCmd(byte[] buf, int n) {
        int gsV0 = indexOf(buf, n, new byte[]{0x1D, 0x76, 0x30});  // GS v 0
        int start = 33; // you reported GS_v0@33
        if (buf.length >= start + 8) {
            int m   = buf[start + 3] & 0xFF;
            int xL  = buf[start + 4] & 0xFF;
            int xH  = buf[start + 5] & 0xFF;
            int yL  = buf[start + 6] & 0xFF;
            int yH  = buf[start + 7] & 0xFF;

            int xBytes = xL | (xH << 8);
            int rows   = yL | (yH << 8);
            int expect = xBytes * rows;
            StatusLog.get().i(TAG, "GS v0 header: m=" + m +
                    ", xBytes=" + xBytes + " (width=" + (xBytes*8) + " px)" +
                    ", rows=" + rows + ", expectedData=" + expect + " bytes");
        }
        if (gsV0 >= 0) return "GS_v0@" + gsV0;

        int escStar = indexOf(buf, n, new byte[]{0x1B, 0x2A});     // ESC *
        if (escStar >= 0) return "ESC_*@" + escStar;

        int gsK = indexOf(buf, n, new byte[]{0x1D, 0x28, 0x6B});   // GS ( k (QR/NV variants)
        if (gsK >= 0) return "GS_(k)@" + gsK;

        return "unknown";
    }

    @Override
    public void onDestroy() {
        running.set(false);
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }

        try { if (nsdManager != null && registrationListener != null)
            nsdManager.unregisterService(registrationListener);
        } catch (Throwable ignored) {}

        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); }
        catch (Throwable ignored) {}

        registrationListener = null;
        nsdManager = null;
        multicastLock = null;

        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private class ServerRunnable implements Runnable {
        private final int port;
        ServerRunnable(int port) { this.port = port; }

        @Override public void run() {
            ServerSocket server = null;
            try {
                server = new ServerSocket(port);
                StatusLog.get().i(TAG, "Listening on tcp/" + port + " for raw ESC/POS");

                while (running.get()) {
                    Socket socket = null;
                    try {
                        socket = server.accept(); // one connection handled per thread
                        handleClient(socket);
                    } catch (IOException e) {
                        if (running.get()) Log.w(TAG, "Accept error: " + e.getMessage());
                    } finally {
                        if (socket != null) try { socket.close(); } catch (IOException ignored) {}
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Server error: " + t.getMessage(), t);
            } finally {
                if (server != null) try { server.close(); } catch (IOException ignored) {}
            }
        }

        /**
         * Bridges the TCP input to the serial printer output.
         * Simple flow control: open serial per connection; write through; close on EOF.
         */

        private void handleClient(Socket socket) {
            StatusLog.get().i(TAG, "===== New client: " + socket.getInetAddress().getHostAddress());
            String path = PreferenceManager.getDefaultSharedPreferences(Raw9100ShareService.this)
                    .getString("device_path", DEFAULT_PATH);
            int baud  = PreferenceManager.getDefaultSharedPreferences(Raw9100ShareService.this)
                    .getInt("baud_rate", DEFAULT_BAUD);

            SerialPort sp = null;
            try {
                sp = new SerialPort(path, baud, 0);
                BufferedOutputStream printerOut = new BufferedOutputStream(sp.getOutputStream());
                BufferedInputStream clientIn = new BufferedInputStream(socket.getInputStream());

                byte[] buf = new byte[4096];
                int n;
                boolean first = true;
                int state = 0;
                int xBytes = 0, rows = 0, expected = 0, got = 0;

                while ((n = clientIn.read(buf)) != -1) {

                    if (first) {
                        String found = detectCmd(buf, n);
                        StatusLog.get().i(TAG, "Image command detected: " + found);
                        StatusLog.get().i(TAG, "Raw data " + hexHead(buf, n, 64));
                        StatusLog.get().i(TAG, "Wrote " + n + " bytes to serial");
                        first = false;
                    }

                    

                    printerOut.write(buf, 0, n);
                }
                printerOut.flush();
            } catch (Throwable t) {
                StatusLog.get().i(TAG, "Client session error: " + t.getMessage());
            } finally {
                try { if (sp != null) sp.close(); } catch (Throwable ignored) {}
                StatusLog.get().i(TAG, "Client disconnected");
            }
        }
    }
}
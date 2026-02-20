package com.example.citaqh10printer;

import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.hardware.usb.*;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Raw9101UsbShareService extends Service {
    private static final String TAG = "Raw9101UsbShare";
    private static final String ACTION_USB_PERMISSION =
            "com.example.citaqh10printer.USB_PERMISSION";

    private Thread serverThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // NSD (Bonjour)
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener regListener;
    private WifiManager.MulticastLock multicastLock;

    // USB
    private UsbManager usbManager;
    private PendingIntent permissionIntent;
    private volatile boolean usbPermissionGranted = false;

    @Override public void onCreate() {
        super.onCreate();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        permissionIntent = PendingIntent.getBroadcast(
                this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));

        int port = PreferenceManager.getDefaultSharedPreferences(this)
                .getInt("share_usb_port", 9101);

        running.set(true);
        serverThread = new Thread(new ServerRunnable(port), "RawUSB-Server");
        serverThread.start();

        // Multicast lock (mDNS)
        try {
            WifiManager wifi = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            multicastLock = wifi.createMulticastLock("usb-mdns");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Throwable t) { Log.w(TAG, "MulticastLock: " + t.getMessage()); }

        // Advertise as _pdl-datastream._tcp (like a raw socket printer)
        try {
            nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName("Android USB (Brother HL-1210W)");
            info.setServiceType("_pdl-datastream._tcp."); // common raw-socket printer type
            info.setPort(port);
            regListener = new NsdManager.RegistrationListener() {
                @Override public void onRegistrationFailed(NsdServiceInfo s, int e) {
                    StatusLog.get().w(TAG, "NSD registration failed: " + e);
                }
                @Override public void onUnregistrationFailed(NsdServiceInfo s, int e) {
                    StatusLog.get().w(TAG, "NSD unregistration failed: " + e);
                }
                @Override public void onServiceRegistered(NsdServiceInfo s) {
                    StatusLog.get().i(TAG, "NSD registered: " + s.getServiceName() + " : " + s.getPort());
                }
                @Override public void onServiceUnregistered(NsdServiceInfo s) {
                    StatusLog.get().i(TAG, "NSD unregistered");
                }
            };
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener);
        } catch (Throwable t) { StatusLog.get().w(TAG, "NSD: " + t.getMessage()); }
    }

    @Override public void onDestroy() {
        running.set(false);
        if (serverThread != null) { serverThread.interrupt(); serverThread = null; }

        try { if (nsdManager != null && regListener != null)
            nsdManager.unregisterService(regListener); } catch (Throwable ignored) {}

        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); }
        catch (Throwable ignored) {}

        unregisterReceiver(usbReceiver);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // === TCP server thread ===
    private class ServerRunnable implements Runnable {
        private final int port;
        ServerRunnable(int port) { this.port = port; }

        @Override public void run() {
            try (ServerSocket server = new ServerSocket(port)) {
                StatusLog.get().i(TAG, "Listening on tcp/" + port + " (USB raw)");
                while (running.get()) {
                    try (Socket socket = server.accept()) {
                        StatusLog.get().i(TAG, "Client " + socket.getInetAddress().getHostAddress());
                        handleClient(socket);
                    } catch (Exception ex) {
                        if (running.get()) StatusLog.get().w(TAG, "Client error: " + ex.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Server error: " + e.getMessage(), e);
            }
        }
    }

    // === Bridge TCP -> USB bulk OUT ===
    private void handleClient(Socket socket) throws Exception {
        // Find a USB Printer-class device (bInterfaceClass == 7)
        UsbHandle handle = openUsbPrinterOrRequestPermission();
        if (handle == null) {
            StatusLog.get().w(TAG, "USB printer not available or no permission");
            return;
        }

        try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                // Write to the USB printer bulk OUT endpoint
                int wrote = handle.conn.bulkTransfer(handle.out, buf, n, 10_000);
                if (wrote < 0) {
                    throw new Exception("USB bulkTransfer failed");
                }
            }
            // No flush() call is needed; bulkTransfer() already sends data to the device.
        } finally {
            try { handle.conn.releaseInterface(handle.intf); } catch (Throwable ignored) {}
            try { handle.conn.close(); } catch (Throwable ignored) {}
        }
    }

    // Holds claimed interface and endpoints
    private static class UsbHandle {
        UsbDeviceConnection conn;
        UsbInterface intf;
        UsbEndpoint out;
        UsbEndpoint in; // optional, not used
    }

    private UsbHandle openUsbPrinterOrRequestPermission() {
        HashMap<String, UsbDevice> map = usbManager.getDeviceList();
        if (map == null || map.isEmpty()) return null;

        for (Map.Entry<String, UsbDevice> e : map.entrySet()) {
            UsbDevice dev = e.getValue();
            // Look for an interface with class PRINTER (0x07)
            for (int i = 0; i < dev.getInterfaceCount(); i++) {
                UsbInterface intf = dev.getInterface(i);
                if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                    // Permission?
                    if (!usbManager.hasPermission(dev)) {
                        usbPermissionGranted = false;
                        usbManager.requestPermission(dev, permissionIntent);
                        // We'll return null for now; user must grant and re-try next job.
                        return null;
                    }
                    UsbDeviceConnection conn = usbManager.openDevice(dev);
                    if (conn == null) return null;
                    if (!conn.claimInterface(intf, true)) {
                        conn.close(); return null;
                    }
                    UsbEndpoint out = null, in = null;
                    for (int eidx = 0; eidx < intf.getEndpointCount(); eidx++) {
                        UsbEndpoint ep = intf.getEndpoint(eidx);
                        if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.getDirection() == UsbConstants.USB_DIR_OUT) out = ep;
                            else in = ep;
                        }
                    }
                    if (out == null) { conn.releaseInterface(intf); conn.close(); return null; }
                    UsbHandle h = new UsbHandle();
                    h.conn = conn; h.intf = intf; h.out = out; h.in = in;
                    return h;
                }
            }
        }
        return null;
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        usbPermissionGranted = true;
                        StatusLog.get().i(TAG, "USB permission granted for " + device);
                    } else {
                        usbPermissionGranted = false;
                        StatusLog.get().w(TAG, "USB permission denied for " + device);
                    }
                }
            }
        }
    };
}
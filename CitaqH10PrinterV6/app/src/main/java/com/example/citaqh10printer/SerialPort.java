package com.example.citaqh10printer;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SerialPort {
    static {
        System.loadLibrary("serial_port");
    }

    private FileDescriptor mFd;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    public SerialPort(String device, int baudrate, int flags) throws IOException {
        File dev = new File(device);
        if (!dev.canRead() || !dev.canWrite()) {
            // May require device-side permission changes.
        }
        mFd = open(device, baudrate, flags);
        if (mFd == null) {
            throw new IOException("Cannot open serial port " + device);
        }
        mInputStream = new FileInputStream(mFd);
        mOutputStream = new FileOutputStream(mFd);
    }

    public FileInputStream getInputStream() { return mInputStream; }
    public FileOutputStream getOutputStream() { return mOutputStream; }

    public void close() {
        try { if (mInputStream != null) mInputStream.close(); } catch (IOException ignored) {}
        try { if (mOutputStream != null) mOutputStream.close(); } catch (IOException ignored) {}
        if (mFd != null) { closeNative(); mFd = null; }
    }

    private native static FileDescriptor open(String path, int baudrate, int flags);
    private native void closeNative();
}

package com.example.citaqprintershare;

public class SerialPort {
    static { System.loadLibrary("serial_port"); }

    // native handles
    public native int open(String path, int baudrate);
    public native void close(int fd);
    public native int write(int fd, byte[] data, int len);
    public native int read(int fd, byte[] buffer, int len);
}

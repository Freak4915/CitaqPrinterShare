package com.example.citaqh10printer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Thread-safe in-memory log with listener callbacks (API 22 compatible). */
public class StatusLog {

    public interface Listener {
        void onLogAppended(String line);
        void onLogCleared();
    }

    private static final StatusLog INSTANCE = new StatusLog();
    public static StatusLog get() { return INSTANCE; }

    private final ArrayList<String> lines = new ArrayList<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private final SimpleDateFormat ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private StatusLog() {}

    public synchronized void add(String tag, String msg) {
        String line = "[" + ts.format(new Date()) + "][" + tag + "] " + msg;
        lines.add(line);
        for (Listener l : new ArrayList<>(listeners)) {
            try { l.onLogAppended(line); } catch (Throwable ignored) {}
        }
    }

    public void i(String tag, String msg) { add(tag, msg); }
    public void w(String tag, String msg) { add(tag, "WARN: " + msg); }
    public void e(String tag, String msg) { add(tag, "ERROR: " + msg); }

    public synchronized ArrayList<String> snapshot() {
        return new ArrayList<>(lines);
    }

    public synchronized void clear() {
        lines.clear();
        for (Listener l : new ArrayList<>(listeners)) {
            try { l.onLogCleared(); } catch (Throwable ignored) {}
        }
    }

    public synchronized void register(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public synchronized void unregister(Listener l) {
        listeners.remove(l);
    }
}
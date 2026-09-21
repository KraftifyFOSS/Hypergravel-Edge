package pdx.dev.hypergravel.proxy.extension;

import java.util.ArrayList;
import java.util.List;

/** Shared, parent-classloader-side recorder that extension jars see. */
public final class ExtensionSink {

    public static final List<String> EVENTS = new ArrayList<>();

    public static void record(String event) {
        synchronized (EVENTS) {
            EVENTS.add(event);
        }
    }

    public static List<String> events() {
        synchronized (EVENTS) {
            return List.copyOf(EVENTS);
        }
    }

    public static void reset() {
        synchronized (EVENTS) {
            EVENTS.clear();
        }
    }

    private ExtensionSink() {
    }
}
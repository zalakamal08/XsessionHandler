package burp;

import java.util.concurrent.atomic.AtomicLong;

/** Counters shown in the card headers. */
public final class Stats {

    private static final AtomicLong CAPTURED = new AtomicLong();
    private static final AtomicLong INJECTED = new AtomicLong();

    private Stats() {
    }

    public static void countCapture() {
        CAPTURED.incrementAndGet();
    }

    public static void countInjection() {
        INJECTED.incrementAndGet();
    }

    public static long captured() {
        return CAPTURED.get();
    }

    public static long injected() {
        return INJECTED.get();
    }
}

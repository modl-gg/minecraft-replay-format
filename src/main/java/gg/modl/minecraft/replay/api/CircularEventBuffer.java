package gg.modl.minecraft.replay.api;

import gg.modl.minecraft.replay.format.ReplayEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CircularEventBuffer {

    private final int bufferDurationMs;
    private final ArrayDeque<ReplayEvent> buffer = new ArrayDeque<>();
    private final Consumer<ReplayEvent> evictionCallback;

    public CircularEventBuffer(int bufferDurationSeconds) {
        this(bufferDurationSeconds, null);
    }

    public CircularEventBuffer(int bufferDurationSeconds, Consumer<ReplayEvent> evictionCallback) {
        this.bufferDurationMs = bufferDurationSeconds * 1000;
        this.evictionCallback = evictionCallback;
    }

    public void pushEvent(ReplayEvent event) {
        List<ReplayEvent> evicted = null;
        synchronized (this) {
            buffer.addLast(event);
            int cutoff = event.getTimestampDeltaMs() - bufferDurationMs;
            while (!buffer.isEmpty() && buffer.peekFirst().getTimestampDeltaMs() < cutoff) {
                if (evictionCallback != null) {
                    if (evicted == null) evicted = new ArrayList<>();
                    evicted.add(buffer.pollFirst());
                } else {
                    buffer.pollFirst();
                }
            }
        }
        if (evicted != null) {
            for (ReplayEvent e : evicted) {
                evictionCallback.accept(e);
            }
        }
    }

    public synchronized List<ReplayEvent> drainPreRoll() {
        List<ReplayEvent> result = new ArrayList<>(buffer);
        buffer.clear();
        return result;
    }

    public synchronized int size() {
        return buffer.size();
    }

    public synchronized long memoryEstimateBytes() {
        return (long) buffer.size() * 64;
    }
}

package gg.modl.replay;

import gg.modl.replay.api.CircularEventBuffer;
import gg.modl.replay.format.ReplayEvent;
import gg.modl.replay.format.events.BlockChangeEvent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CircularEventBufferTest {

    // ── Size tracking ──────────────────────────────────────────────────

    @Test
    void sizeIncreasesOnPush() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);

        assertEquals(0, buffer.size());

        buffer.pushEvent(blockChange(1000));
        assertEquals(1, buffer.size());

        buffer.pushEvent(blockChange(2000));
        assertEquals(2, buffer.size());

        buffer.pushEvent(blockChange(3000));
        assertEquals(3, buffer.size());
    }

    @Test
    void sizeStartsAtZero() {
        CircularEventBuffer buffer = new CircularEventBuffer(5);
        assertEquals(0, buffer.size());
    }

    // ── Eviction based on bufferDuration ───────────────────────────────

    @Test
    void evictsOldEventsWhenExceedingBufferDuration() {
        // 5-second buffer
        CircularEventBuffer buffer = new CircularEventBuffer(5);

        // Push events spanning 0ms to 6000ms
        buffer.pushEvent(blockChange(0));
        buffer.pushEvent(blockChange(1000));
        buffer.pushEvent(blockChange(2000));
        buffer.pushEvent(blockChange(3000));
        buffer.pushEvent(blockChange(4000));
        assertEquals(5, buffer.size());

        // Push event at 5001ms: cutoff = 5001 - 5000 = 1; t=0 (<1) is evicted
        buffer.pushEvent(blockChange(5001));
        // remaining: 1000, 2000, 3000, 4000, 5001
        assertEquals(5, buffer.size());

        // Push event at 7001ms: cutoff = 7001 - 5000 = 2001; t=1000 and t=2000 (<2001) are evicted
        buffer.pushEvent(blockChange(7001));
        // remaining: 3000, 4000, 5001, 7001
        assertEquals(4, buffer.size());
    }

    @Test
    void evictsAllOldEventsOnLargeTimeJump() {
        CircularEventBuffer buffer = new CircularEventBuffer(5);

        buffer.pushEvent(blockChange(0));
        buffer.pushEvent(blockChange(1000));
        buffer.pushEvent(blockChange(2000));

        // Jump far ahead: all previous events should be evicted
        buffer.pushEvent(blockChange(100000));
        assertEquals(1, buffer.size());
    }

    @Test
    void doesNotEvictEventsWithinDuration() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);

        buffer.pushEvent(blockChange(0));
        buffer.pushEvent(blockChange(5000));
        buffer.pushEvent(blockChange(9999));

        // All within 10-second window, none should be evicted
        assertEquals(3, buffer.size());
    }

    // ── drainPreRoll ───────────────────────────────────────────────────

    @Test
    void drainPreRollReturnsEventsInOrder() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);

        buffer.pushEvent(blockChange(1000));
        buffer.pushEvent(blockChange(2000));
        buffer.pushEvent(blockChange(3000));

        List<ReplayEvent> drained = buffer.drainPreRoll();

        assertEquals(3, drained.size());
        assertEquals(1000, drained.get(0).getTimestampDeltaMs());
        assertEquals(2000, drained.get(1).getTimestampDeltaMs());
        assertEquals(3000, drained.get(2).getTimestampDeltaMs());
    }

    @Test
    void drainPreRollEmptiesBuffer() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);

        buffer.pushEvent(blockChange(1000));
        buffer.pushEvent(blockChange(2000));

        buffer.drainPreRoll();
        assertEquals(0, buffer.size());
    }

    @Test
    void drainPreRollOnEmptyBufferReturnsEmptyList() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);

        List<ReplayEvent> drained = buffer.drainPreRoll();

        assertNotNull(drained);
        assertTrue(drained.isEmpty());
    }

    @Test
    void drainPreRollCalledTwiceSecondReturnsEmpty() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);

        buffer.pushEvent(blockChange(1000));
        buffer.pushEvent(blockChange(2000));

        List<ReplayEvent> first = buffer.drainPreRoll();
        assertEquals(2, first.size());

        List<ReplayEvent> second = buffer.drainPreRoll();
        assertTrue(second.isEmpty());
    }

    @Test
    void drainPreRollAfterEviction() {
        CircularEventBuffer buffer = new CircularEventBuffer(5);

        buffer.pushEvent(blockChange(0));
        buffer.pushEvent(blockChange(1000));
        buffer.pushEvent(blockChange(6000)); // evicts t=0

        List<ReplayEvent> drained = buffer.drainPreRoll();
        assertEquals(2, drained.size());
        assertEquals(1000, drained.get(0).getTimestampDeltaMs());
        assertEquals(6000, drained.get(1).getTimestampDeltaMs());
    }

    // ── memoryEstimateBytes ────────────────────────────────────────────

    @Test
    void memoryEstimateBytesNonNegativeEmpty() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);
        assertTrue(buffer.memoryEstimateBytes() >= 0);
    }

    @Test
    void memoryEstimateBytesNonNegativeWithEvents() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);
        buffer.pushEvent(blockChange(1000));
        buffer.pushEvent(blockChange(2000));

        long estimate = buffer.memoryEstimateBytes();
        assertTrue(estimate >= 0, "Memory estimate should be non-negative");
        assertTrue(estimate > 0, "Memory estimate should be positive when buffer has events");
    }

    @Test
    void memoryEstimateBytesIncreasesWithMoreEvents() {
        CircularEventBuffer buffer = new CircularEventBuffer(60);

        buffer.pushEvent(blockChange(1000));
        long estimateOne = buffer.memoryEstimateBytes();

        buffer.pushEvent(blockChange(2000));
        long estimateTwo = buffer.memoryEstimateBytes();

        assertTrue(estimateTwo > estimateOne,
                "Memory estimate should increase with more events");
    }

    @Test
    void memoryEstimateBytesDecreasesAfterDrain() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);

        buffer.pushEvent(blockChange(1000));
        buffer.pushEvent(blockChange(2000));
        assertTrue(buffer.memoryEstimateBytes() > 0);

        buffer.drainPreRoll();
        assertEquals(0, buffer.memoryEstimateBytes());
    }

    // ── Buffer can be reused after drain ───────────────────────────────

    @Test
    void bufferCanBeReusedAfterDrain() {
        CircularEventBuffer buffer = new CircularEventBuffer(10);

        buffer.pushEvent(blockChange(1000));
        buffer.drainPreRoll();

        buffer.pushEvent(blockChange(2000));
        buffer.pushEvent(blockChange(3000));
        assertEquals(2, buffer.size());

        List<ReplayEvent> drained = buffer.drainPreRoll();
        assertEquals(2, drained.size());
        assertEquals(2000, drained.get(0).getTimestampDeltaMs());
        assertEquals(3000, drained.get(1).getTimestampDeltaMs());
    }

    // ── Helper ─────────────────────────────────────────────────────────

    private BlockChangeEvent blockChange(int timestampDeltaMs) {
        return new BlockChangeEvent(timestampDeltaMs, 0, (short) 64, 0, 1);
    }
}

package gg.modl.minecraft.replay;

import gg.modl.minecraft.replay.api.FileReplayOutput;
import gg.modl.minecraft.replay.api.RecordingConfig;
import gg.modl.minecraft.replay.api.ReplayMetadata;
import gg.modl.minecraft.replay.api.ReplayOutput;
import gg.modl.minecraft.replay.api.ReplaySession;
import gg.modl.minecraft.replay.format.ReplayEvent;
import gg.modl.minecraft.replay.format.ReplayHeader;
import gg.modl.minecraft.replay.format.events.BlockChangeEvent;
import gg.modl.minecraft.replay.format.events.ChatEvent;
import gg.modl.minecraft.replay.format.events.EntityMoveEvent;
import gg.modl.minecraft.replay.format.events.EntityRemoveEvent;
import gg.modl.minecraft.replay.format.events.EntitySpawnEvent;
import gg.modl.minecraft.replay.format.events.PlayerMoveEvent;
import gg.modl.minecraft.replay.format.events.PlayerSpawnEvent;
import gg.modl.minecraft.replay.util.BlockSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaySessionTest {

    private static final UUID TEST_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    // ── Full lifecycle ─────────────────────────────────────────────────

    @Test
    void fullLifecycleWriteAndReadBack(@TempDir Path tempDir) throws IOException {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        FileReplayOutput output = new FileReplayOutput(tempDir.toFile());
        ReplaySession session = new ReplaySession(config, output);

        // Not recording yet
        assertFalse(session.isRecording());
        assertEquals(0, session.getRecordedEventCount());

        // Set initial snapshot
        List<BlockSnapshot> snapshot = Arrays.asList(
                new BlockSnapshot(10, (short) 64, -20, 1),
                new BlockSnapshot(11, (short) 65, -19, 2)
        );
        session.setInitialSnapshot(snapshot);

        // Now recording
        assertTrue(session.isRecording());

        // Add pre-roll events
        List<ReplayEvent> preRoll = Arrays.asList(
                new BlockChangeEvent(100, 10, (short) 64, -20, 5),
                new PlayerSpawnEvent(200, TEST_UUID, "Tester", 10.0f, 64.0f, -20.0f,
                        0.0f, 0.0f, new byte[0])
        );
        session.addPreRollEvents(preRoll);
        assertEquals(2, session.getRecordedEventCount());

        // Record live events
        session.recordEvent(new PlayerMoveEvent(300, TEST_UUID, 11.0f, 64.0f, -19.0f,
                11.0f, 0.0f));
        assertEquals(3, session.getRecordedEventCount());

        session.recordEvent(new ChatEvent(400, TEST_UUID, "test message"));
        assertEquals(4, session.getRecordedEventCount());

        // Stop recording
        ReplayMetadata metadata = session.stop();
        assertFalse(session.isRecording());

        // Verify metadata
        assertNotNull(metadata);
        assertTrue(metadata.getDurationMs() >= 0, "Duration should be non-negative");
        assertEquals(4, metadata.getEventCount());
        assertTrue(metadata.getFileSizeBytes() > 0, "File size should be positive");
        assertEquals(output.getOutputFile(), metadata.getOutputFile());

        // Read back the output file and verify contents
        File outputFile = output.getOutputFile();
        assertNotNull(outputFile, "Output file should be set after recording");
        assertTrue(outputFile.exists(), "Output file should exist");

        try (ReplayReader reader = new ReplayReader(new FileInputStream(outputFile))) {
            // Verify header
            ReplayHeader header = reader.readHeader();
            assertEquals(4, header.getVersion());
            assertEquals("1.21.4", header.getMcVersion());
            assertEquals(config.getRadiusBlocks(), header.getRadiusBlocks());

            // Verify snapshot
            List<BlockSnapshot> readBlocks = reader.readSnapshot();
            assertEquals(2, readBlocks.size());
            assertEquals(10, readBlocks.get(0).getX());
            assertEquals(64, readBlocks.get(0).getY());
            assertEquals(-20, readBlocks.get(0).getZ());
            assertEquals(1, readBlocks.get(0).getStateId());

            // Verify events
            ReplayEvent event1 = reader.readEvent();
            assertInstanceOf(BlockChangeEvent.class, event1);
            assertEquals(100, event1.getTimestampDeltaMs());

            ReplayEvent event2 = reader.readEvent();
            assertInstanceOf(PlayerSpawnEvent.class, event2);
            assertEquals(200, event2.getTimestampDeltaMs());
            assertEquals("Tester", ((PlayerSpawnEvent) event2).getPlayerName());

            ReplayEvent event3 = reader.readEvent();
            assertInstanceOf(PlayerMoveEvent.class, event3);
            assertEquals(300, event3.getTimestampDeltaMs());

            ReplayEvent event4 = reader.readEvent();
            assertInstanceOf(ChatEvent.class, event4);
            assertEquals(400, event4.getTimestampDeltaMs());
            assertEquals("test message", ((ChatEvent) event4).getMessage());

            // No more events
            assertNull(reader.readEvent());
        }
    }

    // ── isRecording state transitions ──────────────────────────────────

    @Test
    void isRecordingStateTransitions(@TempDir Path tempDir) throws IOException {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        FileReplayOutput output = new FileReplayOutput(tempDir.toFile());
        ReplaySession session = new ReplaySession(config, output);

        // Initial state: not recording
        assertFalse(session.isRecording(), "Should not be recording initially");

        // After setInitialSnapshot: recording
        session.setInitialSnapshot(Collections.emptyList());
        assertTrue(session.isRecording(), "Should be recording after setInitialSnapshot");

        // After stop: not recording
        session.stop();
        assertFalse(session.isRecording(), "Should not be recording after stop");
    }

    // ── getRecordedEventCount ──────────────────────────────────────────

    @Test
    void eventCountIncrementsCorrectly(@TempDir Path tempDir) throws IOException {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        FileReplayOutput output = new FileReplayOutput(tempDir.toFile());
        ReplaySession session = new ReplaySession(config, output);

        session.setInitialSnapshot(Collections.emptyList());

        assertEquals(0, session.getRecordedEventCount());

        session.recordEvent(new BlockChangeEvent(100, 0, (short) 0, 0, 1));
        assertEquals(1, session.getRecordedEventCount());

        session.recordEvent(new BlockChangeEvent(200, 1, (short) 1, 1, 2));
        assertEquals(2, session.getRecordedEventCount());

        session.recordEvent(new BlockChangeEvent(300, 2, (short) 2, 2, 3));
        assertEquals(3, session.getRecordedEventCount());

        session.stop();
    }

    @Test
    void preRollEventsCountTowardsEventCount(@TempDir Path tempDir) throws IOException {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        FileReplayOutput output = new FileReplayOutput(tempDir.toFile());
        ReplaySession session = new ReplaySession(config, output);

        session.setInitialSnapshot(Collections.emptyList());

        List<ReplayEvent> preRoll = Arrays.asList(
                new BlockChangeEvent(100, 0, (short) 0, 0, 1),
                new BlockChangeEvent(200, 1, (short) 1, 1, 2)
        );
        session.addPreRollEvents(preRoll);
        assertEquals(2, session.getRecordedEventCount());

        session.recordEvent(new BlockChangeEvent(300, 2, (short) 2, 2, 3));
        assertEquals(3, session.getRecordedEventCount());

        session.stop();
    }

    // ── stop returns metadata ──────────────────────────────────────────

    @Test
    void stopReturnsMetadata(@TempDir Path tempDir) throws IOException {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        FileReplayOutput output = new FileReplayOutput(tempDir.toFile());
        ReplaySession session = new ReplaySession(config, output);

        session.setInitialSnapshot(Collections.emptyList());
        session.recordEvent(new BlockChangeEvent(100, 0, (short) 0, 0, 1));
        session.recordEvent(new BlockChangeEvent(200, 1, (short) 1, 1, 2));

        ReplayMetadata metadata = session.stop();

        assertNotNull(metadata, "stop() should return non-null metadata");
        assertTrue(metadata.getDurationMs() >= 0, "Duration should be non-negative");
        assertEquals(2, metadata.getEventCount());
        assertTrue(metadata.getFileSizeBytes() > 0, "File size should be positive for non-empty recording");
        assertEquals(output.getOutputFile(), metadata.getOutputFile());
    }

    // ── stop throws if not recording ───────────────────────────────────

    @Test
    void stopReturnsEmptyMetadataWhenNotRecording() {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        FileReplayOutput output = new FileReplayOutput(new File("/tmp/nonexistent"));
        ReplaySession session = new ReplaySession(config, output);

        ReplayMetadata metadata = session.stop();
        assertNotNull(metadata, "stop() should return non-null metadata even when not recording");
        assertEquals(0, metadata.getEventCount());
        assertEquals(0, metadata.getDurationMs());
        assertEquals(0, metadata.getFileSizeBytes());
    }

    @Test
    void setInitialSnapshotClosesOutputAndReportsErrorWhenSnapshotWriteFails() {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();
        TrackingReplayOutput output = new TrackingReplayOutput(new FailingOutputStream(8, false));
        ReplaySession session = new ReplaySession(config, output);

        List<BlockSnapshot> snapshot = new ArrayList<>();
        for (int i = 0; i < 700; i++) {
            snapshot.add(new BlockSnapshot(i, (short) 64, -20, 1));
        }

        IOException thrown = assertThrows(IOException.class, () -> session.setInitialSnapshot(snapshot));

        assertNotNull(thrown);
        assertFalse(session.isRecording());
        assertTrue(output.stream.closed);
        assertSame(thrown, output.error);
    }

    @Test
    void stopReturnsMetadataAndReportsErrorWhenFinalizationFails() throws IOException {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();
        TrackingReplayOutput output = new TrackingReplayOutput(new FailingOutputStream(Integer.MAX_VALUE, true));
        ReplaySession session = new ReplaySession(config, output);

        session.setInitialSnapshot(Collections.emptyList());
        session.recordEvent(new BlockChangeEvent(100, 0, (short) 0, 0, 1));

        ReplayMetadata metadata = session.stop();

        assertNotNull(metadata);
        assertEquals(1, metadata.getEventCount());
        assertSame(output.stream.closeFailure, output.error);
        assertFalse(session.isRecording());
    }

    // ── addPreRollEvents throws if not recording ───────────────────────

    @Test
    void addPreRollThrowsWhenNotRecording() {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        FileReplayOutput output = new FileReplayOutput(new File("/tmp/nonexistent"));
        ReplaySession session = new ReplaySession(config, output);

        List<ReplayEvent> preRoll = Arrays.asList(
                new BlockChangeEvent(100, 0, (short) 0, 0, 1)
        );
        assertThrows(IllegalStateException.class, () -> session.addPreRollEvents(preRoll),
                "addPreRollEvents should throw IllegalStateException when not recording");
    }

    // ── Output file is valid modlreplay ────────────────────────────────

    @Test
    void outputFileIsValidModlreplay(@TempDir Path tempDir) throws IOException {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        FileReplayOutput output = new FileReplayOutput(tempDir.toFile());
        ReplaySession session = new ReplaySession(config, output);

        List<BlockSnapshot> snapshot = Arrays.asList(
                new BlockSnapshot(5, (short) 10, 15, 99)
        );
        session.setInitialSnapshot(snapshot);

        session.recordEvent(new EntitySpawnEvent(500, 1, (short) 10, 5.0f, 10.0f, 15.0f, new byte[0]));
        session.recordEvent(new EntityMoveEvent(1000, 1, 6.0f, 10.0f, 16.0f, 1.1f, -0.55f));
        session.recordEvent(new EntityRemoveEvent(1500, 1));

        session.stop();

        // Read back to verify it is a valid file
        File outputFile = output.getOutputFile();
        try (ReplayReader reader = new ReplayReader(new FileInputStream(outputFile))) {
            ReplayHeader header = reader.readHeader();
            assertNotNull(header);

            List<BlockSnapshot> blocks = reader.readSnapshot();
            assertEquals(1, blocks.size());

            int count = 0;
            while (reader.readEvent() != null) {
                count++;
            }
            assertEquals(3, count);
        }
    }

    // ── Empty snapshot recording ───────────────────────────────────────

    @Test
    void recordingWithEmptySnapshotAndNoEvents(@TempDir Path tempDir) throws IOException {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        FileReplayOutput output = new FileReplayOutput(tempDir.toFile());
        ReplaySession session = new ReplaySession(config, output);

        session.setInitialSnapshot(Collections.emptyList());
        ReplayMetadata metadata = session.stop();

        assertNotNull(metadata);
        assertEquals(0, metadata.getEventCount());

        // Verify the file is still a valid (but minimal) modlreplay
        File outputFile = output.getOutputFile();
        try (ReplayReader reader = new ReplayReader(new FileInputStream(outputFile))) {
            ReplayHeader header = reader.readHeader();
            assertNotNull(header);

            List<BlockSnapshot> blocks = reader.readSnapshot();
            assertTrue(blocks.isEmpty());

            assertNull(reader.readEvent());
        }
    }

    // ── RecordingConfig defaults ───────────────────────────────────────

    @Test
    void recordingConfigDefaults() {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.21.4")
                .build();

        assertEquals(128, config.getRadiusBlocks());
        assertEquals(10, config.getBufferDurationSeconds());
        assertEquals(300, config.getMaxDurationSeconds());
        assertEquals("1.21.4", config.getMcVersion());
    }

    @Test
    void recordingConfigCustomValues() {
        RecordingConfig config = RecordingConfig.builder()
                .mcVersion("1.20.1")
                .radiusBlocks(256)
                .bufferDurationSeconds(30)
                .maxDurationSeconds(600)
                .build();

        assertEquals(256, config.getRadiusBlocks());
        assertEquals(30, config.getBufferDurationSeconds());
        assertEquals(600, config.getMaxDurationSeconds());
        assertEquals("1.20.1", config.getMcVersion());
    }

    private static class TrackingReplayOutput implements ReplayOutput {
        private final FailingOutputStream stream;
        private Exception error;

        private TrackingReplayOutput(FailingOutputStream stream) {
            this.stream = stream;
        }

        @Override
        public OutputStream openStream() {
            return stream;
        }

        @Override
        public void onComplete(ReplayMetadata metadata) {
        }

        @Override
        public void onError(Exception e) {
            this.error = e;
        }
    }

    private static class FailingOutputStream extends OutputStream {
        private final int failAfterBytes;
        private final boolean failOnClose;
        private int written;
        private boolean closed;

        private FailingOutputStream(int failAfterBytes, boolean failOnClose) {
            this.failAfterBytes = failAfterBytes;
            this.failOnClose = failOnClose;
        }

        @Override
        public void write(int b) throws IOException {
            if (written >= failAfterBytes) {
                throw new IOException("write failed");
            }
            written++;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failOnClose) {
                closeFailure = new IOException("close failed");
                throw closeFailure;
            }
        }

        private IOException closeFailure;
    }
}

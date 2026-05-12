package gg.modl.minecraft.replay.api;

import gg.modl.minecraft.replay.ReplayWriter;
import gg.modl.minecraft.replay.format.ReplayEvent;
import gg.modl.minecraft.replay.format.ReplayHeader;
import gg.modl.minecraft.replay.util.BlockSnapshot;
import gg.modl.minecraft.replay.util.FormatConstants;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class ReplaySession {

    private final RecordingConfig config;
    private final ReplayOutput output;
    private ReplayWriter writer;
    private OutputStream outputStream;
    private volatile boolean recording;
    private long startTimeMs;
    private long eventCount;

    public ReplaySession(RecordingConfig config, ReplayOutput output) {
        this.config = config;
        this.output = output;
    }

    public void setInitialSnapshot(List<BlockSnapshot> blocks) throws IOException {
        this.startTimeMs = System.currentTimeMillis();
        try {
            this.outputStream = output.openStream();
            this.writer = new ReplayWriter(outputStream);

            ReplayHeader header = ReplayHeader.builder()
                    .version(FormatConstants.VERSION)
                    .startTime(startTimeMs)
                    .mcVersion(config.getMcVersion())
                    .targetX(0)
                    .targetY(0)
                    .targetZ(0)
                    .radiusBlocks(config.getRadiusBlocks())
                    .build();

            writer.writeHeader(header);
            writer.writeSnapshot(blocks);
            this.recording = true;
        } catch (IOException e) {
            recording = false;
            closeAfterFailedStart(e);
            output.onError(e);
            throw e;
        }
    }

    public void addPreRollEvents(List<ReplayEvent> preRoll) throws IOException {
        if (!recording) throw new IllegalStateException("Not recording");
        for (ReplayEvent event : preRoll) {
            writer.writeEvent(event);
            eventCount++;
        }
    }

    public void recordEvent(ReplayEvent event) throws IOException {
        if (!recording) return;

        // Check max duration
        long elapsed = System.currentTimeMillis() - startTimeMs;
        if (elapsed > config.getMaxDurationSeconds() * 1000L) {
            stop();
            return;
        }

        writer.writeEvent(event);
        eventCount++;
    }

    public boolean isRecording() {
        return recording;
    }

    public long getElapsedMs() {
        if (startTimeMs == 0) return 0;
        return System.currentTimeMillis() - startTimeMs;
    }

    public long getRecordedEventCount() {
        return eventCount;
    }

    public ReplayMetadata stop() {
        if (!recording) {
            // Snapshot never finished writing — nothing to flush
            return ReplayMetadata.builder()
                    .durationMs(0)
                    .eventCount(0)
                    .fileSizeBytes(0)
                    .build();
        }
        recording = false;

        long durationMs = System.currentTimeMillis() - startTimeMs;
        long fileSize = 0;

        File outputFile = null;
        if (output instanceof FileReplayOutput) {
            outputFile = ((FileReplayOutput) output).getOutputFile();
        }

        try {
            writer.flush();
            writer.close();

            if (outputFile != null) fileSize = outputFile.length();

            ReplayMetadata metadata = ReplayMetadata.builder()
                    .durationMs(durationMs)
                    .eventCount(eventCount)
                    .fileSizeBytes(fileSize)
                    .outputFile(outputFile)
                    .build();

            output.onComplete(metadata);
            return metadata;
        } catch (IOException e) {
            output.onError(e);
            if (outputFile != null) fileSize = outputFile.length();
            return ReplayMetadata.builder()
                    .durationMs(durationMs)
                    .eventCount(eventCount)
                    .fileSizeBytes(fileSize)
                    .outputFile(outputFile)
                    .build();
        }
    }

    private void closeAfterFailedStart(IOException original) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException closeFailure) {
            original.addSuppressed(closeFailure);
        } finally {
            writer = null;
            outputStream = null;
        }
    }
}

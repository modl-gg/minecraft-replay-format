package gg.modl.replay.api;

import lombok.Builder;
import lombok.Value;

import java.io.File;

@Value
@Builder
public class ReplayMetadata {
    long durationMs;
    long eventCount;
    long fileSizeBytes;
    File outputFile;
}

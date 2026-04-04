package gg.modl.minecraft.replay.api;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RecordingConfig {
    @Builder.Default int radiusBlocks = 128;
    @Builder.Default int bufferDurationSeconds = 10;
    @Builder.Default int maxDurationSeconds = 300;
    String mcVersion;
}

package gg.modl.replay.format;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReplayHeader {
    int version;
    long startTime;
    String mcVersion;
    int targetX;
    int targetY;
    int targetZ;
    int radiusBlocks;
}

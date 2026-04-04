package gg.modl.replay.util;

import lombok.Value;

@Value
public class BlockSnapshot {
    int x;
    short y;
    int z;
    int stateId;
}

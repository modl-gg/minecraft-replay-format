package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;

@Getter
@ToString
public class BlockChangeEvent extends ReplayEvent {
    private final int x;
    private final short y;
    private final int z;
    private final int stateId;

    public BlockChangeEvent(int timestampDeltaMs, int x, short y, int z, int stateId) {
        super(timestampDeltaMs);
        this.x = x;
        this.y = y;
        this.z = z;
        this.stateId = stateId;
    }

    @Override public EventType getType() { return EventType.BLOCK_CHANGE; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeInt(x);
        out.writeShort(y);
        out.writeInt(z);
        out.writeInt(stateId);
    }

    @Override
    public int payloadSize() {
        return 4 + 2 + 4 + 4;
    }
}

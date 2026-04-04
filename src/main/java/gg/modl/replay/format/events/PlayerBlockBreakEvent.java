package gg.modl.replay.format.events;

import gg.modl.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

@Getter
@ToString
public class PlayerBlockBreakEvent extends ReplayEvent {
    private final UUID breakerUuid;
    private final int x;
    private final short y;
    private final int z;
    private final int previousStateId;

    public PlayerBlockBreakEvent(int timestampDeltaMs, UUID breakerUuid, int x, short y, int z, int previousStateId) {
        super(timestampDeltaMs);
        this.breakerUuid = breakerUuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.previousStateId = previousStateId;
    }

    @Override public EventType getType() { return EventType.PLAYER_BLOCK_BREAK; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(breakerUuid.getMostSignificantBits());
        out.writeLong(breakerUuid.getLeastSignificantBits());
        out.writeInt(x);
        out.writeShort(y);
        out.writeInt(z);
        out.writeInt(previousStateId);
    }

    @Override
    public int payloadSize() {
        return 16 + 4 + 2 + 4 + 4; // UUID(16) + x(4) + y(2) + z(4) + previousStateId(4) = 30
    }
}

package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

@Getter
@ToString
public class PlayerBlockPlaceEvent extends ReplayEvent {
    private final UUID placerUuid;
    private final int x;
    private final short y;
    private final int z;
    private final int stateId;

    public PlayerBlockPlaceEvent(int timestampDeltaMs, UUID placerUuid, int x, short y, int z, int stateId) {
        super(timestampDeltaMs);
        this.placerUuid = placerUuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.stateId = stateId;
    }

    @Override public EventType getType() { return EventType.PLAYER_BLOCK_PLACE; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(placerUuid.getMostSignificantBits());
        out.writeLong(placerUuid.getLeastSignificantBits());
        out.writeInt(x);
        out.writeShort(y);
        out.writeInt(z);
        out.writeInt(stateId);
    }

    @Override
    public int payloadSize() {
        return 16 + 4 + 2 + 4 + 4; // UUID(16) + x(4) + y(2) + z(4) + stateId(4) = 30
    }
}

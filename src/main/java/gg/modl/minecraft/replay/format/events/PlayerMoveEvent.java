package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

@Getter
@ToString
public class PlayerMoveEvent extends ReplayEvent {
    private final UUID uuid;
    private final float x, y, z;
    private final float yaw, pitch;

    public PlayerMoveEvent(int timestampDeltaMs, UUID uuid, float x, float y, float z, float yaw, float pitch) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override public EventType getType() { return EventType.PLAYER_MOVE; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
    }

    @Override
    public int payloadSize() {
        return 16 + 12 + 8; // UUID(16) + xyz(12) + yaw+pitch(8)
    }
}

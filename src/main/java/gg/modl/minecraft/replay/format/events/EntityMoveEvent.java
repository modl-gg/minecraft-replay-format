package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;

@Getter
@ToString
public class EntityMoveEvent extends ReplayEvent {
    private final int entityId;
    private final float x, y, z;
    private final float yaw, pitch;

    public EntityMoveEvent(int timestampDeltaMs, int entityId, float x, float y, float z, float yaw, float pitch) {
        super(timestampDeltaMs);
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override public EventType getType() { return EventType.ENTITY_MOVE; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeInt(entityId);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
    }

    @Override
    public int payloadSize() {
        return 4 + 12 + 8; // entityId(4) + xyz(12) + yaw+pitch(8)
    }
}

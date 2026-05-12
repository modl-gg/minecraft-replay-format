package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;

@Getter
@ToString
public class EntitySpawnEvent extends ReplayEvent {
    private final int entityId;
    private final short entityTypeId;
    private final float x, y, z;
    private final byte[] metadata;

    public EntitySpawnEvent(int timestampDeltaMs, int entityId, short entityTypeId, float x, float y, float z, byte[] metadata) {
        super(timestampDeltaMs);
        this.entityId = entityId;
        this.entityTypeId = entityTypeId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.metadata = metadata != null ? metadata.clone() : new byte[0];
    }

    public byte[] getMetadata() {
        return metadata.clone();
    }

    @Override public EventType getType() { return EventType.ENTITY_SPAWN; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeInt(entityId);
        out.writeShort(entityTypeId);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
        out.writeShort(metadata.length);
        out.write(metadata);
    }

    @Override
    public int payloadSize() {
        return 4 + 2 + 12 + 2 + metadata.length;
    }
}

package gg.modl.replay.format.events;

import gg.modl.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;

@Getter
@ToString
public class EntityRemoveEvent extends ReplayEvent {
    private final int entityId;

    public EntityRemoveEvent(int timestampDeltaMs, int entityId) {
        super(timestampDeltaMs);
        this.entityId = entityId;
    }

    @Override public EventType getType() { return EventType.ENTITY_REMOVE; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeInt(entityId);
    }

    @Override
    public int payloadSize() {
        return 4;
    }
}

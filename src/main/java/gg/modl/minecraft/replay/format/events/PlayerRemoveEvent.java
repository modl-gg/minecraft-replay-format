package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

@Getter
@ToString
public class PlayerRemoveEvent extends ReplayEvent {
    private final UUID uuid;

    public PlayerRemoveEvent(int timestampDeltaMs, UUID uuid) {
        super(timestampDeltaMs);
        this.uuid = uuid;
    }

    @Override public EventType getType() { return EventType.PLAYER_REMOVE; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    @Override
    public int payloadSize() {
        return 16;
    }
}

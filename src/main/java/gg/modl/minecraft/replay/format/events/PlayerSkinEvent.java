package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

@Getter
@ToString
public class PlayerSkinEvent extends ReplayEvent {
    private final UUID uuid;
    private final byte[] skinPng;

    public PlayerSkinEvent(int timestampDeltaMs, UUID uuid, byte[] skinPng) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.skinPng = skinPng != null ? skinPng : new byte[0];
    }

    @Override
    public EventType getType() { return EventType.PLAYER_SKIN; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeInt(skinPng.length);
        out.write(skinPng);
    }

    @Override
    public int payloadSize() {
        return 16 + 4 + skinPng.length;
    }
}

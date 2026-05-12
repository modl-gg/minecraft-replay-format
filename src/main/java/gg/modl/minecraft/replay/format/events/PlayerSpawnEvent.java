package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Getter
@ToString
public class PlayerSpawnEvent extends ReplayEvent {
    private final UUID uuid;
    private final String playerName;
    private final float x, y, z;
    private final float yaw, pitch;
    private final byte[] equipment;

    public PlayerSpawnEvent(int timestampDeltaMs, UUID uuid, String playerName, float x, float y, float z, float yaw, float pitch, byte[] equipment) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.playerName = playerName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.equipment = equipment != null ? equipment.clone() : new byte[0];
    }

    public byte[] getEquipment() {
        return equipment.clone();
    }

    @Override public EventType getType() { return EventType.PLAYER_SPAWN; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        byte[] nameBytes = playerName.getBytes(StandardCharsets.UTF_8);
        out.writeByte(nameBytes.length);
        out.write(nameBytes);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
        out.writeShort(equipment.length);
        out.write(equipment);
    }

    @Override
    public int payloadSize() {
        byte[] nameBytes = playerName.getBytes(StandardCharsets.UTF_8);
        return 16 + 1 + nameBytes.length + 12 + 8 + 2 + equipment.length;
    }
}

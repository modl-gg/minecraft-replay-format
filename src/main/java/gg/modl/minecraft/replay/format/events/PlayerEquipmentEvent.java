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
public class PlayerEquipmentEvent extends ReplayEvent {

    private final UUID uuid;
    private final String itemName;

    public PlayerEquipmentEvent(int timestampDeltaMs, UUID uuid, String itemName) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.itemName = itemName;
    }

    @Override public EventType getType() { return EventType.PLAYER_EQUIPMENT; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        byte[] nameBytes = itemName.getBytes(StandardCharsets.UTF_8);
        out.writeByte(nameBytes.length);
        out.write(nameBytes);
    }

    @Override
    public int payloadSize() {
        return 16 + 1 + itemName.getBytes(StandardCharsets.UTF_8).length;
    }
}

package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

@Getter
@ToString
public class PlayerHealthEvent extends ReplayEvent {

    private final UUID uuid;
    private final float health;
    private final byte food;
    private final float saturation;

    public PlayerHealthEvent(int timestampDeltaMs, UUID uuid, float health, byte food, float saturation) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
    }

    @Override public EventType getType() { return EventType.PLAYER_HEALTH; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeFloat(health);
        out.writeByte(food);
        out.writeFloat(saturation);
    }

    @Override
    public int payloadSize() {
        return 16 + 4 + 1 + 4;
    }
}

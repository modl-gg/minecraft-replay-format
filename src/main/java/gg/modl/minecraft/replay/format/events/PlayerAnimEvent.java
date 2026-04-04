package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

@Getter
@ToString
public class PlayerAnimEvent extends ReplayEvent {

    public enum AnimationType {
        SWING_MAIN_ARM(0),
        SWING_OFF_ARM(1),
        SNEAK_START(2),
        SNEAK_STOP(3),
        DAMAGE(4);

        @Getter private final int id;
        AnimationType(int id) { this.id = id; }

        public static AnimationType fromId(int id) {
            for (AnimationType a : values()) {
                if (a.id == id) return a;
            }
            throw new IllegalArgumentException("Unknown animation type: " + id);
        }
    }

    private final UUID uuid;
    private final AnimationType animationType;

    public PlayerAnimEvent(int timestampDeltaMs, UUID uuid, AnimationType animationType) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.animationType = animationType;
    }

    @Override public EventType getType() { return EventType.PLAYER_ANIM; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeByte(animationType.getId());
    }

    @Override
    public int payloadSize() {
        return 16 + 1;
    }
}

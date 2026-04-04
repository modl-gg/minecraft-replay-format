package gg.modl.replay.format.events;

import gg.modl.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@ToString
public class PlayerEffectsEvent extends ReplayEvent {

    private final UUID uuid;
    private final List<EffectEntry> effects;

    public PlayerEffectsEvent(int timestampDeltaMs, UUID uuid, List<EffectEntry> effects) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.effects = effects;
    }

    public static PlayerEffectsEvent fromList(int timestampDeltaMs, UUID uuid, List<int[]> effectList) {
        List<EffectEntry> entries = new ArrayList<>();
        for (int[] e : effectList) {
            entries.add(new EffectEntry(e[0], e[1], e[2]));
        }
        return new PlayerEffectsEvent(timestampDeltaMs, uuid, entries);
    }

    @Override public EventType getType() { return EventType.PLAYER_EFFECTS; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeByte(effects.size());
        for (EffectEntry e : effects) {
            out.writeByte(e.effectTypeId);
            out.writeByte(e.amplifier);
            out.writeInt(e.durationTicks);
        }
    }

    @Override
    public int payloadSize() {
        return 16 + 1 + effects.size() * 6; // UUID + count + per-effect(1+1+4)
    }

    @Getter
    @ToString
    public static class EffectEntry {
        private final int effectTypeId;
        private final int amplifier;
        private final int durationTicks;

        public EffectEntry(int effectTypeId, int amplifier, int durationTicks) {
            this.effectTypeId = effectTypeId;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
        }
    }
}

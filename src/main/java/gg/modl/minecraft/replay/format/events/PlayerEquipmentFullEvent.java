package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@ToString
public class PlayerEquipmentFullEvent extends ReplayEvent {

    private final UUID uuid;
    private final List<SlotEntry> slots;

    public PlayerEquipmentFullEvent(int timestampDeltaMs, UUID uuid, List<SlotEntry> slots) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.slots = slots != null ? Collections.unmodifiableList(new ArrayList<>(slots)) : Collections.emptyList();
    }

    public static PlayerEquipmentFullEvent fromMap(int timestampDeltaMs, UUID uuid, Map<Integer, String> slotMap) {
        List<SlotEntry> slots = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : slotMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                slots.add(new SlotEntry(entry.getKey(), entry.getValue()));
            }
        }
        return new PlayerEquipmentFullEvent(timestampDeltaMs, uuid, slots);
    }

    @Override public EventType getType() { return EventType.PLAYER_EQUIPMENT_FULL; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeByte(slots.size());
        for (SlotEntry slot : slots) {
            out.writeByte(slot.slotId);
            byte[] nameBytes = slot.itemName.getBytes(StandardCharsets.UTF_8);
            out.writeByte(nameBytes.length);
            out.write(nameBytes);
            // V3: enchantments
            out.writeByte(slot.enchantments.size());
            for (EnchantEntry ench : slot.enchantments) {
                byte[] enchIdBytes = ench.enchantId.getBytes(StandardCharsets.UTF_8);
                out.writeByte(enchIdBytes.length);
                out.write(enchIdBytes);
                out.writeByte(ench.level);
            }
        }
    }

    @Override
    public int payloadSize() {
        int size = 16 + 1; // UUID + slotCount
        for (SlotEntry slot : slots) {
            byte[] nameBytes = slot.itemName.getBytes(StandardCharsets.UTF_8);
            size += 1 + 1 + nameBytes.length + 1; // slotId + nameLen + name + enchCount
            for (EnchantEntry ench : slot.enchantments) {
                byte[] enchIdBytes = ench.enchantId.getBytes(StandardCharsets.UTF_8);
                size += 1 + enchIdBytes.length + 1; // enchIdLen + enchId + level
            }
        }
        return size;
    }

    @Getter
    @ToString
    public static class EnchantEntry {
        private final String enchantId;
        private final int level;

        public EnchantEntry(String enchantId, int level) {
            this.enchantId = enchantId;
            this.level = level;
        }
    }

    @Getter
    @ToString
    public static class SlotEntry {
        private final int slotId;
        private final String itemName;
        private final List<EnchantEntry> enchantments;

        public SlotEntry(int slotId, String itemName) {
            this.slotId = slotId;
            this.itemName = itemName;
            this.enchantments = Collections.emptyList();
        }

        public SlotEntry(int slotId, String itemName, List<EnchantEntry> enchantments) {
            this.slotId = slotId;
            this.itemName = itemName;
            this.enchantments = enchantments != null
                    ? Collections.unmodifiableList(new ArrayList<>(enchantments))
                    : Collections.emptyList();
        }
    }
}

package gg.modl.replay.format.events;

import gg.modl.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@ToString
public class PlayerInventoryEvent extends ReplayEvent {

    private final UUID uuid;
    private final boolean fullSnapshot;
    private final List<SlotEntry> slots;

    public PlayerInventoryEvent(int timestampDeltaMs, UUID uuid, boolean fullSnapshot, List<SlotEntry> slots) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.fullSnapshot = fullSnapshot;
        this.slots = slots;
    }

    @Override public EventType getType() { return EventType.PLAYER_INVENTORY; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        out.writeByte(fullSnapshot ? 1 : 0);
        out.writeByte(slots.size());
        for (SlotEntry slot : slots) {
            out.writeByte(slot.slotIndex);
            byte[] nameBytes = slot.itemName.getBytes(StandardCharsets.UTF_8);
            out.writeShort(nameBytes.length);
            out.write(nameBytes);
            out.writeByte(slot.count);
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
        int size = 16 + 1 + 1; // UUID + fullSnapshot + slotCount
        for (SlotEntry slot : slots) {
            byte[] nameBytes = slot.itemName.getBytes(StandardCharsets.UTF_8);
            size += 1 + 2 + nameBytes.length + 1 + 1; // slotIndex + nameLen + name + count + enchCount
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
        private final int slotIndex;
        private final String itemName;
        private final int count;
        private final List<EnchantEntry> enchantments;

        public SlotEntry(int slotIndex, String itemName, int count) {
            this.slotIndex = slotIndex;
            this.itemName = itemName;
            this.count = count;
            this.enchantments = Collections.emptyList();
        }

        public SlotEntry(int slotIndex, String itemName, int count, List<EnchantEntry> enchantments) {
            this.slotIndex = slotIndex;
            this.itemName = itemName;
            this.count = count;
            this.enchantments = enchantments != null ? enchantments : Collections.emptyList();
        }
    }
}

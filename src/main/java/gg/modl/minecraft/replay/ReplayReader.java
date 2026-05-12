package gg.modl.minecraft.replay;

import gg.modl.minecraft.replay.format.ReplayEvent;
import gg.modl.minecraft.replay.format.ReplayHeader;
import gg.modl.minecraft.replay.format.events.BlockChangeEvent;
import gg.modl.minecraft.replay.format.events.ChatEvent;
import gg.modl.minecraft.replay.format.events.EntityMoveEvent;
import gg.modl.minecraft.replay.format.events.EntityRemoveEvent;
import gg.modl.minecraft.replay.format.events.EntitySpawnEvent;
import gg.modl.minecraft.replay.format.events.PlayerAnimEvent;
import gg.modl.minecraft.replay.format.events.PlayerBlockBreakEvent;
import gg.modl.minecraft.replay.format.events.PlayerBlockPlaceEvent;
import gg.modl.minecraft.replay.format.events.PlayerEffectsEvent;
import gg.modl.minecraft.replay.format.events.PlayerEquipmentEvent;
import gg.modl.minecraft.replay.format.events.PlayerEquipmentFullEvent;
import gg.modl.minecraft.replay.format.events.PlayerHealthEvent;
import gg.modl.minecraft.replay.format.events.PlayerInventoryEvent;
import gg.modl.minecraft.replay.format.events.PlayerMoveEvent;
import gg.modl.minecraft.replay.format.events.PlayerRemoveEvent;
import gg.modl.minecraft.replay.format.events.PlayerSkinEvent;
import gg.modl.minecraft.replay.format.events.PlayerSpawnEvent;
import gg.modl.minecraft.replay.util.BlockSnapshot;
import gg.modl.minecraft.replay.util.FormatConstants;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

public class ReplayReader implements Closeable {

    private final DataInputStream in;
    private int formatVersion = 2; // default for backwards compat

    public ReplayReader(InputStream inputStream) throws IOException {
        this.in = new DataInputStream(wrapIfGzip(inputStream));
    }

    private static InputStream wrapIfGzip(InputStream input) throws IOException {
        BufferedInputStream buffered = (input instanceof BufferedInputStream)
                ? (BufferedInputStream) input : new BufferedInputStream(input, 8192);
        buffered.mark(2);
        int b0 = buffered.read();
        int b1 = buffered.read();
        buffered.reset();
        if (b0 == 0x1F && b1 == 0x8B) {
            return new GZIPInputStream(buffered);
        }
        return buffered;
    }

    public ReplayHeader readHeader() throws IOException {
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'M' || magic[1] != 'O' || magic[2] != 'D' || magic[3] != 'L') {
            throw new IOException("Invalid magic bytes: not a .modlreplay file");
        }

        int version = in.readUnsignedShort();
        this.formatVersion = version;

        long startTime = in.readLong();
        int mcVersionLen = in.readUnsignedShort();
        byte[] mcVersionBytes = new byte[mcVersionLen];
        in.readFully(mcVersionBytes);
        String mcVersion = new String(mcVersionBytes, StandardCharsets.UTF_8);

        int targetX = in.readInt();
        int targetY = in.readInt();
        int targetZ = in.readInt();
        int radiusBlocks = in.readInt();

        return ReplayHeader.builder()
                .version(version)
                .startTime(startTime)
                .mcVersion(mcVersion)
                .targetX(targetX)
                .targetY(targetY)
                .targetZ(targetZ)
                .radiusBlocks(radiusBlocks)
                .build();
    }

    public List<BlockSnapshot> readSnapshot() throws IOException {
        int blockCount = in.readInt();
        List<BlockSnapshot> blocks = new ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            int x = in.readInt();
            short y = in.readShort();
            int z = in.readInt();
            int stateId = in.readInt();
            blocks.add(new BlockSnapshot(x, y, z, stateId));
        }
        return blocks;
    }

    /**
     * Reads the next event from the stream.
     * Returns null on EOF.
     */
    public ReplayEvent readEvent() throws IOException {
        while (true) {
            int eventTypeByte;
            try {
                eventTypeByte = in.readUnsignedByte();
            } catch (EOFException e) {
                return null;
            }

            ReplayEvent.EventType type = ReplayEvent.EventType.fromId(eventTypeByte);
            int timestampDeltaMs = in.readInt();

            if (formatVersion >= 4) {
                int payloadLength = in.readUnsignedShort();
                byte[] payload = new byte[payloadLength];
                in.readFully(payload);
                if (type == null) {
                    continue;
                }
                try (DataInputStream payloadInput = new DataInputStream(new ByteArrayInputStream(payload))) {
                    return readKnownEvent(type, timestampDeltaMs, payloadInput);
                }
            }

            if (type == null) {
                throw new IOException("Unknown event type: 0x" + Integer.toHexString(eventTypeByte));
            }

            return readKnownEvent(type, timestampDeltaMs, in);
        }
    }

    private ReplayEvent readKnownEvent(ReplayEvent.EventType type, int timestampDeltaMs, DataInputStream source) throws IOException {
        switch (type) {
            case BLOCK_CHANGE: {
                int x = source.readInt();
                short y = source.readShort();
                int z = source.readInt();
                int stateId = source.readInt();
                return new BlockChangeEvent(timestampDeltaMs, x, y, z, stateId);
            }
            case ENTITY_SPAWN: {
                int entityId = source.readInt();
                short entityTypeId = source.readShort();
                float x = source.readFloat();
                float y = source.readFloat();
                float z = source.readFloat();
                int metaLen = source.readUnsignedShort();
                byte[] metadata = new byte[metaLen];
                if (metaLen > 0) source.readFully(metadata);
                return new EntitySpawnEvent(timestampDeltaMs, entityId, entityTypeId, x, y, z, metadata);
            }
            case ENTITY_MOVE: {
                int entityId = source.readInt();
                float x = source.readFloat();
                float y = source.readFloat();
                float z = source.readFloat();
                float yaw, pitch;
                if (formatVersion >= 4) {
                    yaw = source.readFloat();
                    pitch = source.readFloat();
                } else {
                    yaw = FormatConstants.decodeAngle(source.readShort());
                    pitch = FormatConstants.decodeAngle(source.readShort());
                }
                return new EntityMoveEvent(timestampDeltaMs, entityId, x, y, z, yaw, pitch);
            }
            case ENTITY_REMOVE: {
                int entityId = source.readInt();
                return new EntityRemoveEvent(timestampDeltaMs, entityId);
            }
            case PLAYER_SPAWN: {
                long uuidMost = source.readLong();
                long uuidLeast = source.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int nameLen = source.readUnsignedByte();
                byte[] nameBytes = new byte[nameLen];
                source.readFully(nameBytes);
                String playerName = new String(nameBytes, StandardCharsets.UTF_8);
                float x = source.readFloat();
                float y = source.readFloat();
                float z = source.readFloat();
                float yaw, pitch;
                if (formatVersion >= 4) {
                    yaw = source.readFloat();
                    pitch = source.readFloat();
                } else {
                    yaw = FormatConstants.decodeAngle(source.readShort());
                    pitch = FormatConstants.decodeAngle(source.readShort());
                }
                int equipLen = source.readUnsignedShort();
                byte[] equipment = new byte[equipLen];
                if (equipLen > 0) source.readFully(equipment);
                return new PlayerSpawnEvent(timestampDeltaMs, uuid, playerName, x, y, z, yaw, pitch, equipment);
            }
            case PLAYER_MOVE: {
                long uuidMost = source.readLong();
                long uuidLeast = source.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                float x = source.readFloat();
                float y = source.readFloat();
                float z = source.readFloat();
                float yaw, pitch;
                if (formatVersion >= 4) {
                    yaw = source.readFloat();
                    pitch = source.readFloat();
                } else {
                    yaw = FormatConstants.decodeAngle(source.readShort());
                    pitch = FormatConstants.decodeAngle(source.readShort());
                }
                return new PlayerMoveEvent(timestampDeltaMs, uuid, x, y, z, yaw, pitch);
            }
            case PLAYER_REMOVE: {
                long uuidMost = source.readLong();
                long uuidLeast = source.readLong();
                return new PlayerRemoveEvent(timestampDeltaMs, new UUID(uuidMost, uuidLeast));
            }
            case PLAYER_ANIM: {
                long uuidMost = source.readLong();
                long uuidLeast = source.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int animId = source.readUnsignedByte();
                return new PlayerAnimEvent(timestampDeltaMs, uuid, PlayerAnimEvent.AnimationType.fromId(animId));
            }
            case CHAT: {
                long uuidMost = source.readLong();
                long uuidLeast = source.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int msgLen = source.readUnsignedShort();
                byte[] msgBytes = new byte[msgLen];
                source.readFully(msgBytes);
                return new ChatEvent(timestampDeltaMs, uuid, new String(msgBytes, StandardCharsets.UTF_8));
            }
            case PLAYER_EQUIPMENT: {
                long uuidMost = source.readLong();
                long uuidLeast = source.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int nameLen = source.readUnsignedByte();
                byte[] nameBytes = new byte[nameLen];
                if (nameLen > 0) source.readFully(nameBytes);
                return new PlayerEquipmentEvent(timestampDeltaMs, uuid, new String(nameBytes, StandardCharsets.UTF_8));
            }
            case PLAYER_EQUIPMENT_FULL: {
                long eqfUuidMost = source.readLong();
                long eqfUuidLeast = source.readLong();
                UUID eqfUuid = new UUID(eqfUuidMost, eqfUuidLeast);
                int eqfSlotCount = source.readUnsignedByte();
                List<PlayerEquipmentFullEvent.SlotEntry> eqfSlots = new ArrayList<>();
                for (int i = 0; i < eqfSlotCount; i++) {
                    int slotId = source.readUnsignedByte();
                    int itemNameLen = source.readUnsignedByte();
                    byte[] itemNameBytes = new byte[itemNameLen];
                    if (itemNameLen > 0) source.readFully(itemNameBytes);
                    String itemName = new String(itemNameBytes, StandardCharsets.UTF_8);
                    List<PlayerEquipmentFullEvent.EnchantEntry> enchants = Collections.emptyList();
                    if (formatVersion >= 3) {
                        int enchCount = source.readUnsignedByte();
                        if (enchCount > 0) {
                            enchants = new ArrayList<>();
                            for (int e = 0; e < enchCount; e++) {
                                int enchIdLen = source.readUnsignedByte();
                                byte[] enchIdBytes = new byte[enchIdLen];
                                if (enchIdLen > 0) source.readFully(enchIdBytes);
                                int enchLevel = source.readUnsignedByte();
                                enchants.add(new PlayerEquipmentFullEvent.EnchantEntry(
                                        new String(enchIdBytes, StandardCharsets.UTF_8), enchLevel));
                            }
                        }
                    }
                    eqfSlots.add(new PlayerEquipmentFullEvent.SlotEntry(slotId, itemName, enchants));
                }
                return new PlayerEquipmentFullEvent(timestampDeltaMs, eqfUuid, eqfSlots);
            }
            case PLAYER_HEALTH: {
                long phUuidMost = source.readLong();
                long phUuidLeast = source.readLong();
                UUID phUuid = new UUID(phUuidMost, phUuidLeast);
                float health = source.readFloat();
                byte food = source.readByte();
                float saturation = source.readFloat();
                return new PlayerHealthEvent(timestampDeltaMs, phUuid, health, food, saturation);
            }
            case PLAYER_EFFECTS: {
                long peUuidMost = source.readLong();
                long peUuidLeast = source.readLong();
                UUID peUuid = new UUID(peUuidMost, peUuidLeast);
                int effectCount = source.readUnsignedByte();
                List<int[]> effects = new ArrayList<>();
                for (int i = 0; i < effectCount; i++) {
                    int effectTypeId = source.readUnsignedByte();
                    int amplifier = source.readUnsignedByte();
                    int durationTicks = source.readInt();
                    effects.add(new int[]{effectTypeId, amplifier, durationTicks});
                }
                return PlayerEffectsEvent.fromList(timestampDeltaMs, peUuid, effects);
            }
            case PLAYER_INVENTORY: {
                long piUuidMost = source.readLong();
                long piUuidLeast = source.readLong();
                UUID piUuid = new UUID(piUuidMost, piUuidLeast);
                boolean fullSnapshot = source.readUnsignedByte() != 0;
                int invSlotCount = source.readUnsignedByte();
                List<PlayerInventoryEvent.SlotEntry> invSlots = new ArrayList<>();
                for (int i = 0; i < invSlotCount; i++) {
                    int slotIndex = source.readUnsignedByte();
                    int itemNameLen = source.readUnsignedShort();
                    byte[] itemNameBytes = new byte[itemNameLen];
                    if (itemNameLen > 0) source.readFully(itemNameBytes);
                    int count = source.readUnsignedByte();
                    List<PlayerInventoryEvent.EnchantEntry> enchants = Collections.emptyList();
                    if (formatVersion >= 3) {
                        int enchCount = source.readUnsignedByte();
                        if (enchCount > 0) {
                            enchants = new ArrayList<>();
                            for (int e = 0; e < enchCount; e++) {
                                int enchIdLen = source.readUnsignedByte();
                                byte[] enchIdBytes = new byte[enchIdLen];
                                if (enchIdLen > 0) source.readFully(enchIdBytes);
                                int enchLevel = source.readUnsignedByte();
                                enchants.add(new PlayerInventoryEvent.EnchantEntry(
                                        new String(enchIdBytes, StandardCharsets.UTF_8), enchLevel));
                            }
                        }
                    }
                    invSlots.add(new PlayerInventoryEvent.SlotEntry(slotIndex, new String(itemNameBytes, StandardCharsets.UTF_8), count, enchants));
                }
                return new PlayerInventoryEvent(timestampDeltaMs, piUuid, fullSnapshot, invSlots);
            }
            case PLAYER_SKIN: {
                long psUuidMost = source.readLong();
                long psUuidLeast = source.readLong();
                UUID psUuid = new UUID(psUuidMost, psUuidLeast);
                int skinLen = source.readInt();
                if (skinLen < 0 || skinLen > source.available()) {
                    throw new IOException("Invalid player skin payload length: " + skinLen);
                }
                byte[] skinData = new byte[skinLen];
                if (skinLen > 0) source.readFully(skinData);
                return new PlayerSkinEvent(timestampDeltaMs, psUuid, skinData);
            }
            case PLAYER_BLOCK_PLACE: {
                long uuidMost = source.readLong();
                long uuidLeast = source.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int x = source.readInt();
                short y = source.readShort();
                int z = source.readInt();
                int stateId = source.readInt();
                return new PlayerBlockPlaceEvent(timestampDeltaMs, uuid, x, y, z, stateId);
            }
            case PLAYER_BLOCK_BREAK: {
                long uuidMost = source.readLong();
                long uuidLeast = source.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int x = source.readInt();
                short y = source.readShort();
                int z = source.readInt();
                int previousStateId = source.readInt();
                return new PlayerBlockBreakEvent(timestampDeltaMs, uuid, x, y, z, previousStateId);
            }
            default:
                throw new IOException("Unhandled event type: " + type);
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private void skipFully(int payloadLength) throws IOException {
        int remaining = payloadLength;
        while (remaining > 0) {
            int skipped = in.skipBytes(remaining);
            if (skipped <= 0) {
                if (in.read() == -1) {
                    throw new EOFException("Unable to skip unknown event payload: " + remaining + " bytes remaining");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}

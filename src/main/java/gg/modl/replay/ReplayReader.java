package gg.modl.replay;

import gg.modl.replay.format.ReplayEvent;
import gg.modl.replay.format.ReplayHeader;
import gg.modl.replay.format.events.*;
import gg.modl.replay.util.BlockSnapshot;
import gg.modl.replay.util.FormatConstants;

import java.io.*;
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
     * Returns null on EOF or for unknown event types (TLV skip).
     */
    public ReplayEvent readEvent() throws IOException {
        int eventTypeByte;
        try {
            eventTypeByte = in.readUnsignedByte();
        } catch (EOFException e) {
            return null;
        }

        ReplayEvent.EventType type = ReplayEvent.EventType.fromId(eventTypeByte);
        int timestampDeltaMs = in.readInt();

        // v4+: TLV envelope — read payload length
        int payloadLength = -1;
        if (formatVersion >= 4) {
            payloadLength = in.readUnsignedShort();
        }

        // Unknown event type: skip if TLV available, otherwise error
        if (type == null) {
            if (formatVersion >= 4 && payloadLength >= 0) {
                in.skipBytes(payloadLength);
                return null;
            }
            throw new IOException("Unknown event type: 0x" + Integer.toHexString(eventTypeByte));
        }

        switch (type) {
            case BLOCK_CHANGE: {
                int x = in.readInt();
                short y = in.readShort();
                int z = in.readInt();
                int stateId = in.readInt();
                return new BlockChangeEvent(timestampDeltaMs, x, y, z, stateId);
            }
            case ENTITY_SPAWN: {
                int entityId = in.readInt();
                short entityTypeId = in.readShort();
                float x = in.readFloat();
                float y = in.readFloat();
                float z = in.readFloat();
                int metaLen = in.readUnsignedShort();
                byte[] metadata = new byte[metaLen];
                if (metaLen > 0) in.readFully(metadata);
                return new EntitySpawnEvent(timestampDeltaMs, entityId, entityTypeId, x, y, z, metadata);
            }
            case ENTITY_MOVE: {
                int entityId = in.readInt();
                float x = in.readFloat();
                float y = in.readFloat();
                float z = in.readFloat();
                float yaw, pitch;
                if (formatVersion >= 4) {
                    yaw = in.readFloat();
                    pitch = in.readFloat();
                } else {
                    yaw = FormatConstants.decodeAngle(in.readShort());
                    pitch = FormatConstants.decodeAngle(in.readShort());
                }
                return new EntityMoveEvent(timestampDeltaMs, entityId, x, y, z, yaw, pitch);
            }
            case ENTITY_REMOVE: {
                int entityId = in.readInt();
                return new EntityRemoveEvent(timestampDeltaMs, entityId);
            }
            case PLAYER_SPAWN: {
                long uuidMost = in.readLong();
                long uuidLeast = in.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int nameLen = in.readUnsignedByte();
                byte[] nameBytes = new byte[nameLen];
                in.readFully(nameBytes);
                String playerName = new String(nameBytes, StandardCharsets.UTF_8);
                float x = in.readFloat();
                float y = in.readFloat();
                float z = in.readFloat();
                float yaw, pitch;
                if (formatVersion >= 4) {
                    yaw = in.readFloat();
                    pitch = in.readFloat();
                } else {
                    yaw = FormatConstants.decodeAngle(in.readShort());
                    pitch = FormatConstants.decodeAngle(in.readShort());
                }
                int equipLen = in.readUnsignedShort();
                byte[] equipment = new byte[equipLen];
                if (equipLen > 0) in.readFully(equipment);
                return new PlayerSpawnEvent(timestampDeltaMs, uuid, playerName, x, y, z, yaw, pitch, equipment);
            }
            case PLAYER_MOVE: {
                long uuidMost = in.readLong();
                long uuidLeast = in.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                float x = in.readFloat();
                float y = in.readFloat();
                float z = in.readFloat();
                float yaw, pitch;
                if (formatVersion >= 4) {
                    yaw = in.readFloat();
                    pitch = in.readFloat();
                } else {
                    yaw = FormatConstants.decodeAngle(in.readShort());
                    pitch = FormatConstants.decodeAngle(in.readShort());
                }
                return new PlayerMoveEvent(timestampDeltaMs, uuid, x, y, z, yaw, pitch);
            }
            case PLAYER_REMOVE: {
                long uuidMost = in.readLong();
                long uuidLeast = in.readLong();
                return new PlayerRemoveEvent(timestampDeltaMs, new UUID(uuidMost, uuidLeast));
            }
            case PLAYER_ANIM: {
                long uuidMost = in.readLong();
                long uuidLeast = in.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int animId = in.readUnsignedByte();
                return new PlayerAnimEvent(timestampDeltaMs, uuid, PlayerAnimEvent.AnimationType.fromId(animId));
            }
            case CHAT: {
                long uuidMost = in.readLong();
                long uuidLeast = in.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int msgLen = in.readUnsignedShort();
                byte[] msgBytes = new byte[msgLen];
                in.readFully(msgBytes);
                return new ChatEvent(timestampDeltaMs, uuid, new String(msgBytes, StandardCharsets.UTF_8));
            }
            case PLAYER_EQUIPMENT: {
                long uuidMost = in.readLong();
                long uuidLeast = in.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int nameLen = in.readUnsignedByte();
                byte[] nameBytes = new byte[nameLen];
                if (nameLen > 0) in.readFully(nameBytes);
                return new PlayerEquipmentEvent(timestampDeltaMs, uuid, new String(nameBytes, StandardCharsets.UTF_8));
            }
            case PLAYER_EQUIPMENT_FULL: {
                long eqfUuidMost = in.readLong();
                long eqfUuidLeast = in.readLong();
                UUID eqfUuid = new UUID(eqfUuidMost, eqfUuidLeast);
                int eqfSlotCount = in.readUnsignedByte();
                List<PlayerEquipmentFullEvent.SlotEntry> eqfSlots = new ArrayList<>();
                for (int i = 0; i < eqfSlotCount; i++) {
                    int slotId = in.readUnsignedByte();
                    int itemNameLen = in.readUnsignedByte();
                    byte[] itemNameBytes = new byte[itemNameLen];
                    if (itemNameLen > 0) in.readFully(itemNameBytes);
                    String itemName = new String(itemNameBytes, StandardCharsets.UTF_8);
                    List<PlayerEquipmentFullEvent.EnchantEntry> enchants = Collections.emptyList();
                    if (formatVersion >= 3) {
                        int enchCount = in.readUnsignedByte();
                        if (enchCount > 0) {
                            enchants = new ArrayList<>();
                            for (int e = 0; e < enchCount; e++) {
                                int enchIdLen = in.readUnsignedByte();
                                byte[] enchIdBytes = new byte[enchIdLen];
                                if (enchIdLen > 0) in.readFully(enchIdBytes);
                                int enchLevel = in.readUnsignedByte();
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
                long phUuidMost = in.readLong();
                long phUuidLeast = in.readLong();
                UUID phUuid = new UUID(phUuidMost, phUuidLeast);
                float health = in.readFloat();
                byte food = in.readByte();
                float saturation = in.readFloat();
                return new PlayerHealthEvent(timestampDeltaMs, phUuid, health, food, saturation);
            }
            case PLAYER_EFFECTS: {
                long peUuidMost = in.readLong();
                long peUuidLeast = in.readLong();
                UUID peUuid = new UUID(peUuidMost, peUuidLeast);
                int effectCount = in.readUnsignedByte();
                java.util.List<int[]> effects = new java.util.ArrayList<>();
                for (int i = 0; i < effectCount; i++) {
                    int effectTypeId = in.readUnsignedByte();
                    int amplifier = in.readUnsignedByte();
                    int durationTicks = in.readInt();
                    effects.add(new int[]{effectTypeId, amplifier, durationTicks});
                }
                return PlayerEffectsEvent.fromList(timestampDeltaMs, peUuid, effects);
            }
            case PLAYER_INVENTORY: {
                long piUuidMost = in.readLong();
                long piUuidLeast = in.readLong();
                UUID piUuid = new UUID(piUuidMost, piUuidLeast);
                boolean fullSnapshot = in.readUnsignedByte() != 0;
                int invSlotCount = in.readUnsignedByte();
                List<PlayerInventoryEvent.SlotEntry> invSlots = new ArrayList<>();
                for (int i = 0; i < invSlotCount; i++) {
                    int slotIndex = in.readUnsignedByte();
                    int itemNameLen = in.readUnsignedShort();
                    byte[] itemNameBytes = new byte[itemNameLen];
                    if (itemNameLen > 0) in.readFully(itemNameBytes);
                    int count = in.readUnsignedByte();
                    List<PlayerInventoryEvent.EnchantEntry> enchants = Collections.emptyList();
                    if (formatVersion >= 3) {
                        int enchCount = in.readUnsignedByte();
                        if (enchCount > 0) {
                            enchants = new ArrayList<>();
                            for (int e = 0; e < enchCount; e++) {
                                int enchIdLen = in.readUnsignedByte();
                                byte[] enchIdBytes = new byte[enchIdLen];
                                if (enchIdLen > 0) in.readFully(enchIdBytes);
                                int enchLevel = in.readUnsignedByte();
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
                long psUuidMost = in.readLong();
                long psUuidLeast = in.readLong();
                UUID psUuid = new UUID(psUuidMost, psUuidLeast);
                int skinLen = in.readInt();
                byte[] skinData = new byte[skinLen];
                if (skinLen > 0) in.readFully(skinData);
                return new PlayerSkinEvent(timestampDeltaMs, psUuid, skinData);
            }
            case PLAYER_BLOCK_PLACE: {
                long uuidMost = in.readLong();
                long uuidLeast = in.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int x = in.readInt();
                short y = in.readShort();
                int z = in.readInt();
                int stateId = in.readInt();
                return new PlayerBlockPlaceEvent(timestampDeltaMs, uuid, x, y, z, stateId);
            }
            case PLAYER_BLOCK_BREAK: {
                long uuidMost = in.readLong();
                long uuidLeast = in.readLong();
                UUID uuid = new UUID(uuidMost, uuidLeast);
                int x = in.readInt();
                short y = in.readShort();
                int z = in.readInt();
                int previousStateId = in.readInt();
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
}

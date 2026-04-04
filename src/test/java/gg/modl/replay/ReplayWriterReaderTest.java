package gg.modl.replay;

import gg.modl.replay.format.ReplayEvent;
import gg.modl.replay.format.ReplayHeader;
import gg.modl.replay.format.events.*;
import gg.modl.replay.util.BlockSnapshot;
import gg.modl.replay.util.FormatConstants;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReplayWriterReaderTest {

    private static final UUID TEST_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    // ── Header round-trip ──────────────────────────────────────────────

    @Test
    void headerRoundTrip() throws IOException {
        ReplayHeader header = ReplayHeader.builder()
                .version(FormatConstants.VERSION)
                .startTime(1700000000000L)
                .mcVersion("1.21.4")
                .targetX(100)
                .targetY(64)
                .targetZ(-200)
                .radiusBlocks(128)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReplayWriter writer = new ReplayWriter(baos);
        writer.writeHeader(header);
        writer.writeSnapshot(Collections.emptyList());
        writer.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        ReplayHeader readHeader = reader.readHeader();
        reader.readSnapshot();
        reader.close();

        assertEquals(FormatConstants.VERSION, readHeader.getVersion());
        assertEquals(1700000000000L, readHeader.getStartTime());
        assertEquals("1.21.4", readHeader.getMcVersion());
        assertEquals(100, readHeader.getTargetX());
        assertEquals(64, readHeader.getTargetY());
        assertEquals(-200, readHeader.getTargetZ());
        assertEquals(128, readHeader.getRadiusBlocks());
    }

    // ── Snapshot round-trip ────────────────────────────────────────────

    @Test
    void snapshotRoundTripMultipleBlocks() throws IOException {
        List<BlockSnapshot> blocks = Arrays.asList(
                new BlockSnapshot(100, (short) 64, -200, 1),
                new BlockSnapshot(101, (short) 65, -199, 42),
                new BlockSnapshot(99, (short) 63, -201, 100)
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReplayWriter writer = new ReplayWriter(baos);
        writer.writeHeader(minimalHeader());
        writer.writeSnapshot(blocks);
        writer.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        reader.readHeader();
        List<BlockSnapshot> readBlocks = reader.readSnapshot();
        reader.close();

        assertEquals(3, readBlocks.size());
        assertBlockEquals(100, (short) 64, -200, 1, readBlocks.get(0));
        assertBlockEquals(101, (short) 65, -199, 42, readBlocks.get(1));
        assertBlockEquals(99, (short) 63, -201, 100, readBlocks.get(2));
    }

    @Test
    void emptySnapshotRoundTrip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReplayWriter writer = new ReplayWriter(baos);
        writer.writeHeader(minimalHeader());
        writer.writeSnapshot(Collections.emptyList());
        writer.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        reader.readHeader();
        List<BlockSnapshot> readBlocks = reader.readSnapshot();
        reader.close();

        assertTrue(readBlocks.isEmpty());
    }

    // ── Event round-trips (one per type) ───────────────────────────────

    @Test
    void blockChangeEventRoundTrip() throws IOException {
        BlockChangeEvent event = new BlockChangeEvent(1000, 100, (short) 64, -200, 0);
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(BlockChangeEvent.class, readEvent);
        BlockChangeEvent e = (BlockChangeEvent) readEvent;
        assertEquals(ReplayEvent.EventType.BLOCK_CHANGE, e.getType());
        assertEquals(1000, e.getTimestampDeltaMs());
        assertEquals(100, e.getX());
        assertEquals(64, e.getY());
        assertEquals(-200, e.getZ());
        assertEquals(0, e.getStateId());
    }

    @Test
    void playerSpawnEventRoundTrip() throws IOException {
        PlayerSpawnEvent event = new PlayerSpawnEvent(
                500, TEST_UUID, "Steve",
                100.5f, 64.0f, -199.5f,
                90.0f, 0.0f,
                new byte[0]
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(PlayerSpawnEvent.class, readEvent);
        PlayerSpawnEvent e = (PlayerSpawnEvent) readEvent;
        assertEquals(ReplayEvent.EventType.PLAYER_SPAWN, e.getType());
        assertEquals(500, e.getTimestampDeltaMs());
        assertEquals(TEST_UUID, e.getUuid());
        assertEquals("Steve", e.getPlayerName());
        assertEquals(100.5f, e.getX());
        assertEquals(64.0f, e.getY());
        assertEquals(-199.5f, e.getZ());
        assertEquals(90.0f, e.getYaw());
        assertEquals(0.0f, e.getPitch());
        assertEquals(0, e.getEquipment().length);
    }

    @Test
    void playerMoveEventRoundTrip() throws IOException {
        PlayerMoveEvent event = new PlayerMoveEvent(
                1500, TEST_UUID,
                101.0f, 64.0f, -198.0f,
                45.0f, -22.5f
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(PlayerMoveEvent.class, readEvent);
        PlayerMoveEvent e = (PlayerMoveEvent) readEvent;
        assertEquals(ReplayEvent.EventType.PLAYER_MOVE, e.getType());
        assertEquals(1500, e.getTimestampDeltaMs());
        assertEquals(TEST_UUID, e.getUuid());
        assertEquals(101.0f, e.getX());
        assertEquals(64.0f, e.getY());
        assertEquals(-198.0f, e.getZ());
        assertEquals(45.0f, e.getYaw());
        assertEquals(-22.5f, e.getPitch());
    }

    @Test
    void entitySpawnEventRoundTrip() throws IOException {
        byte[] metadata = {0x01, 0x02, 0x03};
        EntitySpawnEvent event = new EntitySpawnEvent(
                2000, 42, (short) 51,
                105.0f, 64.0f, -195.0f,
                metadata
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(EntitySpawnEvent.class, readEvent);
        EntitySpawnEvent e = (EntitySpawnEvent) readEvent;
        assertEquals(ReplayEvent.EventType.ENTITY_SPAWN, e.getType());
        assertEquals(2000, e.getTimestampDeltaMs());
        assertEquals(42, e.getEntityId());
        assertEquals(51, e.getEntityTypeId());
        assertEquals(105.0f, e.getX());
        assertEquals(64.0f, e.getY());
        assertEquals(-195.0f, e.getZ());
        assertArrayEquals(metadata, e.getMetadata());
    }

    @Test
    void entitySpawnEventEmptyMetadataRoundTrip() throws IOException {
        EntitySpawnEvent event = new EntitySpawnEvent(
                2000, 42, (short) 51,
                105.0f, 64.0f, -195.0f,
                new byte[0]
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(EntitySpawnEvent.class, readEvent);
        EntitySpawnEvent e = (EntitySpawnEvent) readEvent;
        assertEquals(0, e.getMetadata().length);
    }

    @Test
    void entityMoveEventRoundTrip() throws IOException {
        EntityMoveEvent event = new EntityMoveEvent(
                2500, 42,
                104.5f, 64.0f, -196.0f,
                180.0f, 0.0f
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(EntityMoveEvent.class, readEvent);
        EntityMoveEvent e = (EntityMoveEvent) readEvent;
        assertEquals(ReplayEvent.EventType.ENTITY_MOVE, e.getType());
        assertEquals(2500, e.getTimestampDeltaMs());
        assertEquals(42, e.getEntityId());
        assertEquals(104.5f, e.getX());
        assertEquals(64.0f, e.getY());
        assertEquals(-196.0f, e.getZ());
        assertEquals(180.0f, e.getYaw());
        assertEquals(0.0f, e.getPitch());
    }

    @Test
    void playerAnimEventRoundTrip() throws IOException {
        PlayerAnimEvent event = new PlayerAnimEvent(
                3000, TEST_UUID, PlayerAnimEvent.AnimationType.SWING_MAIN_ARM
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(PlayerAnimEvent.class, readEvent);
        PlayerAnimEvent e = (PlayerAnimEvent) readEvent;
        assertEquals(ReplayEvent.EventType.PLAYER_ANIM, e.getType());
        assertEquals(3000, e.getTimestampDeltaMs());
        assertEquals(TEST_UUID, e.getUuid());
        assertEquals(PlayerAnimEvent.AnimationType.SWING_MAIN_ARM, e.getAnimationType());
    }

    @Test
    void chatEventRoundTrip() throws IOException {
        ChatEvent event = new ChatEvent(3500, TEST_UUID, "Hello world!");
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(ChatEvent.class, readEvent);
        ChatEvent e = (ChatEvent) readEvent;
        assertEquals(ReplayEvent.EventType.CHAT, e.getType());
        assertEquals(3500, e.getTimestampDeltaMs());
        assertEquals(TEST_UUID, e.getUuid());
        assertEquals("Hello world!", e.getMessage());
    }

    @Test
    void entityRemoveEventRoundTrip() throws IOException {
        EntityRemoveEvent event = new EntityRemoveEvent(4000, 42);
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(EntityRemoveEvent.class, readEvent);
        EntityRemoveEvent e = (EntityRemoveEvent) readEvent;
        assertEquals(ReplayEvent.EventType.ENTITY_REMOVE, e.getType());
        assertEquals(4000, e.getTimestampDeltaMs());
        assertEquals(42, e.getEntityId());
    }

    @Test
    void playerRemoveEventRoundTrip() throws IOException {
        PlayerRemoveEvent event = new PlayerRemoveEvent(5000, TEST_UUID);
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(PlayerRemoveEvent.class, readEvent);
        PlayerRemoveEvent e = (PlayerRemoveEvent) readEvent;
        assertEquals(ReplayEvent.EventType.PLAYER_REMOVE, e.getType());
        assertEquals(5000, e.getTimestampDeltaMs());
        assertEquals(TEST_UUID, e.getUuid());
    }

    // ── New v4 event round-trips ────────────────────────────────────────

    @Test
    void playerBlockPlaceEventRoundTrip() throws IOException {
        PlayerBlockPlaceEvent event = new PlayerBlockPlaceEvent(
                6000, TEST_UUID, 100, (short) 64, -200, 42
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(PlayerBlockPlaceEvent.class, readEvent);
        PlayerBlockPlaceEvent e = (PlayerBlockPlaceEvent) readEvent;
        assertEquals(ReplayEvent.EventType.PLAYER_BLOCK_PLACE, e.getType());
        assertEquals(6000, e.getTimestampDeltaMs());
        assertEquals(TEST_UUID, e.getPlacerUuid());
        assertEquals(100, e.getX());
        assertEquals(64, e.getY());
        assertEquals(-200, e.getZ());
        assertEquals(42, e.getStateId());
    }

    @Test
    void playerBlockBreakEventRoundTrip() throws IOException {
        PlayerBlockBreakEvent event = new PlayerBlockBreakEvent(
                7000, TEST_UUID, 101, (short) 65, -199, 99
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(PlayerBlockBreakEvent.class, readEvent);
        PlayerBlockBreakEvent e = (PlayerBlockBreakEvent) readEvent;
        assertEquals(ReplayEvent.EventType.PLAYER_BLOCK_BREAK, e.getType());
        assertEquals(7000, e.getTimestampDeltaMs());
        assertEquals(TEST_UUID, e.getBreakerUuid());
        assertEquals(101, e.getX());
        assertEquals(65, e.getY());
        assertEquals(-199, e.getZ());
        assertEquals(99, e.getPreviousStateId());
    }

    // ── All events in sequence ─────────────────────────────────────────

    @Test
    void allEventTypesInSequence() throws IOException {
        List<ReplayEvent> events = Arrays.asList(
                new BlockChangeEvent(1000, 100, (short) 64, -200, 0),
                new PlayerSpawnEvent(500, TEST_UUID, "Steve", 100.5f, 64.0f, -199.5f, 90.0f, 0.0f, new byte[0]),
                new PlayerMoveEvent(1500, TEST_UUID, 101.0f, 64.0f, -198.0f, 45.0f, -22.5f),
                new EntitySpawnEvent(2000, 42, (short) 51, 105.0f, 64.0f, -195.0f, new byte[0]),
                new EntityMoveEvent(2500, 42, 104.5f, 64.0f, -196.0f, 180.0f, 0.0f),
                new PlayerAnimEvent(3000, TEST_UUID, PlayerAnimEvent.AnimationType.SWING_MAIN_ARM),
                new ChatEvent(3500, TEST_UUID, "Hello world!"),
                new EntityRemoveEvent(4000, 42),
                new PlayerRemoveEvent(5000, TEST_UUID),
                new PlayerBlockPlaceEvent(6000, TEST_UUID, 100, (short) 64, -200, 42),
                new PlayerBlockBreakEvent(7000, TEST_UUID, 101, (short) 65, -199, 99)
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReplayWriter writer = new ReplayWriter(baos);
        writer.writeHeader(minimalHeader());
        writer.writeSnapshot(Collections.emptyList());
        for (ReplayEvent event : events) {
            writer.writeEvent(event);
        }
        writer.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        reader.readHeader();
        reader.readSnapshot();

        ReplayEvent.EventType[] expectedTypes = {
                ReplayEvent.EventType.BLOCK_CHANGE,
                ReplayEvent.EventType.PLAYER_SPAWN,
                ReplayEvent.EventType.PLAYER_MOVE,
                ReplayEvent.EventType.ENTITY_SPAWN,
                ReplayEvent.EventType.ENTITY_MOVE,
                ReplayEvent.EventType.PLAYER_ANIM,
                ReplayEvent.EventType.CHAT,
                ReplayEvent.EventType.ENTITY_REMOVE,
                ReplayEvent.EventType.PLAYER_REMOVE,
                ReplayEvent.EventType.PLAYER_BLOCK_PLACE,
                ReplayEvent.EventType.PLAYER_BLOCK_BREAK
        };

        for (int i = 0; i < expectedTypes.length; i++) {
            ReplayEvent readEvent = reader.readEvent();
            assertNotNull(readEvent, "Event " + i + " should not be null");
            assertEquals(expectedTypes[i], readEvent.getType(), "Event " + i + " type mismatch");
        }

        assertNull(reader.readEvent(), "Should return null after last event (EOF)");
        reader.close();
    }

    // ── V4 float yaw/pitch round-trip ───────────────────────────────────

    @Test
    void v4FloatYawPitchRoundTrip() throws IOException {
        float yaw = 123.456f;
        float pitch = -45.789f;

        PlayerMoveEvent event = new PlayerMoveEvent(100, TEST_UUID, 0f, 0f, 0f, yaw, pitch);
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(PlayerMoveEvent.class, readEvent);
        PlayerMoveEvent e = (PlayerMoveEvent) readEvent;
        assertEquals(yaw, e.getYaw(), 0.001f);
        assertEquals(pitch, e.getPitch(), 0.001f);
    }

    // ── Backward-compat: read v3 binary with int16 angles ───────────────

    @Test
    void backwardCompatV3Int16Angles() throws IOException {
        // Manually write a v3 replay (no TLV) with int16 encoded angles
        float originalYaw = 90.0f;
        float originalPitch = 45.0f;
        short encodedYaw = FormatConstants.encodeAngle(originalYaw);
        short encodedPitch = FormatConstants.encodeAngle(originalPitch);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        // Header
        out.write(FormatConstants.MAGIC);
        out.writeShort(3); // v3
        out.writeLong(1700000000000L);
        byte[] mcVersion = "1.21.4".getBytes();
        out.writeShort(mcVersion.length);
        out.write(mcVersion);
        out.writeInt(0); // targetX
        out.writeInt(64); // targetY
        out.writeInt(0); // targetZ
        out.writeInt(64); // radius
        // Empty snapshot
        out.writeInt(0);
        // PlayerMove event (v3 format: no TLV, short yaw/pitch)
        out.writeByte(0x06); // PLAYER_MOVE
        out.writeInt(1000); // timestamp
        out.writeLong(TEST_UUID.getMostSignificantBits());
        out.writeLong(TEST_UUID.getLeastSignificantBits());
        out.writeFloat(10.0f);
        out.writeFloat(64.0f);
        out.writeFloat(20.0f);
        out.writeShort(encodedYaw);
        out.writeShort(encodedPitch);
        out.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        reader.readHeader();
        reader.readSnapshot();
        ReplayEvent readEvent = reader.readEvent();
        reader.close();

        assertInstanceOf(PlayerMoveEvent.class, readEvent);
        PlayerMoveEvent e = (PlayerMoveEvent) readEvent;
        assertEquals(originalYaw, e.getYaw(), 0.02f);
        assertEquals(originalPitch, e.getPitch(), 0.02f);
    }

    // ── TLV skip: unknown event type is skipped in v4 ───────────────────

    @Test
    void tlvSkipUnknownEventType() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        // Header
        out.write(FormatConstants.MAGIC);
        out.writeShort(4); // v4
        out.writeLong(1700000000000L);
        byte[] mcVersion = "1.21.4".getBytes();
        out.writeShort(mcVersion.length);
        out.write(mcVersion);
        out.writeInt(0);
        out.writeInt(64);
        out.writeInt(0);
        out.writeInt(64);
        // Empty snapshot
        out.writeInt(0);
        // Known event: BlockChange (with TLV)
        out.writeByte(0x01); // BLOCK_CHANGE
        out.writeInt(500); // timestamp
        out.writeShort(14); // payload length
        out.writeInt(10); // x
        out.writeShort(64); // y
        out.writeInt(20); // z
        out.writeInt(1); // stateId
        // Unknown event type 0x20 (with TLV)
        out.writeByte(0x20);
        out.writeInt(1000); // timestamp
        out.writeShort(8); // payload length = 8 bytes
        out.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}); // fabricated payload
        // Another known event: BlockChange
        out.writeByte(0x01); // BLOCK_CHANGE
        out.writeInt(1500); // timestamp
        out.writeShort(14); // payload length
        out.writeInt(30); // x
        out.writeShort(65); // y
        out.writeInt(40); // z
        out.writeInt(2); // stateId
        out.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        reader.readHeader();
        reader.readSnapshot();

        // First event: BlockChange
        ReplayEvent first = reader.readEvent();
        assertNotNull(first);
        assertInstanceOf(BlockChangeEvent.class, first);
        assertEquals(500, first.getTimestampDeltaMs());

        // Second event: unknown 0x20 — should be skipped (return null)
        ReplayEvent second = reader.readEvent();
        assertNull(second, "Unknown event type should be skipped and return null");

        // Third event: BlockChange
        ReplayEvent third = reader.readEvent();
        assertNotNull(third);
        assertInstanceOf(BlockChangeEvent.class, third);
        assertEquals(1500, third.getTimestampDeltaMs());
        assertEquals(30, ((BlockChangeEvent) third).getX());

        // EOF
        assertNull(reader.readEvent());
        reader.close();
    }

    // ── Edge value tests ───────────────────────────────────────────────

    @Test
    void blockChangeEdgeValueYMin() throws IOException {
        BlockChangeEvent event = new BlockChangeEvent(0, 0, Short.MIN_VALUE, 0, 0);
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(BlockChangeEvent.class, readEvent);
        assertEquals(Short.MIN_VALUE, ((BlockChangeEvent) readEvent).getY());
    }

    @Test
    void blockChangeEdgeValueYMax() throws IOException {
        BlockChangeEvent event = new BlockChangeEvent(0, 0, Short.MAX_VALUE, 0, 0);
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(BlockChangeEvent.class, readEvent);
        assertEquals(Short.MAX_VALUE, ((BlockChangeEvent) readEvent).getY());
    }

    @Test
    void blockChangeEdgeValueStateIdZero() throws IOException {
        BlockChangeEvent event = new BlockChangeEvent(0, 0, (short) 0, 0, 0);
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(BlockChangeEvent.class, readEvent);
        assertEquals(0, ((BlockChangeEvent) readEvent).getStateId());
    }

    @Test
    void blockChangeEdgeValueStateIdMaxInt() throws IOException {
        BlockChangeEvent event = new BlockChangeEvent(0, 0, (short) 0, 0, Integer.MAX_VALUE);
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(BlockChangeEvent.class, readEvent);
        assertEquals(Integer.MAX_VALUE, ((BlockChangeEvent) readEvent).getStateId());
    }

    @Test
    void snapshotEdgeValueYMinMax() throws IOException {
        List<BlockSnapshot> blocks = Arrays.asList(
                new BlockSnapshot(0, Short.MIN_VALUE, 0, 0),
                new BlockSnapshot(0, Short.MAX_VALUE, 0, Integer.MAX_VALUE)
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReplayWriter writer = new ReplayWriter(baos);
        writer.writeHeader(minimalHeader());
        writer.writeSnapshot(blocks);
        writer.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        reader.readHeader();
        List<BlockSnapshot> readBlocks = reader.readSnapshot();
        reader.close();

        assertEquals(2, readBlocks.size());
        assertEquals(Short.MIN_VALUE, readBlocks.get(0).getY());
        assertEquals(Short.MAX_VALUE, readBlocks.get(1).getY());
        assertEquals(Integer.MAX_VALUE, readBlocks.get(1).getStateId());
    }

    // ── Max-length player name (255 bytes for string8) ─────────────────

    @Test
    void playerSpawnMaxLengthName() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 255; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String maxName = sb.toString();

        PlayerSpawnEvent event = new PlayerSpawnEvent(
                100, TEST_UUID, maxName,
                0.0f, 0.0f, 0.0f,
                0.0f, 0.0f,
                new byte[0]
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(PlayerSpawnEvent.class, readEvent);
        assertEquals(maxName, ((PlayerSpawnEvent) readEvent).getPlayerName());
    }

    // ── Empty file (header + empty snapshot + 0 events) ────────────────

    @Test
    void headerAndEmptySnapshotNoEvents() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReplayWriter writer = new ReplayWriter(baos);
        writer.writeHeader(minimalHeader());
        writer.writeSnapshot(Collections.emptyList());
        writer.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        ReplayHeader readHeader = reader.readHeader();
        List<BlockSnapshot> readBlocks = reader.readSnapshot();
        ReplayEvent readEvent = reader.readEvent();
        reader.close();

        assertNotNull(readHeader);
        assertTrue(readBlocks.isEmpty());
        assertNull(readEvent, "Should return null when no events are present (EOF)");
    }

    // ── EOF returns null ───────────────────────────────────────────────

    @Test
    void readerReturnsNullAfterLastEvent() throws IOException {
        BlockChangeEvent event = new BlockChangeEvent(1000, 1, (short) 2, 3, 4);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReplayWriter writer = new ReplayWriter(baos);
        writer.writeHeader(minimalHeader());
        writer.writeSnapshot(Collections.emptyList());
        writer.writeEvent(event);
        writer.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        reader.readHeader();
        reader.readSnapshot();

        ReplayEvent first = reader.readEvent();
        assertNotNull(first);

        ReplayEvent second = reader.readEvent();
        assertNull(second, "readEvent should return null after last event");

        ReplayEvent third = reader.readEvent();
        assertNull(third, "readEvent should continue returning null on repeated calls past EOF");
        reader.close();
    }

    // ── PlayerSpawnEvent with non-empty equipment ──────────────────────

    @Test
    void playerSpawnWithEquipmentRoundTrip() throws IOException {
        byte[] equipment = {0x10, 0x20, 0x30, 0x40, 0x50};
        PlayerSpawnEvent event = new PlayerSpawnEvent(
                700, TEST_UUID, "Alex",
                50.0f, 70.0f, -100.0f,
                110.0f, -55.0f,
                equipment
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(PlayerSpawnEvent.class, readEvent);
        PlayerSpawnEvent e = (PlayerSpawnEvent) readEvent;
        assertArrayEquals(equipment, e.getEquipment());
        assertEquals("Alex", e.getPlayerName());
    }

    // ── EntitySpawnEvent with non-empty metadata ───────────────────────

    @Test
    void entitySpawnWithMetadataRoundTrip() throws IOException {
        byte[] metadata = new byte[256];
        for (int i = 0; i < metadata.length; i++) {
            metadata[i] = (byte) (i & 0xFF);
        }
        EntitySpawnEvent event = new EntitySpawnEvent(
                100, 999, (short) 10,
                0.0f, 0.0f, 0.0f,
                metadata
        );
        ReplayEvent readEvent = roundTripEvent(event);

        assertInstanceOf(EntitySpawnEvent.class, readEvent);
        EntitySpawnEvent e = (EntitySpawnEvent) readEvent;
        assertArrayEquals(metadata, e.getMetadata());
    }

    // ── All animation types round-trip ─────────────────────────────────

    @Test
    void allAnimationTypesRoundTrip() throws IOException {
        for (PlayerAnimEvent.AnimationType animType : PlayerAnimEvent.AnimationType.values()) {
            PlayerAnimEvent event = new PlayerAnimEvent(100, TEST_UUID, animType);
            ReplayEvent readEvent = roundTripEvent(event);

            assertInstanceOf(PlayerAnimEvent.class, readEvent);
            assertEquals(animType, ((PlayerAnimEvent) readEvent).getAnimationType(),
                    "Animation type mismatch for " + animType);
        }
    }

    // ── Helper methods ─────────────────────────────────────────────────

    private ReplayHeader minimalHeader() {
        return ReplayHeader.builder()
                .version(FormatConstants.VERSION)
                .startTime(1700000000000L)
                .mcVersion("1.21.4")
                .targetX(0)
                .targetY(64)
                .targetZ(0)
                .radiusBlocks(64)
                .build();
    }

    private ReplayEvent roundTripEvent(ReplayEvent event) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReplayWriter writer = new ReplayWriter(baos);
        writer.writeHeader(minimalHeader());
        writer.writeSnapshot(Collections.emptyList());
        writer.writeEvent(event);
        writer.close();

        ReplayReader reader = new ReplayReader(new ByteArrayInputStream(baos.toByteArray()));
        reader.readHeader();
        reader.readSnapshot();
        ReplayEvent readEvent = reader.readEvent();
        reader.close();

        assertNotNull(readEvent, "readEvent should not return null");
        return readEvent;
    }

    private void assertBlockEquals(int expectedX, short expectedY, int expectedZ, int expectedStateId, BlockSnapshot actual) {
        assertEquals(expectedX, actual.getX());
        assertEquals(expectedY, actual.getY());
        assertEquals(expectedZ, actual.getZ());
        assertEquals(expectedStateId, actual.getStateId());
    }
}

package gg.modl.minecraft.replay;

import gg.modl.minecraft.replay.format.ReplayEvent;
import gg.modl.minecraft.replay.format.ReplayHeader;
import gg.modl.minecraft.replay.format.events.*;
import gg.modl.minecraft.replay.util.BlockSnapshot;

import gg.modl.minecraft.replay.util.FormatConstants;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GoldenFileTest {

    private static final UUID PLAYER_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    // ── Golden fixture ─────────────────────────────────────────────────

    @Test
    void parseGoldenFileHeader() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/golden.modlreplay")) {
            assertNotNull(is, "golden.modlreplay fixture not found on classpath");
            ReplayReader reader = new ReplayReader(is);

            ReplayHeader header = reader.readHeader();
            assertEquals(1, header.getVersion());
            assertEquals(1700000000000L, header.getStartTime());
            assertEquals("1.21.4", header.getMcVersion());
            assertEquals(100, header.getTargetX());
            assertEquals(64, header.getTargetY());
            assertEquals(-200, header.getTargetZ());
            assertEquals(128, header.getRadiusBlocks());

            reader.close();
        }
    }

    @Test
    void parseGoldenFileSnapshot() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/golden.modlreplay")) {
            assertNotNull(is);
            ReplayReader reader = new ReplayReader(is);
            reader.readHeader();

            List<BlockSnapshot> blocks = reader.readSnapshot();
            assertEquals(3, blocks.size());

            // Block 1
            assertEquals(100, blocks.get(0).getX());
            assertEquals(64, blocks.get(0).getY());
            assertEquals(-200, blocks.get(0).getZ());
            assertEquals(1, blocks.get(0).getStateId());

            // Block 2
            assertEquals(101, blocks.get(1).getX());
            assertEquals(65, blocks.get(1).getY());
            assertEquals(-199, blocks.get(1).getZ());
            assertEquals(42, blocks.get(1).getStateId());

            // Block 3
            assertEquals(99, blocks.get(2).getX());
            assertEquals(63, blocks.get(2).getY());
            assertEquals(-201, blocks.get(2).getZ());
            assertEquals(100, blocks.get(2).getStateId());

            reader.close();
        }
    }

    @Test
    void parseGoldenFileAllEvents() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/golden.modlreplay")) {
            assertNotNull(is);
            ReplayReader reader = new ReplayReader(is);
            reader.readHeader();
            reader.readSnapshot();

            // Event 1: BLOCK_CHANGE at t=1000
            ReplayEvent event1 = reader.readEvent();
            assertInstanceOf(BlockChangeEvent.class, event1);
            BlockChangeEvent blockChange = (BlockChangeEvent) event1;
            assertEquals(1000, blockChange.getTimestampDeltaMs());
            assertEquals(100, blockChange.getX());
            assertEquals(64, blockChange.getY());
            assertEquals(-200, blockChange.getZ());
            assertEquals(0, blockChange.getStateId());

            // Event 2: PLAYER_SPAWN at t=500
            ReplayEvent event2 = reader.readEvent();
            assertInstanceOf(PlayerSpawnEvent.class, event2);
            PlayerSpawnEvent playerSpawn = (PlayerSpawnEvent) event2;
            assertEquals(500, playerSpawn.getTimestampDeltaMs());
            assertEquals(PLAYER_UUID, playerSpawn.getUuid());
            assertEquals("Steve", playerSpawn.getPlayerName());
            assertEquals(100.5f, playerSpawn.getX());
            assertEquals(64.0f, playerSpawn.getY());
            assertEquals(-199.5f, playerSpawn.getZ());
            assertEquals(FormatConstants.decodeAngle((short) 8191), playerSpawn.getYaw(), 0.02f);
            assertEquals(FormatConstants.decodeAngle((short) 0), playerSpawn.getPitch(), 0.02f);
            assertEquals(0, playerSpawn.getEquipment().length);

            // Event 3: PLAYER_MOVE at t=1500
            ReplayEvent event3 = reader.readEvent();
            assertInstanceOf(PlayerMoveEvent.class, event3);
            PlayerMoveEvent playerMove = (PlayerMoveEvent) event3;
            assertEquals(1500, playerMove.getTimestampDeltaMs());
            assertEquals(PLAYER_UUID, playerMove.getUuid());
            assertEquals(101.0f, playerMove.getX());
            assertEquals(64.0f, playerMove.getY());
            assertEquals(-198.0f, playerMove.getZ());
            assertEquals(FormatConstants.decodeAngle((short) 4096), playerMove.getYaw(), 0.02f);
            assertEquals(FormatConstants.decodeAngle((short) -2048), playerMove.getPitch(), 0.02f);

            // Event 4: ENTITY_SPAWN at t=2000
            ReplayEvent event4 = reader.readEvent();
            assertInstanceOf(EntitySpawnEvent.class, event4);
            EntitySpawnEvent entitySpawn = (EntitySpawnEvent) event4;
            assertEquals(2000, entitySpawn.getTimestampDeltaMs());
            assertEquals(42, entitySpawn.getEntityId());
            assertEquals(51, entitySpawn.getEntityTypeId());
            assertEquals(105.0f, entitySpawn.getX());
            assertEquals(64.0f, entitySpawn.getY());
            assertEquals(-195.0f, entitySpawn.getZ());
            assertEquals(0, entitySpawn.getMetadata().length);

            // Event 5: ENTITY_MOVE at t=2500
            ReplayEvent event5 = reader.readEvent();
            assertInstanceOf(EntityMoveEvent.class, event5);
            EntityMoveEvent entityMove = (EntityMoveEvent) event5;
            assertEquals(2500, entityMove.getTimestampDeltaMs());
            assertEquals(42, entityMove.getEntityId());
            assertEquals(104.5f, entityMove.getX());
            assertEquals(64.0f, entityMove.getY());
            assertEquals(-196.0f, entityMove.getZ());
            assertEquals(FormatConstants.decodeAngle((short) 16383), entityMove.getYaw(), 0.02f);
            assertEquals(FormatConstants.decodeAngle((short) 0), entityMove.getPitch(), 0.02f);

            // Event 6: PLAYER_ANIM at t=3000
            ReplayEvent event6 = reader.readEvent();
            assertInstanceOf(PlayerAnimEvent.class, event6);
            PlayerAnimEvent playerAnim = (PlayerAnimEvent) event6;
            assertEquals(3000, playerAnim.getTimestampDeltaMs());
            assertEquals(PLAYER_UUID, playerAnim.getUuid());
            assertEquals(PlayerAnimEvent.AnimationType.SWING_MAIN_ARM, playerAnim.getAnimationType());

            // Event 7: CHAT at t=3500
            ReplayEvent event7 = reader.readEvent();
            assertInstanceOf(ChatEvent.class, event7);
            ChatEvent chat = (ChatEvent) event7;
            assertEquals(3500, chat.getTimestampDeltaMs());
            assertEquals(PLAYER_UUID, chat.getUuid());
            assertEquals("Hello world!", chat.getMessage());

            // Event 8: ENTITY_REMOVE at t=4000
            ReplayEvent event8 = reader.readEvent();
            assertInstanceOf(EntityRemoveEvent.class, event8);
            EntityRemoveEvent entityRemove = (EntityRemoveEvent) event8;
            assertEquals(4000, entityRemove.getTimestampDeltaMs());
            assertEquals(42, entityRemove.getEntityId());

            // Event 9: PLAYER_REMOVE at t=5000
            ReplayEvent event9 = reader.readEvent();
            assertInstanceOf(PlayerRemoveEvent.class, event9);
            PlayerRemoveEvent playerRemove = (PlayerRemoveEvent) event9;
            assertEquals(5000, playerRemove.getTimestampDeltaMs());
            assertEquals(PLAYER_UUID, playerRemove.getUuid());

            // No more events
            assertNull(reader.readEvent(), "Should return null after all 9 events (EOF)");

            reader.close();
        }
    }

    @Test
    void parseGoldenFileEventCount() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/golden.modlreplay")) {
            assertNotNull(is);
            ReplayReader reader = new ReplayReader(is);
            reader.readHeader();
            reader.readSnapshot();

            int count = 0;
            while (reader.readEvent() != null) {
                count++;
            }
            assertEquals(9, count, "Golden file should contain exactly 9 events");

            reader.close();
        }
    }

    @Test
    void parseGoldenFileEventOrder() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/golden.modlreplay")) {
            assertNotNull(is);
            ReplayReader reader = new ReplayReader(is);
            reader.readHeader();
            reader.readSnapshot();

            ReplayEvent.EventType[] expectedOrder = {
                    ReplayEvent.EventType.BLOCK_CHANGE,
                    ReplayEvent.EventType.PLAYER_SPAWN,
                    ReplayEvent.EventType.PLAYER_MOVE,
                    ReplayEvent.EventType.ENTITY_SPAWN,
                    ReplayEvent.EventType.ENTITY_MOVE,
                    ReplayEvent.EventType.PLAYER_ANIM,
                    ReplayEvent.EventType.CHAT,
                    ReplayEvent.EventType.ENTITY_REMOVE,
                    ReplayEvent.EventType.PLAYER_REMOVE
            };

            for (int i = 0; i < expectedOrder.length; i++) {
                ReplayEvent event = reader.readEvent();
                assertNotNull(event, "Event " + (i + 1) + " should not be null");
                assertEquals(expectedOrder[i], event.getType(),
                        "Event " + (i + 1) + " should be " + expectedOrder[i]);
            }

            reader.close();
        }
    }

    // ── Minimal fixture ────────────────────────────────────────────────

    @Test
    void parseMinimalFileHeader() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/minimal.modlreplay")) {
            assertNotNull(is, "minimal.modlreplay fixture not found on classpath");
            ReplayReader reader = new ReplayReader(is);

            ReplayHeader header = reader.readHeader();
            assertEquals(1, header.getVersion());
            assertEquals(1700000000000L, header.getStartTime());
            assertEquals("1.21.4", header.getMcVersion());
            assertEquals(0, header.getTargetX());
            assertEquals(64, header.getTargetY());
            assertEquals(0, header.getTargetZ());
            assertEquals(64, header.getRadiusBlocks());

            reader.close();
        }
    }

    @Test
    void parseMinimalFileEmptySnapshot() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/minimal.modlreplay")) {
            assertNotNull(is);
            ReplayReader reader = new ReplayReader(is);
            reader.readHeader();

            List<BlockSnapshot> blocks = reader.readSnapshot();
            assertTrue(blocks.isEmpty(), "Minimal file should have 0 blocks in snapshot");

            reader.close();
        }
    }

    @Test
    void parseMinimalFileNoEvents() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/minimal.modlreplay")) {
            assertNotNull(is);
            ReplayReader reader = new ReplayReader(is);
            reader.readHeader();
            reader.readSnapshot();

            assertNull(reader.readEvent(), "Minimal file should have no events (readEvent returns null immediately)");

            reader.close();
        }
    }
}

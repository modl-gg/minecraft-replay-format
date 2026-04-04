package gg.modl.replay.format;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.DataOutputStream;
import java.io.IOException;

@Getter
@RequiredArgsConstructor
public abstract class ReplayEvent {

    public enum EventType {
        BLOCK_CHANGE(0x01),
        ENTITY_SPAWN(0x02),
        ENTITY_MOVE(0x03),
        ENTITY_REMOVE(0x04),
        PLAYER_SPAWN(0x05),
        PLAYER_MOVE(0x06),
        PLAYER_REMOVE(0x07),
        PLAYER_ANIM(0x08),
        CHAT(0x09),
        PLAYER_EQUIPMENT(0x0A),
        PLAYER_EQUIPMENT_FULL(0x0B),
        PLAYER_HEALTH(0x0C),
        PLAYER_EFFECTS(0x0D),
        PLAYER_INVENTORY(0x0E),
        PLAYER_SKIN(0x0F),
        PLAYER_BLOCK_PLACE(0x10),
        PLAYER_BLOCK_BREAK(0x11);

        @Getter
        private final int id;

        EventType(int id) { this.id = id; }

        /**
         * Returns the EventType for the given id, or null if unknown (TLV skip support).
         */
        public static EventType fromId(int id) {
            for (EventType t : values()) {
                if (t.id == id) return t;
            }
            return null;
        }
    }

    private final int timestampDeltaMs;

    public abstract EventType getType();

    public abstract void writePayload(DataOutputStream out) throws IOException;

    /**
     * Returns the size in bytes of this event's payload (excluding the event type byte,
     * timestamp, and TLV length field). Used by the v4 TLV envelope.
     */
    public abstract int payloadSize();
}

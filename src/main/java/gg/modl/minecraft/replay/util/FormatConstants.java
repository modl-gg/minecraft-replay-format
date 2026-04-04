package gg.modl.minecraft.replay.util;

public final class FormatConstants {
    public static final byte[] MAGIC = {'M', 'O', 'D', 'L'};
    public static final int VERSION = 4;

    // Event type bytes
    public static final int EVENT_BLOCK_CHANGE = 0x01;
    public static final int EVENT_ENTITY_SPAWN = 0x02;
    public static final int EVENT_ENTITY_MOVE = 0x03;
    public static final int EVENT_ENTITY_REMOVE = 0x04;
    public static final int EVENT_PLAYER_SPAWN = 0x05;
    public static final int EVENT_PLAYER_MOVE = 0x06;
    public static final int EVENT_PLAYER_REMOVE = 0x07;
    public static final int EVENT_PLAYER_ANIM = 0x08;
    public static final int EVENT_CHAT = 0x09;
    public static final int EVENT_PLAYER_EQUIPMENT = 0x0A;
    public static final int EVENT_PLAYER_EQUIPMENT_FULL = 0x0B;
    public static final int EVENT_PLAYER_HEALTH = 0x0C;
    public static final int EVENT_PLAYER_EFFECTS = 0x0D;
    public static final int EVENT_PLAYER_INVENTORY = 0x0E;
    public static final int EVENT_PLAYER_SKIN = 0x0F;
    public static final int EVENT_PLAYER_BLOCK_PLACE = 0x10;
    public static final int EVENT_PLAYER_BLOCK_BREAK = 0x11;

    /**
     * Encode a degree angle to the format's short representation (v1-v3).
     * Maps 0..360 degrees to 0..32767.
     */
    public static short encodeAngle(float degrees) {
        return (short) (degrees / 360.0f * 32767.0f);
    }

    /**
     * Decode a v1-v3 short-encoded angle back to degrees.
     */
    public static float decodeAngle(short raw) {
        return raw * 360.0f / 32767.0f;
    }

    private FormatConstants() {}
}

package gg.modl.minecraft.replay.format.events;

import gg.modl.minecraft.replay.format.ReplayEvent;
import lombok.Getter;
import lombok.ToString;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Getter
@ToString
public class ChatEvent extends ReplayEvent {
    private final UUID uuid;
    private final String message;

    public ChatEvent(int timestampDeltaMs, UUID uuid, String message) {
        super(timestampDeltaMs);
        this.uuid = uuid;
        this.message = message;
    }

    @Override public EventType getType() { return EventType.CHAT; }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        out.writeShort(msgBytes.length);
        out.write(msgBytes);
    }

    @Override
    public int payloadSize() {
        return 16 + 2 + message.getBytes(StandardCharsets.UTF_8).length;
    }
}

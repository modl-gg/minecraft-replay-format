package gg.modl.minecraft.replay;

import gg.modl.minecraft.replay.format.ReplayEvent;
import gg.modl.minecraft.replay.format.ReplayHeader;
import gg.modl.minecraft.replay.util.BlockSnapshot;
import gg.modl.minecraft.replay.util.FormatConstants;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ReplayWriter implements Closeable {

    private final DataOutputStream out;

    public ReplayWriter(OutputStream outputStream) {
        this.out = new DataOutputStream(new BufferedOutputStream(outputStream, 8192));
    }

    public void writeHeader(ReplayHeader header) throws IOException {
        out.write(FormatConstants.MAGIC);
        out.writeShort(header.getVersion());
        out.writeLong(header.getStartTime());
        byte[] mcVersionBytes = header.getMcVersion().getBytes(StandardCharsets.UTF_8);
        out.writeShort(mcVersionBytes.length);
        out.write(mcVersionBytes);
        out.writeInt(header.getTargetX());
        out.writeInt(header.getTargetY());
        out.writeInt(header.getTargetZ());
        out.writeInt(header.getRadiusBlocks());
    }

    public void writeSnapshot(List<BlockSnapshot> blocks) throws IOException {
        out.writeInt(blocks.size());
        for (BlockSnapshot block : blocks) {
            out.writeInt(block.getX());
            out.writeShort(block.getY());
            out.writeInt(block.getZ());
            out.writeInt(block.getStateId());
        }
    }

    public void writeEvent(ReplayEvent event) throws IOException {
        out.writeByte(event.getType().getId());
        out.writeInt(event.getTimestampDeltaMs());
        out.writeShort(event.payloadSize());
        event.writePayload(out);
    }

    public void writeEvent(ReplayEvent event, long timestampOffset) throws IOException {
        out.writeByte(event.getType().getId());
        out.writeInt((int) Math.max(0, event.getTimestampDeltaMs() - timestampOffset));
        out.writeShort(event.payloadSize());
        event.writePayload(out);
    }

    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}

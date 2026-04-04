package gg.modl.minecraft.replay.api;

import java.io.IOException;
import java.io.OutputStream;

public interface ReplayOutput {
    OutputStream openStream() throws IOException;
    void onComplete(ReplayMetadata metadata);
    void onError(Exception e);
}

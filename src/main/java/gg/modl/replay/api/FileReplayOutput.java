package gg.modl.replay.api;

import lombok.Getter;

import java.io.*;
import java.util.zip.GZIPOutputStream;

public class FileReplayOutput implements ReplayOutput {

    private final File directory;
    private final boolean compress;
    @Getter
    private File outputFile;

    public FileReplayOutput(File directory) {
        this(directory, true);
    }

    public FileReplayOutput(File directory, boolean compress) {
        this.directory = directory;
        this.compress = compress;
    }

    @Override
    public OutputStream openStream() throws IOException {
        directory.mkdirs();
        outputFile = new File(directory, "replay-" + System.currentTimeMillis() + ".modlreplay");
        OutputStream out = new FileOutputStream(outputFile);
        return compress ? new GZIPOutputStream(out) : out;
    }

    @Override
    public void onComplete(ReplayMetadata metadata) {
    }

    @Override
    public void onError(Exception e) {
        if (outputFile != null && outputFile.exists()) {
            outputFile.delete();
        }
    }
}

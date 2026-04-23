package minigit.core;

import java.nio.file.Path;

import minigit.utils.FileUtils;
import minigit.utils.HashUtils;

public class ObjectStore {
    private final Path objectsDir;

    public ObjectStore(Path objectsDir) {
        this.objectsDir = objectsDir;
    }

    public String storeBlob(Path file) {
        byte[] data = FileUtils.readBytes(file);
        String id = HashUtils.sha256(data);
        Path obj = objectsDir.resolve(id);
        if (!FileUtils.exists(obj)) {
            FileUtils.writeBytes(obj, data);
        }
        return id;
    }

    public Blob readBlob(String id) {
        Path obj = objectsDir.resolve(id);
        if (!FileUtils.exists(obj)) {
            throw new IllegalArgumentException("Missing object: " + id);
        }
        byte[] data = FileUtils.readBytes(obj);
        return new Blob(id, data);
    }

    public void storeCommit(Commit commit) {
        Path obj = objectsDir.resolve(commit.id);
        if (!FileUtils.exists(obj)) {
            FileUtils.writeText(obj, commit.serialize());
        }
    }

    public Commit readCommit(String id) {
        Path obj = objectsDir.resolve(id);
        if (!FileUtils.exists(obj)) {
            throw new IllegalArgumentException("Missing commit: " + id);
        }
        String raw = FileUtils.readText(obj);
        Commit commit = Commit.parse(raw);
        if (!commit.id.equals(id)) {
            throw new IllegalStateException("Commit hash mismatch for " + id);
        }
        return commit;
    }
}

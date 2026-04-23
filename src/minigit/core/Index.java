package minigit.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import minigit.utils.FileUtils;

public class Index {
    public static final class Entry {
        private final String path;
        private final String blobId;
        private final boolean deletion;

        private Entry(String path, String blobId, boolean deletion) {
            this.path = path;
            this.blobId = blobId;
            this.deletion = deletion;
        }

        public static Entry file(String path, String blobId) {
            return new Entry(path, blobId, false);
        }

        public static Entry deletion(String path) {
            return new Entry(path, null, true);
        }

        public String getPath() {
            return path;
        }

        public String getBlobId() {
            return blobId;
        }

        public boolean isDeletion() {
            return deletion;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static Index load(Path indexFile) {
        Index index = new Index();
        if (!FileUtils.exists(indexFile)) {
            return index;
        }
        List<String> lines = FileUtils.readLines(indexFile);
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", 2);
            if (parts.length == 2) {
                if ("D".equals(parts[0])) {
                    index.stageDeletion(parts[1]);
                } else if ("A".equals(parts[0])) {
                    String[] addParts = line.split("\t", 3);
                    if (addParts.length == 3) {
                        index.stageFile(addParts[1], addParts[2]);
                    }
                } else {
                    index.stageFile(parts[0], parts[1]);
                }
            }
        }
        return index;
    }

    public void save(Path indexFile) {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : entries.values()) {
            if (entry.isDeletion()) {
                sb.append("D").append("\t").append(entry.getPath()).append("\n");
            } else {
                sb.append("A").append("\t").append(entry.getPath()).append("\t").append(entry.getBlobId()).append("\n");
            }
        }
        FileUtils.writeText(indexFile, sb.toString());
    }

    public Collection<Entry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public Entry get(String path) {
        return entries.get(path);
    }

    public boolean contains(String path) {
        return entries.containsKey(path);
    }

    public void stageFile(String path, String blobId) {
        entries.put(path, Entry.file(path, blobId));
    }

    public void stageDeletion(String path) {
        entries.put(path, Entry.deletion(path));
    }

    public void unstage(String path) {
        entries.remove(path);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}

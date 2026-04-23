package minigit.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import minigit.utils.HashUtils;

public class Commit {
    public final String id;
    public final String parent;
    public final long timestamp;
    public final String message;
    public final Map<String, String> files;

    public Commit(String parent, long timestamp, String message, Map<String, String> files) {
        this.parent = parent;
        this.timestamp = timestamp;
        this.message = message;
        this.files = new LinkedHashMap<>(files);
        this.id = HashUtils.sha256(toRawString(parent, timestamp, message, files));
    }

    public static Commit parse(String raw) {
        String[] lines = raw.split("\n");
        String parent = null;
        long timestamp = 0L;
        String message = "";
        Map<String, String> files = new LinkedHashMap<>();
        boolean inFiles = false;
        for (String line : lines) {
            if (line.startsWith("parent: ")) {
                parent = line.substring("parent: ".length());
                if (parent.isEmpty()) {
                    parent = null;
                }
            } else if (line.startsWith("timestamp: ")) {
                timestamp = Long.parseLong(line.substring("timestamp: ".length()));
            } else if (line.startsWith("message: ")) {
                message = line.substring("message: ".length());
            } else if (line.equals("files:")) {
                inFiles = true;
            } else if (inFiles && !line.trim().isEmpty()) {
                String[] parts = line.split("\t", 2);
                if (parts.length == 2) {
                    files.put(parts[0], parts[1]);
                }
            }
        }
        return new Commit(parent, timestamp, message, files);
    }

    public String serialize() {
        return toRawString(parent, timestamp, message, files);
    }

    private static String toRawString(String parent, long timestamp, String message, Map<String, String> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("type: commit\n");
        sb.append("parent: ").append(parent == null ? "" : parent).append("\n");
        sb.append("timestamp: ").append(timestamp).append("\n");
        sb.append("message: ").append(message == null ? "" : message).append("\n");
        sb.append("files:\n");
        for (Map.Entry<String, String> entry : files.entrySet()) {
            sb.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    public static Commit create(String parent, String message, Map<String, String> files) {
        return new Commit(parent, Instant.now().toEpochMilli(), message, files);
    }
}

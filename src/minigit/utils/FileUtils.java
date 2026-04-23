package minigit.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import minigit.core.IgnoreRules;

public class FileUtils {
    public static boolean exists(Path path) {
        return Files.exists(path);
    }

    public static Path findRepoRoot(Path start) {
        Path current = start.toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve(".minigit"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    public static List<Path> listFiles(Path repoRoot, IgnoreRules ignore) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                if (!ignore.isIgnored(repoRoot, path)) {
                    files.add(path);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list files", ex);
        }
        Collections.sort(files);
        return files;
    }

    public static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read file: " + path, ex);
        }
    }

    public static void writeBytes(Path path, byte[] data) {
        try {
            ensureParent(path);
            Files.write(path, data);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write file: " + path, ex);
        }
    }

    public static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read file: " + path, ex);
        }
    }

    public static String readText(Path path) {
        return new String(readBytes(path), StandardCharsets.UTF_8);
    }

    public static void writeText(Path path, String content) {
        try {
            ensureParent(path);
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write file: " + path, ex);
        }
    }

    public static String toRepoRelative(Path repoRoot, Path file) {
        return repoRoot.relativize(file).toString().replace("\\", "/");
    }

    public static void ensureParent(Path path) {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create directories: " + parent, ex);
            }
        }
    }
}

package minigit.core;

import java.nio.file.Path;

import minigit.utils.FileUtils;

public class Branch {
    public static boolean exists(Path repoRoot, String name) {
        return FileUtils.exists(repoRoot.resolve(".minigit").resolve("refs").resolve("heads").resolve(name));
    }

    public static String readBranch(Path repoRoot, String name) {
        Path ref = repoRoot.resolve(".minigit").resolve("refs").resolve("heads").resolve(name);
        if (!FileUtils.exists(ref)) {
            return null;
        }
        String value = FileUtils.readText(ref).trim();
        return value.isEmpty() ? null : value;
    }

    public static void writeBranch(Path repoRoot, String name, String commitId) {
        Path ref = repoRoot.resolve(".minigit").resolve("refs").resolve("heads").resolve(name);
        FileUtils.writeText(ref, commitId == null ? "" : commitId);
    }

    public static String getHeadRef(Path repoRoot) {
        Path head = repoRoot.resolve(".minigit").resolve("HEAD");
        String value = FileUtils.readText(head).trim();
        if (value.startsWith("ref: ")) {
            return value.substring("ref: ".length());
        }
        return null;
    }

    public static String readHeadCommit(Path repoRoot) {
        Path head = repoRoot.resolve(".minigit").resolve("HEAD");
        String value = FileUtils.readText(head).trim();
        if (value.startsWith("ref: ")) {
            String refPath = value.substring("ref: ".length());
            Path ref = repoRoot.resolve(".minigit").resolve(refPath);
            if (!FileUtils.exists(ref)) {
                return null;
            }
            String refValue = FileUtils.readText(ref).trim();
            return refValue.isEmpty() ? null : refValue;
        }
        return value.isEmpty() ? null : value;
    }

    public static void updateHeadCommit(Path repoRoot, String commitId) {
        String headRef = getHeadRef(repoRoot);
        if (headRef != null) {
            Path ref = repoRoot.resolve(".minigit").resolve(headRef);
            FileUtils.writeText(ref, commitId == null ? "" : commitId);
        } else {
            Path head = repoRoot.resolve(".minigit").resolve("HEAD");
            FileUtils.writeText(head, commitId == null ? "" : commitId);
        }
    }

    public static void setHeadToBranch(Path repoRoot, String name) {
        Path head = repoRoot.resolve(".minigit").resolve("HEAD");
        FileUtils.writeText(head, "ref: refs/heads/" + name);
    }

    public static void setHeadDetached(Path repoRoot, String commitId) {
        Path head = repoRoot.resolve(".minigit").resolve("HEAD");
        FileUtils.writeText(head, commitId == null ? "" : commitId);
    }
}

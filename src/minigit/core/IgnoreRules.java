package minigit.core;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

import minigit.utils.FileUtils;

public class IgnoreRules {
    private final List<PathMatcher> matchers = new ArrayList<>();
    private final List<String> rawRules = new ArrayList<>();

    public static IgnoreRules load(Path repoRoot) {
        IgnoreRules rules = new IgnoreRules();
        Path ignoreFile = repoRoot.resolve(".minigitignore");
        if (!FileUtils.exists(ignoreFile)) {
            return rules;
        }
        List<String> lines = FileUtils.readLines(ignoreFile);
        FileSystem fs = FileSystems.getDefault();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            rules.rawRules.add(trimmed);
            if (trimmed.endsWith("/")) {
                String dirRule = trimmed.substring(0, trimmed.length() - 1) + "/**";
                rules.matchers.add(fs.getPathMatcher("glob:" + dirRule));
            } else {
                rules.matchers.add(fs.getPathMatcher("glob:" + trimmed));
            }
        }
        return rules;
    }

    public boolean isIgnored(Path repoRoot, Path path) {
        Path rel = repoRoot.relativize(path);
        if (rel.startsWith(".minigit")) {
            return true;
        }
        for (int i = 0; i < matchers.size(); i++) {
            PathMatcher matcher = matchers.get(i);
            if (matcher.matches(rel)) {
                return true;
            }
            String rule = rawRules.get(i);
            if (!rule.contains("/")) {
                Path fileName = rel.getFileName();
                if (fileName != null && matcher.matches(fileName)) {
                    return true;
                }
            }
        }
        return false;
    }
}

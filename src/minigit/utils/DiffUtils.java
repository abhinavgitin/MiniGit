package minigit.utils;

import java.util.ArrayList;
import java.util.List;

public class DiffUtils {
    public static List<String> diffLines(String oldText, String newText) {
        List<String> oldLines = splitLines(oldText);
        List<String> newLines = splitLines(newText);
        List<String> out = new ArrayList<>();
        int max = Math.max(oldLines.size(), newLines.size());
        for (int i = 0; i < max; i++) {
            String oldLine = i < oldLines.size() ? oldLines.get(i) : null;
            String newLine = i < newLines.size() ? newLines.get(i) : null;
            if (oldLine == null) {
                out.add("+" + newLine);
            } else if (newLine == null) {
                out.add("-" + oldLine);
            } else if (!oldLine.equals(newLine)) {
                out.add("-" + oldLine);
                out.add("+" + newLine);
            }
        }
        return out;
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] parts = text.split("\r?\n", -1);
        for (String part : parts) {
            lines.add(part);
        }
        return lines;
    }
}

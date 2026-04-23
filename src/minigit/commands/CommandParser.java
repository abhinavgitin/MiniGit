package minigit.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandParser {
    public static ParsedCommand parse(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        String name = args[0];
        ParsedCommand cmd = new ParsedCommand(name);
        int i = 1;
        while (i < args.length) {
            String token = args[i];
            if (token.startsWith("-")) {
                String value = null;
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    value = args[i + 1];
                    i += 2;
                } else {
                    i += 1;
                }
                cmd.options.put(token, value);
            } else {
                cmd.args.add(token);
                i += 1;
            }
        }
        return cmd;
    }

    public static class ParsedCommand {
        public final String name;
        public final List<String> args = new ArrayList<>();
        public final Map<String, String> options = new HashMap<>();

        public ParsedCommand(String name) {
            this.name = name;
        }

        public String getRequiredArg(int index) {
            if (index >= args.size()) {
                throw new IllegalArgumentException("Missing argument for command: " + name);
            }
            return args.get(index);
        }

        public String getOption(String key) {
            return options.get(key);
        }
    }
}

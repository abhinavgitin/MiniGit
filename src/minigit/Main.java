package minigit;

import minigit.commands.CommandParser;
import minigit.commands.CommandParser.ParsedCommand;
import minigit.core.Repository;

public class Main {
    public static void main(String[] args) {
        ParsedCommand command = CommandParser.parse(args);
        if (command == null) {
            printHelp();
            return;
        }

        try {
            Repository repo = new Repository(System.getProperty("user.dir"));
            switch (command.name) {
                case "init":
                    repo.init();
                    break;
                case "add":
                    repo.add(command.getRequiredArg(0));
                    break;
                case "commit":
                    repo.commit(command.getOption("-m"));
                    break;
                case "log":
                    repo.log();
                    break;
                case "status":
                    repo.status();
                    break;
                case "checkout":
                    repo.checkout(command.getRequiredArg(0));
                    break;
                case "branch":
                    repo.branch(command.getRequiredArg(0));
                    break;
                case "diff":
                    repo.diff();
                    break;
                case "merge":
                    repo.merge(command.getRequiredArg(0));
                    break;
                default:
                    printHelp();
                    break;
            }
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    private static void printHelp() {
        System.out.println("MiniGit usage:");
        System.out.println("  init");
        System.out.println("  add <file|dir>");
        System.out.println("  commit -m \"message\"");
        System.out.println("  log");
        System.out.println("  status");
        System.out.println("  diff");
        System.out.println("  branch <name>");
        System.out.println("  checkout <commit_id|branch>");
        System.out.println("  merge <branch>");
    }
}

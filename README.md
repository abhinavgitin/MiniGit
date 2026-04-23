# MiniGit

A lightweight Git-like version control system in Java. It supports repository init, staging, commits, log history, checkout/restore, branching, status, diff, and a basic fast-forward merge.

## Features
- init, add, commit, log, checkout
- branch creation and switching
- status (staged/modified/untracked)
- diff (working tree vs HEAD)
- merge (fast-forward only)
- .minigitignore support

## Build
```bash
javac -d out src/minigit/**/*.java
```

## Run
```bash
java -cp out minigit.Main init
java -cp out minigit.Main add file.txt
java -cp out minigit.Main commit -m "first commit"
java -cp out minigit.Main log
java -cp out minigit.Main status
java -cp out minigit.Main diff
java -cp out minigit.Main branch feature
java -cp out minigit.Main checkout feature
```

## Project Layout
```
src/
  minigit/
    Main.java
    commands/
    core/
    utils/
```

## Resume Description
Developed a Git-like version control system in Java implementing core features such as commits, branching, and file tracking using hashing and persistent storage.

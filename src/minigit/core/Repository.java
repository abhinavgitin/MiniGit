package minigit.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import minigit.utils.DiffUtils;
import minigit.utils.FileUtils;
import minigit.utils.HashUtils;
import minigit.utils.TimeUtils;

public class Repository {
    private final Path workingDir;

    public Repository(String workingDir) {
        this.workingDir = Paths.get(workingDir).toAbsolutePath();
    }

    public void init() {
        Path repoRoot = workingDir;
        Path repoDir = repoRoot.resolve(".minigit");
        if (FileUtils.exists(repoDir)) {
            throw new IllegalStateException("Repository already initialized");
        }
        try {
            Files.createDirectories(repoDir.resolve("objects"));
            Files.createDirectories(repoDir.resolve("refs").resolve("heads"));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize repository structure", ex);
        }
        FileUtils.writeText(repoDir.resolve("HEAD"), "ref: refs/heads/master");
        FileUtils.writeText(repoDir.resolve("refs").resolve("heads").resolve("master"), "");
        FileUtils.writeText(repoDir.resolve("index"), "");
        System.out.println("Initialized empty MiniGit repository in " + repoDir);
    }

    public void add(String target) {
        Path repoRoot = requireRepoRoot();
        Path targetPath = normalizeRepoPath(repoRoot, target);
        ObjectStore store = new ObjectStore(repoRoot.resolve(".minigit").resolve("objects"));
        RepositoryState state = loadState(repoRoot);
        TreeSet<String> touchedPaths = new TreeSet<>();

        if (FileUtils.exists(targetPath)) {
            if (Files.isDirectory(targetPath)) {
                List<Path> files = collectTargets(repoRoot, targetPath, state.ignoreRules);
                for (Path file : files) {
                    String relPath = FileUtils.toRepoRelative(repoRoot, file);
                    stageFile(repoRoot, state, store, file, relPath);
                    touchedPaths.add(relPath);
                }
                stageDirectoryDeletions(repoRoot, targetPath, state, touchedPaths);
            } else {
                String relPath = FileUtils.toRepoRelative(repoRoot, targetPath);
                stageFile(repoRoot, state, store, targetPath, relPath);
                touchedPaths.add(relPath);
            }
        } else {
            String relPath = FileUtils.toRepoRelative(repoRoot, targetPath);
            if (state.headFiles.containsKey(relPath)) {
                state.index.stageDeletion(relPath);
                touchedPaths.add(relPath);
            } else if (state.index.contains(relPath)) {
                state.index.unstage(relPath);
                touchedPaths.add(relPath);
            } else {
                throw new IllegalArgumentException("Path does not exist: " + target);
            }
        }

        state.index.save(indexFile(repoRoot));
        System.out.println("Updated staging for " + touchedPaths.size() + " path(s)");
    }

    public void commit(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Commit message required");
        }

        Path repoRoot = requireRepoRoot();
        RepositoryState state = loadState(repoRoot);
        if (state.index.isEmpty()) {
            throw new IllegalStateException("Nothing to commit");
        }

        Map<String, String> committedFiles = applyIndex(state.headFiles, state.index.getEntries());
        if (committedFiles.equals(state.headFiles)) {
            clearIndex(repoRoot);
            throw new IllegalStateException("Nothing to commit");
        }

        Commit commit = Commit.create(Branch.readHeadCommit(repoRoot), message, committedFiles);
        ObjectStore store = new ObjectStore(objectsDir(repoRoot));
        store.storeCommit(commit);
        Branch.updateHeadCommit(repoRoot, commit.id);
        clearIndex(repoRoot);
        System.out.println("Committed " + commit.id.substring(0, 8) + " - " + message);
    }

    public void log() {
        Path repoRoot = requireRepoRoot();
        ObjectStore store = new ObjectStore(objectsDir(repoRoot));
        String current = Branch.readHeadCommit(repoRoot);
        if (current == null) {
            System.out.println("No commits yet");
            return;
        }

        while (current != null && !current.isEmpty()) {
            Commit commit = store.readCommit(current);
            System.out.println("commit " + commit.id);
            System.out.println("Date:   " + TimeUtils.format(commit.timestamp));
            System.out.println("\n    " + commit.message + "\n");
            current = commit.parent;
        }
    }

    public void status() {
        Path repoRoot = requireRepoRoot();
        RepositoryState state = loadState(repoRoot);

        List<String> stagedAdded = new ArrayList<>();
        List<String> stagedModified = new ArrayList<>();
        List<String> stagedDeleted = new ArrayList<>();
        for (String path : new TreeSet<>(state.stagedFiles.keySet())) {
            String headBlob = state.headFiles.get(path);
            String stagedBlob = state.stagedFiles.get(path);
            if (headBlob == null) {
                stagedAdded.add(path);
            } else if (!headBlob.equals(stagedBlob)) {
                stagedModified.add(path);
            }
        }
        for (String path : new TreeSet<>(state.headFiles.keySet())) {
            if (!state.stagedFiles.containsKey(path)) {
                stagedDeleted.add(path);
            }
        }

        List<String> unstagedModified = new ArrayList<>();
        List<String> unstagedDeleted = new ArrayList<>();
        for (String path : new TreeSet<>(state.stagedFiles.keySet())) {
            String stagedBlob = state.stagedFiles.get(path);
            String workingBlob = state.workingFiles.get(path);
            if (workingBlob == null) {
                unstagedDeleted.add(path);
            } else if (!stagedBlob.equals(workingBlob)) {
                unstagedModified.add(path);
            }
        }

        List<String> untracked = new ArrayList<>();
        for (String path : new TreeSet<>(state.workingFiles.keySet())) {
            if (!state.stagedFiles.containsKey(path)) {
                untracked.add(path);
            }
        }

        printList("Staged changes (added):", stagedAdded);
        printList("Staged changes (modified):", stagedModified);
        printList("Staged changes (deleted):", stagedDeleted);
        printList("Unstaged changes (modified):", unstagedModified);
        printList("Unstaged changes (deleted):", unstagedDeleted);
        printList("Untracked files:", untracked);
    }

    public void checkout(String target) {
        Path repoRoot = requireRepoRoot();
        RepositoryState state = loadState(repoRoot);

        if (Branch.exists(repoRoot, target)) {
            String targetCommitId = Branch.readBranch(repoRoot, target);
            Map<String, String> targetFiles = readCommitFiles(repoRoot, targetCommitId);
            ensureRestoreSafe(state, targetFiles);
            restoreWorkingTree(repoRoot, state.stagedFiles, targetFiles);
            Branch.setHeadToBranch(repoRoot, target);
            clearIndex(repoRoot);
            System.out.println("Switched to branch " + target);
            return;
        }

        ObjectStore store = new ObjectStore(objectsDir(repoRoot));
        Commit commit;
        try {
            commit = store.readCommit(target);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown commit or branch: " + target);
        }

        Map<String, String> targetFiles = sortedCopy(commit.files);
        ensureRestoreSafe(state, targetFiles);
        restoreWorkingTree(repoRoot, state.stagedFiles, targetFiles);
        Branch.setHeadDetached(repoRoot, commit.id);
        clearIndex(repoRoot);
        System.out.println("Checked out commit " + commit.id.substring(0, 8));
    }

    public void branch(String name) {
        Path repoRoot = requireRepoRoot();
        if (Branch.exists(repoRoot, name)) {
            throw new IllegalStateException("Branch already exists: " + name);
        }
        Branch.writeBranch(repoRoot, name, Branch.readHeadCommit(repoRoot));
        System.out.println("Created branch " + name);
    }

    public void diff() {
        Path repoRoot = requireRepoRoot();
        RepositoryState state = loadState(repoRoot);
        if (Branch.readHeadCommit(repoRoot) == null) {
            System.out.println("No commits to diff against");
            return;
        }

        ObjectStore store = new ObjectStore(objectsDir(repoRoot));
        TreeSet<String> paths = new TreeSet<>();
        paths.addAll(state.headFiles.keySet());
        paths.addAll(state.workingFiles.keySet());

        for (String path : paths) {
            String oldBlob = state.headFiles.get(path);
            String newBlob = state.workingFiles.get(path);
            String oldText = oldBlob == null ? "" : new String(store.readBlob(oldBlob).data, StandardCharsets.UTF_8);
            String newText = newBlob == null ? "" : new String(FileUtils.readBytes(repoRoot.resolve(path)), StandardCharsets.UTF_8);
            if (oldText.equals(newText)) {
                continue;
            }
            System.out.println("diff -- " + path);
            for (String line : DiffUtils.diffLines(oldText, newText)) {
                System.out.println(line);
            }
        }
    }

    public void merge(String branchName) {
        Path repoRoot = requireRepoRoot();
        if (!Branch.exists(repoRoot, branchName)) {
            throw new IllegalArgumentException("Branch not found: " + branchName);
        }

        RepositoryState state = loadState(repoRoot);
        String headCommitId = Branch.readHeadCommit(repoRoot);
        String targetCommitId = Branch.readBranch(repoRoot, branchName);

        if (targetCommitId == null) {
            System.out.println("Branch is empty: " + branchName);
            return;
        }
        if (headCommitId != null && headCommitId.equals(targetCommitId)) {
            System.out.println("Already up to date");
            return;
        }
        if (headCommitId != null && isAncestor(repoRoot, targetCommitId, headCommitId)) {
            System.out.println("Already up to date");
            return;
        }
        if (headCommitId != null && !isAncestor(repoRoot, headCommitId, targetCommitId)) {
            throw new IllegalStateException("Non fast-forward merge is not supported");
        }

        Map<String, String> targetFiles = readCommitFiles(repoRoot, targetCommitId);
        ensureRestoreSafe(state, targetFiles);
        restoreWorkingTree(repoRoot, state.stagedFiles, targetFiles);
        Branch.updateHeadCommit(repoRoot, targetCommitId);
        clearIndex(repoRoot);
        System.out.println("Fast-forward merge to " + targetCommitId.substring(0, 8));
    }

    private void stageFile(Path repoRoot, RepositoryState state, ObjectStore store, Path file, String relPath) {
        if (state.ignoreRules.isIgnored(repoRoot, file)) {
            return;
        }

        String blobId = store.storeBlob(file);
        String headBlob = state.headFiles.get(relPath);
        if (blobId.equals(headBlob)) {
            state.index.unstage(relPath);
        } else {
            state.index.stageFile(relPath, blobId);
        }
    }

    private void stageDirectoryDeletions(Path repoRoot, Path targetDir, RepositoryState state, TreeSet<String> touchedPaths) {
        TreeSet<String> candidatePaths = new TreeSet<>(state.headFiles.keySet());
        for (Index.Entry entry : state.index.getEntries()) {
            candidatePaths.add(entry.getPath());
        }

        for (String relPath : candidatePaths) {
            Path candidate = repoRoot.resolve(relPath).normalize();
            if (!candidate.startsWith(targetDir) || FileUtils.exists(candidate)) {
                continue;
            }
            if (state.headFiles.containsKey(relPath)) {
                state.index.stageDeletion(relPath);
            } else {
                state.index.unstage(relPath);
            }
            touchedPaths.add(relPath);
        }
    }

    private void ensureRestoreSafe(RepositoryState state, Map<String, String> targetFiles) {
        if (!state.index.isEmpty()) {
            throw new IllegalStateException("Uncommitted staged changes present");
        }

        if (hasTrackedWorkingTreeChanges(state)) {
            throw new IllegalStateException("Uncommitted changes present");
        }

        for (String path : state.workingFiles.keySet()) {
            if (!state.stagedFiles.containsKey(path) && targetFiles.containsKey(path)) {
                throw new IllegalStateException("Untracked file would be overwritten: " + path);
            }
        }
    }

    private boolean hasTrackedWorkingTreeChanges(RepositoryState state) {
        TreeSet<String> trackedPaths = new TreeSet<>(state.stagedFiles.keySet());
        for (String path : trackedPaths) {
            String stagedBlob = state.stagedFiles.get(path);
            String workingBlob = state.workingFiles.get(path);
            if (workingBlob == null || !workingBlob.equals(stagedBlob)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAncestor(Path repoRoot, String ancestor, String commitId) {
        ObjectStore store = new ObjectStore(objectsDir(repoRoot));
        String current = commitId;
        while (current != null && !current.isEmpty()) {
            if (current.equals(ancestor)) {
                return true;
            }
            Commit commit = store.readCommit(current);
            current = commit.parent;
        }
        return false;
    }

    private void restoreWorkingTree(Path repoRoot, Map<String, String> currentFiles, Map<String, String> targetFiles) {
        ObjectStore store = new ObjectStore(objectsDir(repoRoot));

        for (String path : currentFiles.keySet()) {
            if (!targetFiles.containsKey(path)) {
                Path file = repoRoot.resolve(path);
                if (FileUtils.exists(file)) {
                    try {
                        Files.delete(file);
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed to delete file: " + file, ex);
                    }
                }
            }
        }

        for (Map.Entry<String, String> entry : targetFiles.entrySet()) {
            Blob blob = store.readBlob(entry.getValue());
            FileUtils.writeBytes(repoRoot.resolve(entry.getKey()), blob.data);
        }
    }

    private RepositoryState loadState(Path repoRoot) {
        IgnoreRules ignoreRules = IgnoreRules.load(repoRoot);
        Map<String, String> headFiles = readHeadFiles(repoRoot);
        Index index = Index.load(indexFile(repoRoot));
        Map<String, String> stagedFiles = applyIndex(headFiles, index.getEntries());
        Map<String, String> workingFiles = readWorkingFiles(repoRoot, ignoreRules);
        return new RepositoryState(ignoreRules, index, headFiles, stagedFiles, workingFiles);
    }

    private Map<String, String> readWorkingFiles(Path repoRoot, IgnoreRules ignoreRules) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Path file : FileUtils.listFiles(repoRoot, ignoreRules)) {
            files.put(FileUtils.toRepoRelative(repoRoot, file), HashUtils.sha256(FileUtils.readBytes(file)));
        }
        return sortedCopy(files);
    }

    private Map<String, String> readHeadFiles(Path repoRoot) {
        String headCommitId = Branch.readHeadCommit(repoRoot);
        if (headCommitId == null || headCommitId.isEmpty()) {
            return Collections.emptyMap();
        }
        return readCommitFiles(repoRoot, headCommitId);
    }

    private Map<String, String> readCommitFiles(Path repoRoot, String commitId) {
        if (commitId == null || commitId.isEmpty()) {
            return Collections.emptyMap();
        }
        ObjectStore store = new ObjectStore(objectsDir(repoRoot));
        Commit commit = store.readCommit(commitId);
        return sortedCopy(commit.files);
    }

    private Map<String, String> applyIndex(Map<String, String> baseFiles, Collection<Index.Entry> stagedEntries) {
        Map<String, String> files = new LinkedHashMap<>(baseFiles);
        for (Index.Entry entry : stagedEntries) {
            if (entry.isDeletion()) {
                files.remove(entry.getPath());
            } else {
                files.put(entry.getPath(), entry.getBlobId());
            }
        }
        return sortedCopy(files);
    }

    private Map<String, String> sortedCopy(Map<String, String> files) {
        LinkedHashMap<String, String> sorted = new LinkedHashMap<>();
        for (String path : new TreeSet<>(files.keySet())) {
            sorted.put(path, files.get(path));
        }
        return sorted;
    }

    private Path requireRepoRoot() {
        Path root = FileUtils.findRepoRoot(workingDir);
        if (root == null) {
            throw new IllegalStateException("Not a MiniGit repository. Run init first.");
        }
        return root;
    }

    private Path normalizeRepoPath(Path repoRoot, String target) {
        Path targetPath = repoRoot.resolve(target).normalize();
        if (!targetPath.startsWith(repoRoot)) {
            throw new IllegalArgumentException("Path is outside repository: " + target);
        }
        return targetPath;
    }

    private List<Path> collectTargets(Path repoRoot, Path target, IgnoreRules ignore) {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(target)) {
            try {
                Files.walk(target).filter(Files::isRegularFile).forEach(path -> {
                    if (!ignore.isIgnored(repoRoot, path)) {
                        files.add(path);
                    }
                });
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to scan directory: " + target, ex);
            }
        } else if (!ignore.isIgnored(repoRoot, target)) {
            files.add(target);
        }
        Collections.sort(files);
        return files;
    }

    private void printList(String title, List<String> items) {
        System.out.println(title);
        if (items.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        for (String item : items) {
            System.out.println("  " + item);
        }
    }

    private void clearIndex(Path repoRoot) {
        new Index().save(indexFile(repoRoot));
    }

    private Path indexFile(Path repoRoot) {
        return repoRoot.resolve(".minigit").resolve("index");
    }

    private Path objectsDir(Path repoRoot) {
        return repoRoot.resolve(".minigit").resolve("objects");
    }

    private static final class RepositoryState {
        private final IgnoreRules ignoreRules;
        private final Index index;
        private final Map<String, String> headFiles;
        private final Map<String, String> stagedFiles;
        private final Map<String, String> workingFiles;

        private RepositoryState(IgnoreRules ignoreRules, Index index, Map<String, String> headFiles, Map<String, String> stagedFiles, Map<String, String> workingFiles) {
            this.ignoreRules = ignoreRules;
            this.index = index;
            this.headFiles = headFiles;
            this.stagedFiles = stagedFiles;
            this.workingFiles = workingFiles;
        }
    }
}

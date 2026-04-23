package minigit.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import minigit.core.Repository;

public class MiniGitRegressionTest {
    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<>();

        run(failures, "init creates repo structure", MiniGitRegressionTest::testInitCreatesRepoStructure);
        run(failures, "init rejects reinitialization", MiniGitRegressionTest::testInitRejectsReinit);
        run(failures, "add same file multiple times stays stable", MiniGitRegressionTest::testAddSameFileMultipleTimes);
        run(failures, "commit modified file works", MiniGitRegressionTest::testCommitModifiedFile);
        run(failures, "no-op commit is rejected", MiniGitRegressionTest::testNoOpCommitRejected);
        run(failures, "tracked deletion can be staged and committed", MiniGitRegressionTest::testTrackedDeletionCommit);
        run(failures, "status reports staged unstaged and untracked categories", MiniGitRegressionTest::testStatusCategories);
        run(failures, "branch switch works on clean tree", MiniGitRegressionTest::testBranchSwitchWorks);
        run(failures, "branch switch blocked by uncommitted changes", MiniGitRegressionTest::testBranchSwitchBlocked);
        run(failures, "checkout restores previous state", MiniGitRegressionTest::testCheckoutRestoresState);
        run(failures, "invalid checkout target fails clearly", MiniGitRegressionTest::testInvalidCheckoutFailsClearly);
        run(failures, "fast-forward merge updates branch", MiniGitRegressionTest::testFastForwardMerge);
        run(failures, "already-up-to-date merge exits cleanly", MiniGitRegressionTest::testAlreadyUpToDateMerge);
        run(failures, "divergent merge is rejected", MiniGitRegressionTest::testDivergentMergeRejected);
        run(failures, "ignore rules are respected", MiniGitRegressionTest::testIgnoreRulesRespected);
        run(failures, "diff reports changed lines against head", MiniGitRegressionTest::testDiffOutput);

        if (!failures.isEmpty()) {
            System.err.println("MiniGit regression suite failed:");
            for (String failure : failures) {
                System.err.println(" - " + failure);
            }
            System.exit(1);
        }

        System.out.println("MiniGit regression suite passed (" + 16 + " tests)");
    }

    private static void testInitCreatesRepoStructure() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            assertTrue(Files.isDirectory(repo.root.resolve(".minigit")), "expected .minigit directory");
            assertTrue(Files.isDirectory(repo.root.resolve(".minigit").resolve("objects")), "expected objects directory");
            assertTrue(Files.exists(repo.root.resolve(".minigit").resolve("HEAD")), "expected HEAD file");
            assertTrue(Files.exists(repo.root.resolve(".minigit").resolve("index")), "expected index file");
        });
    }

    private static void testInitRejectsReinit() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            expectThrows(IllegalStateException.class, "Repository already initialized", repo.repository::init);
        });
    }

    private static void testAddSameFileMultipleTimes() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "hello\n");
            repo.repository.add("a.txt");
            repo.repository.add("a.txt");

            List<String> lines = Files.readAllLines(repo.root.resolve(".minigit").resolve("index"), StandardCharsets.UTF_8);
            assertEquals(1, lines.size(), "index should contain one staged entry");
        });
    }

    private static void testCommitModifiedFile() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "v1\n");
            repo.repository.add("a.txt");
            repo.repository.commit("first");

            writeFile(repo.root, "a.txt", "v2\n");
            repo.repository.add("a.txt");
            repo.repository.commit("second");

            String log = captureStdout(repo.repository::log);
            assertContains(log, "second", "log should include second commit");
            assertContains(log, "first", "log should include first commit");
        });
    }

    private static void testNoOpCommitRejected() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "v1\n");
            repo.repository.add("a.txt");
            repo.repository.commit("first");

            repo.repository.add("a.txt");
            expectThrows(IllegalStateException.class, "Nothing to commit", () -> repo.repository.commit("noop"));
        });
    }

    private static void testTrackedDeletionCommit() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "v1\n");
            repo.repository.add("a.txt");
            repo.repository.commit("first");

            Files.delete(repo.root.resolve("a.txt"));
            repo.repository.add("a.txt");
            String status = captureStdout(repo.repository::status);
            assertContains(status, "Staged changes (deleted):", "status should show deleted section");
            assertContains(status, "a.txt", "status should list deleted file");

            repo.repository.commit("delete a");
            assertTrue(!Files.exists(repo.root.resolve("a.txt")), "deleted file should stay deleted after commit");
        });
    }

    private static void testStatusCategories() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "tracked.txt", "base\n");
            repo.repository.add("tracked.txt");
            repo.repository.commit("base");

            writeFile(repo.root, "tracked.txt", "staged change\n");
            repo.repository.add("tracked.txt");

            writeFile(repo.root, "tracked.txt", "working change\n");
            writeFile(repo.root, "untracked.txt", "extra\n");

            String status = captureStdout(repo.repository::status);
            assertContains(status, "Staged changes (modified):", "status should show staged modified section");
            assertContains(status, "tracked.txt", "status should include tracked file");
            assertContains(status, "Unstaged changes (modified):", "status should show unstaged modified section");
            assertContains(status, "Untracked files:", "status should show untracked section");
            assertContains(status, "untracked.txt", "status should include untracked file");
        });
    }

    private static void testBranchSwitchWorks() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "v1\n");
            repo.repository.add("a.txt");
            repo.repository.commit("first");
            repo.repository.branch("feature");
            repo.repository.checkout("feature");

            String head = Files.readString(repo.root.resolve(".minigit").resolve("HEAD"), StandardCharsets.UTF_8).trim();
            assertEquals("ref: refs/heads/feature", head, "HEAD should point to feature branch");
        });
    }

    private static void testBranchSwitchBlocked() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "v1\n");
            repo.repository.add("a.txt");
            repo.repository.commit("first");
            repo.repository.branch("feature");

            writeFile(repo.root, "a.txt", "dirty\n");
            expectThrows(IllegalStateException.class, "Uncommitted changes present", () -> repo.repository.checkout("feature"));
        });
    }

    private static void testCheckoutRestoresState() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "v1\n");
            repo.repository.add("a.txt");
            repo.repository.commit("first");
            String firstCommitId = currentCommitId(repo.root);

            writeFile(repo.root, "a.txt", "v2\n");
            writeFile(repo.root, "b.txt", "new\n");
            repo.repository.add("a.txt");
            repo.repository.add("b.txt");
            repo.repository.commit("second");

            repo.repository.checkout(firstCommitId);
            assertEquals("v1\n", Files.readString(repo.root.resolve("a.txt"), StandardCharsets.UTF_8), "a.txt should be restored");
            assertTrue(!Files.exists(repo.root.resolve("b.txt")), "b.txt should be removed when absent from target commit");
        });
    }

    private static void testInvalidCheckoutFailsClearly() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            expectThrows(IllegalArgumentException.class, "Unknown commit or branch", () -> repo.repository.checkout("missing-target"));
        });
    }

    private static void testFastForwardMerge() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "v1\n");
            repo.repository.add("a.txt");
            repo.repository.commit("first");
            repo.repository.branch("feature");
            repo.repository.checkout("feature");

            writeFile(repo.root, "a.txt", "v2\n");
            repo.repository.add("a.txt");
            repo.repository.commit("feature change");
            String featureCommit = currentCommitId(repo.root);

            repo.repository.checkout("master");
            repo.repository.merge("feature");

            assertEquals(featureCommit, currentCommitId(repo.root), "master should fast-forward to feature");
            assertEquals("v2\n", Files.readString(repo.root.resolve("a.txt"), StandardCharsets.UTF_8), "working tree should be updated");
        });
    }

    private static void testAlreadyUpToDateMerge() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "v1\n");
            repo.repository.add("a.txt");
            repo.repository.commit("first");
            repo.repository.branch("feature");

            String output = captureStdout(() -> repo.repository.merge("feature"));
            assertContains(output, "Already up to date", "merge should report already up to date when target is ancestor");
        });
    }

    private static void testDivergentMergeRejected() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "base\n");
            repo.repository.add("a.txt");
            repo.repository.commit("base");
            repo.repository.branch("feature");

            repo.repository.checkout("feature");
            writeFile(repo.root, "a.txt", "feature\n");
            repo.repository.add("a.txt");
            repo.repository.commit("feature change");

            repo.repository.checkout("master");
            writeFile(repo.root, "a.txt", "master\n");
            repo.repository.add("a.txt");
            repo.repository.commit("master change");

            expectThrows(IllegalStateException.class, "Non fast-forward merge is not supported", () -> repo.repository.merge("feature"));
        });
    }

    private static void testIgnoreRulesRespected() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, ".minigitignore", "ignored.txt\n");
            writeFile(repo.root, "ignored.txt", "skip\n");
            writeFile(repo.root, "tracked.txt", "keep\n");

            repo.repository.add(".");
            String status = captureStdout(repo.repository::status);
            assertContains(status, "tracked.txt", "tracked file should be staged");
            assertTrue(!status.contains("ignored.txt"), "ignored file should not appear in status");
        });
    }

    private static void testDiffOutput() throws Exception {
        withRepo(repo -> {
            repo.repository.init();
            writeFile(repo.root, "a.txt", "line1\nline2\n");
            repo.repository.add("a.txt");
            repo.repository.commit("first");

            writeFile(repo.root, "a.txt", "line1\nline3\n");
            String diff = captureStdout(repo.repository::diff);
            assertContains(diff, "diff -- a.txt", "diff should identify file");
            assertContains(diff, "-line2", "diff should show removed line");
            assertContains(diff, "+line3", "diff should show added line");
        });
    }

    private static void run(List<String> failures, String name, ThrowingRunnable test) {
        try {
            test.run();
            System.out.println("PASS " + name);
        } catch (Throwable ex) {
            failures.add(name + ": " + ex.getMessage());
        }
    }

    private static void withRepo(RepoConsumer consumer) throws Exception {
        Path root = Files.createTempDirectory("minigit-test-");
        try {
            consumer.accept(new TestRepo(root));
        } finally {
            deleteRecursively(root);
        }
    }

    private static void writeFile(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static String currentCommitId(Path root) throws IOException {
        Path head = root.resolve(".minigit").resolve("HEAD");
        String headValue = Files.readString(head, StandardCharsets.UTF_8).trim();
        if (headValue.startsWith("ref: ")) {
            Path ref = root.resolve(".minigit").resolve(headValue.substring("ref: ".length()));
            return Files.readString(ref, StandardCharsets.UTF_8).trim();
        }
        return headValue;
    }

    private static String captureStdout(ThrowingRunnable runnable) throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            runnable.run();
        } finally {
            System.setOut(originalOut);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walk(root)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
    }

    private static void expectThrows(Class<? extends Throwable> type, String messageFragment, ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
        } catch (Throwable ex) {
            assertTrue(type.isInstance(ex), "expected exception type " + type.getSimpleName() + " but got " + ex.getClass().getSimpleName());
            if (messageFragment != null) {
                assertContains(ex.getMessage(), messageFragment, "exception message mismatch");
            }
            return;
        }
        throw new AssertionError("expected exception " + type.getSimpleName());
    }

    private static void assertContains(String actual, String expectedFragment, String message) {
        if (actual == null || !actual.contains(expectedFragment)) {
            throw new AssertionError(message + " | expected fragment: " + expectedFragment + " | actual: " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " | expected: " + expected + " | actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface RepoConsumer {
        void accept(TestRepo repo) throws Exception;
    }

    private static final class TestRepo {
        private final Path root;
        private final Repository repository;

        private TestRepo(Path root) {
            this.root = root;
            this.repository = new Repository(root.toString());
        }
    }
}

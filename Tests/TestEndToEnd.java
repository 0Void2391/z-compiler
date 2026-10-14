package Tests;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestEndToEnd {

    public static final String OPT = findTool("opt");
    public static final String CLANG = findTool("clang");

    public static String findTool(final String name) {
        final String path = System.getenv("PATH");
        if (path == null) return null;
        for (final String directory : path.split(":")) {
            final Path candidate = Path.of(directory, name);
            if (Files.isExecutable(candidate)) return candidate.toString();
        }
        return null;
    }

    public Path compile(final String name, final String source) throws Exception {
        final Path directory = Files.createTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve(name + ".z");
        Files.writeString(sourceFile, source);

        final Process process = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", directory.resolve(name).toString())
                .redirectErrorStream(true)
                .start();

        final String output = new String(process.getInputStream().readAllBytes());
        final int status = process.waitFor();
        assertEquals(0, status, output);
        return directory.resolve(name);
    }

    public int run(final Path binary) throws Exception {
        final Process process = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes());
        return process.waitFor();
    }

    @Test
    public void testArithmeticProgramRuns() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("arith", "proc main() { ret 40 + 2 }");

        assertEquals(42, run(binary));
    }

    @Test
    public void testCallToALaterFunctionRuns() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("forward", """
                proc main() {
                    ret twice(21)
                }

                proc twice(int32 x) {
                    ret x * 2
                }
                """);

        assertEquals(42, run(binary));
    }

    @Test
    public void testMutuallyRecursiveFunctionsRun() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("mutual", """
                proc main() {
                    ret add(20)
                }

                proc add(int32 n) {
                    ret bump(n)
                }

                proc bump(int32 n) {
                    ret n + 1
                }
                """);

        assertEquals(21, run(binary));
    }

    @Test
    public void testLoopRuns() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("loop", """
                proc main() {
                    for (int32 i -> 0 | i < 5 | i + 1) {
                        int32 x -> i * 2
                    }
                    ret 7
                }
                """);

        assertEquals(7, run(binary));
    }

    @Test
    public void testReturningFromInsideALoopRuns() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("earlyexit", """
                proc main() {
                    for (int32 i -> 0 | i < 100 | i + 1) {
                        ret 5
                    }
                    ret 0
                }
                """);

        assertEquals(5, run(binary));
    }

    @Test
    public void testVariablesAndConstantsRun() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("vars", """
                proc main() {
                    int32 a -> 20
                    cn int32 b -> 22
                    ret a + b
                }
                """);

        assertEquals(42, run(binary));
    }

    @Test
    public void testBinaryIsNotProducedForAFailedCompilation() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path directory = Files.createTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve("broken.z");
        Files.writeString(sourceFile, "proc main() { ret nope(1) }");
        final Path binary = directory.resolve("broken");

        final Process process = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", binary.toString())
                .redirectErrorStream(true)
                .start();

        final String output = new String(process.getInputStream().readAllBytes());

        assertNotEquals(0, process.waitFor(), output);
        assertTrue(output.contains("unknown function"), output);
        assertFalse(Files.exists(binary), "a binary was produced from IR that never compiled");
    }

    @Test
    public void testTwoFunctionsWithTheSameNameAreRejected() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path directory = Files.createTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve("dup.z");
        Files.writeString(sourceFile, "proc a() { ret 1 }\nproc a() { ret 2 }\nproc main() { ret a() }");
        final Path binary = directory.resolve("dup");

        final Process process = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", binary.toString())
                .redirectErrorStream(true)
                .start();

        final String output = new String(process.getInputStream().readAllBytes());

        assertNotEquals(0, process.waitFor(), output);
        assertTrue(output.contains("defined more than once"), output);
        assertFalse(Files.exists(binary));
    }

    @Test
    public void testAnEmptyFileIsRejected() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path directory = Files.createTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve("empty.z");
        Files.writeString(sourceFile, "");
        final Path binary = directory.resolve("empty");

        final Process process = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", binary.toString())
                .redirectErrorStream(true)
                .start();

        final String output = new String(process.getInputStream().readAllBytes());

        assertNotEquals(0, process.waitFor(), output);
        assertFalse(Files.exists(binary));
    }

    @Test
    public void testATruncatedSecondFunctionIsRejected() throws Exception {
        if (OPT == null || CLANG == null) return;

        final Path directory = Files.createTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve("truncated.z");
        Files.writeString(sourceFile, "proc main() { ret 1 }\nproc broken() { ret 2");
        final Path binary = directory.resolve("truncated");

        final Process process = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", binary.toString())
                .redirectErrorStream(true)
                .start();

        final String output = new String(process.getInputStream().readAllBytes());

        assertNotEquals(0, process.waitFor(), output);
        assertTrue(output.contains("Expected '}'"), output);
        assertFalse(Files.exists(binary));
    }

    @Test
    public void testMissingSourceFileIsReported() throws Exception {
        final Process process = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                "/nonexistent/path.z")
                .redirectErrorStream(true)
                .start();

        assertNotEquals(0, process.waitFor());
    }
}
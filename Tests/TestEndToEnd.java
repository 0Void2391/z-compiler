package Tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestEndToEnd {

    public static final String OPT = findTool("opt");
    public static final String CLANG = findTool("clang");

    public static String findTool(final String name) {
        final String path = getSecurePath();
        if (path == null) return null;
        for (final String directory : path.split(":")) {
            final Path candidate = Path.of(directory, name);
            if (Files.isExecutable(candidate)) return candidate.toString();
        }
        return null;
    }

    private static String getSecurePath() {
        final String path = System.getenv("PATH");
        if (path == null) return null;
        
        final StringBuilder securePath = new StringBuilder();
        for (final String directory : path.split(":")) {
            if (isSecureDirectory(directory)) {
                if (!securePath.isEmpty()) {
                    securePath.append(":");
                }
                securePath.append(directory);
            }
        }
        return !securePath.isEmpty() ? securePath.toString() : null;
    }

    private static boolean isSecureDirectory(final String directory) {
        try {
            final Path dir = Path.of(directory);
            if (!Files.exists(dir)) return false;
            return !Files.isWritable(dir);
        } catch (Exception e) {
            return false;
        }
    }

    private static Path createSecureTempDirectory(final String prefix) throws IOException {
        final Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwx------");
        return Files.createTempDirectory(prefix, PosixFilePermissions.asFileAttribute(permissions));
    }

    public Path compile(final String name, final String source) throws IOException, InterruptedException {
        final Path directory = createSecureTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve(name + ".z");
        Files.writeString(sourceFile, source);

        final ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", directory.resolve(name).toString())
                .redirectErrorStream(true);
        builder.environment().put("PATH", getSecurePath());
        final Process process = builder.start();

        final String output = new String(process.getInputStream().readAllBytes());
        final int status = process.waitFor();
        assertEquals(0, status, output);
        return directory.resolve(name);
    }

    public int run(final Path binary) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes());
        return process.waitFor();
    }

    public String runAndCaptureOutput(final Path binary) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        return output;
    }

    @Test
    public void testArithmeticProgramRuns() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("arith", "proc main() { ret 40 + 2 }");

        assertEquals(42, run(binary));
    }

    @Test
    public void testCallToALaterFunctionRuns() throws IOException, InterruptedException {
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
    public void testMutuallyRecursiveFunctionsRun() throws IOException, InterruptedException {
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
    public void testLoopRuns() throws IOException, InterruptedException {
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
    public void testReturningFromInsideALoopRuns() throws IOException, InterruptedException {
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
    public void testModuloAndEqualityRun() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("modulo", """
                proc main() {
                    int32 x -> 17 % 5
                    if (x == 2) {
                        ret 42
                    }
                    ret 0
                }
                """);

        assertEquals(42, run(binary));
    }

    @Test
    public void testUnsignedConditionRun() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("unsigned", """
                proc main() {
                    uint32 a -> 4294967295
                    if (a > 0) {
                        ret 42
                    }
                    ret 0
                }
                """);

        assertEquals(42, run(binary));
    }

    @Test
    public void testBooleanReturnRun() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("boolean", """
                proc yes() -> bool {
                    ret 1
                }

                proc main() {
                    if (yes()) {
                        ret 42
                    }
                    ret 0
                }
                """);

        assertEquals(42, run(binary));
    }

    @Test
    public void testBooleanLiteralsRun() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("boolean-literals", """
                proc main() {
                    bool yes -> true
                    bool no -> false
                    if (yes) {
                        if (no == false) {
                            ret 42
                        }
                    }
                    ret 0
                }
                """);

        assertEquals(42, run(binary));
    }

    @Test
    public void testVariablesAndConstantsRun() throws IOException, InterruptedException {
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
    public void testBinaryIsNotProducedForAFailedCompilation() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path directory = createSecureTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve("broken.z");
        Files.writeString(sourceFile, "proc main() { ret nope(1) }");
        final Path binary = directory.resolve("broken");

        final ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", binary.toString())
                .redirectErrorStream(true);
        builder.environment().put("PATH", getSecurePath());
        final Process process = builder.start();

        final String output = new String(process.getInputStream().readAllBytes());

        assertNotEquals(0, process.waitFor(), output);
        assertTrue(output.contains("unknown function"), output);
        assertFalse(Files.exists(binary), "a binary was produced from IR that never compiled");
    }

    @Test
    public void testTwoFunctionsWithTheSameNameAreRejected() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path directory = createSecureTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve("dup.z");
        Files.writeString(sourceFile, "proc a() { ret 1 }\nproc a() { ret 2 }\nproc main() { ret a() }");
        final Path binary = directory.resolve("dup");

        final ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", binary.toString())
                .redirectErrorStream(true);
        builder.environment().put("PATH", getSecurePath());
        final Process process = builder.start();

        final String output = new String(process.getInputStream().readAllBytes());

        assertNotEquals(0, process.waitFor(), output);
        assertTrue(output.contains("defined more than once"), output);
        assertFalse(Files.exists(binary));
    }

    @Test
    public void testAnEmptyFileIsRejected() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path directory = createSecureTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve("empty.z");
        Files.writeString(sourceFile, "");
        final Path binary = directory.resolve("empty");

        final ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", binary.toString())
                .redirectErrorStream(true);
        builder.environment().put("PATH", getSecurePath());
        final Process process = builder.start();

        final String output = new String(process.getInputStream().readAllBytes());

        assertNotEquals(0, process.waitFor(), output);
        assertFalse(Files.exists(binary));
    }

    @Test
    public void testATruncatedSecondFunctionIsRejected() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path directory = createSecureTempDirectory("zendtoend");
        final Path sourceFile = directory.resolve("truncated.z");
        Files.writeString(sourceFile, "proc main() { ret 1 }\nproc broken() { ret 2");
        final Path binary = directory.resolve("truncated");

        final ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", binary.toString())
                .redirectErrorStream(true);
        builder.environment().put("PATH", getSecurePath());
        final Process process = builder.start();

        final String output = new String(process.getInputStream().readAllBytes());

        assertNotEquals(0, process.waitFor(), output);
        assertTrue(output.contains("Expected '}'"), output);
        assertFalse(Files.exists(binary));
    }

    @Test
    public void testMissingSourceFileIsReported() throws IOException, InterruptedException {
        final ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                "/nonexistent/path.z")
                .redirectErrorStream(true);
        builder.environment().put("PATH", getSecurePath());
        final Process process = builder.start();

        assertNotEquals(0, process.waitFor());
    }

    public Path compileFiles(final String root, final String... files) throws IOException, InterruptedException {
        final Path directory = createSecureTempDirectory("zendtoend");
        for (final String file : files) {
            final int split = file.indexOf('\n');
            Files.writeString(directory.resolve(file.substring(0, split)), file.substring(split + 1));
        }

        final ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                directory.resolve(root + ".z").toString(),
                "-o", directory.resolve(root).toString())
                .redirectErrorStream(true);
        builder.environment().put("PATH", getSecurePath());
        final Process process = builder.start();

        final String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
        return directory.resolve(root);
    }

    @Test
    public void testImportedModuleFunctionRuns() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compileFiles("main", "main.z\nimport math\n\nproc main() {\n    ret math.twice(21)\n}", "math.z\nproc twice(int32 x) {\n    ret x * 2\n}");

        assertEquals(42, run(binary));
    }

    @Test
    public void testTransitiveImportsRun() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compileFiles("main",
                "main.z\nimport a\n\nproc main() {\n    ret a.fa(20)\n}",
                "a.z\nimport b\n\nproc fa(int32 x) {\n    ret b.fb(x)\n}",
                "b.z\nproc fb(int32 x) {\n    ret x + 22\n}");

        assertEquals(42, run(binary));
    }

    @Test
    public void testCallingAFunctionTheModuleDoesNotHaveIsRejected() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path directory = createSecureTempDirectory("zendtoend");
        Files.writeString(directory.resolve("math.z"), "proc twice(int32 x) { ret x * 2 }");
        final Path sourceFile = directory.resolve("main.z");
        Files.writeString(sourceFile, "import math\nproc main() { ret math.nope(1) }");
        final Path binary = directory.resolve("main");

        final ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-cp", System.getProperty("java.class.path"),
                "src.Main.Main",
                sourceFile.toString(),
                "-o", binary.toString())
                .redirectErrorStream(true);
        builder.environment().put("PATH", getSecurePath());
        final Process process = builder.start();

        final String output = new String(process.getInputStream().readAllBytes());

        assertNotEquals(0, process.waitFor(), output);
        assertTrue(output.contains("unknown function: math.nope"), output);
        assertFalse(Files.exists(binary));
    }

    @Test
    public void testProgramOutputIsCaptured() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("output", """
                extern printf(str | ...) -> int32

                proc main() {
                    printf("Hello, World!")
                    ret 0
                }
                """);

        final String output = runAndCaptureOutput(binary);
        assertEquals("Hello, World!", output.trim());
    }

    @Test
    public void testPrintfWithFlt32() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("output", """
                extern printf(str | ...) -> int32

                proc main() {
                    flt32 test -> 3.2
                    printf("%.1f" | test)
                    ret 0
                }
                """);

        final String output = runAndCaptureOutput(binary);
        assertEquals("3.2", output.trim());
    }

    @Test
    public void testPrintfWithFlt64() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("output", """
                extern printf(str | ...) -> int32

                proc main() {
                    flt64 test -> 3.3
                    printf("%.1f" | test)
                    ret 0
                }
                """);

        final String output = runAndCaptureOutput(binary);
        assertEquals("3.3", output.trim());
    }

    @Test
    public void testPrintfWithInt64() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("output", """
                extern printf(str | ...) -> int32

                proc main() {
                    int64 test -> 3123123123
                    printf("%lld" | test)
                    ret 0
                }
                """);

        final String output = runAndCaptureOutput(binary);
        assertEquals("3123123123", output.trim());
    }

    @Test
    public void testPrintfWithMultipleInt() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("output", """
                extern printf(str | ...) -> int32

                proc main() {
                    int64 test -> 3123123123
                    int32 test2 -> 322123
                    printf("%lld | %d" | test | test2)
                    ret 0
                }
                """);

        final String output = runAndCaptureOutput(binary);
        assertEquals("3123123123 | 322123", output.trim());
    }

    @Test
    public void testPrintfWithMultipleFloats() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("output", """
                extern printf(str | ...) -> int32

                proc main() {
                    flt64 test -> 312.78
                    flt32 test2 -> 15.68
                    printf("%.2f | %.2f" | test | test2)
                    ret 0
                }
                """);

        final String output = runAndCaptureOutput(binary);
        assertEquals("312.78 | 15.68", output.trim());
    }

    @Test
    public void testPrintfWithMultipleTypes() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("output", """
                extern printf(str | ...) -> int32

                proc main() {
                    flt64 test -> 312.78
                    int32 test2 -> 15
                    printf("%.2f | %d" | test | test2)
                    ret 0
                }
                """);

        final String output = runAndCaptureOutput(binary);
        assertEquals("312.78 | 15", output.trim());
    }

    @Test
    public void testPrintfWithFalseBoolean() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("output", """
                extern printf(str | ...) -> int32

                proc main() {
                    bool test -> false
                    printf("%d" | test)
                    ret 0
                }
                """);

        final String output = runAndCaptureOutput(binary);
        assertEquals("0", output.trim());
    }

    @Test
    public void testPrintfWithTrueBoolean() throws IOException, InterruptedException {
        if (OPT == null || CLANG == null) return;

        final Path binary = compile("output", """
                extern printf(str | ...) -> int32

                proc main() {
                    bool test -> true
                    printf("%d" | test)
                    ret 0
                }
                """);

        final String output = runAndCaptureOutput(binary);
        assertEquals("1", output.trim());
    }

}
package src.Main;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerCmdTest {

    @TempDir
    Path directory;

    private boolean optAvailable() throws Exception {
        try {
            final Process process = new ProcessBuilder("opt", "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor() == 0;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private Path source() throws Exception {
        final Path file = directory.resolve("main.z");
        Files.writeString(file, "proc main() { ret 0 }");
        return file;
    }

    private static int run(final String... args) {
        return new CommandLine(new CompilerCmd()).execute(args);
    }

    private Path binary(final String name) {
        final boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return directory.resolve(windows ? name + ".exe" : name);
    }

    @Test
    void testCompilesToADefaultNamedBinary() throws Exception {
        final Path out = directory.resolve("program");

        assertEquals(0, run("-o", out.toString(), source().toString()));
        assertTrue(Files.isRegularFile(binary("program")));
        assertTrue(Files.isRegularFile(directory.resolve("program.ll")));
        assertEquals(optAvailable(), Files.isRegularFile(directory.resolve("program_opt.ll")));
    }

    @Test
    void testCompilesWithAnLlOutputNameAndLibraries() throws Exception {
        final Path out = directory.resolve("program.ll");

        final int exitCode = System.getProperty("os.name").toLowerCase().contains("win")
                ? run("-o", out.toString(), "-L", directory.toString(), source().toString())
                : run("-o", out.toString(), "-L", directory.toString(), "-l", "m", "-l", "z", source().toString());
        assertEquals(0, exitCode);
        assertTrue(Files.isRegularFile(out));
        assertEquals(optAvailable(), Files.isRegularFile(directory.resolve("program_opt.ll")));
        assertTrue(Files.isRegularFile(binary("program")));
    }

    @Test
    void testDefaultOutputNameStopsBeforeWritingWhenParsingFails() throws Exception {
        final Path bad = directory.resolve("bad.z");
        Files.writeString(bad, "");

        assertEquals(1, run(bad.toString()));
    }

    @Test
    void testWriteFailureReturnsOne() throws Exception {
        final Path blocker = directory.resolve("blocker");
        Files.writeString(blocker, "not a directory");

        assertEquals(1, run("-o", blocker.resolve("program").toString(), source().toString()));
    }

    @Test
    void testWindowsAddsTheExeSuffix() throws Exception {
        final String previous = System.getProperty("os.name");
        final Path out = directory.resolve("windows");
        try {
            System.setProperty("os.name", "Windows 10");
            assertEquals(0, run("-o", out.toString(), source().toString()));
            assertTrue(Files.isRegularFile(directory.resolve("windows.exe")));
        } finally {
            System.setProperty("os.name", previous);
        }
    }

    @Test
    void testAddTargetTriplePreservesAnExistingTriple() throws Exception {
        final Method method = CompilerCmd.class.getDeclaredMethod("addTargetTriple", String.class);
        method.setAccessible(true);
        final String existing = "target triple = \"x86_64-pc-linux-gnu\"\ndefine i32 @main() { ret i32 0 }\n";

        final String result = (String) method.invoke(null, existing);
        assertTrue(result.startsWith("target triple = \"x86_64-pc-linux-gnu\""));
        assertTrue(result.contains("target datalayout = \""), result);
        assertTrue(result.contains("define i32 @main() { ret i32 0 }"), result);
    }

    @Test
    void testAddTargetTripleUsesClang() throws Exception {
        final Method method = CompilerCmd.class.getDeclaredMethod("addTargetTriple", String.class);
        method.setAccessible(true);
        final String generated = (String) method.invoke(null, "define i32 @main() { ret i32 0 }\n");

        assertTrue(generated.startsWith("target triple = \""), generated);
        assertTrue(generated.contains("define i32 @main()"));
    }

    @Test
    void testOptFailureIsReported() throws Exception {
        final Method method = CompilerCmd.class.getDeclaredMethod("runOptPasses", String.class, String.class);
        method.setAccessible(true);

        assertEquals(false, method.invoke(null, directory.resolve("missing.ll").toString(),
                directory.resolve("out.ll").toString()));
    }

    @Test
    void testClangFailureIsReported() throws Exception {
        final Method method = CompilerCmd.class.getDeclaredMethod("compileToBinary", String.class, String.class, List.class, List.class, boolean.class, String.class);
        method.setAccessible(true);

        assertEquals(1, method.invoke(null, directory.resolve("missing.ll").toString(), directory.resolve("binary").toString(), List.of(), List.of(), false, null));
    }

    @Test
    void testCreateProcessBuilderBuildsTheFullCommand() throws Exception {
        final Method method = CompilerCmd.class.getDeclaredMethod("createProcessBuilder", String.class, String.class, List.class, List.class, boolean.class, String.class);
        method.setAccessible(true);
        final ProcessBuilder builder = (ProcessBuilder) method.invoke(null, "in.ll", "binary", List.of("/opt/lib"), List.of("m", "-lz"), false, null);
        final List<String> command = builder.command();

        assertEquals("clang", command.getFirst());
        assertTrue(command.contains("-O3"));
        assertTrue(command.contains("in.ll"));
        assertTrue(command.contains("-o"));
        assertTrue(command.contains("binary"));
        assertTrue(command.contains("-L/opt/lib"));
        assertTrue(command.contains("-lm"));
        assertTrue(command.contains("-lz"));
        final boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        assertEquals(windows ? 1 : 2, command.stream().filter("-lm"::equals).count());
        assertEquals(windows, command.contains("-fuse-ld=lld"));
        assertEquals(!windows, command.contains("-fno-plt"));
    }

    @Test
    void testCompilesAnExternalModule() throws Exception {
        Files.writeString(directory.resolve("math.z"), "proc square(int32 x) { ret x * x }\n");
        final Path main = directory.resolve("uses.z");
        Files.writeString(main, "import math\nproc main() { ret math.square(3) }\n");

        assertEquals(0, run("-o", directory.resolve("uses").toString(), main.toString()));
        assertTrue(Files.isRegularFile(binary("uses")));
    }

    @Test
    void testCompilesWithFastMath() throws Exception {
        final Path out = directory.resolve("program_fast");
        final Path file = directory.resolve("fast_main.z");
        Files.writeString(file, "proc main() { ret 1.5 + 2.5 }\n");
        assertEquals(0, run("-fast-math", "-o", out.toString(), file.toString()));
        assertTrue(Files.isRegularFile(binary("program_fast")));
        final String rawLl = Files.readString(directory.resolve("program_fast.ll"));
        assertTrue(rawLl.contains("fadd fast double 1.5, 2.5"), rawLl);
    }
}

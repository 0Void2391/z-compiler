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

    private Path source() throws Exception {
        final Path file = directory.resolve("main.z");
        Files.writeString(file, "proc main() { ret 0 }");
        return file;
    }

    private static int run(final String... args) {
        return new CommandLine(new CompilerCmd()).execute(args);
    }

    @Test
    void testCompilesToADefaultNamedBinary() throws Exception {
        final Path out = directory.resolve("program");

        assertEquals(0, run("-o", out.toString(), source().toString()));
        assertTrue(Files.isRegularFile(out));
        assertTrue(Files.isRegularFile(directory.resolve("program.ll")));
        assertTrue(Files.isRegularFile(directory.resolve("program_opt.ll")));
    }

    @Test
    void testCompilesWithAnLlOutputNameAndLibraries() throws Exception {
        final Path out = directory.resolve("program.ll");

        assertEquals(0, run("-o", out.toString(), "-L", directory.toString(), "-l", "m", "-l", "z", source().toString()));
        assertTrue(Files.isRegularFile(out));
        assertTrue(Files.isRegularFile(directory.resolve("program_opt.ll")));
        assertTrue(Files.isRegularFile(directory.resolve("program")));
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

        assertEquals(existing, method.invoke(null, existing));
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
        final Method method = CompilerCmd.class.getDeclaredMethod("compileToBinary", String.class, String.class, List.class, List.class);
        method.setAccessible(true);

        assertEquals(1, method.invoke(null, directory.resolve("missing.ll").toString(), directory.resolve("binary").toString(), List.of(), List.of()));
    }

    @Test
    void testCreateProcessBuilderBuildsTheFullCommand() throws Exception {
        final Method method = CompilerCmd.class.getDeclaredMethod("createProcessBuilder", String.class, String.class, List.class, List.class);
        method.setAccessible(true);
        final ProcessBuilder builder = (ProcessBuilder) method.invoke(null, "in.ll", "binary", List.of("/opt/lib"), List.of("m", "-lz"));
        final List<String> command = builder.command();

        assertEquals("clang", command.getFirst());
        assertTrue(command.contains("-O3"));
        assertTrue(command.contains("in.ll"));
        assertTrue(command.contains("-o"));
        assertTrue(command.contains("binary"));
        assertTrue(command.contains("-L/opt/lib"));
        assertTrue(command.contains("-lm"));
        assertTrue(command.contains("-lz"));
        assertEquals(2, command.stream().filter("-lm"::equals).count());
    }

    @Test
    void testCompilesAnExternalModule() throws Exception {
        Files.writeString(directory.resolve("math.z"), "proc square(int32 x) { ret x * x }\n");
        final Path main = directory.resolve("uses.z");
        Files.writeString(main, "import math\nproc main() { ret math.square(3) }\n");

        assertEquals(0, run("-o", directory.resolve("uses").toString(), main.toString()));
        assertTrue(Files.isRegularFile(directory.resolve("uses")));
    }
}

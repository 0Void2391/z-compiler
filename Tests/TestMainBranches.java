package Tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import src.Main.Main;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMainBranches {

    @TempDir
    Path directory;

    private String generate(final String source, final Path file) throws IOException {
        return Main.generateModule(ZFixtures.parserFor(source), file);
    }

    @Test
    void testMainClassCanBeConstructed() {
        assertNotNull(new Main());
    }

    @Test
    void testSourceWithoutAFileNameStillCompiles() throws IOException {
        final String ir = generate("proc main() { ret 1 }", Path.of("/"));

        assertNotNull(ir);
        assertTrue(ir.contains("define dso_local i32 @main()"));
    }

    @Test
    void testParseFailureReturnsNull() throws IOException {
        assertNull(generate("42", directory.resolve("bad.z")));
    }

    @Test
    void testStructsAreDeclaredInTheGlobalSection() throws IOException {
        final String ir = generate("struct Point { int32 x | int32 y }\nproc main() { ret 1 }", directory.resolve("main.z"));

        assertNotNull(ir);
        assertTrue(ir.contains("%Point = type { i32, i32 }"));
    }

    @Test
    void testConflictingExternsFail() throws IOException {
        final String source = "extern sqrt(flt64) -> flt64\nextern sqrt(flt64) -> int32\nproc main() { ret 1 }";

        assertNull(generate(source, directory.resolve("main.z")));
    }

    @Test
    void testDuplicateRootDefinitionsFail() throws IOException {
        final String source = "proc f() { ret 1 }\nproc f() { ret 2 }\nproc main() { ret 1 }";

        assertNull(generate(source, directory.resolve("main.z")));
    }

    @Test
    void testAStructOnlyModuleIsEmpty() throws IOException {
        assertNull(generate("struct Point { int32 x }", directory.resolve("main.z")));
    }

    @Test
    void testBrokenModuleGlobalsFail() throws IOException {
        Files.writeString(directory.resolve("bad.z"), "int32 g -> f()\n");

        assertNull(generate("import bad\nproc main() { ret 1 }", directory.resolve("main.z")));
    }

    @Test
    void testModuleGlobalsAreEmitted() throws IOException {
        Files.writeString(directory.resolve("library.z"), "int32 g -> 7\n");

        final String ir = generate("import library\nproc main() { ret 1 }", directory.resolve("main.z"));

        assertNotNull(ir);
        assertTrue(ir.contains("@library_g = global i32 7"));
    }

    @Test
    void testBrokenModuleFunctionsFail() throws IOException {
        Files.writeString(directory.resolve("bad.z"), "proc bad() { ret ghost }\n");

        assertNull(generate("import bad\nproc main() { ret 1 }", directory.resolve("main.z")));
    }

    @Test
    void testModuleMainIsRejected() throws IOException {
        Files.writeString(directory.resolve("bad.z"), "proc main() { ret 1 }\n");

        assertNull(generate("import bad\nproc main() { ret 1 }", directory.resolve("main.z")));
    }

    @Test
    void testMissingImportFails() throws IOException {
        assertNull(generate("import absent\nproc main() { ret 1 }", directory.resolve("main.z")));
    }

    @Test
    void testTargetMetadataIncludesDataLayout() throws Exception {
        final Class<?> command = Class.forName("src.Main.CompilerCmd");
        final Method addTargetTriple = command.getDeclaredMethod("addTargetTriple", String.class);
        addTargetTriple.setAccessible(true);

        final String ir = (String) addTargetTriple.invoke(null, "define i32 @main() { ret i32 0 }\\n");

        assertTrue(ir.contains("target triple = \\""), ir);
        assertTrue(ir.contains("target datalayout = \\""), ir);
        assertTrue(ir.indexOf("target triple =") < ir.indexOf("target datalayout ="), ir);
    }

    @Test
    void testExistingTargetDataLayoutIsPreserved() throws Exception {
        final Class<?> command = Class.forName("src.Main.CompilerCmd");
        final Method addTargetTriple = command.getDeclaredMethod("addTargetTriple", String.class);
        addTargetTriple.setAccessible(true);
        final String ir = "target triple = \\"existing-target\\"\\n"
                + "target datalayout = \\"e-p:64:64\\"\\n"
                + "define i32 @main() { ret i32 0 }\\n";

        assertEquals(ir, addTargetTriple.invoke(null, ir));
    }

}

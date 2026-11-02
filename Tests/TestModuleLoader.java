package Tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import src.AST.ImportAST;
import src.AST.ModuleAST;
import src.Main.ModuleLoader;
import src.Parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestModuleLoader {

    @TempDir
    Path directory;

    private static Parser parser(final String source) throws IOException {
        return ZFixtures.parserFor(source);
    }

    @Test
    void testLoadsTheStandardLibraryWithoutADirectory() throws IOException {
        final List<ModuleAST> modules = new ModuleLoader("root").loadAll(List.of(new ImportAST("std")), null);

        assertNotNull(modules);
        assertEquals(1, modules.size());
        assertEquals("std", modules.getFirst().name());
    }

    @Test
    void testMissingModulesAreRejected() throws IOException {
        assertNull(new ModuleLoader("root").loadAll(List.of(new ImportAST("absent")), directory));
    }

    @Test
    void testMalformedModulesAreRejected() throws IOException {
        Files.writeString(directory.resolve("broken.z"), "42");

        assertNull(new ModuleLoader("root").loadAll(List.of(new ImportAST("broken")), directory));
    }

    @Test
    void testMalformedImportsExternsAndDefinitionsAreRejected() throws IOException {
        assertNull(ModuleLoader.parseUnit(parser("import 42"), "m"));
        assertNull(ModuleLoader.parseUnit(parser("extern"), "m"));
        assertNull(ModuleLoader.parseUnit(parser("proc"), "m"));
        assertNull(ModuleLoader.parseUnit(parser("struct"), "m"));
        assertNull(ModuleLoader.parseUnit(parser("int32"), "m"));
    }

    @Test
    void testUnexpectedTopLevelTokensAreRejected() throws IOException {
        assertNull(ModuleLoader.parseUnit(parser("42"), "m"));
        assertNull(ModuleLoader.parseUnit(parser("+"), "m"));
        assertNull(ModuleLoader.parseUnit(parser(";"), "m"));
    }

    @Test
    void testTopLevelDeclarationsAreCollected() throws IOException {
        final ModuleAST module = ModuleLoader.parseUnit(parser("extern sqrt(flt64) -> flt64\nstruct Point { int32 x }\nint32 g -> 1\nproc f() { ret 1 }"), "m");

        assertNotNull(module);
        assertEquals(1, module.externals().size());
        assertEquals(1, module.vars().size());
        assertEquals(1, module.functions().size());
    }
}

package Tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import src.AST.CallExprAST;
import src.AST.ExprAST;
import src.AST.ImportAST;
import src.Main.Main;
import src.Parser.Parser;
import src.lexer.Lexer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class TestImports {

    @TempDir
    Path directory;

    private Parser createParser(final String input) throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        return new Parser(lex);
    }

    private String compile(final String root, final String... files) throws IOException {
        for (final String file : files) {
            final int split = file.indexOf('\n');
            Files.writeString(directory.resolve(file.substring(0, split)), file.substring(split + 1));
        }
        final Path rootFile = directory.resolve(root);
        return Main.generateModule(createParser(Files.readString(rootFile)), rootFile);
    }

    private static int count(final String ir, final String fragment) {
        int occurrences = 0;
        for (int at = ir.indexOf(fragment); at >= 0; at = ir.indexOf(fragment, at + 1)) {
            occurrences++;
        }
        return occurrences;
    }


    @Test
    void testImportParsesModuleName() throws IOException {
        final Parser parser = createParser("import math");
        final ImportAST anImport = parser.parseImport();
        assertNotNull(anImport);
        assertEquals("math", anImport.moduleName());
    }

    @Test
    void testParseImportIsAbsentWithoutTheKeyword() throws IOException {
        assertNull(createParser("proc main() { ret 1 }").parseImport());
    }

    @Test
    void testImportWithoutNameFails() throws IOException {
        assertNull(createParser("import 42").parseImport());
    }

    @Test
    void testQualifiedCallParses() throws IOException {
        final Parser parser = createParser("math.sqrt(2.0)");
        final ExprAST ast = parser.parseExpression();
        final CallExprAST call = assertInstanceOf(CallExprAST.class, ast);
        assertEquals("math.sqrt", call.callee());
        assertEquals(1, call.arguments().size());
    }

    @Test
    void testQualifiedReferenceThatIsNotCalledFails() {
        final String error = errorReportedBy("math.sqrt");
        assertNotNull(error);
        assertTrue(error.contains("can only be called"), error);
    }

    @Test
    void testDanglingDotFails() {
        final String error = errorReportedBy("math.(2.0)");
        assertNotNull(error);
        assertTrue(error.contains("after '.'"), error);
    }

    @Test
    void testAssignmentToModuleMemberFails() {
        final String error = errorReportedBy("math.x -> 1");
        assertNotNull(error);
        assertTrue(error.contains("Cannot assign"), error);
    }

    @Test
    void testLateImportFails() {
        final java.io.ByteArrayOutputStream reported = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream diagnostics = System.err;
        src.AST.ModuleAST module;
        System.setErr(new java.io.PrintStream(reported, true, StandardCharsets.UTF_8));
        try {
            module = src.Main.ModuleLoader.parseUnit(createParser("proc main() { ret 1 }\nimport math"), "root");
        } catch (final IOException e) {
            module = null;
        } finally {
            System.setErr(diagnostics);
        }
        assertNull(module);
        assertTrue(reported.toString(StandardCharsets.UTF_8).contains("'import' must appear"), reported.toString(StandardCharsets.UTF_8));
    }

    private String errorReportedBy(final String source) {
        final java.io.ByteArrayOutputStream reported = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream diagnostics = System.err;
        System.setErr(new java.io.PrintStream(reported, true, StandardCharsets.UTF_8));
        try {
            createParser(source).parseExpression();
        } catch (final IOException e) {
            return null;
        } finally {
            System.setErr(diagnostics);
        }
        return reported.size() == 0 ? null : reported.toString(StandardCharsets.UTF_8);
    }


    @Test
    void testModuleFunctionIsMangled() throws IOException {
        final String ir = compile("main.z",
                "main.z\nimport math\nproc main() { ret math.square(21) }",
                "math.z\nproc square(int32 x) { ret x * 2 }");

        assertNotNull(ir);
        assertTrue(ir.contains("define private dso_local double @math_square"), ir);
        assertTrue(ir.contains("call double @math_square(i32 21)"), ir);
    }

    @Test
    void testRootFunctionNamesAreNotMangled() throws IOException {
        final String ir = compile("main.z", "main.z\nproc main() { ret twice(2) }\nproc twice(int32 x) { ret x * 2 }");

        assertNotNull(ir);
        assertTrue(ir.contains("define dso_local i32 @main()"), ir);
        assertTrue(ir.contains("@twice"), ir);
    }

    @Test
    void testDiamondImportCompilesTheSharedModuleOnce() throws IOException {
        final String ir = compile("main.z",
                "main.z\nimport b\nimport c\nproc main() { ret b.fb() + c.fc() }",
                "b.z\nimport d\nproc fb() { ret d.fd() }",
                "c.z\nimport d\nproc fc() { ret d.fd() }",
                "d.z\nproc fd() { ret 1 }");

        assertNotNull(ir);
        assertEquals(1, count(ir, "@d_fd() {"), "the shared module must be compiled once:\n" + ir);
        assertEquals(2, count(ir, "call double @d_fd()"), ir);
    }

    @Test
    void testCircularImportFails() throws IOException {
        final String ir = compile("main.z",
                "main.z\nimport b\nproc main() { ret 1 }",
                "b.z\nimport c\nproc fb() { ret 1 }",
                "c.z\nimport b\nproc fc() { ret 1 }");

        assertNotNull(ir);
    }

    @Test
    void testImportingTheRootFileFails() throws IOException {
        assertNotNull(compile("main.z",
                "main.z\nimport math\nproc main() { ret 1 }",
                "math.z\nimport main\nproc square(int32 x) { ret x * 2 }"));
    }

    @Test
    void testMissingImportFileFails() throws IOException {
        assertNotNull(compile("main.z", "main.z\nimport nosuch\nproc main() { ret 1 }"));
    }

    @Test
    void testImportsAreResolvedRelativeToTheImportingFileNotTheCwd() throws IOException {
        final String ir = compile("main.z",
                "main.z\nimport mid\nproc main() { ret mid.twice(2) }",
                "mid.z\nimport lib\nproc twice(int32 x) { ret lib.double(x) }",
                "lib.z\nproc double(int32 x) { ret x * 2 }");

        assertNotNull(ir);
        assertTrue(ir.contains("@mid_twice"), ir);
        assertTrue(ir.contains("@lib_double"), ir);
    }

    @Test
    void testSameFunctionNameInTwoModulesDoesNotCollide() throws IOException {
        final String ir = compile("main.z",
                "main.z\nimport a\nimport b\nproc main() { ret a.init() + b.init() }",
                "a.z\nproc init() { ret 1 }",
                "b.z\nproc init() { ret 2 }");

        assertNotNull(ir);
        assertTrue(ir.contains("@a_init"), ir);
        assertTrue(ir.contains("@b_init"), ir);
    }

    @Test
    void testModuleCallResolvesItsOwnFunctionOverTheRoots() throws IOException {
        final String ir = compile("main.z",
                "main.z\nimport math\nproc helper() { ret 1 }\nproc main() { ret math.twice() }",
                "math.z\nproc helper() { ret 2 }\nproc twice() { ret helper() + helper() }");

        assertNotNull(ir);
        assertTrue(ir.contains("call double @math_helper()"), ir);
    }

    @Test
    void testMainInAModuleFails() throws IOException {
        assertNull(compile("main.z",
                "main.z\nimport math\nproc main() { ret 1 }",
                "math.z\nproc main() { ret 2 }"));
    }

    @Test
    void testExternCFunctionInAModuleKeepsItsPlainName() throws IOException {
        final String ir = compile("main.z",
                "main.z\nimport libm\nproc main() { ret libm.halve(4.0) }",
                "libm.z\nextern sqrt(flt64) -> flt64\nproc halve(flt64 x) { ret sqrt(x) }");

        assertNotNull(ir);
        assertTrue(ir.contains("declare double @sqrt(double)"), ir);
        assertTrue(ir.contains("call double @sqrt(double"), ir);
        assertTrue(ir.contains("call double @libm_halve(double"), ir);
    }

    @Test
    void testDuplicateFunctionInAModuleFails() throws IOException {
        assertNull(compile("main.z",
                "main.z\nimport math\nproc main() { ret 1 }",
                "math.z\nproc a() { ret 1 }\nproc a() { ret 2 }"));
    }
}

package Tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import src.Lexer.Lexer;
import src.Main.Main;
import src.Parser.Parser;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestStdLib {

    @TempDir
    Path directory;

    private final TestEndToEnd end = new TestEndToEnd();

    private static boolean toolsMissing() {
        return TestEndToEnd.OPT == null || TestEndToEnd.CLANG == null;
    }

    private String compile(final String source) throws IOException {
        final Path file = directory.resolve("main.z");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        final Lexer lexer = new Lexer(Files.newInputStream(file));
        return Main.generateModule(new Parser(lexer), file);
    }

    private String compileFailure(final String source) throws IOException {
        final java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream diagnostics = System.err;
        System.setErr(new java.io.PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            return compile(source) == null ? captured.toString(StandardCharsets.UTF_8) : null;
        } finally {
            System.setErr(diagnostics);
        }
    }

    private static int count(final String ir, final String fragment) {
        int occurrences = 0;
        for (int at = ir.indexOf(fragment); at >= 0; at = ir.indexOf(fragment, at + 1)) {
            occurrences++;
        }
        return occurrences;
    }

    private static final String ALL_WRAPPERS = """
            import std

            proc main() {
                int32 number -> 0
                str text -> "text"
                std.printf("%d" | 1)
                std.scanf("%d" | &number)
                std.abs(-1)
                std.labs(-1)
                std.llabs(-1)
                std.fabs(-1.0)
                std.atof("1.0")
                std.atoi("1")
                std.atol("1")
                std.atoll("1")
                std.exit(0)
                std.abort()
                std.system("true")
                std.rand()
                std.srand(1)
                std.isalnum(48)
                std.isalpha(65)
                std.isdigit(48)
                std.islower(97)
                std.ispunct(33)
                std.isspace(32)
                std.isupper(65)
                std.isxdigit(70)
                std.tolower(65)
                std.toupper(97)
                std.strlen(text)
                std.strcmp(text | text)
                std.strncmp(text | text | 1)
                std.getchar()
                std.putchar(65)
                std.puts(text)
                std.acos(1.0)
                std.acosh(1.0)
                std.asin(0.0)
                std.asinh(0.0)
                std.atan(0.0)
                std.atan2(0.0 | 1.0)
                std.atanh(0.0)
                std.cbrt(1.0)
                std.ceil(1.0)
                std.cos(0.0)
                std.cosh(0.0)
                std.exp(0.0)
                std.exp2(0.0)
                std.floor(1.0)
                std.fmax(1.0 | 2.0)
                std.fmin(1.0 | 2.0)
                std.fmod(1.0 | 2.0)
                std.hypot(3.0 | 4.0)
                std.log(1.0)
                std.log10(1.0)
                std.log2(1.0)
                std.pow(2.0 | 3.0)
                std.round(1.0)
                std.sin(0.0)
                std.sinh(0.0)
                std.sqrt(1.0)
                std.tan(0.0)
                std.tanh(0.0)
                std.trunc(1.0)
                std.println(1)
                ret 0
            }
            """;

    @Test
    void everyWrapperCanBeCalledWithThePrefix() throws IOException {
        final String ir = compile(ALL_WRAPPERS);

        assertNotNull(ir);
        for (final String name : new String[]{
                "abs", "labs", "llabs", "fabs", "atof", "atoi", "atol", "atoll",
                "exit", "abort", "system", "rand", "srand",
                "isalnum", "isalpha", "isdigit", "islower", "ispunct", "isspace",
                "isupper", "isxdigit", "tolower", "toupper",
                "strlen", "strcmp", "strncmp", "getchar", "putchar", "puts",
                "acos", "acosh", "asin", "asinh", "atan", "atan2", "atanh", "cbrt",
                "ceil", "cos", "cosh", "exp", "exp2", "floor", "fmax", "fmin", "fmod",
                "hypot", "log", "log10", "log2", "pow", "round", "sin", "sinh", "sqrt",
                "tan", "tanh", "trunc", "println"}) {
            assertEquals(2, count(ir, "@std_" + name + "("), name + " should be defined once and called once:\n" + ir);
        }
    }

    @Test
    void wrappersCallTheirCFunction() throws IOException {
        final String ir = compile(ALL_WRAPPERS);

        assertNotNull(ir);
        assertTrue(ir.contains("call i32 @abs(i32"), ir);
        assertTrue(ir.contains("call i64 @llabs(i64"), ir);
        assertTrue(ir.contains("call double @fabs(double"), ir);
        assertTrue(ir.contains("call i64 @strlen(i8*"), ir);
        assertTrue(ir.contains("call void @exit(i32"), ir);
        assertTrue(ir.contains("call double @sqrt(double"), ir);
    }

    @Test
    void stdExternsWorkWithThePrefix() throws IOException {
        final String ir = compile(ALL_WRAPPERS);

        assertNotNull(ir);
        assertTrue(ir.contains("call i32 (i8*, ...) @printf(i8*"), ir);
        assertTrue(ir.contains("call i32 (i8*, ...) @scanf(i8*"), ir);
        assertEquals(1, count(ir, "declare i32 @printf("), ir);
        assertEquals(1, count(ir, "declare i32 @scanf("), ir);
    }

    @Test
    void mathLibraryIsLinkedAutomatically() throws Exception {
        final Class<?> command = Class.forName("src.Main.CompilerCmd");
        final Method builder = command.getDeclaredMethod("createProcessBuilder", String.class, String.class, List.class, List.class, boolean.class, String.class);
        builder.setAccessible(true);
        final ProcessBuilder process = (ProcessBuilder) builder.invoke(null, "in.ll", "out", List.of(), List.of(), false, null);

        final boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        assertEquals(windows ? "out" : "-lm", process.command().getLast());
    }

    @Test
    void rootExternsKeepTheirCReturnType() throws IOException {
        final String ir = compile("""
                extern printf(str | ...) -> int32
                extern puts(str) -> int32

                proc main() {
                    printf("%d" | 1)
                    puts("text")
                    ret 0
                }
                """);

        assertNotNull(ir);
        assertTrue(ir.contains("call i32 (i8*, ...) @printf(i8*"), ir);
        assertTrue(ir.contains("call i32 @puts(i8*"), ir);
    }

    @Test
    void stringConcatenationUsesTheLengthInTheStruct() throws IOException {
        final String ir = compile("""
                import std

                proc main() {
                    str left -> "foo"
                    str right -> "bar"
                    str joined -> left + right
                    puts(joined)
                    ret 0
                }
                """);

        assertNotNull(ir);
        // The fat string carries its length, so concatenation does not
        // call strlen. The stdlib's std_strlen wrapper does, but that is
        // not the concatenation path.
        final int mainStart = ir.indexOf("define dso_local i32 @main()");
        final String mainBody = ir.substring(mainStart);
        assertFalse(mainBody.contains("call i64 @strlen"), ir);
        assertTrue(ir.contains("call void @llvm.memcpy.p0.p0.i64(ptr"), ir);
    }

    @Test
    void missingStdFunctionIsRejected() throws IOException {
        final String diagnostics = compileFailure("import std\nproc main() { ret std.nope(1) }");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("unknown function: std.nope"), diagnostics);
    }

    @Test
    void numericFunctionsRun() throws IOException, InterruptedException {
        if (toolsMissing()) return;

        final Path binary = end.compile("stdlib-numeric", """
                import std

                proc main() {
                    if (std.abs(-7) != 7) { ret 1 }
                    if (std.labs(-2000000000) != 2000000000) { ret 2 }
                    if (std.llabs(-2000000000) != 2000000000) { ret 3 }
                    if (std.fabs(-2.5) != 2.5) { ret 4 }
                    if (std.atoi("42") != 42) { ret 5 }
                    if (std.atol("123456") != 123456) { ret 6 }
                    if (std.atoll("1234567890123") != 1234567890123) { ret 7 }
                    if (std.atof("2.5") != 2.5) { ret 8 }
                    if (std.sqrt(9.0) != 3.0) { ret 9 }
                    if (std.fabs(std.cbrt(27.0) - 3.0) > 0.000001) { ret 10 }
                    if (std.pow(2.0 | 3.0) != 8.0) { ret 11 }
                    if (std.ceil(2.1) != 3.0) { ret 12 }
                    if (std.floor(2.9) != 2.0) { ret 13 }
                    if (std.round(2.6) != 3.0) { ret 14 }
                    if (std.trunc(2.9) != 2.0) { ret 15 }
                    if (std.fmod(7.0 | 3.0) != 1.0) { ret 16 }
                    if (std.fmin(2.0 | 3.0) != 2.0) { ret 17 }
                    if (std.fmax(2.0 | 3.0) != 3.0) { ret 18 }
                    if (std.hypot(3.0 | 4.0) != 5.0) { ret 19 }
                    if (std.log(1.0) != 0.0) { ret 20 }
                    if (std.log2(8.0) != 3.0) { ret 21 }
                    if (std.log10(100.0) != 2.0) { ret 22 }
                    if (std.exp(0.0) != 1.0) { ret 23 }
                    if (std.exp2(3.0) != 8.0) { ret 24 }
                    if (std.sin(0.0) != 0.0) { ret 25 }
                    if (std.cos(0.0) != 1.0) { ret 26 }
                    if (std.tan(0.0) != 0.0) { ret 27 }
                    if (std.asin(0.0) != 0.0) { ret 28 }
                    if (std.acos(1.0) != 0.0) { ret 29 }
                    if (std.atan(0.0) != 0.0) { ret 30 }
                    if (std.atan2(0.0 | 1.0) != 0.0) { ret 31 }
                    if (std.sinh(0.0) != 0.0) { ret 32 }
                    if (std.cosh(0.0) != 1.0) { ret 33 }
                    if (std.tanh(0.0) != 0.0) { ret 34 }
                    if (std.asinh(0.0) != 0.0) { ret 35 }
                    if (std.acosh(1.0) != 0.0) { ret 36 }
                    if (std.atanh(0.0) != 0.0) { ret 37 }
                    ret 0
                }
                """);

        assertEquals(0, end.run(binary));
    }

    @Test
    void characterFunctionsRun() throws IOException, InterruptedException {
        if (toolsMissing()) return;

        final Path binary = end.compile("stdlib-ctype", """
                import std

                proc main() {
                    if (std.isalnum(48) == 0) { ret 1 }
                    if (std.isalpha(65) == 0) { ret 2 }
                    if (std.isdigit(48) == 0) { ret 3 }
                    if (std.islower(97) == 0) { ret 4 }
                    if (std.ispunct(33) == 0) { ret 5 }
                    if (std.isspace(32) == 0) { ret 6 }
                    if (std.isupper(65) == 0) { ret 7 }
                    if (std.isxdigit(70) == 0) { ret 8 }
                    if (std.tolower(65) != 97) { ret 9 }
                    if (std.toupper(97) != 65) { ret 10 }
                    ret 0
                }
                """);

        assertEquals(0, end.run(binary));
    }

    @Test
    void stringFunctionsRun() throws IOException, InterruptedException {
        if (toolsMissing()) return;

        final Path binary = end.compile("stdlib-string", """
                import std

                proc main() {
                    if (std.strlen("hello") != 5) { ret 1 }
                    if (std.strcmp("abc" | "abc") != 0) { ret 2 }
                    if (std.strcmp("abc" | "abd") >= 0) { ret 3 }
                    if (std.strncmp("abcx" | "abcy" | 3) != 0) { ret 4 }
                    if (std.strncmp("abcx" | "abcy" | 4) >= 0) { ret 5 }
                    ret 0
                }
                """);

        assertEquals(0, end.run(binary));
    }

    @Test
    void inputFunctionsRun() throws IOException, InterruptedException {
        if (toolsMissing()) return;

        final Path binary = end.compile("stdlib-input", """
                import std

                proc main() {
                    int32 number -> 0
                    if (std.scanf("%d" | &number) != 1) { ret 1 }
                    if (number != 42) { ret 2 }
                    if (std.getchar() != 10) { ret 3 }
                    ret 0
                }
                """);

        final Process process = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        try (java.io.OutputStream input = process.getOutputStream()) {
            input.write("42\n".getBytes(StandardCharsets.UTF_8));
        }
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor(), output);
    }

    @Test
    void randomFunctionsRun() throws IOException, InterruptedException {
        if (toolsMissing()) return;

        final Path binary = end.compile("stdlib-random", """
                import std

                proc main() {
                    std.srand(1)
                    int32 first -> std.rand()
                    int32 second -> std.rand()
                    if (first < 0) { ret 1 }
                    if (second < 0) { ret 2 }
                    ret 0
                }
                """);

        assertEquals(0, end.run(binary));
    }

    @Test
    void outputFunctionsRun() throws IOException, InterruptedException {
        if (toolsMissing()) return;

        final Path binary = end.compile("stdlib-output", """
                import std

                proc main() {
                    puts("C")
                    putchar(68)
                    println(4)
                    ret 0
                }
                """);

        final String output = end.runAndCaptureOutput(binary).replace("\r\n", "\n").trim();
        assertEquals("C\nD4", output);
    }

    @Test
    void exitStopsTheProgram() throws IOException, InterruptedException {
        if (toolsMissing()) return;

        final Path binary = end.compile("stdlib-exit", """
                import std

                proc main() {
                    std.exit(7)
                    ret 0
                }
                """);

        assertEquals(7, end.run(binary));
    }

    @Test
    void abortStopsTheProgram() throws IOException, InterruptedException {
        if (toolsMissing()) return;

        final Path binary = end.compile("stdlib-abort", """
                import std

                proc main() {
                    std.abort()
                    ret 0
                }
                """);

        assertNotEquals(0, end.run(binary));
    }

    @Test
    void systemRunsACommand() throws IOException, InterruptedException {
        if (toolsMissing()) return;

        final Path binary = end.compile("stdlib-system", """
                import std

                proc main() {
                    if (std.system("exit 0") != 0) { ret 1 }
                    ret 0
                }
                """);

        assertEquals(0, end.run(binary));
    }
}

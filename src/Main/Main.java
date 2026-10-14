package src.Main;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import src.AST.FunctionAST;
import src.AST.Value;
import src.Codegen.IRBuilder;
import src.Parser.Parser;
import src.lexer.Lexer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "compiler", mixinStandardHelpOptions = true, version = "1", description = "Compiler z-code")
/**
 * Command line interface for the compiler
 */
class CompilerCmd implements Callable<Integer> {
    @CommandLine.Parameters(index = "0", description = "Source file to be compiled")
    private File sourceFile;

    @CommandLine.Option(names = {"-o", "--out"}, description = "Output path")
    private String outPath;

    @Override
    /**
     * Main method for the compiler
     * @return 0 if the compilation is successful, 1 otherwise
     * @throws Exception if an I/O error occurs
     */
    public Integer call() throws Exception {
        final String zCode = Files.readString(sourceFile.toPath());

        System.out.println("Starting compilation...");

        try {
            InputStream input = new ByteArrayInputStream(zCode.getBytes(StandardCharsets.UTF_8));

            final String llFilePath;
            final String binaryName;
            final String optFilePath;

            if (outPath == null) {
                llFilePath = "output.ll";
                optFilePath = "output_opt.ll";
                binaryName = "output";
            } else if (outPath.endsWith(".ll")) {
                llFilePath = outPath;
                optFilePath = outPath.substring(0, outPath.length() - 3) + "_opt.ll";
                binaryName = outPath.substring(0, outPath.length() - 3);
            } else {
                llFilePath = outPath + ".ll";
                optFilePath = outPath + "_opt.ll";
                binaryName = outPath;
            }

            final Lexer lexer = new Lexer(input);
            final Parser parser = new Parser(lexer);

            final String llvmIR = generateModule(parser, lexer);

            if (llvmIR == null) {
                return 1;
            }

            final File irFile = new File(llFilePath);
            Files.writeString(irFile.toPath(), llvmIR);
            System.out.println("Raw IR built successfully: " + llFilePath);

            // opt passes
            boolean optSuccess = runOptPasses(llFilePath, optFilePath);
            if (!optSuccess) {
                return 1;
            }

            // compilation
            return compileToBinary(new File(optFilePath).getAbsolutePath(), binaryName);

        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * Generates every function in the file, in the order they were written.
     *
     * @param parser the parser, positioned on the next token
     * @param lexer the lexer it reads from
     * @return the whole module, or null if anything in it failed
     * @throws IOException if the input cannot be read
     */
    private static String generateModule(final Parser parser, final Lexer lexer) throws IOException {
        final IRBuilder builder = new IRBuilder();
        final StringBuilder module = new StringBuilder();

        final List<FunctionAST> definitions = parseDefinitions(parser, lexer);
        if (definitions == null) return null;
        if (definitions.isEmpty()) {
            System.err.println("Syntax error");
            return null;
        }

        for (final FunctionAST definition : definitions) {
            if (!builder.declare(definition.prototype())) {
                System.err.println("Error: '" + definition.prototype().getName() + "' is defined more than once");
                return null;
            }
        }

        for (final FunctionAST definition : definitions) {
            final Value function = definition.Codegen(builder);
            if (function == null || builder.hasFailed()) {
                System.err.println("Error building the IR");
                return null;
            }
            module.append(function.text());
        }

        if (module.isEmpty()) {
            System.err.println("Syntax error");
            return null;
        }

        final String body = module.toString();
        final String globals = builder.getGlobals();
        return globals.isEmpty() ? body : globals + "\n" + body;
    }

    /**
     * Reads every definition in the file, reporting the first syntax error.
     *
     * @param parser the parser, positioned on the next token
     * @param lexer the lexer it reads from
     * @return the definitions in source order, or null if one failed to parse
     * @throws IOException if the input cannot be read
     */
    private static List<FunctionAST> parseDefinitions(final Parser parser, final Lexer lexer) throws IOException {
        final List<FunctionAST> definitions = new ArrayList<>();

        while (true) {
            final PrintStream diagnostics = System.err;
            final ByteArrayOutputStream reported = new ByteArrayOutputStream();

            final FunctionAST definition;
            final boolean endOfInput;
            System.setErr(new PrintStream(reported, true, StandardCharsets.UTF_8));
            try {
                definition = parser.ParseDefinition();
                endOfInput = definition == null && lexer.GetTok() == Lexer.Tokens.EOF.value;
            } finally {
                System.setErr(diagnostics);
            }

            if (reported.size() > 0) {
                diagnostics.print(reported.toString(StandardCharsets.UTF_8));
            }

            if (endOfInput && reported.size() == 0) return definitions;

            if (definition == null) {
                System.err.println("Syntax error");
                return Collections.emptyList();
            }

            definitions.add(definition);
        }
    }

    /**
     * Run LLVM optimization passes using checks
     * @param inputLl LLVM IR input
     * @param outputLl LLVM IR output
     * @return true if the optimization passes were successful, false otherwise
     */
    private static boolean runOptPasses(String inputLl, String outputLl) {
        System.out.println("Running LLVM Optimization passes...");
        ProcessBuilder processBuilder = new ProcessBuilder(
                "opt",
                "-O3",
                "-S",             // maintains a readable output
                inputLl,
                "-o",
                outputLl
        );

        processBuilder.inheritIO();

        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("Optimized IR saved to: " + outputLl);
                return true;
            } else {
                // The raw IR was not a module, so there is nothing to optimise and
                // nothing to hand to the linker either. Carrying on from here built a
                // binary from IR that had already been rejected — or none at all —
                // and reported success.
                System.err.println("Opt tool failed with exit code " + exitCode + ".");
                return false;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to launch 'opt' tool. Is it installed and in your PATH? idiot. Do sudo apt install opt");
            return false;
        }
    }

    /**
     * Wrapper for calling cLang with a try/catch block
     * @param llFilePath file path for the LLVM file
     * @param outputBinaryName name for the final binary file
     * @return 0 if the executable was produced, 1 otherwise
     */
    private static int compileToBinary(String llFilePath, String outputBinaryName) {
        System.out.println("Launching Clang...");

        final ProcessBuilder processBuilder = createProcessBuilder(llFilePath, outputBinaryName);

        try {
            final Process process = processBuilder.start();
            final int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("Executable created: ./" + outputBinaryName);
                return 0;
            } else {
                System.err.println("Error during compilation. Exit code: " + exitCode);
                return 1;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to launch Clang");
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * invokes Clang to create the final binary
     * @param llFilePath path for the LLVM file
     * @param outputBinaryName name for the final binary file
     * @return a ProcesssBuilder object
     */
    private static ProcessBuilder createProcessBuilder(String llFilePath, String outputBinaryName) {
        final ProcessBuilder processBuilder = new ProcessBuilder(
                "clang",
                "-O3",
                "-flto",
                "-march=native",
                "-funroll-loops",
                "-fno-rtti",
                "-fno-exceptions",
                "-mtune=native",
                llFilePath,
                "-o",
                outputBinaryName
        );

        processBuilder.inheritIO();
        return processBuilder;
    }
}

/**
 * Main class, nothing to say
 */
public class Main {
    public static void main(String[] args) {
        final int exitCode = new CommandLine(new CompilerCmd()).execute(args);
        System.exit(exitCode);
    }
}
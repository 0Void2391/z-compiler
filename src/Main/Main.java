package src.Main;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import src.AST.ExternExprAST;
import src.AST.FunctionAST;
import src.AST.ModuleAST;
import src.AST.Value;
import src.Codegen.IRBuilder;
import src.Parser.Parser;
import src.lexer.Lexer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    @CommandLine.Option(names = {"-l", "--link"}, description = "Libraries to link against (e.g., -lcurl, -lm)")
    private final List<String> linkLibraries = new ArrayList<>();

    @CommandLine.Option(names = {"-L", "--library-path"}, description = "Directories to search for libraries")
    private final List<String> libraryPaths = new ArrayList<>();


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
            final Parser parser = new Parser(lexer, sourceFile.getName());

            final String llvmIR = Main.generateModule(parser, sourceFile.toPath());

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
            return compileToBinary(new File(optFilePath).getAbsolutePath(), binaryName, libraryPaths, linkLibraries);

        } catch (IOException e) {
            e.printStackTrace();
            return 1;
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
    private static int compileToBinary(String llFilePath, String outputBinaryName, List<String> libPaths, List<String> libFlags) {
        System.out.println("Launching Clang...");

        final ProcessBuilder processBuilder = createProcessBuilder(llFilePath, outputBinaryName, libPaths, libFlags);

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
    private static ProcessBuilder createProcessBuilder(String llFilePath, String outputBinaryName, List<String> libPaths, List<String> libFlags) {

        final List<String> command = new ArrayList<>();
        command.add("clang");
        command.add("-O3");
        command.add("-flto");
        command.add("-march=native");
        command.add("-funroll-loops");
        command.add("-fno-rtti");
        command.add("-fno-exceptions");
        command.add("-mtune=native");
        command.add(llFilePath);
        command.add("-o");
        command.add(outputBinaryName);

        for (String path : libPaths) {
            command.add("-L" + path);
        }

        for (String lib : libFlags) {
            command.add(lib.startsWith("-l") ? lib : "-l" + lib);
        }

        final ProcessBuilder processBuilder = new ProcessBuilder(command);

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


    public static String generateModule(final Parser parser, final Path sourceFile) throws IOException {
        final IRBuilder builder = new IRBuilder();
        final StringBuilder module = new StringBuilder();

        final String rootName = sourceFile.getFileName().toString().replaceFirst("\\.z$", "");
        final ModuleAST root = ModuleLoader.parseUnit(parser, rootName);
        if (root == null) return null;

        final Path fromDir = sourceFile.toAbsolutePath().getParent();
        final List<ModuleAST> dependencies = new ModuleLoader(rootName).loadAll(root.imports(), fromDir);
        if (dependencies == null) return null;

        for (final ModuleAST dependency : dependencies) {
            if (!emitModule(dependency, builder, module)) return null;
        }
        builder.setCurrentModule(null);

        for (final ExternExprAST extern : root.externs()) {
            final Value externValue = extern.Codegen(builder);
            if (externValue == null || builder.hasFailed()) {
                System.err.println("Error building the IR of the externs");
                return null;
            }
            module.append(externValue.text());
        }

        if (!declareAll(root, builder)) return null;

        for (final FunctionAST definition : root.functions()) {
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


    private static boolean emitModule(final ModuleAST module, final IRBuilder builder, final StringBuilder unit) {
        builder.setCurrentModule(module.name());

        for (final ExternExprAST extern : module.externs()) {
            final Value externValue = extern.Codegen(builder);
            if (externValue == null || builder.hasFailed()) {
                System.err.println("Error building the IR of the externs of module '" + module.name() + "'");
                return false;
            }
            unit.append(externValue.text());
        }

        for (final FunctionAST definition : module.functions()) {
            if (definition.prototype().isMain()) {
                System.err.println("Error: 'main' can only be defined in the root file, not in module '" + module.name() + "'");
                return false;
            }
        }

        if (!declareAll(module, builder)) return false;

        for (final FunctionAST definition : module.functions()) {
            final Value function = definition.Codegen(builder);
            if (function == null || builder.hasFailed()) {
                System.err.println("Error building the IR of module '" + module.name() + "'");
                return false;
            }
            unit.append(function.text());
        }
        return true;
    }

    private static boolean declareAll(final ModuleAST module, final IRBuilder builder) {
        for (final FunctionAST definition : module.functions()) {
            if (!builder.declare(definition.prototype())) {
                System.err.println("Error: '" + builder.qualify(definition.prototype().name()) + "' is defined more than once");
                return false;
            }
        }
        return true;
    }
}
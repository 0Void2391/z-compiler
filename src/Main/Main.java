package src.Main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import src.AST.ExternExprAST;
import src.AST.FunctionAST;
import src.AST.ModuleAST;
import src.AST.Value;
import src.Codegen.IRBuilder;
import src.Parser.Parser;
import src.lexer.Lexer;
import src.AST.DeclaratorExprAST;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command line interface for the compiler
 */
@Command(name = "compiler", mixinStandardHelpOptions = true, version = "1", description = "Compiler z-code")
class CompilerCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(CompilerCmd.class);

    /**
     * name of the source file
     */
    @CommandLine.Parameters(index = "0", description = "Source file to be compiled")
    private File sourceFile;

    /**
     * output path for the binary
     */
    @CommandLine.Option(names = {"-o", "--out"}, description = "Output path")
    private String outPath;

    /**
     * libraries to be linked
     */
    @CommandLine.Option(names = {"-l", "--link"}, description = "Libraries to link against (e.g., -lcurl, -lm)")
    private final List<String> linkLibraries = new ArrayList<>();

    /**
     * directories in which to search for libraries
     */
    @CommandLine.Option(names = {"-L", "--library-path"}, description = "Directories to search for libraries")
    private final List<String> libraryPaths = new ArrayList<>();


    /**
     * Main method for the compiler
     * @return 0 if the compilation is successful, 1 otherwise
     * @throws Exception if an I/O error occurs
     */
    @Override
    public Integer call() throws Exception {
        // reads the file
        final String zCode = Files.readString(sourceFile.toPath());

        logger.info("Starting compilation...");

        // parses the string in bytes
        try {
            InputStream input = new ByteArrayInputStream(zCode.getBytes(StandardCharsets.UTF_8));

            final String llFilePath;
            final String binaryName;
            final String optFilePath;

            // handles default parameters and
            // adds the output_opt.ll file which is the file where the optimized IR is put
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

            // prepares the lexer with the byte input
            final Lexer lexer = new Lexer(input);

            // prepares the parser with the lexer and the name of the file (for linking reason)
            final Parser parser = new Parser(lexer);


            final String llvmIR = Main.generateModule(parser, sourceFile.toPath());

            // returns 1 (error) if the generation failed
            if (llvmIR == null) {
                return 1;
            }

            // creates a new file and writes the unoptimized IR
            final File irFile = new File(llFilePath);
            Files.writeString(irFile.toPath(), llvmIR);

            logger.info("Raw IR built successfully: {}", llFilePath);

            final boolean optSuccess = runOptPasses(llFilePath, optFilePath);
            final String toCompile = optSuccess ? optFilePath : llFilePath;
            if (!optSuccess) {
                logger.warn("opt not available, compiling the unoptimized IR");
            }

            final boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
            final String exeName = windows && !binaryName.endsWith(".exe") ? binaryName + ".exe" : binaryName;

            return compileToBinary(new File(toCompile).getAbsolutePath(), exeName, libraryPaths, linkLibraries);

        } catch (IOException e) {
            logger.error("IO error during compilation", e);
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
        logger.info("Running LLVM Optimization passes...");
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
                logger.info("Optimized IR saved to: {}", outputLl);
                return true;
            } else {
                logger.error("Opt tool failed with exit code {}.", exitCode);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to launch 'opt' tool. Is it installed and in your PATH? idiot. Do sudo apt install opt", e);
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
        logger.info("Launching Clang...");

        final ProcessBuilder processBuilder = createProcessBuilder(llFilePath, outputBinaryName, libPaths, libFlags);

        try {
            final Process process = processBuilder.start();
            final int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("Executable created: ./{}", outputBinaryName);
                return 0;
            } else {
                logger.error("Error during compilation. Exit code: {}", exitCode);
                return 1;
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to launch Clang", e);
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
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new CompilerCmd()).execute(args);
        System.exit(exitCode);
    }


    /**
     * Generates the module
     * @param parser parser class
     * @param sourceFile in which the code module is contained
     * @return only the globals externed or also the body (main) or null if an error is present
     * @throws IOException if an exception is found
     */
    public static String generateModule(final Parser parser, final Path sourceFile) throws IOException {
        // prepares classes
        final IRBuilder builder = new IRBuilder();
        final StringBuilder module = new StringBuilder();

        // gets the name of the file removing the final .z
        final String rootName = sourceFile.getFileName().toString().replaceFirst("\\.z$", "");

        // parses an entire module
        final ModuleAST root = ModuleLoader.parseUnit(parser, rootName);
        if (root == null) return null; // error

        final Path fromDir = sourceFile.toAbsolutePath().getParent();
        final List<ModuleAST> dependencies = new ModuleLoader(rootName).loadAll(root.imports(), fromDir);
        if (dependencies == null) return null;

        for (final ModuleAST dependency : dependencies) {
            if (!emitModule(dependency, builder, module)) return null;
        }
        builder.setCurrentModule(null);

        for (final ExternExprAST extern : root.externals()) {
            final Value externValue = extern.codeGen(builder);
            if (externValue == null || builder.hasFailed()) {
                logger.error("Error building the IR of the externs");
                return null;
            }
            module.append(externValue.text());
        }

        for (final DeclaratorExprAST var : root.vars()) {
            var.codeGen(builder);
            if (builder.hasFailed()) {
                logger.error("Error building the globals");
                return null;
            }
        }

        if (declareAll(root, builder)) return null;

        for (final FunctionAST definition : root.functions()) {
            final Value function = definition.codeGen(builder);
            if (function == null || builder.hasFailed()) {
                logger.error("Error building the IR");
                return null;
            }
            module.append(function.text());
        }

        if (module.isEmpty()) {
            logger.error("Syntax error");
            return null;
        }

        final String body = module.toString();
        final String globals = builder.getGlobals();
        return globals.isEmpty() ? body : globals + "\n" + body;
    }


    private static boolean emitModule(final ModuleAST module, final IRBuilder builder, final StringBuilder unit) {
        builder.setCurrentModule(module.name());

        for (final ExternExprAST extern : module.externals()) {
            final Value externValue = extern.codeGen(builder);
            if (externValue == null || builder.hasFailed()) {
                if (logger.isErrorEnabled()) {
                    logger.error("Error building the IR of the externs of module '{}'", module.name());
                }
                return false;
            }
            unit.append(externValue.text());
        }

        for (final DeclaratorExprAST var : module.vars()) {
            var.codeGen(builder);
            if (builder.hasFailed()) {
                logger.error("Error building the globals of module '{}'", module.name());
                return false;
            }
        }

        for (final FunctionAST definition : module.functions()) {
            if (definition.prototype().isMain()) {
                if (logger.isErrorEnabled()) {
                    logger.error("Error: 'main' can only be defined in the root file, not in module '{}'", module.name());
                }
                return false;
            }
        }

        if (declareAll(module, builder)) return false;

        for (final FunctionAST definition : module.functions()) {
            final Value function = definition.codeGen(builder);
            if (function == null || builder.hasFailed()) {
                if (logger.isErrorEnabled()) {
                    logger.error("Error building the IR of module '{}'", module.name());
                }
                return false;
            }
            unit.append(function.text());
        }
        return true;
    }

    private static boolean declareAll(final ModuleAST module, final IRBuilder builder) {
        for (final FunctionAST definition : module.functions()) {
            if (!builder.declare(definition.prototype())) {
                if (logger.isErrorEnabled()) {
                    logger.error("Error: '{}' is defined more than once", builder.qualify(definition.prototype().name()));
                }
                return true;
            }
        }
        return false;
    }
}
package Tests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.AST.DeclaratorExprAST;
import src.AST.FunctionAST;
import src.AST.StructDeclAST;
import src.AST.LLVMTextUnit;
import src.Codegen.IRBuilder;
import src.Parser.Parser;
import src.Lexer.Lexer;
import src.Parser.StructRegistry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ZFixtures {
    private static final Logger logger = LoggerFactory.getLogger(ZFixtures.class);

    private ZFixtures() {
    }

    /**
     * The result of parsing a whole file: its functions and its top-level
     * variables, in source order.
     *
     * @param functions the function definitions, in source order
     * @param globals   the top-level variable declarations, in source order
     */
    record ParsedUnit(List<FunctionAST> functions, List<DeclaratorExprAST> globals, List<StructDeclAST> structs) {
    }

    /**
     * Builds a lexer over a source string.
     *
     * @param source the program text
     * @return the lexer, positioned before the first token
     */
    static Lexer lexerFor(final String source) {
        final InputStream input = new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
        return new Lexer(input);
    }

    /**
     * Builds a parser over a source string.
     *
     * @param source the program text
     * @return the parser, positioned on the first token
     * @throws IOException if the input cannot be read
     */
    static Parser parserFor(final String source) throws IOException {
        return new Parser(lexerFor(source));
    }

    /**
     * Parses every definition in a file.
     *
     * @param source the program text
     * @return the definitions in source order, or null if one failed to parse
     * @throws IOException if the input cannot be read
     */
    static List<FunctionAST> parseAll(final String source) throws IOException {
        final ParsedUnit unit = parseUnit(source);
        return unit == null ? null : unit.functions();
    }

    /**
     * Parses every declaration in a file: struct declarations, top-level
     * variables and function definitions, in source order.
     *
     * @param source the program text
     * @return the parsed unit, or null if one of its declarations failed
     * @throws IOException if the input cannot be read
     */
    static ParsedUnit parseUnit(final String source) throws IOException {
        final Lexer lexer = lexerFor(source);
        final Parser parser = new Parser(lexer);
        final List<FunctionAST> functions = new ArrayList<>();
        final List<DeclaratorExprAST> globals = new ArrayList<>();
        final List<StructDeclAST> structs = new ArrayList<>();

        while (true) {
            if (parser.getContext().getCurTok() == Lexer.Tokens.STRUCT.value) {
                final StructDeclAST s = parseStruct(parser);
                if (s == null) return null;
                structs.add(s);
                continue;
            }

            if (parser.atTypeDeclaration()) {
                final DeclaratorExprAST global = parser.parseVariable(true);
                if (global == null) return null;
                globals.add(global);
                continue;
            }

            final java.io.ByteArrayOutputStream reported = new java.io.ByteArrayOutputStream();
            @SuppressWarnings("squid:S106") // System.err is intentionally captured for test diagnostics
            final java.io.PrintStream diagnostics = System.err;
            final FunctionAST definition;
            final boolean endOfInput;

            System.setErr(new java.io.PrintStream(reported, true, StandardCharsets.UTF_8));
            try {
                definition = parser.parseDefinition();
                endOfInput = definition == null && lexer.getTok() == Lexer.Tokens.EOF.value;
            } finally {
                System.setErr(diagnostics);
            }

            if (reported.size() > 0) {
                diagnostics.print(reported.toString(StandardCharsets.UTF_8));
            }

            if (definition == null) {
                return !endOfInput || reported.size() > 0
                        ? null : new ParsedUnit(functions, globals, structs);
            }
            functions.add(definition);
        }
    }

    /**
     * Parses one struct declaration.
     *
     * @param parser the parser, positioned on {@code struct}
     * @return the declaration, or null on error
     * @throws IOException if the input cannot be read
     */
    private static StructDeclAST parseStruct(final Parser parser) throws IOException {
        final java.io.ByteArrayOutputStream reported = new java.io.ByteArrayOutputStream();
        @SuppressWarnings("squid:S106")
        final java.io.PrintStream diagnostics = System.err;
        final StructDeclAST struct;
        System.setErr(new java.io.PrintStream(reported, true, StandardCharsets.UTF_8));
        try {
            struct = parser.parseStructDecl();
        } finally {
            System.setErr(diagnostics);
        }
        if (reported.size() > 0) {
            diagnostics.print(reported.toString(StandardCharsets.UTF_8));
        }
        return struct;
    }

    /**
     * Generates the IR for a whole source file.
     *
     * @param source the program text
     * @return the module, or null when a definition failed to parse or to build
     */
    static String module(final String source) throws IOException {
        StructRegistry.clear();

        final ParsedUnit unit = parseUnit(source);
        if (unit == null) return null;

        final IRBuilder builder = new IRBuilder();
        for (final StructDeclAST s : unit.structs()) {
            s.codeGen(builder);
        }
        for (final FunctionAST definition : unit.functions()) {
            if (!builder.declare(definition.prototype())) return null;
        }

        for (final DeclaratorExprAST global : unit.globals()) {
            global.codeGen(builder);
            if (builder.hasFailed()) return null;
        }

        final StringBuilder module = new StringBuilder();
        for (final FunctionAST definition : unit.functions()) {
            final LLVMTextUnit function = definition.codeGen(builder);
            if (function == null || builder.hasFailed()) return null;
            module.append(function.text());
        }
        final String body = module.toString();
        final String globals = builder.getGlobals();
        return globals.isEmpty() ? body : globals + "\n" + body;
    }

    /**
     * Builds a module that is expected to fail, with its diagnostics captured.
     *
     * @param source the program text
     * @return what the compiler wrote to stderr, or null if it succeeded
     */
    static String failureOf(final String source) {
        final java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        @SuppressWarnings("squid:S106") // System.err is intentionally captured for test diagnostics
        final java.io.PrintStream diagnostics = System.err;
        System.setErr(new java.io.PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            return module(source) == null ? captured.toString(StandardCharsets.UTF_8) : null;
        } catch (final IOException e) {
            return null;
        } finally {
            System.setErr(diagnostics);
        }
    }
}
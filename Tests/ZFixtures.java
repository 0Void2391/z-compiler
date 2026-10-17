package Tests;

import src.AST.FunctionAST;
import src.AST.Value;
import src.Codegen.IRBuilder;
import src.Parser.Parser;
import src.lexer.Lexer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ZFixtures {

    private ZFixtures() {
    }

    /**
     * Builds a lexer over a source string.
     *
     * @param source the program text
     * @return the lexer, positioned before the first token
     */
    static Lexer lexerFor(final String source) {
        final InputStream input =
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
        return new Lexer(input);
    }

    /**
     * Builds a parser over a source string.
     *
     * @param source the program text
     * @return the parser, positioned on the first token
     * @throws IOException if the input cannot be read
     */
    static Parser parserFor(final String source, final String filename) throws IOException {
        return new Parser(lexerFor(source), filename);
    }

    /**
     * Parses every definition in a file.
     *
     * @param source the program text
     * @return the definitions in source order, or null if one failed to parse
     * @throws IOException if the input cannot be read
     */
    static List<FunctionAST> parseAll(final String source) throws IOException {
        final Lexer lexer = lexerFor(source);
        final Parser parser = new Parser(lexer, "");
        final List<FunctionAST> definitions = new ArrayList<>();

        while (true) {
            final java.io.ByteArrayOutputStream reported = new java.io.ByteArrayOutputStream();
            final java.io.PrintStream diagnostics = System.err;
            final FunctionAST definition;
            final boolean endOfInput;

            System.setErr(new java.io.PrintStream(reported, true, StandardCharsets.UTF_8));
            try {
                definition = parser.ParseDefinition(false);
                endOfInput = definition == null && lexer.GetTok() == Lexer.Tokens.EOF.value;
            } finally {
                System.setErr(diagnostics);
            }

            if (reported.size() > 0) {
                diagnostics.print(reported.toString(StandardCharsets.UTF_8));
            }

            if (definition == null) {
                return !endOfInput || reported.size() > 0 ? null : definitions;
            }
            definitions.add(definition);
        }
    }

    /**
     * Generates the IR for a whole source file.
     *
     * @param source the program text
     * @return the module, or null when a definition failed to parse or to build
     */
    static String module(final String source) throws IOException {
        final List<FunctionAST> definitions = parseAll(source);
        if (definitions == null) return null;

        final IRBuilder builder = new IRBuilder();
        for (final FunctionAST definition : definitions) {
            if (!builder.declare(definition.prototype())) return null;
        }

        final StringBuilder module = new StringBuilder();
        for (final FunctionAST definition : definitions) {
            final Value function = definition.Codegen(builder);
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
        final java.io.PrintStream diagnostics = System.err;
        System.setErr(new java.io.PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            return module(source) == null
                    ? captured.toString(StandardCharsets.UTF_8)
                    : null;
        } catch (final IOException e) {
            return null;
        } finally {
            System.setErr(diagnostics);
        }
    }
}
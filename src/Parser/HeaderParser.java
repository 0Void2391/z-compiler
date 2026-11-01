package src.Parser;

import org.jspecify.annotations.Nullable;
import src.AST.ExternExprAST;
import src.AST.ImportAST;
import src.AST.PrototypeAST;
import src.Lexer.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static src.Parser.Parser.error;
import static src.Parser.TypeParser.*;
import static src.Parser.TypeParser.describeToken;

public class HeaderParser {
    private final ParserContext context;
    private final TypeParser typeParser;

    public HeaderParser(final Parser parser) {
        this.context = parser.getContext();
        this.typeParser = parser.getParserType();
    }

    public @Nullable ImportAST parseImport() throws IOException {
        if (context.getCurTok() != Lexer.Tokens.IMPORT.value) {
            return null;
        }

        context.next();  // consume 'import'

        if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected module name after 'import'");
        }

        final String moduleName = context.getLex().getIdentifierStr();
        context.next();

        return new ImportAST(moduleName);
    }

    public @Nullable ExternExprAST parseExtern() throws IOException {
        if (context.getCurTok() != Lexer.Tokens.EXTERN.value) {
            return null;
        }
        context.next();  // consume 'extern'

        final PrototypeAST proto = parsePrototype(true);
        if (proto == null) return null;

        if (context.getCurTok() != Lexer.Tokens.ASSIGN.value) {
            return error("Missing return value for extern function: " + proto.name());
        }

        context.next();
        final Lexer.Tokens token = Lexer.Tokens.fromValues(context.getCurTok());
        if (!isTypeToken(token)) {
            return error("Invalid return type for extern function: " + proto.name());
        }
        final ZType returnType = resolveType(token.description);
        if (returnType == null) return null;
        context.next();

        return new ExternExprAST(proto, token.description);
    }

    /**
     * Parses a function prototype
     * @return an error or a PrototypeAST node
     * @throws IOException if an exception is found
     */
    public @Nullable PrototypeAST parsePrototype(final boolean extern) throws IOException {
        if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected function name in prototype, got " + describeToken(context.getCurTok()) + " instead");
        }
        final String fnName = context.getLex().getIdentifierStr();
        context.next();

        // Expect '('
        if (context.getCurTok() != Lexer.Tokens.OPEN_PAR.value ) {
            return error("Expected '(' in prototype, got " + describeToken(context.getCurTok()) + " instead");
        }
        context.next();

        final List<PrototypeAST.Param> params = new ArrayList<>();
        if (context.getCurTok() != Lexer.Tokens.CLOSE_PAR.value) {
            while (true) {
                final TypeRef paramType = typeParser.parseType(extern); // added support for varargs (only found in extern declaration)
                if (paramType == null) {
                    return error("Expected parameter type");
                }
                if (paramType == ZType.VARARG && context.getCurTok() != Lexer.Tokens.CLOSE_PAR.value) {
                    return error("'...' must be the last parameter of function: " + fnName);
                }

                if (extern) {
                    params.add(new PrototypeAST.Param(paramType.zName(), ""));
                } else {
                    if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
                        return error("Expected parameter name");
                    }
                    final String paramName = context.getLex().getIdentifierStr();
                    context.next();
                    params.add(new PrototypeAST.Param(paramType.zName(), paramName));
                }

                if (context.getCurTok() == Lexer.Tokens.PARAMS.value) {
                    context.next();
                    continue;
                }
                if (context.getCurTok() == Lexer.Tokens.CLOSE_PAR.value) {
                    break;
                }
                return error("Expected '|' or ')' in parameter list of function: " + fnName);
            }
        }

        context.next();

        TypeRef returnType = null;
        if (!extern && context.getCurTok() == Lexer.Tokens.ASSIGN.value) {
            context.next();
            final Lexer.Tokens retTok = Lexer.Tokens.fromValues(context.getCurTok());
            final boolean structName = retTok == Lexer.Tokens.IDENTIFIER
                    && StructRegistry.isStruct(context.getLex().getIdentifierStr());
            if (!isTypeToken(retTok) && !structName) {
                return error("Expected return type after '->', got " + describeToken(context.getCurTok()) + " instead");
            }
            returnType = typeParser.parseType(false);
            if (returnType == null) {
                return error("Expected return type after '->', got " + describeToken(context.getCurTok()) + " instead");
            }
            context.next();
        }

        return new PrototypeAST(fnName, params, returnType);
    }
}

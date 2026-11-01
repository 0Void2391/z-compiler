package src.Parser;

import src.AST.StructDeclAST;
import src.Lexer.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static src.Parser.Parser.error;

public class StructParser {
    private final ParserContext context;
    private final TypeParser typeParser;

    public StructParser(final Parser parser) {
        this.context = parser.getContext();
        this.typeParser = parser.getParserType();
    }

    /**
     * Parses a struct declaration:
     * {@code struct Point { int32 x | int32 y }}.
     *
     * @return a StructDeclAST node or an error
     * @throws IOException if an exception is found
     */
    public StructDeclAST parseStructDecl() throws IOException {
        context.next();  // consume 'struct'

        if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected struct name after 'struct'");
        }
        final String name = context.getLex().getIdentifierStr();
        context.next();

        if (context.getCurTok() != Lexer.Tokens.OPEN_FUNC.value) {
            return error("Expected '{' after struct name");
        }
        context.next();

        final List<StructType.Field> fields = new ArrayList<>();
        while (context.getCurTok() != Lexer.Tokens.CLOSE_FUNC.value && context.getCurTok() != Lexer.Tokens.EOF.value) {
            final TypeRef fieldType = typeParser.parseType();
            if (fieldType == null) {
                return error("Expected field type in struct '" + name + "'");
            }
            if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
                return error("Expected field name in struct '" + name + "'");
            }
            final String fieldName = context.getLex().getIdentifierStr();
            context.next();
            fields.add(new StructType.Field(fieldName, fieldType));
            if (context.getCurTok() == Lexer.Tokens.PARAMS.value) {
                context.next();
                continue;
            }
            break;
        }

        if (context.getCurTok() != Lexer.Tokens.CLOSE_FUNC.value) {
            return error("Expected '}' at the end of struct '" + name + "'");
        }
        context.next();

        if (StructRegistry.isStruct(name)) {
            return error("Struct '" + name + "' is already declared");
        }
        final StructType st = new StructType(name, fields);
        StructRegistry.register(st);
        return new StructDeclAST(st);
    }
}

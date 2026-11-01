package src.Parser;

import src.AST.ArrayAST;
import src.AST.NumberExprAST;
import src.Lexer.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static src.Parser.Parser.error;
import static src.Parser.TypeParser.isTypeToken;
import static src.Parser.TypeParser.resolveType;

public class ArrayParser {
    private final ParserContext context;
    private final ExpressionParser expressionParser;

    public ArrayParser(final Parser parser) {
        this.context = parser.getContext();
        this.expressionParser = parser.getExpressionParser();
    }

    public ArrayAST parseArray() throws IOException {
        //context.next(); (I believe this isn't necessary here)
        //Array declaration does NOT support expressions inside of it yet. you can only declare with a number
        final Lexer.Tokens typeTok = Lexer.Tokens.fromValues(context.getCurTok());
        if (!isTypeToken(typeTok)) {
            return error("Expected type after array definition");
        }
        final ZType zType = resolveType(typeTok.description+"*"); //it will resolve it as a ptr
        if (zType == null) return null;
        context.next();

        if (context.getCurTok() != Lexer.Tokens.PARAMS.value) {
            return error("Expected '|' after type definition");
        }
        context.next();
        if (context.getCurTok() != Lexer.Tokens.NUMBER.value) {
            error("Expected number");
        }
        double size = context.getLex().getNumVal();
        NumberExprAST arraySize = (NumberExprAST) expressionParser.parseNumberExpr(size);
        NumberExprAST arrayDepth = (NumberExprAST) expressionParser.parseNumberExpr(1);
        context.next();

        if (context.getCurTok() == Lexer.Tokens.PARAMS.value) {
            context.next();
            if (context.getCurTok() != Lexer.Tokens.NUMBER.value) {
                error("Expected number");
            }
            double depth = context.getLex().getNumVal();
            if (size < 1) {
                return error("Invalid depth");
            }
            arrayDepth = (NumberExprAST) expressionParser.parseNumberExpr(depth); //for matrixes, depth 2 means [][], 1 is default
            context.next();
        } else if (context.getCurTok() != Lexer.Tokens.ARRAY_CLOSE.value) {
            return error("Expected closing bracket");
        }
        context.next();

        if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected identifier name");
        }
        final String name = context.getLex().getIdentifierStr();
        context.next();

        if (context.getCurTok() != Lexer.Tokens.OPEN_FUNC.value) {
            return new ArrayAST(zType, arraySize, arrayDepth, name, new ArrayList<NumberExprAST>());
        }
        context.next();

        if (context.getCurTok() != Lexer.Tokens.ASSIGN.value && context.getCurTok() != Lexer.Tokens.SAME.value) {
            return error("Expected '->' or '=' in constant declaration");
        }
        context.next();

        final List<NumberExprAST> members = new ArrayList<>((int) size);

        for (int i = 0; i < (int) size; ++i) {

            NumberExprAST valAST = (NumberExprAST) expressionParser.parseNumberExpr(context.getLex().getNumVal()); //qua dentro fa già un next
            members.add(valAST);
            if (context.getCurTok() != Lexer.Tokens.PARAMS.value && context.getCurTok() != Lexer.Tokens.CLOSE_FUNC.value) {
                return error("Expected '|' after type definition");
            } else if (context.getCurTok() == Lexer.Tokens.CLOSE_FUNC.value) {
                break;
            }
            context.next();
        }

        return new ArrayAST(zType, arraySize, arrayDepth, name, members);
    }
}

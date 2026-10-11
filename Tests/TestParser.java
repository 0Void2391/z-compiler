package Tests;

import org.junit.jupiter.api.Test;
import src.AST.ExprAST;
import src.Parser.Parser;
import src.lexer.Lexer;
import src.AST.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class TestParser {

    private final Parser createParser(final String input) throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        return new Parser(lex);
    }
    @Test
    void testSimpleNumber() throws IOException {
        final Parser parser = createParser("42");
        final ExprAST ast = parser.ParseExpression();
        assertNotNull(ast);
        assertInstanceOf(NumberExprAST.class, ast);
    }

    @Test
    void testBinaryPrecedence() throws IOException {
        final Parser parser = createParser("5 + 10 * 2");
        final ExprAST ast = parser.ParseExpression();
        assertNotNull(ast);
        assertInstanceOf(BinaryExprAST.class, ast);
    }

    @Test
    void testParenthesis() throws IOException {
        final Parser parser = createParser("(5 + 10) * 2");
        final ExprAST ast = parser.ParseExpression();
        assertNotNull(ast);
        assertInstanceOf(BinaryExprAST.class, ast);
    }

    @Test
    void testFunctionCall() throws IOException {
        final Parser parser = createParser("foo(x | y)");
        final ExprAST ast = parser.ParseExpression();
        assertNotNull(ast);
        assertInstanceOf(CallExprAST.class, ast);
    }

    @Test
    void testLeftParsing() throws IOException {
        final Parser parser = createParser("5 - 3 - 1");
        final ExprAST ast = parser.ParseExpression();
        assertNotNull(ast);
    }

    @Test
    void testFunctionCallWithoutArgs() throws IOException {
        final Parser parser = createParser("foo()");
        final ExprAST ast = parser.ParseExpression();
        assertNotNull(ast);
        assertInstanceOf(CallExprAST.class, ast);
    }

    @Test
    void testFunctionCallWithExpression() throws IOException {
        final Parser parser = createParser("foo(x + 5 | 42)");
        final ExprAST ast = parser.ParseExpression();
        assertNotNull(ast);
    }

    @Test
    void testErrorUnclosedParenthesis() throws IOException {
        final Parser parser = createParser("(5 + x");
        final ExprAST ast = parser.ParseExpression();
        assertNull(ast);
    }

    @Test
    void testOrphanOperation() throws IOException {
        final Parser parser = createParser("5 +");
        final ExprAST ast = parser.ParseExpression();
        assertNull(ast);
    }

    @Test
    void testUnexpectedToken() throws IOException {
        final Parser parser = createParser("+ 5");
        final ExprAST ast = parser.ParseExpression();
        assertNull(ast);
    }

    @Test
    void testPrecedenceGroupsMultiplicationFirst() throws IOException {
        final Parser parser = createParser("5 + 10 * 2");
        final ExprAST ast = parser.ParseExpression();
        final BinaryExprAST root = assertInstanceOf(BinaryExprAST.class, ast);

        assertEquals('+', root.operator());
        assertInstanceOf(NumberExprAST.class, root.left());
        assertEquals('*', assertInstanceOf(BinaryExprAST.class, root.right()).operator());
    }

    @Test
    void testSubtractionIsLeftAssociative() throws IOException {
        final Parser parser = createParser("5 - 3 - 1");
        final BinaryExprAST root =
                assertInstanceOf(BinaryExprAST.class, parser.ParseExpression());

        // (5 - 3) - 1: the first subtraction has to end up on the left, or
        // 5 - 3 - 1 would be read as 5 - (3 - 1).
        assertEquals('-', root.operator());
        final BinaryExprAST left = assertInstanceOf(BinaryExprAST.class, root.left());
        assertEquals('-', left.operator());
        assertEquals("5", assertInstanceOf(NumberExprAST.class, left.left()).text());
        assertEquals("3", assertInstanceOf(NumberExprAST.class, left.right()).text());
        assertEquals("1", assertInstanceOf(NumberExprAST.class, root.right()).text());
    }

    @Test
    void testParenthesisOverridesPrecedence() throws IOException {
        final Parser parser = createParser("(5 + 10) * 2");
        final BinaryExprAST root =
                assertInstanceOf(BinaryExprAST.class, parser.ParseExpression());

        assertEquals('*', root.operator());
        final ParenExprAST left = assertInstanceOf(ParenExprAST.class, root.left());
        assertEquals('+', assertInstanceOf(BinaryExprAST.class, left.inner()).operator());
    }

    @Test
    void testUnaryMinusWrapsTheOperand() throws IOException {
        final Parser parser = createParser("-5");
        final UnaryExprAST unary = assertInstanceOf(UnaryExprAST.class, parser.ParseExpression());

        assertInstanceOf(NumberExprAST.class, unary.operand());
    }

    @Test
    void testFunctionCallKeepsItsArguments() throws IOException {
        final Parser parser = createParser("foo(x | y)");
        final CallExprAST call = assertInstanceOf(CallExprAST.class, parser.ParseExpression());

        assertEquals("foo", call.callee());
        assertEquals(2, call.arguments().size());
        assertEquals("x", assertInstanceOf(VarRefExprAST.class, call.arguments().getFirst()).name());
        assertEquals("y", assertInstanceOf(VarRefExprAST.class, call.arguments().get(1)).name());
    }

    @Test
    void testFunctionCallWithAnExpressionArgument() throws IOException {
        final Parser parser = createParser("foo(x + 5 | 42)");
        final CallExprAST call = assertInstanceOf(CallExprAST.class, parser.ParseExpression());

        assertEquals(2, call.arguments().size());
        assertEquals('+', assertInstanceOf(BinaryExprAST.class, call.arguments().getFirst()).operator());
    }

    @Test
    void testFunctionCallWithNoArgumentsHasAnEmptyList() throws IOException {
        final Parser parser = createParser("foo()");
        final CallExprAST call = assertInstanceOf(CallExprAST.class, parser.ParseExpression());

        assertTrue(call.arguments().isEmpty());
    }

    @Test
    void testFunctionCallWithoutArguments() throws IOException {
        final Parser parser = createParser("foo()");
        final CallExprAST call = assertInstanceOf(CallExprAST.class, parser.ParseExpression());

        assertEquals("foo", call.callee());
    }

    @Test
    void testVariableReferenceIsNotACall() throws IOException {
        final Parser parser = createParser("x");
        final VarRefExprAST reference =
                assertInstanceOf(VarRefExprAST.class, parser.ParseExpression());

        assertEquals("x", reference.name());
    }

    @Test
    void testReturnStatement() throws IOException {
        final Parser parser = createParser("ret 1 + 2");
        assertInstanceOf(RetAST.class, parser.ParseStatement());
    }

    @Test
    void testForLoopIsParsed() throws IOException {
        final Parser parser = createParser("for (int32 i -> 0 | i < 10 | i + 1) { }");
        final ForAST loop = assertInstanceOf(ForAST.class, parser.ParseStatement());

        assertEquals("i", loop.varName());
        assertEquals(src.Parser.ZType.INT32, loop.varType());
    }

    @Test
    void testForLoopMissingSeparatorFails() throws IOException {
        final Parser parser = createParser("for (int32 i -> 0, i < 10 | i + 1) { }");
        assertNull(parser.ParseStatement());
    }

    @Test
    void testPrototypeIsParsed() throws IOException {
        final Parser parser = createParser("foo(int32 x | flt64 y)");
        final PrototypeAST prototype = parser.ParsePrototype();

        assertEquals("foo", prototype.getName());
        assertEquals(2, prototype.getParams().size());
        assertEquals("x", prototype.getParams().getFirst().name);
        assertEquals("int32", prototype.getParams().getFirst().type);
        assertEquals("flt64", prototype.getParams().get(1).type);
    }

    @Test
    void testFunctionDefinitionCarriesItsBody() throws IOException {
        final Parser parser = createParser("proc foo(int32 x) { ret x }");
        final FunctionAST function = parser.ParseDefinition();

        assertNotNull(function);
        assertEquals("foo", function.prototype().getName());
        assertEquals(1, function.prototype().getParams().size());
        assertEquals(src.Parser.ZType.FLT64, function.returnType());
    }

    @Test
    void testMainIsTheOnlyFunctionReturningAnInteger() throws IOException {
        final Parser parser = createParser("proc main() { ret 1 }");
        final FunctionAST function = parser.ParseDefinition();

        assertEquals(src.Parser.ZType.INT32, function.returnType());
    }

    @Test
    void testSeveralDefinitionsInOneFile() throws IOException {
        final java.util.List<FunctionAST> definitions =
                ZFixtures.parseAll("proc a() { ret 1 }\nproc b() { ret a() }");

        assertNotNull(definitions);
        assertEquals(2, definitions.size());
        assertEquals("a", definitions.getFirst().prototype().getName());
        assertEquals("b", definitions.get(1).prototype().getName());
    }

    @Test
    void testUnclosedBraceFailsToParse() throws IOException {
        assertNull(ZFixtures.parseAll("proc a() { ret 1"));
    }

    @Test
    void testUnclosedBraceAfterAGoodDefinitionFailsToParse() throws IOException {
        assertNull(ZFixtures.parseAll("proc main() { ret 1 }\nproc broken() { ret"));
    }

    @Test
    void testUnclosedPrototypeAfterAGoodDefinitionFailsToParse() throws IOException {
        assertNull(ZFixtures.parseAll("proc main() { ret 1 }\nproc broken() { ret 2"));
    }

    @Test
    void testTruncatedValueAfterAGoodDefinitionFailsToParse() throws IOException {
        assertNull(ZFixtures.parseAll("proc main() { ret 1 }\nint32 x -> "));
    }

    @Test
    void testGoodDefinitionIsNotLostToALaterSyntaxError() throws IOException {
        final String diagnostics =
                ZFixtures.failureOf("proc main() { ret 1 }\nproc broken() { ret 2");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("Expected '}'"), diagnostics);
    }

    @Test
    void testStatementAfterAFunctionIsNotReadAsAFunction() throws IOException {
        final String diagnostics =
                ZFixtures.failureOf("proc main() { ret 1 }\nint32 x -> ");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("Expected '('"), diagnostics);
    }

    @Test
    void testLastDefinitionWithoutNewlineIsStillParsed() throws IOException {
        assertNotNull(ZFixtures.parseAll("proc a() { ret 1 }"));
    }

    @Test
    void testDeclarationWithNoBodyFailsToParse() throws IOException {
        assertNull(ZFixtures.parseAll("proc a() "));
    }

    @Test
    void testPrototypeWithMissingParameterNameFails() throws IOException {
        final Parser parser = createParser("foo(int32)");
        assertNull(parser.ParsePrototype());
    }

    @Test
    void testPrototypeWithUnknownTypeFails() throws IOException {
        final Parser parser = createParser("foo(int7 x)");
        assertNull(parser.ParsePrototype());
    }

    @Test
    void testWholeFileGeneratesItsModule() throws IOException {
        final String module = ZFixtures.module("proc a() { ret 1 }\nproc main() { ret a() }");

        assertNotNull(module);
        assertTrue(module.contains("define dso_local i32 @main()"), module);
        assertTrue(module.contains("call double @a()"), module);
    }

    @Test
    void testSimpleConstant() throws IOException {
        // cn int64 PI -> 314
        final Parser parser = createParser("cn int64 PI -> 314");
        final DeclaratorExprAST c = parser.ParseConstant();

        assertNotNull(c);
    }

    @Test
    void testConstantWithEquals() throws IOException {
        // 也支持 = 赋值
        final Parser parser = createParser("cn int32 X = 42");
        final DeclaratorExprAST c = parser.ParseConstant();

        assertNotNull(c);
    }

    @Test
    void testConstantWithExpressionValue() throws IOException {
        // 值可以是表达式
        final Parser parser = createParser("cn int64 X -> 2 + 3");
        final DeclaratorExprAST c = parser.ParseConstant();

        assertNotNull(c);
    }

    @Test
    void testConstantWithVariableValue() throws IOException {
        // 值可以是变量引用
        final Parser parser = createParser("cn int64 Y -> X");
        final DeclaratorExprAST c = parser.ParseConstant();

        assertNotNull(c);
    }

    @Test
    void testConstantFloatType() throws IOException {
        final Parser parser = createParser("cn flt64 PI -> 3.14");
        final DeclaratorExprAST c = parser.ParseConstant();

        assertNotNull(c);
    }

    @Test
    void testConstantStringType() throws IOException {
        final Parser parser = createParser("cn int32 NAME -> 42");
        final DeclaratorExprAST c = parser.ParseConstant();

        assertNotNull(c);
    }

    @Test
    void testConstantMissingType() throws IOException {
        final Parser parser = createParser("cn PI -> 314");
        final DeclaratorExprAST c = parser.ParseConstant();
        assertNull(c);
    }

    @Test
    void testConstantMissingName() throws IOException {
        final Parser parser = createParser("cn int64 -> 314");
        final DeclaratorExprAST c = parser.ParseConstant();
        assertNull(c);
    }

    @Test
    void testConstantMissingAssign() throws IOException {
        final Parser parser = createParser("cn int64 PI 314");
        final DeclaratorExprAST c = parser.ParseConstant();
        assertNull(c);
    }

    @Test
    void testMainLoopWithConstants() throws IOException {
        // MainLoop 能连续处理多个常量
        final String code = """
                cn int64 A -> 1
                cn int64 B -> 2
                cn flt64 C -> 3.14
                """;

        final Parser parser = createParser(code);
        assertDoesNotThrow(parser::MainLoop);
    }

    @Test
    void testMainLoopConstantAndFunction() throws IOException {
        // 常量和函数混在一起
        final String code = "cn int64 PI -> 314\nproc foo(int64 x | int64 y) { ret x }\n";

        final Parser parser = createParser(code);
        assertDoesNotThrow(parser::MainLoop);
    }

    @Test
    void testVariableDeclaration() throws IOException {
        final String code = "int32 var";
        final Parser parser = createParser(code);
        final DeclaratorExprAST v = parser.ParseVariable();
        assertInstanceOf(DeclaratorExprAST.class, v);
    }

    @Test
    void testVariableWithAssignment() throws IOException {
        final String code = "flt32 var = 2";
        final Parser parser = createParser(code);
        final DeclaratorExprAST v = parser.ParseVariable();
        assertInstanceOf(DeclaratorExprAST.class, v);
    }

    @Test
    void testIfParsed() throws IOException {
        final Parser parser = createParser("if (1 < 2) { ret 1 }");
        final IfAST ifAst = assertInstanceOf(IfAST.class, parser.ParseStatement());

        assertEquals(1, ifAst.branches().size());
        assertNull(ifAst.elseBody());
    }

    @Test
    void testIfEifElseParsed() throws IOException {
        final Parser parser = createParser(
                "if (1 > 2) { ret 1 } eif (2 > 3) { ret 2 } else { ret 3 }");
        final IfAST ifAst = assertInstanceOf(IfAST.class, parser.ParseStatement());

        assertEquals(2, ifAst.branches().size());
        assertNotNull(ifAst.elseBody());
    }

    @Test
    void testIfMissingConditionFails() throws IOException {
        final Parser parser = createParser("if { ret 1 }");
        assertNull(parser.ParseStatement());
    }

    @Test
    void testIfMissingBodyFails() throws IOException {
        final Parser parser = createParser("if (1 < 2) ret 1");
        assertNull(parser.ParseStatement());
    }
}
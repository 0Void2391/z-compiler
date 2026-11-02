package Tests;

import org.junit.jupiter.api.Test;
import src.AST.ExprAST;
import src.AST.ForAST;
import src.AST.IfAST;
import src.AST.PrototypeAST;
import src.Parser.Parser;
import src.Parser.StructRegistry;
import src.Parser.StructType;
import src.Parser.ZType;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestMoreParser {

    private static Parser parser(final String source) throws IOException {
        return ZFixtures.parserFor(source);
    }

    @Test
    void testAssignmentNeedsAValue() throws IOException {
        assertNull(parser("x ->").parseExpression());
    }

    @Test
    void testStructLiteralNeedsSeparators() throws IOException {
        StructRegistry.clear();
        assertNotNull(parser("struct Point { int32 x | int32 y }").parseStructDecl());
        assertNull(parser("Point{1 2}").parseExpression());
    }

    @Test
    void testStructLiteralNeedsValuesThatParse() throws IOException {
        StructRegistry.clear();
        assertNotNull(parser("struct Point { int32 x | int32 y }").parseStructDecl());
        assertNull(parser("Point{1 + | 2}").parseExpression());
    }

    @Test
    void testCallNeedsSeparators() throws IOException {
        assertNull(parser("f(1 2)").parseExpression());
    }

    @Test
    void testCallNeedsValuesThatParse() throws IOException {
        assertNull(parser("f(1 +)").parseExpression());
    }

    @Test
    void testAddressOfNeedsAnOperand() throws IOException {
        assertNull(parser("&").parseExpression());
    }

    @Test
    void testDerefNeedsAnOperand() throws IOException {
        assertNull(parser("*").parseExpression());
    }

    @Test
    void testUnaryMinusNeedsAnOperand() throws IOException {
        assertNull(parser("-").parseExpression());
    }

    @Test
    void testDefinitionAfterImportIsRejected() throws IOException {
        assertNull(parser("import math").parseDefinition());
    }

    @Test
    void testDefinitionNeedsProc() throws IOException {
        assertNull(parser("int32 x").parseDefinition());
    }

    @Test
    void testDefinitionNeedsAPrototype() throws IOException {
        assertNull(parser("proc").parseDefinition());
    }

    @Test
    void testDefinitionSkipsSemicolons() throws IOException {
        assertNotNull(parser("proc f() { ; ret 1 ; }").parseDefinition());
    }

    @Test
    void testVariableNeedsAType() throws IOException {
        assertNull(parser("42").parseVariable(false));
    }

    @Test
    void testConstantNeedsAValue() throws IOException {
        assertNull(parser("cn int32 X ->").parseConstant());
    }

    @Test
    void testExternRequiresTheKeyword() throws IOException {
        assertNull(parser("int32").parseExtern());
    }

    @Test
    void testExternRequiresAPrototype() throws IOException {
        assertNull(parser("extern").parseExtern());
    }

    @Test
    void testExternRequiresAReturnType() throws IOException {
        assertNull(parser("extern sqrt(flt64)").parseExtern());
    }

    @Test
    void testExternRejectsANonTypeReturn() throws IOException {
        assertNull(parser("extern sqrt(flt64) -> 1").parseExtern());
    }

    @Test
    void testExternVarargMustBeLast() throws IOException {
        assertNull(parser("f(... | int32)").parsePrototype(true));
    }

    @Test
    void testVarargNeedsAType() throws IOException {
        assertNull(parser("f(...)").parsePrototype(false));
    }

    @Test
    void testVarargMustBeTheLastParameter() throws IOException {
        assertNull(parser("f(... int32 x | int32 y)").parsePrototype(false));
    }

    @Test
    void testVarargCanBeTheLastParameter() throws IOException {
        final PrototypeAST prototype = parser("f(... int32 x)").parsePrototype(false);

        assertNotNull(prototype);
        assertEquals(ZType.INT32.zName(), prototype.params().getFirst().type());
        assertEquals("x", prototype.params().getFirst().name());
        assertTrue(prototype.params().getFirst().varArg());
    }

    @Test
    void testParameterListNeedsSeparators() throws IOException {
        assertNull(parser("f(int32 x int32 y)").parsePrototype(false));
    }

    @Test
    void testReturnTypeMustBeAType() throws IOException {
        assertNull(parser("f() -> 1").parsePrototype(false));
    }

    @Test
    void testStructReturnTypeIsAccepted() throws IOException {
        StructRegistry.clear();
        StructRegistry.register(new StructType("Point", List.of()));
        final PrototypeAST prototype = parser("f() -> Point").parsePrototype(false);

        assertNotNull(prototype);
        assertInstanceOf(StructType.class, prototype.returnType());
    }

    @Test
    void testForWithoutParenthesisFails() throws IOException {
        assertNull(parser("for").parseStatement());
    }

    @Test
    void testForWithoutTypeFails() throws IOException {
        assertNull(parser("for (nope i -> 0 | 1 | 1) { }").parseStatement());
    }

    @Test
    void testForWithoutNameFails() throws IOException {
        assertNull(parser("for (int32 -> 0 | 1 | 1) { }").parseStatement());
    }

    @Test
    void testForWithoutInitialValueFails() throws IOException {
        assertNull(parser("for (int32 i 0 | 1 | 1) { }").parseStatement());
    }

    @Test
    void testForWithABrokenInitialValueFails() throws IOException {
        assertNull(parser("for (int32 i -> + | 1 | 1) { }").parseStatement());
    }

    @Test
    void testForWithoutThePipeAfterTheInitialValueFails() throws IOException {
        assertNull(parser("for (int32 i -> 0 1 | 1) { }").parseStatement());
    }

    @Test
    void testForWithABrokenConditionFails() throws IOException {
        assertNull(parser("for (int32 i -> 0 | + | 1) { }").parseStatement());
    }

    @Test
    void testForWithoutThePipeAfterTheConditionFails() throws IOException {
        assertNull(parser("for (int32 i -> 0 | i < 3 i + 1) { }").parseStatement());
    }

    @Test
    void testForWithABrokenUpdateFails() throws IOException {
        assertNull(parser("for (int32 i -> 0 | i < 3 | +) { }").parseStatement());
    }

    @Test
    void testForWithoutAClosingParenthesisFails() throws IOException {
        assertNull(parser("for (int32 i -> 0 | i < 3 | i + 1 { }").parseStatement());
    }

    @Test
    void testForWithoutABodyFails() throws IOException {
        assertNull(parser("for (int32 i -> 0 | i < 3 | i + 1) 1").parseStatement());
    }

    @Test
    void testForWithABrokenStatementFails() throws IOException {
        assertNull(parser("for (int32 i -> 0 | i < 3 | i + 1) { 1 + }").parseStatement());
    }

    @Test
    void testForWithoutAClosingBraceFails() throws IOException {
        assertNull(parser("for (int32 i -> 0 | i < 3 | i + 1) { ret 1").parseStatement());
    }

    @Test
    void testForSkipsSemicolons() throws IOException {
        final ForAST loop = assertInstanceOf(ForAST.class, parser("for (int32 i -> 0 | i < 3 | i + 1) { ; ret 1 ; }").parseStatement());

        assertEquals("i", Objects.requireNonNull(loop).varName());
    }

    @Test
    void testEifNeedsAValidBranch() throws IOException {
        assertNull(parser("if (1) { } eif 1 { }").parseIf());
    }

    @Test
    void testElseNeedsABrace() throws IOException {
        assertNull(parser("if (1) { } else 1").parseIf());
    }

    @Test
    void testIfNeedsACondition() throws IOException {
        assertNull(parser("if () { }").parseIf());
    }

    @Test
    void testIfNeedsAClosingParenthesis() throws IOException {
        assertNull(parser("if (1 { }").parseIf());
    }

    @Test
    void testIfWithABrokenStatementStillBuildsTheBranch() throws IOException {
        final IfAST conditional = assertInstanceOf(IfAST.class, parser("if (1) { 1 + }").parseIf());

        assertNotNull(conditional);
        assertEquals(1, conditional.branches().size());
    }

    @Test
    void testIfNeedsAClosingBrace() throws IOException {
        assertNull(parser("if (1) { ret 1").parseIf());
    }

    @Test
    void testIfSkipsSemicolons() throws IOException {
        assertNotNull(parser("if (1) { ; ret 1 ; }").parseIf());
    }

    @Test
    void testReturnNeedsAValueThatParses() throws IOException {
        assertNull(parser("ret +").parseRet());
    }

    @Test
    void testMainLoopReachesEndOfInput() throws IOException {
        assertDoesNotThrow(() -> parser(";").mainLoop());
    }

    @Test
    void testMainLoopStopsAtAnArray() throws IOException {
        assertDoesNotThrow(() -> parser("[int32|1|1] values").mainLoop());
    }

    @Test
    void testParseAllStopsOnAnUnknownTopLevelToken() {
        assertNotNull(ZFixtures.failureOf("@"));
    }

    @Test
    void testExpressionAtTopLevelIsNotAFunction() throws IOException {
        assertNull(ZFixtures.parseAll("1 + 2"));
    }

    @Test
    void testExpressionStatementIsParsed() throws IOException {
        final Parser parser = parser("1 + 2");
        final ExprAST statement = parser.parseStatement();

        assertNotNull(statement);
    }
}

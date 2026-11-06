package Tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import src.AST.*;
import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.StructRegistry;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAstCoverage {

    private static final class Literal extends ExprAST {
        private final TypeRef type;
        private final String text;

        private Literal(final TypeRef type, final String text) {
            this.type = type;
            this.text = text;
        }

        @Override
        public TypeRef type(final IRBuilder builder) {
            return type;
        }

        @Override
        public LLVMTextUnit codeGen(final IRBuilder builder) {
            return new LLVMTextUnit(text, type);
        }

        @Override
        public LLVMTextUnit constantValue(final IRBuilder builder) {
            return codeGen(builder);
        }
    }

    @AfterEach
    void clean() {
        StructRegistry.clear();
    }

    @Test
    void testBaseCoercionGuardsAndConversions() {
        final IRBuilder builder = new IRBuilder();
        final Literal literal = new Literal(ZType.INT32, "1");
        final LLVMTextUnit llvmTextUnit = new LLVMTextUnit("1", ZType.INT32);

        assertNull(literal.coerce(builder, null, ZType.INT32));
        assertSame(llvmTextUnit, literal.coerce(builder, llvmTextUnit, null));

        assertEqualsType(ZType.FLT32, literal.coerce(builder, new LLVMTextUnit("1.5", ZType.FLT64), ZType.FLT32));
        assertTrue(builder.getIR().contains("fptrunc double 1.5 to float"));
        assertEqualsType(ZType.FLT64, literal.coerce(builder, new LLVMTextUnit("1.5", ZType.FLT32), ZType.FLT64));
        assertTrue(builder.getIR().contains("fpext float 1.5 to double"));
        assertEqualsType(ZType.UINT32, literal.coerce(builder, new LLVMTextUnit("1.5", ZType.FLT64), ZType.UINT32));
        assertTrue(builder.getIR().contains("fptoui double 1.5 to i32"));
        assertEqualsType(ZType.INT32, literal.coerce(builder, new LLVMTextUnit("1", ZType.INT64), ZType.INT32));
        assertTrue(builder.getIR().contains("trunc i64 1 to i32"));
        assertEqualsType(ZType.INT64, literal.coerce(builder, new LLVMTextUnit("1", ZType.UINT32), ZType.INT64));
        assertTrue(builder.getIR().contains("zext i32 1 to i64"));
    }

    private static void assertEqualsType(final TypeRef expected, final LLVMTextUnit llvmTextUnit) {
        assertNotNull(llvmTextUnit);
        assertSame(expected, llvmTextUnit.type());
    }

    @Test
    void testFloatRemainderIsSupported() {
        final IRBuilder builder = new IRBuilder();
        final LLVMTextUnit llvmTextUnit = new BinaryExprAST("%", new Literal(ZType.FLT64, "1.0"), new Literal(ZType.FLT64, "2.0")).codeGen(builder);

        assertNotNull(llvmTextUnit);
        assertTrue(builder.getIR().contains("frem contract double 1.0, 2.0"));
    }

    @Test
    void testFloatSubtractionIsSupported() {
        final IRBuilder builder = new IRBuilder();
        final LLVMTextUnit llvmTextUnit = new BinaryExprAST("-", new Literal(ZType.FLT64, "3.0"), new Literal(ZType.FLT64, "2.0")).codeGen(builder);

        assertNotNull(llvmTextUnit);
        assertTrue(builder.getIR().contains("fsub contract double 3.0, 2.0"));
    }

    @Test
    void testFloatFastMathFlag() {
        final IRBuilder builder = new IRBuilder();
        builder.setFastMathFlags("fast");
        final LLVMTextUnit llvmTextUnit = new BinaryExprAST("+", new Literal(ZType.FLT64, "1.0"), new Literal(ZType.FLT64, "2.0")).codeGen(builder);

        assertNotNull(llvmTextUnit);
        assertTrue(builder.getIR().contains("fadd fast double 1.0, 2.0"));
    }

    @Test
    void testUnknownStructParameterTypeIsRejected() {
        final IRBuilder builder = new IRBuilder();
        builder.declare(new PrototypeAST("take", List.of(new PrototypeAST.Param("Missing", "p", false)), ZType.INT32));

        assertThrows(IllegalArgumentException.class, () -> new CallExprAST("take", List.of(new NumberExprAST("1", 1))).codeGen(builder));
    }

    @Test
    void testTypedVariadicCalls() {
        final IRBuilder builder = new IRBuilder();
        final PrototypeAST sum = new PrototypeAST("sum", List.of(new PrototypeAST.Param("int32", "count", false), new PrototypeAST.Param("int32", "rest", true)));
        builder.declare(sum);

        final LLVMTextUnit llvmTextUnit = new CallExprAST("sum", List.of(new NumberExprAST("1", 1), new NumberExprAST("2", 2))).codeGen(builder);
        assertNotNull(llvmTextUnit);
        assertTrue(builder.getIR().contains("call double (i32, ...) @sum(i32 1, i32 2)"), builder.getIR());
        assertTrue(builder.getIR().contains("(i32, ...)"));
    }

    @Test
    void testVariadicCallWithSeveralFixedParameters() {
        final IRBuilder builder = new IRBuilder();
        final PrototypeAST sum = new PrototypeAST("sum", List.of(new PrototypeAST.Param("int32", "a", false), new PrototypeAST.Param("int32", "b", false), new PrototypeAST.Param("int32", "rest", true)));
        builder.declare(sum);

        final LLVMTextUnit llvmTextUnit = new CallExprAST("sum", List.of(new NumberExprAST("1", 1), new NumberExprAST("2", 2), new NumberExprAST("3", 3))).codeGen(builder);

        assertNotNull(llvmTextUnit);
        assertTrue(builder.getIR().contains("(i32, i32, ...)"));
    }

    @Test
    void testCallResolvesAStructParameterType() {
        final StructType point = new StructType("Point", List.of(new StructType.Field("x", ZType.INT32)));
        StructRegistry.register(point);
        final IRBuilder builder = new IRBuilder();
        builder.declare(new PrototypeAST("take", List.of(new PrototypeAST.Param("Point", "p", false)), ZType.INT32));

        final LLVMTextUnit llvmTextUnit = new CallExprAST("take", List.of(new Literal(point, "zeroinitializer"))).codeGen(builder);

        assertNotNull(llvmTextUnit);
        assertSame(ZType.INT32, llvmTextUnit.type());
        assertTrue(builder.getIR().contains("%Point zeroinitializer"));
    }

    @Test
    void testMissingVariableFieldAccessFallsBackToInt32() {
        assertSame(ZType.INT32, new src.AST.FieldAccessExprAST(new VarRefExprAST("ghost"), "x").type(new IRBuilder()));
    }

    @Test
    void testIfTypeAndElseFailure() {
        final IfAST conditional = new IfAST(List.of(new IfAST.Branch(new NumberExprAST("1", 1), new NumberExprAST("2", 2))), new VarRefExprAST("ghost"));

        assertSame(ZType.VOID, conditional.type(new IRBuilder()));
        final IRBuilder builder = new IRBuilder();
        assertNull(conditional.codeGen(builder));
        assertTrue(builder.hasFailed());
    }

    @Test
    void testAssignmentStopsWhenTheValueFails() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("x", new SymbolTable.SymbolInfo("x", ZType.INT32, "%x", false));

        assertNull(new AssignExprAST("x", new VarRefExprAST("ghost")).codeGen(builder));
        assertTrue(builder.hasFailed());
    }

    @Test
    void testUnaryConstantWithAnUnavailableOperand() {
        assertNull(new UnaryExprAST(new VarRefExprAST("ghost")).constantValue(new IRBuilder()));
    }

    @Test
    void testMainWithAnUnsupportedReturnTypeFails() {
        final FunctionAST function = new FunctionAST(new PrototypeAST("main", List.of(), ZType.VARARG), new BlockAST(List.of()));
        final IRBuilder builder = new IRBuilder();

        assertNull(function.codeGen(builder));
        assertTrue(builder.hasFailed());
        assertFalse(builder.isTerminated());
    }
}

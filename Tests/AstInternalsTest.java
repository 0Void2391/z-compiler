package src.AST;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import src.Codegen.IRBuilder;
import src.Parser.StructRegistry;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AstInternalsTest {

    private static final class Literal extends ExprAST {
        private final ZType type;
        private final String text;

        private Literal(final ZType type, final String text) {
            this.type = type;
            this.text = text;
        }

        @Override
        public ZType type(final IRBuilder builder) {
            return type;
        }

        @Override
        public Value codeGen(final IRBuilder builder) {
            return new Value(text, type);
        }

        @Override
        public Value constantValue(final IRBuilder builder) {
            return codeGen(builder);
        }
    }

    @AfterEach
    void clean() {
        StructRegistry.clear();
    }

    @Test
    void testComparisonDetection() {
        for (final String op : List.of("<", "<=", ">", ">=", "==", "!=")) {
            assertTrue(new BinaryExprAST(op, new Literal(ZType.INT32, "1"), new Literal(ZType.INT32, "2")).isComparison());
        }
        assertFalse(new BinaryExprAST("+", new Literal(ZType.INT32, "1"), new Literal(ZType.INT32, "2")).isComparison());
    }

    @Test
    void testFloatPredicates() {
        assertEquals("olt", new BinaryExprAST("<", new Literal(ZType.FLT64, "1"), new Literal(ZType.FLT64, "2")).predicate(true, ZType.FLT64));
        assertEquals("ole", new BinaryExprAST("<=", new Literal(ZType.FLT64, "1"), new Literal(ZType.FLT64, "2")).predicate(true, ZType.FLT64));
        assertEquals("ogt", new BinaryExprAST(">", new Literal(ZType.FLT64, "1"), new Literal(ZType.FLT64, "2")).predicate(true, ZType.FLT64));
        assertEquals("oge", new BinaryExprAST(">=", new Literal(ZType.FLT64, "1"), new Literal(ZType.FLT64, "2")).predicate(true, ZType.FLT64));
        assertEquals("oeq", new BinaryExprAST("==", new Literal(ZType.FLT64, "1"), new Literal(ZType.FLT64, "2")).predicate(true, ZType.FLT64));
        assertEquals("une", new BinaryExprAST("!=", new Literal(ZType.FLT64, "1"), new Literal(ZType.FLT64, "2")).predicate(true, ZType.FLT64));
    }

    @Test
    void testUnsignedPredicates() {
        assertEquals("ult", new BinaryExprAST("<", new Literal(ZType.UINT32, "1"), new Literal(ZType.UINT32, "2")).predicate(false, ZType.UINT32));
        assertEquals("ule", new BinaryExprAST("<=", new Literal(ZType.UINT32, "1"), new Literal(ZType.UINT32, "2")).predicate(false, ZType.UINT32));
        assertEquals("ugt", new BinaryExprAST(">", new Literal(ZType.UINT32, "1"), new Literal(ZType.UINT32, "2")).predicate(false, ZType.UINT32));
        assertEquals("uge", new BinaryExprAST(">=", new Literal(ZType.UINT32, "1"), new Literal(ZType.UINT32, "2")).predicate(false, ZType.UINT32));
        assertEquals("eq", new BinaryExprAST("==", new Literal(ZType.UINT32, "1"), new Literal(ZType.UINT32, "2")).predicate(false, ZType.UINT32));
        assertEquals("ne", new BinaryExprAST("!=", new Literal(ZType.UINT32, "1"), new Literal(ZType.UINT32, "2")).predicate(false, ZType.UINT32));
    }

    @Test
    void testSignedPredicates() {
        assertEquals("slt", new BinaryExprAST("<", new Literal(ZType.INT32, "1"), new Literal(ZType.INT32, "2")).predicate(false, ZType.INT32));
        assertEquals("sle", new BinaryExprAST("<=", new Literal(ZType.INT32, "1"), new Literal(ZType.INT32, "2")).predicate(false, ZType.INT32));
        assertEquals("sgt", new BinaryExprAST(">", new Literal(ZType.INT32, "1"), new Literal(ZType.INT32, "2")).predicate(false, ZType.INT32));
        assertEquals("sge", new BinaryExprAST(">=", new Literal(ZType.INT32, "1"), new Literal(ZType.INT32, "2")).predicate(false, ZType.INT32));
    }

    @Test
    void testPredicateRejectsNonComparisons() {
        final BinaryExprAST addition = new BinaryExprAST("+", new Literal(ZType.INT32, "1"), new Literal(ZType.INT32, "2"));

        assertThrows(IllegalStateException.class, () -> addition.predicate(true, ZType.FLT64));
        assertThrows(IllegalStateException.class, () -> addition.predicate(false, ZType.INT32));
    }

    @Test
    void testConditionComparisonNeedsBothSides() {
        final IRBuilder builder = new IRBuilder();
        final BinaryExprAST leftMissing = new BinaryExprAST("<", new VarRefExprAST("ghost"), new Literal(ZType.INT32, "1"));
        final BinaryExprAST rightMissing = new BinaryExprAST("<", new Literal(ZType.INT32, "1"), new VarRefExprAST("ghost"));

        assertNull(Conditions.emitI1(builder, leftMissing));
        assertNull(Conditions.emitI1(builder, rightMissing));
    }

    @Test
    void testConditionRejectsMixedTruthValues() {
        final IRBuilder builder = new IRBuilder();
        final BinaryExprAST mixed = new BinaryExprAST("<", new BoolExprAST(true), new Literal(ZType.INT32, "1"));

        assertNull(Conditions.emitI1(builder, mixed));
    }

    @Test
    void testConditionRejectsStructs() {
        final StructType point = new StructType("Point", List.of());
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", point, "%p", false));
        final BinaryExprAST comparison = new BinaryExprAST("<", new VarRefExprAST("p"), new VarRefExprAST("p"));

        assertNull(Conditions.emitI1(builder, comparison));
    }

    @Test
    void testConditionEmitsIntegerAndFloatTests() {
        final IRBuilder builder = new IRBuilder();
        assertNotNull(Conditions.emitI1(builder, new Literal(ZType.INT32, "1")));
        assertNotNull(Conditions.emitI1(builder, new Literal(ZType.FLT64, "1.0")));
        assertNotNull(Conditions.emitI1(builder, new BinaryExprAST("<", new Literal(ZType.INT32, "1"), new Literal(ZType.INT32, "2"))));
    }

    @Test
    void testFieldPointerNeedsAVariableOrFieldBase() {
        final IRBuilder builder = new IRBuilder();
        final FieldAccessExprAST access = new FieldAccessExprAST(new Literal(ZType.INT32, "1"), "x");

        assertNull(access.emitFieldPointer(builder));
    }

    @Test
    void testFieldPointerNeedsAKnownStruct() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("x", new src.Codegen.SymbolTable.SymbolInfo("x", ZType.INT32, "%x", false));

        assertNull(new FieldAccessExprAST(new VarRefExprAST("x"), "field").emitFieldPointer(builder));
        assertNull(new FieldAccessExprAST(new VarRefExprAST("ghost"), "field").emitFieldPointer(builder));
    }

    @Test
    void testFieldPointerForANestedStruct() {
        final StructType inner = new StructType("Inner", List.of(new StructType.Field("v", ZType.INT32)));
        final StructType outer = new StructType("Outer", List.of(new StructType.Field("inner", inner)));
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("o", new src.Codegen.SymbolTable.SymbolInfo("o", outer, "%o", false));

        final FieldAccessExprAST access = new FieldAccessExprAST(
                new FieldAccessExprAST(new VarRefExprAST("o"), "inner"), "v");

        assertNotNull(access.emitFieldPointer(builder));
    }

    private static final class NullCoerce extends ExprAST {
        @Override
        public TypeRef type(final IRBuilder builder) {
            return ZType.INT32;
        }

        @Override
        public Value codeGen(final IRBuilder builder) {
            return new Value("1", ZType.INT32);
        }

        @Override
        public Value coerce(final IRBuilder builder, final Value value, final TypeRef target) {
            return null;
        }
    }

    @Test
    void testConditionStopsWhenACoercionFails() {
        final IRBuilder builder = new IRBuilder();
        final BinaryExprAST comparison = new BinaryExprAST("<", new NullCoerce(), new Literal(ZType.INT32, "2"));

        assertNull(Conditions.emitI1(builder, comparison));
    }

    @Test
    void testNestedFieldPointerFailures() {
        final IRBuilder nestedMissing = new IRBuilder();
        assertNull(new FieldAccessExprAST(new FieldAccessExprAST(new VarRefExprAST("ghost"), "x"), "y").codeGen(nestedMissing));

        final StructType point = new StructType("Point", List.of(new StructType.Field("x", ZType.INT32)));
        final IRBuilder notAStruct = new IRBuilder();
        notAStruct.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", point, "%p", false));
        assertNull(new FieldAccessExprAST(new FieldAccessExprAST(new VarRefExprAST("p"), "x"), "y").codeGen(notAStruct));
    }

    @Test
    void testNestedFieldTypesAndReads() {
        final StructType inner = new StructType("Inner", List.of(new StructType.Field("v", ZType.INT64)));
        final StructType outer = new StructType("Outer", List.of(new StructType.Field("inner", inner)));
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("o", new src.Codegen.SymbolTable.SymbolInfo("o", outer, "%o", false));

        assertSame(ZType.INT64, new FieldAccessExprAST(new FieldAccessExprAST(new VarRefExprAST("o"), "inner"), "v").type(builder));
        final Value value = new FieldAccessExprAST(new FieldAccessExprAST(new VarRefExprAST("o"), "inner"), "v").codeGen(builder);
        assertNotNull(value);
        assertSame(ZType.INT64, value.type());
    }
}

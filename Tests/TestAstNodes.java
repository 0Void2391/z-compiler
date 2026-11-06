package Tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import src.AST.*;
import src.Codegen.IRBuilder;
import src.Parser.StructRegistry;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestAstNodes {

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
    void testArrayAccessors() {
        final NumberExprAST size = new NumberExprAST("3", 3);
        final NumberExprAST depth = new NumberExprAST("1", 1);
        final ArrayAST array = new ArrayAST(ZType.INT32_PTR, size, depth, "values", List.of(new NumberExprAST("1", 1)));

        assertSame(ZType.INT32_PTR, array.type(null));
        assertSame(size, array.getSize());
        assertSame(depth, array.getDepth());
        assertEquals("values", array.getName());
        assertEquals(1, array.getValues().size());
        assertEquals("placeholder", array.codeGen(null).text());
        assertEquals(ZType.VOID, array.codeGen(null).type());
    }

    @Test
    void testArrayWithoutValuesKeepsAnEmptyList() {
        final ArrayAST array = new ArrayAST(ZType.INT32_PTR, new NumberExprAST("0", 0), new NumberExprAST("1", 1), "values", null);

        assertTrue(array.getValues().isEmpty());
    }

    @Test
    void testArrayIndexAccessors() {
        final VariableNode element = new VariableNode();
        final ArrayIdxExprAST index = new ArrayIdxExprAST(element, List.of(new NumberExprAST("1", 1)));

        assertSame(ZType.INT32_PTR, index.type(null));
        assertEquals(1, index.getIndexes().size());
        assertEquals("placeholder", index.codeGen(null).text());
    }

    @Test
    void testArrayIndexWithoutIndexesKeepsAnEmptyList() {
        final ArrayIdxExprAST index = new ArrayIdxExprAST(new VariableNode(), null);

        assertTrue(index.getIndexes().isEmpty());
    }

    @Test
    void testValueDistinguishesRegisters() {
        assertTrue(new LLVMTextUnit("%1", ZType.INT32).isRegister());
        assertFalse(new LLVMTextUnit("1", ZType.INT32).isRegister());
    }

    @Test
    void testParenDelegates() {
        final NumberExprAST number = new NumberExprAST("7", 7);
        final ParenExprAST paren = new ParenExprAST(number);
        final IRBuilder builder = new IRBuilder();

        assertSame(number, paren.inner());
        assertSame(ZType.INT32, paren.type(builder));
        assertEquals("7", paren.codeGen(builder).text());
        assertEquals("7", paren.constantValue(builder).text());
    }

    @Test
    void testStringLiteralAccessors() {
        final StringExprAST string = new StringExprAST("hello");
        final IRBuilder builder = new IRBuilder();

        assertEquals("hello", string.value());
        assertSame(ZType.STRING, string.type(builder));
        assertNotNull(string.codeGen(builder));
        assertEquals("@.str.1", string.constantValue(builder).text());
        assertEquals("@.str.0", string.constantValue(new IRBuilder()).text());
        assertEquals("hello", !builder.getGlobals().isEmpty() ? string.value() : "");
    }

    @Test
    void testNumberAccessors() {
        final NumberExprAST integer = new NumberExprAST("42", 42L);
        final NumberExprAST floating = new NumberExprAST("4.5", 4.5);

        assertFalse(integer.isFloating());
        assertTrue(floating.isFloating());
        assertEquals("42", integer.text());
        assertEquals("4.5", floating.text());
        assertSame(ZType.INT32, integer.type(null));
        assertSame(ZType.FLT64, floating.type(null));
    }

    @Test
    void testNumberCoercions() {
        final IRBuilder builder = new IRBuilder();
        final NumberExprAST integer = new NumberExprAST("42", 42L);
        final NumberExprAST floating = new NumberExprAST("4.5", 4.5);

        assertEquals(ZType.INT64.llvm(), integer.coerce(builder, new LLVMTextUnit("42", ZType.INT32), ZType.INT64).type().llvm());
        assertEquals(ZType.FLT32.llvm(), integer.coerce(builder, new LLVMTextUnit("42", ZType.INT32), ZType.FLT32).type().llvm());
        assertEquals(ZType.INT32.llvm(), floating.coerce(builder, new LLVMTextUnit("4.5", ZType.FLT64), ZType.INT32).type().llvm());
        assertEquals(ZType.FLT32.llvm(), floating.coerce(builder, new LLVMTextUnit("4.5", ZType.FLT64), ZType.FLT32).type().llvm());
        assertSame(ZType.INT32, integer.coerce(builder, new LLVMTextUnit("42", ZType.INT32), ZType.INT32).type());
    }

    @Test
    void testBoolLiteral() {
        final BoolExprAST bool = new BoolExprAST(true);
        final IRBuilder builder = new IRBuilder();

        assertSame(ZType.BOOL, bool.type(builder));
        assertEquals("true", bool.codeGen(builder).text());
        assertEquals("true", bool.constantValue(builder).text());
    }

    @Test
    void testUnaryAccessorsAndConstants() {
        final UnaryExprAST integer = new UnaryExprAST(new NumberExprAST("5", 5L));
        final UnaryExprAST floating = new UnaryExprAST(new NumberExprAST("1.5", 1.5));

        assertInstanceOf(NumberExprAST.class, integer.operand());
        assertSame(ZType.INT32, integer.type(new IRBuilder()));
        assertEquals("-5", Objects.requireNonNull(integer.constantValue(new IRBuilder())).text());
        assertEquals("-1.5", Objects.requireNonNull(floating.constantValue(new IRBuilder())).text());
        assertNull(new UnaryExprAST(new BoolExprAST(true)).constantValue(new IRBuilder()));
        assertNull(new UnaryExprAST(new Literal(ZType.VOID, "void")).constantValue(new IRBuilder()));
    }

    @Test
    void testUnaryCodeGen() {
        final IRBuilder builder = new IRBuilder();

        assertNotNull(new UnaryExprAST(new NumberExprAST("5", 5L)).codeGen(builder));
        assertNull(new UnaryExprAST(new VarRefExprAST("ghost")).codeGen(new IRBuilder()));
    }

    @Test
    void testAddressOfAccessors() {
        final VarRefExprAST variable = new VarRefExprAST("x");
        final AddressOfExprAST address = new AddressOfExprAST(variable);
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("x", new src.Codegen.SymbolTable.SymbolInfo("x", ZType.INT32, "%x", false));

        assertSame(variable, address.operand());
        assertSame(ZType.INT32_PTR, address.type(builder));
        assertEquals("%x", Objects.requireNonNull(address.codeGen(builder)).text());
    }

    @Test
    void testAddressOfNeedsAVariable() {
        final IRBuilder builder = new IRBuilder();

        assertNull(new AddressOfExprAST(new NumberExprAST("1", 1)).codeGen(builder));
        assertNull(new AddressOfExprAST(new VarRefExprAST("ghost")).codeGen(new IRBuilder()));
    }

    @Test
    void testDerefAccessors() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", ZType.INT32_PTR, "%p", false));
        final VarRefExprAST pointer = new VarRefExprAST("p");
        final DerefExprAST deref = new DerefExprAST(pointer);

        assertSame(pointer, deref.pointer());
        assertSame(ZType.INT32, deref.elementType(builder));
        assertSame(ZType.INT32, deref.type(builder));
    }

    @Test
    void testDerefOfAnUntypedPointer() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", ZType.PTR, "%p", false));

        assertSame(ZType.PTR, new DerefExprAST(new VarRefExprAST("p")).type(builder));
    }

    @Test
    void testDerefCodeGenFailures() {
        assertNull(new DerefExprAST(new VarRefExprAST("ghost")).codeGen(new IRBuilder()));

        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", ZType.PTR, "%p", false));
        assertNull(new DerefExprAST(new VarRefExprAST("p")).codeGen(builder));
    }

    @Test
    void testAssignThroughPointerAccessors() {
        final DerefExprAST target = new DerefExprAST(new VarRefExprAST("p"));
        final NumberExprAST value = new NumberExprAST("1", 1);
        final AssignThroughPointerExprAST assign = new AssignThroughPointerExprAST(target, value);

        assertSame(ZType.VOID, assign.type(new IRBuilder()));
        final IRBuilder builder = typedPointerBuilder();
        assertNull(assign.codeGen(builder));
        assertFalse(builder.hasFailed());
    }

    @Test
    void testAssignThroughPointerFailures() {
        assertNull(new AssignThroughPointerExprAST(new DerefExprAST(new VarRefExprAST("ghost")), new NumberExprAST("1", 1)).codeGen(new IRBuilder()));

        final IRBuilder untyped = new IRBuilder();
        untyped.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", ZType.PTR, "%p", false));
        assertNull(new AssignThroughPointerExprAST(new DerefExprAST(new VarRefExprAST("p")), new NumberExprAST("1", 1)).codeGen(untyped));

        final IRBuilder typed = typedPointerBuilder();
        assertNull(new AssignThroughPointerExprAST(new DerefExprAST(new VarRefExprAST("p")), new VarRefExprAST("ghost")).codeGen(typed));

        final IRBuilder stringTarget = typedPointerBuilder();
        assertNull(new AssignThroughPointerExprAST(new DerefExprAST(new VarRefExprAST("p")), new StringExprAST("text")).codeGen(stringTarget));
    }

    private static IRBuilder typedPointerBuilder() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", ZType.INT32_PTR, "%p", false));
        return builder;
    }

    @Test
    void testBlockAccessors() {
        final BlockAST block = new BlockAST(List.of(new NumberExprAST("1", 1)));

        assertEquals(1, block.statements().size());
        assertSame(ZType.VOID, block.type(new IRBuilder()));
    }

    @Test
    void testBlockRunsEveryStatement() {
        final IRBuilder builder = new IRBuilder();
        final BlockAST block = new BlockAST(List.of(new NumberExprAST("1", 1), new NumberExprAST("2", 2)));

        assertNull(block.codeGen(builder));
        assertFalse(builder.hasFailed());
    }

    @Test
    void testBlockRejectsUnreachableStatements() {
        final BlockAST block = new BlockAST(List.of(new RetAST(new NumberExprAST("1", 1)), new NumberExprAST("2", 2)));

        assertNull(block.codeGen(new IRBuilder()));
    }

    @Test
    void testBlockStopsAtAFailedStatement() {
        final BlockAST block = new BlockAST(List.of(new VarRefExprAST("ghost")));

        assertNull(block.codeGen(new IRBuilder()));
    }

    @Test
    void testPrototypeConstructors() {
        final PrototypeAST prototype = new PrototypeAST("main", List.of());

        assertNull(prototype.returnType());
        assertTrue(prototype.isMain());
        assertFalse(new PrototypeAST("other", List.of()).isMain());
        assertTrue(prototype.params().isEmpty());
    }

    @Test
    void testRetWithoutAValue() {
        final IRBuilder builder = new IRBuilder();

        assertSame(ZType.VOID, new RetAST(ZType.VOID).type(builder));
        assertNull(new RetAST((ExprAST) null).codeGen(builder));
        assertFalse(builder.isTerminated());
    }

    @Test
    void testRetOfAFailedExpression() {
        assertNull(new RetAST(new VarRefExprAST("ghost")).codeGen(new IRBuilder()));
    }

    @Test
    void testRetEmitsItsValue() {
        final IRBuilder builder = new IRBuilder();
        builder.setReturnType(ZType.INT32);

        assertNull(new RetAST(new NumberExprAST("1", 1)).codeGen(builder));
        assertTrue(builder.isTerminated());
    }

    @Test
    void testStructLiteralTypeAndAccessors() {
        final StructType point = new StructType("Point", List.of(new StructType.Field("x", ZType.INT32)));
        StructRegistry.register(point);
        final StructLiteralAST literal = new StructLiteralAST("Point", List.of(new NumberExprAST("1", 1)));

        assertSame(point, literal.type(new IRBuilder()));
        assertSame(ZType.PTR, new StructLiteralAST("Missing", List.of()).type(new IRBuilder()));
        assertNotNull(literal.codeGen(new IRBuilder()));
        assertNull(new StructLiteralAST("Missing", List.of()).codeGen(new IRBuilder()));
        assertNull(new StructLiteralAST("Point", List.of()).codeGen(new IRBuilder()));
        assertNull(new StructLiteralAST("Point", List.of(new VarRefExprAST("ghost"))).codeGen(new IRBuilder()));
        assertNotNull(literal.constantValue(new IRBuilder()));
        assertNull(new StructLiteralAST("Missing", List.of()).constantValue(new IRBuilder()));
        assertNull(new StructLiteralAST("Point", List.of(new VarRefExprAST("ghost"))).constantValue(new IRBuilder()));
    }

    @Test
    void testAssignAccessorsAndType() {
        final AssignExprAST assign = new AssignExprAST("x", new NumberExprAST("1", 1));
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("x", new src.Codegen.SymbolTable.SymbolInfo("x", ZType.INT32, "%x", false));

        assertEquals("x", assign.varName());
        assertInstanceOf(NumberExprAST.class, assign.expr());
        assertSame(ZType.INT32, assign.type(builder));
        assertSame(ZType.VOID, assign.type(new IRBuilder()));
    }

    @Test
    void testAssignTypeOfAStructIsVoid() {
        final StructType point = new StructType("Point", List.of());
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", point, "%p", false));

        assertSame(ZType.VOID, new AssignExprAST("p", new NumberExprAST("1", 1)).type(builder));
    }

    @Test
    void testAssignCodeGen() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("x", new src.Codegen.SymbolTable.SymbolInfo("x", ZType.INT32, "%x", false));

        assertNotNull(new AssignExprAST("x", new NumberExprAST("1", 1)).codeGen(builder));
        assertNull(new AssignExprAST("ghost", new NumberExprAST("1", 1)).codeGen(new IRBuilder()));

        final IRBuilder constant = new IRBuilder();
        constant.symbols().define("x", new src.Codegen.SymbolTable.SymbolInfo("x", ZType.INT32, "%x", true));
        assertNull(new AssignExprAST("x", new NumberExprAST("1", 1)).codeGen(constant));

        assertNull(new AssignExprAST("x", new VarRefExprAST("ghost")).codeGen(new IRBuilder()));

        final StructType point = new StructType("Point", List.of());
        final IRBuilder struct = new IRBuilder();
        struct.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", point, "%p", false));
        assertNull(new AssignExprAST("p", new NumberExprAST("1", 1)).codeGen(struct));

        final IRBuilder string = new IRBuilder();
        string.symbols().define("s", new src.Codegen.SymbolTable.SymbolInfo("s", ZType.STRING, "%s", false));
        assertNull(new AssignExprAST("s", new NumberExprAST("1", 1)).codeGen(string));
    }

    @Test
    void testBinaryAccessorsAndType() {
        final NumberExprAST left = new NumberExprAST("1", 1);
        final NumberExprAST right = new NumberExprAST("2", 2);
        final BinaryExprAST binary = new BinaryExprAST("+", left, right);

        assertEquals('+', binary.operator());
        assertSame(left, binary.left());
        assertSame(right, binary.right());
        assertSame(ZType.INT32, binary.type(new IRBuilder()));
    }

    @Test
    void testBinaryTypeWithStructsIsInt32() {
        final StructType point = new StructType("Point", List.of());
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", point, "%p", false));
        final BinaryExprAST binary = new BinaryExprAST("+", new VarRefExprAST("p"), new NumberExprAST("1", 1));

        assertSame(ZType.INT32, binary.type(builder));
    }

    @Test
    void testBinaryTypeCatchesUnificationErrors() {
        final BinaryExprAST binary = new BinaryExprAST("+", new BoolExprAST(true), new NumberExprAST("1", 1));

        assertSame(ZType.BOOL, binary.type(new IRBuilder()));
    }

    @Test
    void testBinaryCodeGenFailures() {
        assertNull(new BinaryExprAST("+", new VarRefExprAST("ghost"), new NumberExprAST("1", 1)).codeGen(new IRBuilder()));
        assertNull(new BinaryExprAST("+", new NumberExprAST("1", 1), new VarRefExprAST("ghost")).codeGen(new IRBuilder()));

        final IRBuilder pointer = new IRBuilder();
        pointer.symbols().define("p", new src.Codegen.SymbolTable.SymbolInfo("p", ZType.PTR, "%p", false));
        assertNull(new BinaryExprAST("+", new VarRefExprAST("p"), new NumberExprAST("1", 1)).codeGen(pointer));

        assertNull(new BinaryExprAST("&", new NumberExprAST("1", 1), new NumberExprAST("2", 2)).codeGen(new IRBuilder()));
        assertNull(new BinaryExprAST("&", new NumberExprAST("1.5", 1.5), new NumberExprAST("2.5", 2.5)).codeGen(new IRBuilder()));
    }

    @Test
    void testBinaryComparisons() {
        assertNotNull(new BinaryExprAST("<", new Literal(ZType.FLT64, "1.0"), new Literal(ZType.FLT64, "2.0")).codeGen(new IRBuilder()));
        assertNotNull(new BinaryExprAST("<=", new Literal(ZType.UINT32, "1"), new Literal(ZType.UINT32, "2")).codeGen(new IRBuilder()));
        assertNotNull(new BinaryExprAST("==", new NumberExprAST("1", 1), new NumberExprAST("1", 1)).codeGen(new IRBuilder()));
    }

    @Test
    void testLiteralNodesKeepTheirRequestedType() {
        final Literal node = new Literal(ZType.INT64, "9");

        assertSame(ZType.INT64, node.type(null));
        assertEquals("9", node.codeGen(null).text());
        assertEquals("9", node.constantValue(null).text());
    }

    private static final class VariableNode extends ExprAST {
        @Override
        public TypeRef type(final IRBuilder builder) {
            return ZType.INT32_PTR;
        }

        @Override
        public LLVMTextUnit codeGen(final IRBuilder builder) {
            return new LLVMTextUnit("placeholder", ZType.VOID);
        }
    }
}

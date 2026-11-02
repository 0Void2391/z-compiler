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
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class TestAstCodeGen {

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
        public Value codeGen(final IRBuilder builder) {
            return new Value(text, type);
        }

        @Override
        public Value constantValue(final IRBuilder builder) {
            return codeGen(builder);
        }
    }

    private static final class ImpureConstant extends ExprAST {
        @Override
        public TypeRef type(final IRBuilder builder) {
            return ZType.INT32;
        }

        @Override
        public Value codeGen(final IRBuilder builder) {
            return constantValue(builder);
        }

        @Override
        public Value constantValue(final IRBuilder builder) {
            builder.appendLine("%impure = add i32 1, 2");
            return new Value("3", ZType.INT32);
        }
    }

    @AfterEach
    void clean() {
        StructRegistry.clear();
    }

    @Test
    void testFieldAccessTypePaths() {
        final StructType point = new StructType("Point", List.of(new StructType.Field("x", ZType.INT64), new StructType.Field("inner", new StructType("Inner", List.of(new StructType.Field("v", ZType.BOOL))))));
        final IRBuilder builder = new IRBuilder();
        builder.symbols().defineGlobal("math.sqrt", new SymbolTable.SymbolInfo("sqrt", ZType.FLT32, "@sqrt", false));
        builder.symbols().define("p", new SymbolTable.SymbolInfo("p", point, "%p", false));

        assertSame(ZType.FLT32, new FieldAccessExprAST(new VarRefExprAST("math"), "sqrt").type(builder));
        assertSame(ZType.INT64, new FieldAccessExprAST(new VarRefExprAST("p"), "x").type(builder));
        assertSame(ZType.INT32, new FieldAccessExprAST(new VarRefExprAST("p"), "missing").type(builder));
        assertSame(ZType.BOOL, new FieldAccessExprAST(new FieldAccessExprAST(new VarRefExprAST("p"), "inner"), "v").type(builder));
        assertSame(ZType.INT32, new FieldAccessExprAST(new FieldAccessExprAST(new VarRefExprAST("p"), "inner"), "missing").type(builder));
        assertSame(ZType.INT32, new FieldAccessExprAST(new NumberExprAST("1", 1), "x").type(builder));
        assertSame(ZType.INT32, new FieldAccessExprAST(new FieldAccessExprAST(new NumberExprAST("1", 1), "x"), "y").type(builder));
    }

    @Test
    void testModuleVariableFieldAccess() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().defineGlobal("math.sqrt", new SymbolTable.SymbolInfo("sqrt", ZType.FLT64, "@sqrt", false));

        final Value value = new FieldAccessExprAST(new VarRefExprAST("math"), "sqrt").codeGen(builder);

        assertNotNull(value);
        assertSame(ZType.FLT64, value.type());
        assertTrue(builder.getIR().contains("load double, ptr @sqrt"), builder.getIR());
    }

    @Test
    void testFieldAccessCodeGenFailsWithoutAStructBase() {
        assertNull(new FieldAccessExprAST(new NumberExprAST("1", 1), "x").codeGen(new IRBuilder()));
    }

    @Test
    void testExternAccessorsAndCodeGen() {
        final PrototypeAST prototype = new PrototypeAST("sqrt", List.of(new PrototypeAST.Param("flt64", "", false)));
        final ExternExprAST extern = new ExternExprAST(prototype, "flt64");
        final IRBuilder builder = new IRBuilder();

        assertSame(prototype, extern.getProto());
        assertEquals("flt64", extern.getType());
        assertSame(ZType.VOID, extern.type(builder));
        final Value declaration = extern.codeGen(builder);
        assertNotNull(declaration);
        assertTrue(declaration.text().contains("declare double @sqrt(double)"), declaration.text());
        assertTrue(Objects.requireNonNull(extern.codeGen(builder)).text().isEmpty());
    }

    @Test
    void testExternConflict() {
        final PrototypeAST prototype = new PrototypeAST("sqrt", List.of(new PrototypeAST.Param("flt64", "", false)));
        final IRBuilder builder = new IRBuilder();
        builder.declare(prototype);

        assertNull(new ExternExprAST(prototype, "flt64").codeGen(builder));
        assertTrue(builder.hasFailed());
    }

    @Test
    void testCallAccessorsAndTypes() {
        final NumberExprAST argument = new NumberExprAST("1", 1);
        final CallExprAST call = new CallExprAST("f", List.of(argument));

        assertEquals("f", call.callee());
        assertEquals(1, call.arguments().size());
        assertSame(ZType.FLT64, call.type(new IRBuilder()));

        final IRBuilder external = new IRBuilder();
        external.declareExtern(new PrototypeAST("printf", List.of(new PrototypeAST.Param("str", "", false), new PrototypeAST.Param("...", "", false))), ZType.INT32);
        assertSame(ZType.INT32, new CallExprAST("printf", List.of(new StringExprAST("x"))).type(external));

        final IRBuilder defined = new IRBuilder();
        defined.declare(new PrototypeAST("f", List.of(), ZType.INT64));
        assertSame(ZType.INT64, new CallExprAST("f", List.of()).type(defined));

        final IRBuilder main = new IRBuilder();
        main.declare(new PrototypeAST("main", List.of()));
        assertSame(ZType.INT32, new CallExprAST("main", List.of()).type(main));

        final IRBuilder floating = new IRBuilder();
        floating.declare(new PrototypeAST("f", List.of()));
        assertSame(ZType.FLT64, new CallExprAST("f", List.of()).type(floating));
    }

    @Test
    void testCallToAnUnknownFunctionFails() {
        assertNull(new CallExprAST("ghost", List.of()).codeGen(new IRBuilder()));
    }

    @Test
    void testVariadicCallConvertsArgumentTypes() {
        final IRBuilder builder = new IRBuilder();
        builder.declareExtern(new PrototypeAST("printf", List.of(new PrototypeAST.Param("str", "", false), new PrototypeAST.Param("...", "", false))), ZType.INT32);

        final Value result = new CallExprAST("printf", List.of(new Literal(ZType.STRING, "@format"), new Literal(ZType.FLT32, "1.5"), new Literal(ZType.BOOL, "true"))).codeGen(builder);

        assertNotNull(result);
        assertTrue(builder.getIR().contains("call i32 (i8*, ...) @printf"), builder.getIR());
    }

    @Test
    void testVariadicCallNeedsTheFixedArguments() {
        final IRBuilder builder = new IRBuilder();
        builder.declareExtern(new PrototypeAST("printf", List.of(new PrototypeAST.Param("str", "", false), new PrototypeAST.Param("...", "", false))), ZType.INT32);

        assertNull(new CallExprAST("printf", List.of()).codeGen(builder));
    }

    @Test
    void testVariadicCallWithoutFixedParameters() {
        final IRBuilder builder = new IRBuilder();
        builder.declareExtern(new PrototypeAST("log", List.of(new PrototypeAST.Param("...", "", false))), ZType.INT32);

        final Value result = new CallExprAST("log", List.of(new NumberExprAST("1", 1))).codeGen(builder);

        assertNotNull(result);
        assertTrue(builder.getIR().contains("call i32 (...) @log"), builder.getIR());
    }

    @Test
    void testStructCannotBePassedToAnUntypedVararg() {
        final StructType point = new StructType("Point", List.of());
        final IRBuilder builder = new IRBuilder();
        builder.declareExtern(new PrototypeAST("printf", List.of(new PrototypeAST.Param("str", "", false), new PrototypeAST.Param("...", "", false))), ZType.INT32);

        assertNull(new CallExprAST("printf", List.of(new Literal(ZType.STRING, "@format"), new Literal(point, "point"))).codeGen(builder));
    }

    @Test
    void testCallToMainUsesInt32() {
        final IRBuilder builder = new IRBuilder();
        builder.declare(new PrototypeAST("main", List.of()));

        final Value result = new CallExprAST("main", List.of()).codeGen(builder);

        assertNotNull(result);
        assertSame(ZType.INT32, result.type());
    }

    @Test
    void testVoidCallProducesNoValue() {
        final IRBuilder builder = new IRBuilder();
        builder.declare(new PrototypeAST("f", List.of(), ZType.VOID));

        assertNull(new CallExprAST("f", List.of()).codeGen(builder));
        assertTrue(builder.getIR().contains("call void @f()"), builder.getIR());
    }

    @Test
    void testFunctionAccessorsAndReturnType() {
        final PrototypeAST main = new PrototypeAST("main", List.of());
        final FunctionAST function = new FunctionAST(main, new RetAST(ZType.VOID));

        assertSame(main, function.prototype());
        assertSame(ZType.INT32, function.returnType());
        assertSame(ZType.VOID, function.type(new IRBuilder()));
        assertSame(ZType.FLT64, new FunctionAST(new PrototypeAST("f", List.of()), new RetAST(ZType.VOID)).returnType());
        assertSame(ZType.INT64, new FunctionAST(new PrototypeAST("f", List.of(), ZType.INT64), new RetAST(ZType.VOID)).returnType());
    }

    @Test
    void testMainFallOffEmitsEachDefault() {
        assertTrue(generate(new PrototypeAST("main", List.of(), ZType.BOOL), null).contains("ret i1 false"));
        assertTrue(generate(new PrototypeAST("main", List.of(), ZType.STRING), null).contains("ret i8* null"));
        assertTrue(generate(new PrototypeAST("main", List.of(), ZType.INT32_PTR), null).contains("ret ptr null"));
        assertTrue(generate(new PrototypeAST("main", List.of(), ZType.FLT32), null).contains("ret float 0.0"));
        assertTrue(generate(new PrototypeAST("main", List.of(), ZType.FLT64), null).contains("ret double 0.0"));
        assertTrue(generate(new PrototypeAST("main", List.of(), ZType.INT32), null).contains("ret i32 0"));
        assertTrue(generate(new PrototypeAST("main", List.of(), ZType.VOID), null).contains("ret void"));
    }

    private static String generate(final PrototypeAST prototype, final ExprAST body) {
        final Value value = new FunctionAST(prototype, body).codeGen(new IRBuilder());
        assertNotNull(value);
        return value.text();
    }

    @Test
    void testNonVoidFunctionFallingOffTheEndFails() {
        assertNull(new FunctionAST(new PrototypeAST("f", List.of(), ZType.INT32), null).codeGen(new IRBuilder()));
    }

    @Test
    void testFunctionWithBodyThatFails() {
        assertNull(new FunctionAST(new PrototypeAST("main", List.of()), new VarRefExprAST("ghost")).codeGen(new IRBuilder()));
    }

    @Test
    void testDuplicateParametersFail() {
        final PrototypeAST prototype = new PrototypeAST("f", List.of(new PrototypeAST.Param("int32", "x", false), new PrototypeAST.Param("int32", "x", false)), ZType.VOID);

        assertNull(new FunctionAST(prototype, null).codeGen(new IRBuilder()));
    }

    @Test
    void testUnknownParameterTypeFails() {
        final PrototypeAST prototype = new PrototypeAST("f", List.of(new PrototypeAST.Param("Nope", "x", false)), ZType.VOID);

        assertThrows(IllegalArgumentException.class, () -> new FunctionAST(prototype, null).codeGen(new IRBuilder()));
    }

    @Test
    void testVariadicFunctionSignature() {
        final PrototypeAST prototype = new PrototypeAST("sum", List.of(new PrototypeAST.Param("int32", "count", false), new PrototypeAST.Param("int32", "rest", true)), ZType.VOID);

        final String text = generate(prototype, null);

        assertTrue(text.contains("@sum(i32 %count, ...)"), text);
        assertTrue(text.contains("llvm.va_start"), text);
    }

    @Test
    void testForAccessors() {
        final NumberExprAST init = new NumberExprAST("0", 0);
        final NumberExprAST cond = new NumberExprAST("1", 1);
        final NumberExprAST update = new NumberExprAST("1", 1);
        final BlockAST body = new BlockAST(List.of());
        final ForAST loop = new ForAST(ZType.INT32, "i", init, cond, update, body);

        assertSame(ZType.INT32, loop.varType());
        assertEquals("i", loop.varName());
        assertSame(init, loop.init());
        assertSame(cond, loop.cond());
        assertSame(update, loop.update());
        assertSame(body, loop.body());
        assertSame(ZType.VOID, loop.type(new IRBuilder()));
    }

    @Test
    void testForWithoutAnInitialiser() {
        final ForAST loop = new ForAST(ZType.INT32, "i", null, new NumberExprAST("1", 1), new NumberExprAST("1", 1), new BlockAST(List.of()));
        final IRBuilder builder = new IRBuilder();

        assertNull(loop.codeGen(builder));
        assertFalse(builder.hasFailed());
    }

    @Test
    void testForRejectsAShadowedVariable() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("i", new SymbolTable.SymbolInfo("i", ZType.INT32, "%i", false));
        final ForAST loop = new ForAST(ZType.INT32, "i", new NumberExprAST("0", 0), new NumberExprAST("1", 1), new NumberExprAST("1", 1), new BlockAST(List.of()));

        assertNull(loop.codeGen(builder));
        assertTrue(builder.hasFailed());
    }

    @Test
    void testForFailuresInEachPart() {
        assertNull(new ForAST(ZType.INT32, "i", new VarRefExprAST("ghost"), new NumberExprAST("1", 1), new NumberExprAST("1", 1), new BlockAST(List.of())).codeGen(new IRBuilder()));
        assertNull(new ForAST(ZType.INT32, "i", new StringExprAST("text"), new NumberExprAST("1", 1), new NumberExprAST("1", 1), new BlockAST(List.of())).codeGen(new IRBuilder()));
        assertNull(new ForAST(ZType.INT32, "i", new NumberExprAST("0", 0), new VarRefExprAST("ghost"), new NumberExprAST("1", 1), new BlockAST(List.of())).codeGen(new IRBuilder()));
        assertNull(new ForAST(ZType.INT32, "i", new NumberExprAST("0", 0), new NumberExprAST("1", 1), new VarRefExprAST("ghost"), new BlockAST(List.of())).codeGen(new IRBuilder()));
        assertNull(new ForAST(ZType.INT32, "i", new NumberExprAST("0", 0), new NumberExprAST("1", 1), new NumberExprAST("1", 1), new VarRefExprAST("ghost")).codeGen(new IRBuilder()));
    }

    @Test
    void testForBodyThatReturnsSkipsTheUpdate() {
        final ForAST loop = new ForAST(ZType.INT32, "i", new NumberExprAST("0", 0), new NumberExprAST("1", 1), new NumberExprAST("1", 1), new RetAST(new NumberExprAST("1", 1)));
        final IRBuilder builder = new IRBuilder();
        builder.setReturnType(ZType.INT32);

        assertNull(loop.codeGen(builder));
        assertFalse(builder.hasFailed());
    }

    @Test
    void testDeclaratorAccessors() {
        final DeclaratorExprAST local = new DeclaratorExprAST(ZType.INT32, "x", new NumberExprAST("1", 1), false, false);

        assertSame(ZType.INT32, local.declaredType());
        assertEquals("x", local.name());
        assertSame(ZType.INT32, local.type(new IRBuilder()));
    }

    @Test
    void testLocalDeclarations() {
        final IRBuilder builder = new IRBuilder();
        assertNull(new DeclaratorExprAST(ZType.INT32, "x", null, false, false).codeGen(builder));
        assertTrue(builder.getIR().contains("alloca i32"), builder.getIR());

        assertNull(new DeclaratorExprAST(ZType.INT32, "y", new NumberExprAST("1", 1), false, false).codeGen(new IRBuilder()));
        assertNull(new DeclaratorExprAST(ZType.INT32, "z", new VarRefExprAST("ghost"), false, false).codeGen(new IRBuilder()));
        assertNull(new DeclaratorExprAST(ZType.STRING, "s", new NumberExprAST("1", 1), false, false).codeGen(new IRBuilder()));
    }

    @Test
    void testGlobalDeclarations() {
        final IRBuilder builder = new IRBuilder();
        assertNull(new DeclaratorExprAST(ZType.INT32, "g", new NumberExprAST("3", 3), false, true).codeGen(builder));
        assertTrue(builder.getGlobals().contains("@g = global i32 3"), builder.getGlobals());

        assertNull(new DeclaratorExprAST(ZType.INT32, "h", null, false, true).codeGen(new IRBuilder()));

        assertNull(new DeclaratorExprAST(ZType.INT32, "g", new NumberExprAST("3", 3), false, true).codeGen(builder));

        assertNull(new DeclaratorExprAST(ZType.INT32, "g", new VarRefExprAST("ghost"), false, true).codeGen(new IRBuilder()));

        assertNull(new DeclaratorExprAST(ZType.STRING, "s", new NumberExprAST("1", 1), false, true).codeGen(new IRBuilder()));

        assertNull(new DeclaratorExprAST(ZType.INT32, "impure", new ImpureConstant(), false, true).codeGen(new IRBuilder()));
    }

    @Test
    void testGlobalNamesAreQualifiedByTheModule() {
        final IRBuilder builder = new IRBuilder();
        builder.setCurrentModule("m");

        assertNull(new DeclaratorExprAST(ZType.INT32, "g", new NumberExprAST("3", 3), false, true).codeGen(builder));
        assertTrue(builder.getGlobals().contains("@m_g = global i32 3"), builder.getGlobals());
        assertNotNull(builder.symbols().lookup("m.g"));
    }
}

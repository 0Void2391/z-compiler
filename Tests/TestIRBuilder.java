package Tests;

import org.junit.jupiter.api.Test;
import src.AST.PrototypeAST;
import src.AST.Value;
import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestIRBuilder {

    private static PrototypeAST prototype(final String name, final PrototypeAST.Param... params) {
        return new PrototypeAST(name, List.of(params));
    }

    @Test
    void testNamesAndRegisterTracking() {
        final IRBuilder builder = new IRBuilder();

        builder.setCurrentModule("math");
        assertEquals("math", builder.currentModule());
        assertEquals("math.x", builder.qualify("x"));
        assertEquals("a.b", builder.qualify("a.b"));
        assertEquals("math_x", builder.irNameOf("x"));
        assertEquals("a_b", builder.irName("a.b"));

        final String reg = builder.emitLoad("i32", "%p");
        assertEquals("i32", builder.getRegisterType(reg));
        assertNull(builder.getRegisterType("%missing"));
        builder.setRegisterType("%x", "double");
        assertEquals("double", builder.getRegisterType("%x"));
    }

    @Test
    void testQualifyWithoutAModule() {
        final IRBuilder builder = new IRBuilder();

        assertNull(builder.currentModule());
        assertEquals("x", builder.qualify("x"));
        assertEquals("x", builder.irNameOf("x"));
    }

    @Test
    void testResolveVariants() {
        final IRBuilder builder = new IRBuilder();
        final PrototypeAST root = prototype("root");
        final PrototypeAST dotted = prototype("mod.fn");
        final PrototypeAST std = prototype("std.sqrt");
        final PrototypeAST external = prototype("cfunc", new PrototypeAST.Param("flt64", "", false));

        assertTrue(builder.declare(root));
        assertTrue(builder.declare(dotted));
        assertTrue(builder.declare(std));
        builder.declareExtern(external, ZType.FLT64);

        assertEquals("root", builder.resolve("root").irName());
        assertEquals("mod_fn", builder.resolve("mod.fn").irName());
        assertNull(builder.resolve("other.fn"));
        assertEquals("std_sqrt", builder.resolve("std.sqrt").irName());
        assertEquals("cfunc", builder.resolve("std.cfunc").irName());
        assertNull(builder.resolve("std.missing"));
        assertEquals("std_sqrt", builder.resolve("sqrt").irName());
        assertEquals("cfunc", builder.resolve("cfunc").irName());
        assertSame(ZType.FLT64, builder.resolve("cfunc").externReturnType());
    }

    @Test
    void testResolveInsideAModule() {
        final IRBuilder builder = new IRBuilder();
        final PrototypeAST external = prototype("cfunc", new PrototypeAST.Param("int32", "", false));
        final PrototypeAST own = prototype("m.own");

        builder.setCurrentModule("m");
        builder.declareExtern(external, ZType.INT32);
        assertTrue(builder.declare(own));

        assertEquals("cfunc", builder.resolve("cfunc").irName());
        assertEquals("m_own", builder.resolve("own").irName());
        assertNull(builder.resolve("missing"));
    }

    @Test
    void testResolveFallsBackWhenAModuleExternCannotBeUsed() {
        final IRBuilder builder = new IRBuilder();

        builder.setCurrentModule("m");
        assertNull(builder.resolve("cfunc"));
        assertEquals("m", builder.currentModule());
    }

    @Test
    void testDeclareRejectsDuplicatesAndConflicts() {
        final IRBuilder builder = new IRBuilder();
        final PrototypeAST first = prototype("f", new PrototypeAST.Param("int32", "", false));

        assertEquals(IRBuilder.ExternDeclaration.DECLARED, builder.declareExtern(first, ZType.INT32));
        assertEquals(IRBuilder.ExternDeclaration.ALREADY_DECLARED, builder.declareExtern(first, ZType.INT32));
        assertEquals(IRBuilder.ExternDeclaration.CONFLICT, builder.declareExtern(first, ZType.INT64));
        builder.declare(prototype("g"));
        assertFalse(builder.declare(prototype("g")));
        final IRBuilder plain = new IRBuilder();
        plain.declare(prototype("h"));
        assertEquals(IRBuilder.ExternDeclaration.CONFLICT, plain.declareExtern(prototype("h"), ZType.INT32));
    }

    @Test
    void testReturnConversionsAndErrors() {
        final IRBuilder builder = new IRBuilder();

        builder.setReturnType(ZType.VOID);
        builder.emitReturn(new Value("1", ZType.INT32));
        assertTrue(builder.isTerminated());
        assertTrue(builder.getIR().contains("ret void"));

        builder.reset();
        builder.setReturnType(ZType.INT32);
        builder.emitReturn(null);
        assertTrue(builder.getIR().contains("ret i32 281"));

        builder.reset();
        builder.setReturnType(ZType.INT32);
        builder.emitReturn(new Value("1", ZType.INT32_PTR));
        assertTrue(builder.hasFailed());
        assertFalse(builder.isTerminated());

        builder.reset();
        builder.setReturnType(ZType.INT32_PTR);
        builder.emitReturn(new Value("1", ZType.INT32));
        assertTrue(builder.hasFailed());

        builder.reset();
        builder.setReturnType(ZType.INT32);
        builder.emitReturn(new Value("1.5", ZType.FLT64));
        assertTrue(builder.getIR().contains("fptosi double 1.5 to i32"));

        builder.reset();
        builder.setReturnType(ZType.UINT32);
        builder.emitReturn(new Value("1.5", ZType.FLT64));
        assertTrue(builder.getIR().contains("fptoui double 1.5 to i32"));

        builder.reset();
        builder.setReturnType(ZType.FLT64);
        builder.emitReturn(new Value("1", ZType.UINT32));
        assertTrue(builder.getIR().contains("uitofp i32 1 to double"));

        builder.reset();
        builder.setReturnType(ZType.FLT32);
        builder.emitReturn(new Value("1.5", ZType.FLT64));
        assertTrue(builder.getIR().contains("fptrunc double 1.5 to float"));

        builder.reset();
        builder.setReturnType(ZType.FLT64);
        builder.emitReturn(new Value("1.5", ZType.FLT32));
        assertTrue(builder.getIR().contains("fpext float 1.5 to double"));

        builder.reset();
        builder.setReturnType(ZType.INT32);
        builder.emitReturn(new Value("1", ZType.INT64));
        assertTrue(builder.getIR().contains("trunc i64 1 to i32"));

        builder.reset();
        builder.setReturnType(ZType.INT64);
        builder.emitReturn(new Value("1", ZType.INT32));
        assertTrue(builder.getIR().contains("sext i32 1 to i64"));

        builder.reset();
        builder.setReturnType(ZType.STRING);
        builder.emitReturn(new Value("1", ZType.INT32));
        assertTrue(builder.hasFailed());
    }

    @Test
    void testConversionOpcodeRejectsSameWidthSignedness() throws Exception {
        final Method method = IRBuilder.class.getDeclaredMethod("conversionOpcode", TypeRef.class, TypeRef.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, ZType.INT32, ZType.UINT32));
        assertEquals("zext ", method.invoke(null, ZType.UINT32, ZType.INT64));
        assertEquals("sext ", method.invoke(null, ZType.INT32, ZType.INT64));
    }

    @Test
    void testBareReturnAndToString() {
        final IRBuilder builder = new IRBuilder();

        builder.emitBareReturn("i32 0");
        assertTrue(builder.getIR().contains("ret i32 0"));
        assertEquals(builder.getIR(), builder.toString());
    }

    @Test
    void testVarArgLifecycle() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("rest", new SymbolTable.SymbolInfo("rest", ZType.INT32, "", false, true));

        assertNull(builder.loadVarArg("rest", 1));
        assertNull(builder.loadVarArg("ghost", 1));
        builder.symbols().define("x", new SymbolTable.SymbolInfo("x", ZType.INT32, "%x", false));
        assertNull(builder.loadVarArg("x", 1));

        builder.beginVarArgFunction();
        builder.beginVarArgFunction();
        assertNull(builder.loadVarArg("rest", 0));
        assertNull(builder.loadVarArg("x", 1));
        final String value = builder.loadVarArg("rest", 2);
        assertNotNull(value);
        assertTrue(builder.getGlobals().contains("declare void @llvm.va_start(ptr)"));
        assertTrue(builder.getIR().contains("va_arg ptr"));

        builder.setReturnType(ZType.VOID);
        builder.emitReturn(null);
        assertTrue(builder.getIR().contains("llvm.va_end"));
    }

    @Test
    void testStringConstantEscaping() {
        final IRBuilder builder = new IRBuilder();
        final String name = builder.emitStringConstant(new byte[]{'"', '\\', '\n', 0x7F});

        assertEquals(".str.0", name);
        assertTrue(builder.getGlobals().contains("\\22"));
        assertTrue(builder.getGlobals().contains("\\5C"));
        assertTrue(builder.getGlobals().contains("\\0A"));
        assertTrue(builder.getGlobals().contains("\\7F"));
        // No malloc: concatenation uses the bump arena.
        assertFalse(builder.getGlobals().contains("declare ptr @malloc(i64)"));
        assertTrue(builder.getGlobals().contains("declare i64 @strlen(ptr)"));
        assertEquals(".str.1", builder.emitStringConstant("hi".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testStringDeclaresRespectAnExistingStrlenExtern() {
        final IRBuilder matching = new IRBuilder();
        matching.declareExtern(prototype("strlen", new PrototypeAST.Param("str", "", false)), ZType.UINT64);
        matching.emitStringConstant(new byte[]{'a'});
        assertFalse(matching.getGlobals().contains("declare i64 @strlen(ptr)"));

        final IRBuilder wrongReturn = new IRBuilder();
        wrongReturn.declareExtern(prototype("strlen", new PrototypeAST.Param("str", "", false)), ZType.INT32);
        wrongReturn.emitStringConstant(new byte[]{'a'});
        assertTrue(wrongReturn.getGlobals().contains("declare i64 @strlen(ptr)"));

        final IRBuilder wrongParams = new IRBuilder();
        wrongParams.declareExtern(prototype("strlen", new PrototypeAST.Param("int32", "", false)), ZType.UINT64);
        wrongParams.emitStringConstant(new byte[]{'a'});
        assertTrue(wrongParams.getGlobals().contains("declare i64 @strlen(ptr)"));
    }

    @Test
    void testGlobalsAndStructDeclaration() {
        final IRBuilder builder = new IRBuilder();
        final StructType point = new StructType("Point", List.of(
                new StructType.Field("x", ZType.INT32),
                new StructType.Field("y", ZType.FLT64)));

        builder.declareStruct(point);
        builder.declareStruct(point);
        assertEquals(1, builder.getGlobals().split("%Point = type", -1).length - 1);
        assertTrue(builder.getGlobals().contains("%Point = type { i32, double }"));
        assertEquals("%g", builder.emitAllocaGlobal("g", "i32"));
        assertTrue(builder.getIR().contains("%g = global i32"));
        builder.appendToGlobals("extra\n");
        assertTrue(builder.getGlobals().contains("extra"));
        assertEquals("%p", builder.emitAlloca("p", "i32"));
        builder.emitStore("1", "i32", "%p");
        assertTrue(builder.getIR().contains("store i32 1, ptr %p"));
        assertEquals("l.0", builder.nextLabel("l"));
        builder.emitLabel("l.0");
        builder.emitBr("l.1");
        builder.emitCondBr("%c", "l.2", "l.3");
        assertTrue(builder.getIR().contains("br i1 %c"));
    }

    @Test
    void testLoopAndResetHelpers() {
        final IRBuilder builder = new IRBuilder();

        builder.enterLoop("continue", "break");
        assertNotNull(builder.currentLoop());
        assertEquals("continue", builder.currentLoop().continueLabel());
        assertEquals("break", builder.currentLoop().breakLabel());
        assertSame(builder.currentLoop(), builder.exitLoop());
        assertNull(builder.currentLoop());

        final String reg = builder.nextRegister();
        builder.appendLine("x");
        builder.setRegisterType(reg, "i32");
        builder.reset();
        assertEquals("%1", builder.nextRegister());
        assertFalse(builder.isTerminated());
        assertNull(builder.getRegisterType(reg));
        assertEquals("", builder.getIR());
    }
}

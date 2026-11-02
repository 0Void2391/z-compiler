package Tests;

import org.junit.jupiter.api.Test;
import src.AST.Value;
import src.AST.VarRefExprAST;
import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.ZType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestVarRefExpr {

    @Test
    void testTypeFallsBackToTheCurrentModule() {
        final IRBuilder builder = new IRBuilder();
        builder.setCurrentModule("math");
        builder.symbols().defineGlobal("math.pi", new SymbolTable.SymbolInfo("pi", ZType.FLT64, "@pi", false));

        assertSame(ZType.FLT64, new VarRefExprAST("pi").type(builder));
        assertSame(ZType.INT32, new VarRefExprAST("missing").type(builder));
        assertSame(ZType.INT32, new VarRefExprAST(".x").type(builder));
    }

    @Test
    void testTypeOfAVariadicElement() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("rest", new SymbolTable.SymbolInfo("rest", ZType.INT64, "", false, true));

        assertSame(ZType.INT64, new VarRefExprAST("rest.1").type(builder));
        assertSame(ZType.INT32, new VarRefExprAST("other.1").type(new IRBuilder()));
    }

    @Test
    void testCodeGenReadsVariablesAndModuleGlobals() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("x", new SymbolTable.SymbolInfo("x", ZType.INT32, "%x", false));
        final Value local = new VarRefExprAST("x").codeGen(builder);
        assertNotNull(local);
        assertSame(ZType.INT32, local.type());
        assertTrue(builder.getIR().contains("load i32, ptr %x"));

        final IRBuilder module = new IRBuilder();
        module.setCurrentModule("math");
        module.symbols().defineGlobal("math.pi", new SymbolTable.SymbolInfo("pi", ZType.FLT64, "@pi", false));
        final Value global = new VarRefExprAST("pi").codeGen(module);
        assertNotNull(global);
        assertSame(ZType.FLT64, global.type());
    }

    @Test
    void testReadingAVariadicParameterWithoutAnIndexFails() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("rest", new SymbolTable.SymbolInfo("rest", ZType.INT32, "", false, true));

        assertNull(new VarRefExprAST("rest").codeGen(builder));
        assertTrue(builder.hasFailed());
    }

    @Test
    void testReadingAVariadicElement() {
        final IRBuilder builder = new IRBuilder();
        builder.symbols().define("rest", new SymbolTable.SymbolInfo("rest", ZType.INT32, "", false, true));
        builder.beginVarArgFunction();

        final Value value = new VarRefExprAST("rest.2").codeGen(builder);
        assertNotNull(value);
        assertSame(ZType.INT32, value.type());
        assertTrue(value.isRegister());
        assertTrue(builder.getIR().contains("va_arg"));
    }

    @Test
    void testVariadicElementFailures() {
        final IRBuilder invalidIndex = new IRBuilder();
        invalidIndex.symbols().define("rest", new SymbolTable.SymbolInfo("rest", ZType.INT32, "", false, true));
        invalidIndex.beginVarArgFunction();
        assertNull(new VarRefExprAST("rest.nope").codeGen(invalidIndex));
        assertTrue(invalidIndex.hasFailed());

        final IRBuilder noVarArg = new IRBuilder();
        noVarArg.symbols().define("rest", new SymbolTable.SymbolInfo("rest", ZType.INT32, "", false, true));
        assertNull(new VarRefExprAST("rest.1").codeGen(noVarArg));
        assertTrue(noVarArg.hasFailed());

        final IRBuilder notVariadic = new IRBuilder();
        notVariadic.symbols().define("rest", new SymbolTable.SymbolInfo("rest", ZType.INT32, "%r", false));
        assertNull(new VarRefExprAST("rest.1").codeGen(notVariadic));
        assertTrue(notVariadic.hasFailed());
    }

    @Test
    void testMissingVariablesAreReported() {
        final IRBuilder builder = new IRBuilder();

        assertNull(new VarRefExprAST("ghost").codeGen(builder));
        assertTrue(builder.hasFailed());
        assertEquals("ghost", new VarRefExprAST("ghost").name());
        assertFalse(new Value("1", ZType.INT32).isRegister());
    }
}

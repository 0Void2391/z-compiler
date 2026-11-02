package Tests;

import org.junit.jupiter.api.Test;
import src.Codegen.SymbolTable;
import src.Parser.ZType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSymbolTable {

    @Test
    void testSymbolInfoGettersAndCounters() {
        final SymbolTable.SymbolInfo info = new SymbolTable.SymbolInfo("x", ZType.INT32, "%x", true);

        assertEquals("x", info.getName());
        assertSame(ZType.INT32, info.getType());
        assertEquals("i32", info.getLlvmType());
        assertEquals("%x", info.getPointerReg());
        assertTrue(info.isConstant());
        assertFalse(info.isVarArg());
        assertFalse(info.isUsed());
        info.setUsed(true);
        assertTrue(info.isUsed());
        assertEquals(0, info.getCallCount());
        info.incrementCallCount();
        assertEquals(1, info.getCallCount());
    }

    @Test
    void testVarArgSymbolInfo() {
        final SymbolTable.SymbolInfo info = new SymbolTable.SymbolInfo("rest", ZType.FLT64, "", false, true);

        assertTrue(info.isVarArg());
        assertFalse(info.isConstant());
    }

    @Test
    void testScopesAndLookups() {
        final SymbolTable table = new SymbolTable();
        final SymbolTable.SymbolInfo global = new SymbolTable.SymbolInfo("g", ZType.INT32, "%g", false);

        assertEquals(1, table.getDepth());
        table.defineGlobal("g", global);
        assertSame(global, table.lookup("g"));
        assertFalse(global.isUsed());

        table.enterScope();
        assertEquals(2, table.getDepth());
        final SymbolTable.SymbolInfo local = new SymbolTable.SymbolInfo("x", ZType.INT64, "%x", false);
        assertTrue(table.define("x", local));
        assertFalse(table.define("x", local));
        assertSame(local, table.lookupCurrentScope("x"));
        assertSame(local, table.lookup("x"));
        assertSame(global, table.lookup("g"));
        assertNull(table.lookupCurrentScope("g"));
        assertNull(table.lookup("missing"));

        table.exitScope();
        assertEquals(1, table.getDepth());
        assertNull(table.lookupCurrentScope("x"));
        table.exitScope();
        assertEquals(1, table.getDepth());
    }
}

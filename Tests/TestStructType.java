package Tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import src.Parser.StructRegistry;
import src.Parser.StructType;
import src.Parser.ZType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestStructType {

    @AfterEach
    void clean() {
        StructRegistry.clear();
    }

    @Test
    void testBasics() {
        final StructType st = new StructType("Point", List.of(
                new StructType.Field("x", ZType.INT32),
                new StructType.Field("y", ZType.INT64)));

        assertEquals("Point", st.zName());
        assertEquals("%Point", st.llvm());
        assertEquals("zeroinitializer", st.initial());
        assertEquals(0, st.bits());
        assertTrue(st.isStruct());
        assertFalse(st.isIntegral());
        assertFalse(st.isFloat());
        assertFalse(st.isSigned());
        assertFalse(st.isUnsigned());
        assertFalse(st.isBoolean());
        assertFalse(st.isString());
        assertFalse(st.isVoid());
        assertFalse(st.isPointer());
        assertFalse(st.isVararg());
    }

    @Test
    void testIndexAndFieldType() {
        final StructType st = new StructType("Point", List.of(
                new StructType.Field("x", ZType.INT32),
                new StructType.Field("y", ZType.FLT64)));
        assertEquals(0, st.indexOf("x"));
        assertEquals(1, st.indexOf("y"));
        assertEquals(-1, st.indexOf("z"));
        assertSame(ZType.INT32, st.fieldType("x"));
        assertSame(ZType.FLT64, st.fieldType("y"));
        assertNull(st.fieldType("z"));
    }

    @Test
    void testPointerHelpers() {
        final StructType st = new StructType("Point", List.of());
        assertSame(ZType.PTR, st.pointerTo());
        assertNull(st.elementType());
    }

    @Test
    void testLiteralThrows() {
        final StructType st = new StructType("Point", List.of());
        assertThrows(IllegalStateException.class, () -> st.literal(1L));
        assertThrows(IllegalStateException.class, () -> st.literal(1.0));
    }

    @Test
    void testRegistry() {
        final StructType st = new StructType("Point", List.of());
        assertFalse(StructRegistry.isStruct("Point"));
        StructRegistry.register(st);
        assertTrue(StructRegistry.isStruct("Point"));
        assertSame(st, StructRegistry.lookup("Point"));
        assertEquals(1, StructRegistry.all().size());
        StructRegistry.clear();
        assertFalse(StructRegistry.isStruct("Point"));
        assertTrue(StructRegistry.all().isEmpty());
    }

    @Test
    void testStructDeclTypeIsVoid() {
        final StructType st = new StructType("Point", List.of());
        final src.AST.StructDeclAST decl = new src.AST.StructDeclAST(st);
        assertSame(ZType.VOID, decl.type(null));
    }

    @Test
    public void testStructWithMissingFieldNameFails() {
        final String diagnostics = ZFixtures.failureOf(
                "struct Point { int32 }");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("Expected field name"), diagnostics);
    }
}
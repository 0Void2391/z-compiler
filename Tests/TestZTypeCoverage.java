package Tests;

import org.junit.jupiter.api.Test;
import src.Parser.ZType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestZTypeCoverage {

    @Test
    void testUnifyHandlesMissingVoidAndEqualOperands() {
        assertSame(ZType.INT32, ZType.unify(null, ZType.INT32));
        assertSame(ZType.INT32, ZType.unify(ZType.INT32, null));
        assertSame(ZType.INT32, ZType.unify(ZType.INT32, ZType.INT32));
        assertSame(ZType.INT32, ZType.unify(ZType.VOID, ZType.INT32));
        assertSame(ZType.INT32, ZType.unify(ZType.INT32, ZType.VOID));
        assertSame(ZType.BOOL, ZType.unify(ZType.BOOL, ZType.BOOL));
    }

    @Test
    void testUnifyPicksTheWiderNumericType() {
        assertSame(ZType.FLT64, ZType.unify(ZType.FLT32, ZType.FLT64));
        assertSame(ZType.FLT64, ZType.unify(ZType.FLT64, ZType.FLT32));
        assertSame(ZType.FLT32, ZType.unify(ZType.FLT32, ZType.FLT32));
        assertSame(ZType.FLT64, ZType.unify(ZType.INT64, ZType.FLT32));
        assertSame(ZType.FLT64, ZType.unify(ZType.FLT64, ZType.INT32));
        assertSame(ZType.UINT32, ZType.unify(ZType.INT32, ZType.UINT32));
        assertSame(ZType.UINT64, ZType.unify(ZType.UINT64, ZType.INT32));
        assertSame(ZType.INT64, ZType.unify(ZType.INT64, ZType.INT32));
        assertSame(ZType.INT32, ZType.unify(ZType.INT32, ZType.INT32));
    }

    @Test
    void testUnifyRejectsMixedFamilies() {
        assertThrows(IllegalArgumentException.class, () -> ZType.unify(ZType.BOOL, ZType.INT32));
        assertThrows(IllegalArgumentException.class, () -> ZType.unify(ZType.INT32_PTR, ZType.INT32));
        assertThrows(IllegalArgumentException.class, () -> ZType.unify(ZType.STRING, ZType.INT32));
        assertThrows(IllegalArgumentException.class, () -> ZType.unify(ZType.INT32, ZType.STRING));
    }

    @Test
    void testIntegerLiteralsAcrossTypes() {
        assertEquals("true", ZType.BOOL.literal(3L));
        assertEquals("false", ZType.BOOL.literal(0L));
        assertEquals("7", ZType.INT32.literal(7L));
        assertEquals("7", ZType.UINT32.literal(7L));
        assertEquals("7", ZType.INT64.literal(7L));
        assertEquals("7", ZType.UINT64.literal(7L));
        assertEquals("7.0", ZType.FLT64.literal(7L));
    }

    @Test
    void testDoubleLiteralsAcrossTypes() {
        assertEquals("true", ZType.BOOL.literal(0.5));
        assertEquals("false", ZType.BOOL.literal(0.0));
        assertEquals("7", ZType.INT32.literal(7.9));
        assertEquals("7", ZType.UINT32.literal(7.9));
        assertEquals("7", ZType.INT64.literal(7.9));
        assertEquals("7", ZType.UINT64.literal(7.9));
        assertEquals("7.5", ZType.FLT64.literal(7.5));
        assertEquals("7.5", ZType.FLT32.literal(7.5));
    }

    @Test
    void testFlt32HexLiteralWhenDecimalDoesNotRoundTrip() {
        assertEquals("0x3FB99999A0000000", ZType.FLT32.literal(0.1));
    }

    @Test
    void testTypesWithoutLiteralsThrow() {
        for (final ZType type : new ZType[]{ZType.PTR, ZType.STRING, ZType.VOID}) {
            assertThrows(IllegalStateException.class, () -> type.literal(1L));
            assertThrows(IllegalStateException.class, () -> type.literal(1.0));
        }
        assertThrows(IllegalStateException.class, () -> ZType.VARARG.literal(1L));
        assertThrows(IllegalStateException.class, () -> ZType.VARARG.literal(1.0));
    }

    @Test
    void testPointerToEveryBuiltInType() {
        assertSame(ZType.INT32_PTR, ZType.INT32.pointerTo());
        assertSame(ZType.INT64_PTR, ZType.INT64.pointerTo());
        assertSame(ZType.UINT32_PTR, ZType.UINT32.pointerTo());
        assertSame(ZType.UINT64_PTR, ZType.UINT64.pointerTo());
        assertSame(ZType.FLT32_PTR, ZType.FLT32.pointerTo());
        assertSame(ZType.FLT64_PTR, ZType.FLT64.pointerTo());
        assertSame(ZType.BOOL_PTR, ZType.BOOL.pointerTo());
        assertSame(ZType.STR_PTR, ZType.STRING.pointerTo());
        assertSame(ZType.PTR, ZType.VOID.pointerTo());
        assertSame(ZType.PTR, ZType.PTR.pointerTo());
        assertSame(ZType.PTR, ZType.VARARG.pointerTo());
    }

    @Test
    void testElementTypeOfEveryPointer() {
        assertSame(ZType.INT32, ZType.INT32_PTR.elementType());
        assertSame(ZType.INT64, ZType.INT64_PTR.elementType());
        assertSame(ZType.UINT32, ZType.UINT32_PTR.elementType());
        assertSame(ZType.UINT64, ZType.UINT64_PTR.elementType());
        assertSame(ZType.FLT32, ZType.FLT32_PTR.elementType());
        assertSame(ZType.FLT64, ZType.FLT64_PTR.elementType());
        assertSame(ZType.BOOL, ZType.BOOL_PTR.elementType());
        assertSame(ZType.STRING, ZType.STR_PTR.elementType());
        assertNull(ZType.PTR.elementType());
        assertNull(ZType.INT32.elementType());
    }

    @Test
    void testTypePredicates() {
        assertTrue(ZType.INT32.isIntegral());
        assertTrue(ZType.UINT64.isIntegral());
        assertTrue(ZType.BOOL.isIntegral());
        assertFalse(ZType.FLT32.isIntegral());
        assertTrue(ZType.FLT32.isFloat());
        assertFalse(ZType.INT32.isFloat());
        assertTrue(ZType.INT32.isSigned());
        assertTrue(ZType.UINT32.isUnsigned());
        assertTrue(ZType.BOOL.isBoolean());
        assertTrue(ZType.STRING.isString());
        assertTrue(ZType.VOID.isVoid());
        assertTrue(ZType.INT32_PTR.isPointer());
        assertTrue(ZType.VARARG.isVararg());
        assertFalse(ZType.INT32.isStruct());
        assertEquals("int32", ZType.fromZName("int32").zName());
        assertTrue(ZType.isType("flt64"));
        assertFalse(ZType.isType("nope"));
        assertThrows(IllegalArgumentException.class, () -> ZType.fromZName("nope"));
        assertEquals("0", ZType.INT32.initial());
        assertEquals(32, ZType.INT32.bits());
    }
}

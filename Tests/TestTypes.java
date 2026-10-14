package Tests;

import org.junit.jupiter.api.Test;
import src.Parser.ZType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTypes {

    @Test
    public void testTypeNames() {
        assertSame(ZType.INT32, ZType.fromZName("int32"));
        assertSame(ZType.INT64, ZType.fromZName("int64"));
        assertSame(ZType.FLT32, ZType.fromZName("flt32"));
        assertSame(ZType.FLT64, ZType.fromZName("flt64"));
        assertSame(ZType.BOOL, ZType.fromZName("bool"));
    }

    @Test
    public void testUnknownTypeName() {
        assertThrows(IllegalArgumentException.class, () -> ZType.fromZName("int7"));
        assertFalse(ZType.isType("int7"));
        assertTrue(ZType.isType("flt64"));
    }

    @Test
    public void testLlvmNames() {
        assertEquals("i32", ZType.INT32.llvm());
        assertEquals("i64", ZType.INT64.llvm());
        assertEquals("float", ZType.FLT32.llvm());
        assertEquals("double", ZType.FLT64.llvm());
        assertEquals("i1", ZType.BOOL.llvm());
        assertEquals("void", ZType.VOID.llvm());
    }

    @Test
    public void testWidths() {
        assertEquals(32, ZType.INT32.bits());
        assertEquals(64, ZType.INT64.bits());
        assertEquals(32, ZType.FLT32.bits());
        assertEquals(64, ZType.FLT64.bits());
    }

    @Test
    public void testClassification() {
        assertTrue(ZType.INT32.isIntegral());
        assertTrue(ZType.INT64.isIntegral());
        assertFalse(ZType.FLT32.isIntegral());
        assertTrue(ZType.FLT32.isFloat());
        assertTrue(ZType.FLT64.isFloat());
        assertTrue(ZType.BOOL.isBoolean());
        assertTrue(ZType.VOID.isVoid());
    }

    @Test
    public void testUnifyPicksTheWiderOfTwoIntegers() {
        assertSame(ZType.INT64, ZType.unify(ZType.INT32, ZType.INT64));
        assertSame(ZType.INT32, ZType.unify(ZType.INT32, ZType.INT32));
    }

    @Test
    public void testUnifyPicksTheWiderOfTwoFloats() {
        assertSame(ZType.FLT64, ZType.unify(ZType.FLT32, ZType.FLT64));
    }

    @Test
    public void testUnifyWidensAnIntegerToFloat() {
        assertSame(ZType.FLT64, ZType.unify(ZType.INT64, ZType.FLT32));
        assertSame(ZType.FLT32, ZType.unify(ZType.INT32, ZType.FLT32));
    }

    @Test
    public void testUnifyIgnoresVoid() {
        assertSame(ZType.INT32, ZType.unify(ZType.VOID, ZType.INT32));
        assertSame(ZType.FLT64, ZType.unify(ZType.FLT64, null));
    }

    @Test
    public void testUnifyRefusesToMixATruthValueWithANumber() {
        assertThrows(IllegalArgumentException.class, () -> ZType.unify(ZType.BOOL, ZType.INT32));
    }

    @Test
    public void testInitialValues() {
        assertEquals("0", ZType.INT32.initial());
        assertEquals("0", ZType.INT64.initial());
        assertEquals("0.0", ZType.FLT32.initial());
        assertEquals("0.0", ZType.FLT64.initial());
    }

    @Test
    public void testWholeNumberLiteralsKeepEveryDigit() {
        assertEquals("9007199254740993", ZType.INT64.literal(9007199254740993L));
        assertEquals("2147483647", ZType.INT32.literal(2147483647L));
        assertEquals("-5", ZType.INT32.literal(-5L));
    }

    @Test
    public void testFloatLiterals() {
        assertEquals("3.14", ZType.FLT64.literal(3.14));
        assertEquals("4.0", ZType.FLT64.literal(4L));
        assertEquals("4.0", ZType.FLT32.literal(4L));
        assertEquals("4", ZType.INT64.literal(4L));
    }

    @Test
    public void testBooleanLiterals() {
        assertEquals("true", ZType.BOOL.literal(1L));
        assertEquals("false", ZType.BOOL.literal(0L));
    }

    @Test
    public void testVoidHasNoLiteral() {
        assertThrows(IllegalStateException.class, () -> ZType.VOID.literal(1L));
        assertThrows(IllegalStateException.class, () -> ZType.VOID.literal(1.0));
    }
}
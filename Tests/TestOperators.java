package Tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOperators {

    private String codeGen(final String code) throws IOException {
        return ZFixtures.module(code);
    }

    @Test
    public void testIntegerRemainderUsesSrem() throws IOException {
        assertTrue(codeGen("proc main() { ret 7 % 3 }").contains("srem i32 7, 3"));
    }

    @Test
    public void testUnsignedRemainderUsesUrem() throws IOException {
        final String ir = codeGen("proc main() { uint32 a -> 7\n ret a % 3 }");
        assertTrue(ir.contains("urem i32"), ir);
    }

    @Test
    public void testFloatingRemainderUsesFrem() throws IOException {
        assertTrue(codeGen("proc main() { ret 7.0 % 3.0 }").contains("frem double 7.0, 3.0"));
    }

    @Test
    public void testEqualityUsesEq() throws IOException {
        final String ir = codeGen("proc main() { ret 3 == 3 }");
        assertTrue(ir.contains("icmp eq i32 3, 3"), ir);
        assertTrue(ir.contains("zext i1"), ir);
    }

    @Test
    public void testInequalityUsesNe() throws IOException {
        assertTrue(codeGen("proc main() { ret 3 != 4 }").contains("icmp ne i32 3, 4"));
    }

    @Test
    public void testLessOrEqualUsesSle() throws IOException {
        assertTrue(codeGen("proc main() { ret 3 <= 4 }").contains("icmp sle i32 3, 4"));
    }

    @Test
    public void testGreaterOrEqualUsesSge() throws IOException {
        assertTrue(codeGen("proc main() { ret 3 >= 4 }").contains("icmp sge i32 3, 4"));
    }

    @Test
    public void testUnsignedLessOrEqualUsesUle() throws IOException {
        final String ir = codeGen("proc main() { uint32 a -> 3\n ret a <= 4 }");
        assertTrue(ir.contains("icmp ule i32"), ir);
    }

    @Test
    public void testUnsignedConditionUsesUnsignedPredicate() throws IOException {
        final String ir = codeGen(
                "proc main() { uint32 a -> 4294967295\n if (a > 0) { ret 1 }\n ret 0 }");
        assertTrue(ir.contains("icmp ugt i32"), ir);
        assertFalse(ir.contains("icmp sgt i32"), ir);
    }

    @Test
    public void testFloatingEqualityUsesOeq() throws IOException {
        assertTrue(codeGen("proc main() { ret 1.5 == 2.5 }").contains("fcmp oeq double"));
    }

    @Test
    public void testFloatingInequalityUsesUne() throws IOException {
        assertTrue(codeGen("proc main() { ret 1.5 != 2.5 }").contains("fcmp une double"));
    }

    @Test
    public void testBooleanReturnIsTruncated() throws IOException {
        final String ir = codeGen("proc f() -> bool { ret 1 }\nproc main() { ret 0 }");
        assertTrue(ir.contains("trunc i32 1 to i1"), ir);
        assertTrue(ir.contains("ret i1"), ir);
    }

    @Test
    public void testBooleanLiteralHasBooleanType() throws IOException {
        final String ir = codeGen("proc yes() -> bool { ret true }\nproc main() { ret 0 }");
        assertTrue(ir.contains("ret i1 true"), ir);
    }

    @Test
    public void testBooleanLiteralWidensWithZext() throws IOException {
        final String ir = codeGen("proc main() { ret true }");
        assertTrue(ir.contains("zext i1 true to i32"), ir);
    }
}

package Tests;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBinding {
    private final TestEndToEnd end = new TestEndToEnd();

    private static boolean verifyTools() {
        return TestEndToEnd.OPT != null && TestEndToEnd.CLANG != null;
    }

    @Test
    void testBasic() throws Exception {
        if (!verifyTools()) return;

        final Path binary = end.compile("test", """
                extern isalpha(int32) -> bool
                
                proc main() {
                    int32 test -> 60
                    bool result -> isalpha(60)
                    ret result
                }
                """);
        assertEquals(0, end.run(binary));
    }

    @Test
    void testSqrt() throws Exception {
        if(!verifyTools()) return;

        final Path binary = end.compile("test2", """
                extern sqrt(flt64) -> flt64
                
                proc main () {
                    flt64 test -> 60
                    flt64 test2 -> sqrt(test)
                    ret test2
                }
                """);

        assertEquals(7, end.run(binary));
    }

    @Test
    void testIsDigit() throws Exception {
        if (!verifyTools()) return;

        final Path binary = end.compile("test3", """
                extern isdigit(int32) -> int32
                
                proc main () {
                    int32 test -> 60
                    int32 test2 -> isdigit(test)
                    ret test2
                }
                """);

        assertEquals(0, end.run(binary));
    }

    @Test
    void testExit() throws Exception {
        if(!verifyTools()) return;

        final Path binary = end.compile("test", """
                extern exit(int32) -> void
                
                proc main () {
                    int32 test -> 60
                    exit(test)
                    ret test
                }
                """);


        assertEquals(60, end.run(binary));
    }

}

package Tests;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBinding {
    private final TestEndToEnd end = new TestEndToEnd();

    private static boolean verifyTools() {
        if (TestEndToEnd.OPT == null || TestEndToEnd.CLANG == null) {
            return false;
        }
        return true;
    }
    @Test
    void testBasic() throws Exception {
        if (!verifyTools()) return;

        final Path binary = end.compile("test", """
                extern isalpha(int32) -> bool
                
                proc main() {
                    int32 test -> 60
                    bool result -> isalpha(60)
                    return result
                }
                """);
        assertEquals(0, end.run(binary));
    }

}

package Tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBenchmarks {

    @Test
    public void benchmarksCompile() throws IOException {
        final Path benchmarks = Path.of(System.getProperty("user.dir"), "benchmarks");

        for (final String name : List.of("fibonacci", "collatz", "pi", "primes")) {
            final String source = Files.readString(benchmarks.resolve(name + ".z"));
            final String module = ZFixtures.module(source);

            assertNotNull(module, name + " did not compile");
            assertTrue(module.contains("define"), name + " produced no functions");
        }
    }
}

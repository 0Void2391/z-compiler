package Tests;

import org.junit.jupiter.api.Test;
import src.Main.Main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBenchmarks {

    @Test
    public void benchmarksCompile() throws IOException {
        final Path benchmarks = Path.of(System.getProperty("user.dir"), "benchmarks");

        final List<Path> sources;
        try (final var entries = Files.list(benchmarks)) {
            sources = entries
                    .filter(path -> path.getFileName().toString().endsWith(".z"))
                    .sorted()
                    .toList();
        }

        assertFalse(sources.isEmpty(), "no benchmark sources found");

        for (final Path source : sources) {
            final String name = source.getFileName().toString();
            final String text = Files.readString(source);
            final String module = Main.generateModule(ZFixtures.parserFor(text), source);

            assertNotNull(module, name + " did not compile");
            assertTrue(module.contains("define"), name + " produced no functions");
        }
    }
}

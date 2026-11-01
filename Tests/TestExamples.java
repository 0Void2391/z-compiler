package Tests;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import src.Main.Main;
import src.Parser.Parser;
import src.Lexer.Lexer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class TestExamples {

    private static final Path EXAMPLES = Path.of("examples");

    @TestFactory
    Collection<DynamicTest> examplesCompile() throws IOException {
        final List<Path> examples;
        try (Stream<Path> files = Files.list(EXAMPLES)) {
            examples = files
                .filter(Files::isRegularFile)
                .filter(file -> {
                    Path fileName = file.getFileName();
                    return fileName != null && fileName.toString().endsWith(".z");
                })
                .sorted()
                .toList();
        }

        assertFalse(examples.isEmpty(), "no .z files found in " + EXAMPLES);

        return examples.stream()
                .map(example -> {
                    final String testName = Optional.ofNullable(example.getFileName()).map(Path::toString).orElse("Unknown File");

                    return dynamicTest(testName, () -> {
                        try (InputStream input = Files.newInputStream(example)) {
                            final Parser parser = new Parser(new Lexer(input));
                            assertNotNull(Main.generateModule(parser, example));
                        }
                    });
                })
                .collect(toList());


    }
}

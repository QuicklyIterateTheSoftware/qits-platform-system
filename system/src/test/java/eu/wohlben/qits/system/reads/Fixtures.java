package eu.wohlben.qits.system.reads;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the captured docker output under {@code src/test/resources/fixtures}.
 *
 * <p>Two readers, because docker prints two shapes and confusing them is the mistake these tests
 * exist to prevent: {@link #lines} for the one-object-per-line output of {@code ls --format}, and
 * {@link #array} for the JSON array an {@code inspect} prints.
 */
final class Fixtures {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Fixtures() {}

  /** One `--format '{{json .}}'` capture: one object per line. */
  static List<JsonNode> lines(String name) {
    List<JsonNode> rows = new ArrayList<>();
    for (String line : read(name).split("\n")) {
      if (!line.isBlank()) {
        rows.add(parse(line));
      }
    }
    return rows;
  }

  /** The single object of a one-line capture — `docker info`. */
  static JsonNode one(String name) {
    return parse(read(name));
  }

  /** An `inspect` capture: a JSON array of documents. */
  static List<JsonNode> array(String name) {
    JsonNode root = parse(read(name));
    List<JsonNode> rows = new ArrayList<>();
    root.forEach(rows::add);
    return rows;
  }

  /** The raw text, for the tests that assert on what a value must NOT contain. */
  static String read(String name) {
    try (InputStream stream = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
      assertNotNull(stream, "missing fixture: " + name);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("could not read fixture " + name, e);
    }
  }

  private static JsonNode parse(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalStateException("fixture is not JSON: " + e.getMessage(), e);
    }
  }
}

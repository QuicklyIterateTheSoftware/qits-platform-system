package eu.wohlben.qits.system.testdocker;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Where the suite's stand-in docker lives, and how a test reads what was asked of it.
 *
 * <p>The script is {@code src/test/resources/fake-docker/docker}; what this resolves is the COPY
 * Maven puts in {@code target/test-classes}, found through the classloader rather than through a
 * relative path, because a relative one depends on which directory the build was launched from.
 *
 * <p><b>It has to be made executable here.</b> Maven's resource copy does not carry the file mode,
 * so the committed {@code 755} does not survive into {@code target/test-classes} — and a
 * ProcessBuilder on a non-executable file fails with "Permission denied", which reads like a
 * sandbox problem and is not one.
 */
public final class FakeDocker {

  private FakeDocker() {}

  /** The absolute path to hand {@code qits.system.docker.binary}. */
  public static String binaryPath() {
    URL found = FakeDocker.class.getClassLoader().getResource("fake-docker/docker");
    if (found == null) {
      throw new IllegalStateException("the fake docker script is not on the test classpath");
    }
    Path script = Path.of(found.getPath());
    if (!script.toFile().canExecute() && !script.toFile().setExecutable(true)) {
      throw new IllegalStateException("could not make the fake docker script executable: " + script);
    }
    return script.toAbsolutePath().toString();
  }

  /** The directory the script writes its call log and its knobs into. */
  public static Path directory() {
    return Path.of(binaryPath()).getParent();
  }

  /** Every command the service has run since the log was last cleared, one per line. */
  public static List<String> calls() {
    Path log = directory().resolve("calls.log");
    if (!Files.exists(log)) {
      return List.of();
    }
    try {
      return Files.readAllLines(log, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("could not read the fake docker call log", e);
    }
  }

  /** Forget every recorded call, so a test asserts on its own. */
  public static void clearCalls() {
    try {
      Files.deleteIfExists(directory().resolve("calls.log"));
    } catch (IOException e) {
      throw new IllegalStateException("could not clear the fake docker call log", e);
    }
  }

  /** Whether any recorded call contains this text. */
  public static boolean called(String fragment) {
    return calls().stream().anyMatch(call -> call.contains(fragment));
  }
}

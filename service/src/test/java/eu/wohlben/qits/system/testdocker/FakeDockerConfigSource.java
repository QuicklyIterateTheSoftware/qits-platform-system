package eu.wohlben.qits.system.testdocker;

import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Points {@code qits.system.docker.binary} at the suite's stand-in script.
 *
 * <p>A config SOURCE rather than a line in {@code src/test/resources/application.properties},
 * because the value is an absolute path that only exists at run time — and a source rather than a
 * per-test profile, because every {@code @QuarkusTest} here needs it and a profile somebody forgot
 * would silently run against the real docker on the developer's machine.
 *
 * <p>The ordinal sits above the test application.properties (260) so it wins, and it is registered
 * through {@code META-INF/services} the way the platform's embedded-postgres sources are.
 */
public class FakeDockerConfigSource implements ConfigSource {

  private static final String KEY = "qits.system.docker.binary";

  private final Map<String, String> values = Map.of(KEY, FakeDocker.binaryPath());

  @Override
  public Set<String> getPropertyNames() {
    return values.keySet();
  }

  @Override
  public String getValue(String name) {
    return values.get(name);
  }

  @Override
  public String getName() {
    return "fake-docker";
  }

  @Override
  public int getOrdinal() {
    return 400;
  }
}

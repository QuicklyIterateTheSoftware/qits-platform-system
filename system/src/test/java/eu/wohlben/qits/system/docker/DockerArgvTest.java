package eu.wohlben.qits.system.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.system.error.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The read command lines, element for element.
 *
 * <p>These assertions look pedantic and are not: a flag lost in a refactor is invisible everywhere
 * else until it is invisible in production. `--no-trunc` missing from `service ps` truncates the
 * error a task failed with; missing from `image ls` turns every image id into a prefix.
 */
class DockerArgvTest {

  @Test
  void everyListAsksForOneJsonObjectPerLine() {
    // The `ls` shape. A parser written for the `inspect` shape against this output reads nothing.
    for (List<String> argv :
        List.of(
            DockerArgv.nodeLs("docker"),
            DockerArgv.serviceLs("docker"),
            DockerArgv.configLs("docker"),
            DockerArgv.secretLs("docker"),
            DockerArgv.psAll("docker", true),
            DockerArgv.imageLs("docker"),
            DockerArgv.volumeLs("docker"),
            DockerArgv.networkLs("docker"),
            DockerArgv.systemDf("docker"),
            DockerArgv.info("docker"))) {
      assertEquals("docker", argv.get(0));
      assertTrue(argv.contains("--format"), argv.toString());
      assertEquals("{{json .}}", argv.get(argv.indexOf("--format") + 1), argv.toString());
    }
  }

  @Test
  void inspectsAskForNoFormatAtAll() {
    // An inspect prints the daemon's own document as a JSON array — which is the shape the detail
    // parsers read. A `--format` here would flatten it into the CLI's columns instead.
    assertEquals(
        List.of("docker", "node", "inspect", "abc"), DockerArgv.nodeInspect("docker", "abc"));
    assertEquals(
        List.of("docker", "service", "inspect", "svc"), DockerArgv.serviceInspect("docker", "svc"));
    assertEquals(
        List.of("docker", "config", "inspect", "cfg"), DockerArgv.configInspect("docker", "cfg"));
    assertEquals(
        List.of("docker", "container", "inspect", "cid"),
        DockerArgv.containerInspect("docker", "cid"));
  }

  @Test
  void serviceTasksAreNotTruncated() {
    List<String> argv = DockerArgv.servicePs("docker", "dev-qits-ci");

    assertEquals(List.of("docker", "service", "ps", "dev-qits-ci"), argv.subList(0, 4));
    assertTrue(argv.contains("--no-trunc"), "a truncated task error is the one field that matters");
  }

  @Test
  void aContainerListIncludesTheDeadOnesOnlyWhenAsked() {
    assertTrue(DockerArgv.psAll("docker", true).contains("-a"));
    assertFalse(DockerArgv.psAll("docker", false).contains("-a"));
    // Both keep --no-trunc: a truncated container id cannot be pasted back into a path.
    assertTrue(DockerArgv.psAll("docker", false).contains("--no-trunc"));
  }

  @Test
  void theExecProbeAsksForExactlyTheThreeFactsTheTerminalNeeds() {
    List<String> argv = DockerArgv.containerProbe("docker", "dev-qits-ci.1.abc");

    assertEquals(
        List.of(
            "docker",
            "container",
            "inspect",
            "--format",
            "{{.Id}}|{{.State.Running}}|{{.Name}}",
            "dev-qits-ci.1.abc"),
        argv);
  }

  @Test
  void logsCarryTheTailAsTwoElements() {
    // Two elements, never "--tail=200": a single element would put a caller's number next to a
    // flag with no boundary between them.
    assertEquals(
        List.of("docker", "logs", "--tail", "50", "cid"), DockerArgv.logsTail("docker", "cid", 50));
  }

  @Test
  void theSweepFiltersOnTheOwnerLabelAndNotOnANamePrefix() {
    List<String> argv = DockerArgv.psByOwner("docker", "qits-platform-system");

    assertEquals(List.of("docker", "ps", "-aq", "--filter"), argv.subList(0, 4));
    // A name can be anything; a label is what this service itself put there. And the VALUE is the
    // application name, so two platforms on one daemon do not reap each other's terminals.
    assertEquals("label=qits.system.owner=qits-platform-system", argv.get(4));
  }

  @Test
  void removalTakesEveryIdInOneCall() {
    assertEquals(
        List.of("docker", "rm", "-f", "a", "b"), DockerArgv.rmForce("docker", List.of("a", "b")));
  }

  @Test
  void anImplausibleReferenceNeverReachesAnArgv() {
    // The second checkpoint. The controller checks too; the point of two is that neither has to be
    // trusted alone. A leading dash would let a positional argument be read as an option.
    assertThrows(
        BadRequestException.class, () -> DockerArgv.containerInspect("docker", "--privileged"));
    assertThrows(BadRequestException.class, () -> DockerArgv.logsTail("docker", "a b", 10));
    assertThrows(BadRequestException.class, () -> DockerArgv.nodeInspect("docker", "a/b"));
    assertThrows(BadRequestException.class, () -> DockerArgv.serviceInspect("docker", ""));
  }

  @Test
  void theBinaryIsWhateverTheConfigSays() {
    // Not a constant: the whole suite runs against a fake `docker` shell script, which is what
    // makes a docker-free clone able to test the routes.
    assertEquals("/tmp/fake-docker/docker", DockerArgv.version("/tmp/fake-docker/docker").get(0));
  }
}

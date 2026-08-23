package eu.wohlben.qits.system.reads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The node-local parsers, against real `ps`, `inspect`, `image ls`, `volume ls` and `network ls`. */
class NodeParsersTest {

  @Test
  void readsAContainerList() {
    List<ContainerSummary> containers =
        Fixtures.lines("docker-ps.jsonl").stream().map(ContainerSummary::from).toList();

    assertEquals(3, containers.size());
    ContainerSummary first = containers.get(0);
    assertEquals("dev-qits-ci.1.k4g0nn1ld7o272jl7fciatg6m", first.name());
    assertEquals("running", first.state());
    // The CLI's own sentence — the one a person reads in a shell.
    assertEquals("Up 4 hours (healthy)", first.status());
    assertEquals("healthy", first.health());
    assertEquals("qits-net", first.networks());
    assertEquals("8080/tcp", first.ports());
  }

  @Test
  void aContainerListCarriesNoLabelMap() {
    // The CLI joins labels with commas and a value may CONTAIN one. This very fixture has
    // `maintainer=Red Hat, Inc.`, which a naive split turns into a label named " Inc." with no
    // value. So a summary carries no labels at all and a detail reads the real map from inspect.
    assertTrue(Fixtures.read("docker-ps.jsonl").contains("maintainer=Red Hat, Inc."));
    assertFalse(
        List.of(ContainerSummary.class.getRecordComponents()).stream()
            .anyMatch(component -> component.getName().equals("labels")),
        "ContainerSummary must not offer a labels map parsed from a lossy column");
  }

  @Test
  void readsAContainerDocument() {
    ContainerDetail container =
        ContainerDetail.from(Fixtures.array("docker-container-inspect.json").get(0));

    // Docker prefixes a container name with a slash; a caller would never type that back.
    assertEquals("dev-qits-ci.1.k4g0nn1ld7o272jl7fciatg6m", container.name());
    assertEquals(64, container.id().length(), "the canonical id, not a prefix");
    assertEquals("running", container.state());
    assertTrue(container.running());
    assertEquals("healthy", container.health());
    assertEquals(0, container.exitCode());
    assertEquals(0, container.restartCount());
    assertEquals("1001", container.user());
    assertEquals(List.of("/work/qits-ci", "-Dquarkus.http.host=0.0.0.0"), container.command());
    assertEquals(
        "qits-ci", container.labels().get("qits.platform.deployments.app-name"));
    assertEquals("qits-net", container.networks().get(0).name());
  }

  @Test
  void aContainerDocumentNeverCarriesAnEnvironmentValue() {
    ContainerDetail container =
        ContainerDetail.from(Fixtures.array("docker-container-inspect.json").get(0));

    assertTrue(container.envKeys().contains("QITS_RESOURCE_DB_PASSWORD"));
    assertTrue(container.envKeys().contains("QUARKUS_OIDC_CLIENT_CREDENTIALS_SECRET"));
    assertFalse(
        container.envKeys().stream().anyMatch(key -> key.contains("=")),
        "a key must never carry its value: " + container.envKeys());
    assertFalse(
        container.toString().contains("not-a-real-secret"),
        "the fixture's scrubbed secret marker must not reach a parsed record");
  }

  @Test
  void readsImagesVolumesAndNetworks() {
    ImageSummary image =
        ImageSummary.from(Fixtures.lines("docker-image-ls.jsonl").get(0));
    assertEquals("registry.dev.localhost:8080/qits/qits-ci", image.repository());
    assertEquals("325MB", image.size());
    // The same figure as a number, for a client that sorts and totals on it.
    assertEquals(325_000_000L, image.sizeBytes());
    assertEquals("3", image.containers());

    VolumeSummary volume = VolumeSummary.from(Fixtures.lines("docker-volume-ls.jsonl").get(0));
    assertEquals("local", volume.driver());
    assertTrue(volume.mountpoint().startsWith("/var/lib/docker/volumes/"));

    List<NetworkSummary> networks =
        Fixtures.lines("docker-network-ls.jsonl").stream().map(NetworkSummary::from).toList();
    NetworkSummary bridge = networks.get(0);
    assertEquals("bridge", bridge.name());
    // The CLI prints these two as the STRINGS "true"/"false" in its json column, not as booleans:
    // asBoolean on a text node answers false, which would report every network as external.
    assertFalse(bridge.internal());
    assertFalse(bridge.ipv6());
    NetworkSummary platform =
        networks.stream().filter(n -> n.name().equals("qits-platform")).findFirst().orElseThrow();
    assertEquals("overlay", platform.driver());
    assertEquals("swarm", platform.scope());
    assertEquals("platform", platform.labels().get("qits.platform.deployments.network"));
  }
}

package eu.wohlben.qits.system.reads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * The host parsers, against a real {@code docker info} and {@code docker system df}.
 *
 * <p>Fixtures rather than hand-built JSON, because the failure these guard against is reading the
 * API's field names when the CLI printed its own — {@code MemTotal} not {@code memory}, {@code
 * Driver} not {@code StorageDriver} — which produces nulls that nothing else notices.
 */
class HostParsersTest {

  @Test
  void readsTheMachineOffDockerInfo() {
    HostInfo host = HostInfo.from(Fixtures.one("docker-info.json"));

    assertEquals("DESKTOP-3630IEI", host.hostname());
    assertEquals("29.7.2", host.dockerVersion());
    assertEquals("Fedora Linux 43 (WSL)", host.os());
    assertEquals("linux", host.osType());
    assertEquals("x86_64", host.architecture());
    assertEquals("6.6.87.2-microsoft-standard-WSL2", host.kernelVersion());
    assertEquals(24, host.cpus());
    assertEquals(33_529_409_536L, host.memoryBytes());
    assertEquals("/var/lib/docker", host.dockerRootDir());
    // `Driver`, not `StorageDriver`: the field is named for what it drives, not for what it stores.
    assertEquals("overlayfs", host.storageDriver());
    assertEquals("systemd", host.cgroupDriver());
    assertEquals("2", host.cgroupVersion());
    assertEquals(26, host.containers());
    assertEquals(23, host.containersRunning());
    assertEquals(3, host.containersStopped());
  }

  @Test
  void readsTheSwarmOutOfTheSameDocument() {
    // One `docker info` answers both halves of the overview; a second call would be a second fork
    // for a document this process already has.
    SwarmInfo swarm = SwarmInfo.from(Fixtures.one("docker-info.json"));

    assertEquals("active", swarm.state());
    assertTrue(swarm.active());
    assertEquals("iwbwrdui2z0n62kqcjfo1erwh", swarm.nodeId());
    assertEquals("192.168.152.4", swarm.nodeAddress());
    assertTrue(swarm.controlAvailable(), "a manager can answer the swarm-wide reads");
    assertEquals(1, swarm.managers());
    assertEquals(1, swarm.nodes());
    assertEquals("lysckrdwpfyqovchlqs02nvs1", swarm.clusterId());
    assertEquals("", swarm.error());
  }

  @Test
  void keepsDockersOwnRenderedSizesAndTheNumbersBehindThem() {
    DiskUsage usage = DiskUsage.from(Fixtures.lines("docker-system-df.jsonl"));

    assertEquals(4, usage.entries().size());
    DiskUsage.Entry images = usage.entries().get(0);
    assertEquals("Images", images.type());
    assertEquals("425", images.totalCount());
    assertEquals("18", images.active());
    // The CLI renders these and the API does not. Re-deriving bytes would cost a much more
    // expensive call for a number the operator would then have to render again.
    assertEquals("308.3GB", images.size());
    assertEquals("286.8GB (93%)", images.reclaimable());
    assertEquals("Build Cache", usage.entries().get(3).type());

    // And the same figures as NUMBERS, read back out of the rendering because the CLI offers no
    // other source. Decimal, the way go-units renders them: 308.3GB is 308.3 thousand million.
    assertEquals(308_300_000_000L, usage.imagesBytes());
    assertEquals(59_340_000L, usage.containersBytes());
    assertEquals(33_490_000_000L, usage.volumesBytes());
    assertEquals(302_500_000_000L, usage.buildCacheBytes());
    assertEquals(
        286_800_000_000L + 110_600L + 1_492_000_000L + 103_500_000_000L,
        usage.reclaimableBytes(),
        "reclaimable is the sum of every class, percentages stripped");
  }

  @Test
  void aMissingFieldIsBlankRatherThanAnException() {
    // Every field docker prints is optional to this parser: an older daemon, a different object
    // shape, a renamed column. One absent field must cost one blank value, not a whole panel.
    JsonNode empty = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

    HostInfo host = HostInfo.from(empty);

    assertEquals(null, host.hostname());
    assertEquals(0, host.cpus());
    assertTrue(host.warnings().isEmpty());
    assertFalse(SwarmInfo.from(empty).active());
  }
}

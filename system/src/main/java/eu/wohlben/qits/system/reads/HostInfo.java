package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * The machine, as `docker info` describes it — the answer to "what am I actually running on".
 *
 * <p>Every field is one line of `docker info` output an operator would otherwise ssh in to read.
 * `warnings` is included because the daemon puts real problems there (no swap limit support, an
 * insecure registry) and nothing else on the platform surfaces them.
 *
 * <p>The component names are the CLIENT's vocabulary, not docker's: `hostname` rather than `Name`,
 * `os` rather than `OperatingSystem`, `dockerVersion` rather than `ServerVersion`. The parser is
 * where the two meet, which is the whole reason there is one.
 */
public record HostInfo(
    String hostname,
    String dockerVersion,
    String os,
    String osType,
    String osVersion,
    String kernelVersion,
    String architecture,
    int cpus,
    long memoryBytes,
    String dockerRootDir,
    String storageDriver,
    String cgroupDriver,
    String cgroupVersion,
    String loggingDriver,
    int containers,
    int containersRunning,
    int containersPaused,
    int containersStopped,
    int images,
    List<String> warnings) {

  /** Read one `docker info --format '{{json .}}'` document. */
  public static HostInfo from(JsonNode node) {
    return new HostInfo(
        Json.text(node, "Name"),
        Json.text(node, "ServerVersion"),
        Json.text(node, "OperatingSystem"),
        Json.text(node, "OSType"),
        Json.text(node, "OSVersion"),
        Json.text(node, "KernelVersion"),
        Json.text(node, "Architecture"),
        Json.intAt(node, "NCPU"),
        Json.longAt(node, "MemTotal"),
        Json.text(node, "DockerRootDir"),
        Json.text(node, "Driver"),
        Json.text(node, "CgroupDriver"),
        Json.text(node, "CgroupVersion"),
        Json.text(node, "LoggingDriver"),
        Json.intAt(node, "Containers"),
        Json.intAt(node, "ContainersRunning"),
        Json.intAt(node, "ContainersPaused"),
        Json.intAt(node, "ContainersStopped"),
        Json.intAt(node, "Images"),
        Json.strings(node, "Warnings"));
  }
}

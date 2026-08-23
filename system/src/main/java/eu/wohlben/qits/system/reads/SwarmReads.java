package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import eu.wohlben.qits.system.docker.DockerArgv;
import eu.wohlben.qits.system.docker.DockerCli;
import eu.wohlben.qits.system.error.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * The swarm: nodes, services, configs and secrets, read from the manager this service runs on.
 *
 * <p>These are the CLUSTER-WIDE reads, and unlike the node-scoped half they are not limited to one
 * machine — a manager knows the whole swarm, so a three-node cluster lists three nodes here even
 * though only one of them can be shelled into.
 *
 * <p><b>A read on a worker fails, and it fails honestly.</b> `docker node ls` on a non-manager
 * answers "This node is not a swarm manager", which {@code DockerCli} turns into a 503 carrying
 * that sentence. There is no fallback to an empty list: an empty swarm and an unreadable one are
 * different facts and must not look the same.
 *
 * <p><b>An empty inspect is a 404.</b> Docker exits non-zero for an unknown reference, so a missing
 * object arrives as a failed call — but the message says "no such", not "cannot connect". The
 * distinction is made here rather than in {@code DockerCli} because only the caller knows which
 * reference it asked about.
 */
@ApplicationScoped
public class SwarmReads {

  @Inject DockerCli docker;

  public List<NodeSummary> nodes() {
    return docker.jsonLines(DockerArgv.nodeLs(docker.binary())).stream()
        .map(NodeSummary::from)
        .toList();
  }

  public NodeDetail node(String ref) {
    return NodeDetail.from(inspectOne(DockerArgv.nodeInspect(docker.binary(), ref), "node", ref));
  }

  public List<ServiceSummary> services() {
    return docker.jsonLines(DockerArgv.serviceLs(docker.binary())).stream()
        .map(ServiceSummary::from)
        .toList();
  }

  /** A service with its tasks — two calls, because `inspect` does not carry them. */
  public ServiceDetail service(String ref) {
    JsonNode node = inspectOne(DockerArgv.serviceInspect(docker.binary(), ref), "service", ref);
    List<TaskSummary> tasks =
        docker.jsonLines(DockerArgv.servicePs(docker.binary(), ref)).stream()
            .map(TaskSummary::from)
            .toList();
    return ServiceDetail.from(node, tasks);
  }

  public List<ConfigSummary> configs() {
    return docker.jsonLines(DockerArgv.configLs(docker.binary())).stream()
        .map(ConfigSummary::from)
        .toList();
  }

  public ConfigDetail config(String ref) {
    return ConfigDetail.from(
        inspectOne(DockerArgv.configInspect(docker.binary(), ref), "config", ref));
  }

  public List<SecretSummary> secrets() {
    return docker.jsonLines(DockerArgv.secretLs(docker.binary())).stream()
        .map(SecretSummary::from)
        .toList();
  }

  /**
   * Inspect one object, turning docker's own "no such X" into a 404 and every other failure into
   * the 503 {@code DockerCli} already builds.
   */
  private JsonNode inspectOne(List<String> argv, String what, String ref) {
    var result = docker.attempt(argv);
    if (!result.ok()) {
      String said = result.output() == null ? "" : result.output().toLowerCase(java.util.Locale.ROOT);
      // Docker's wording across objects: "Error: no such node: x", "service x not found".
      if (said.contains("no such") || said.contains("not found")) {
        throw new NotFoundException("no such " + what + ": " + ref);
      }
      throw docker.unavailable(argv, result);
    }
    // Parse what that call already printed. Calling jsonArray here would fork docker a second time
    // for the same document — and the two answers could differ.
    List<JsonNode> rows = docker.parseArray(argv, result.output());
    if (rows.isEmpty()) {
      throw new NotFoundException("no such " + what + ": " + ref);
    }
    return rows.get(0);
  }
}

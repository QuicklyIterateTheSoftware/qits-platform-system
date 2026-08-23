package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * One node, from `docker node inspect` — the daemon's own document rather than the CLI's columns,
 * which is where the resources, the labels and the manager's reachability live.
 *
 * @param cpus derived from `NanoCPUs`, because a node reports its CPU as a nanocore count
 */
public record NodeDetail(
    String id,
    String hostname,
    String role,
    String availability,
    String state,
    String address,
    String engineVersion,
    String architecture,
    String operatingSystem,
    double cpus,
    long memoryBytes,
    Map<String, String> labels,
    boolean managerLeader,
    String managerReachability,
    String managerAddress,
    String createdAt,
    String updatedAt) {

  private static final double NANO = 1_000_000_000d;

  public static NodeDetail from(JsonNode node) {
    return new NodeDetail(
        Json.text(node, "ID"),
        Json.textAt(node, "Description", "Hostname"),
        Json.textAt(node, "Spec", "Role"),
        Json.textAt(node, "Spec", "Availability"),
        Json.textAt(node, "Status", "State"),
        Json.textAt(node, "Status", "Addr"),
        Json.textAt(node, "Description", "Engine", "EngineVersion"),
        Json.textAt(node, "Description", "Platform", "Architecture"),
        Json.textAt(node, "Description", "Platform", "OS"),
        Json.longAt(node, "Description", "Resources", "NanoCPUs") / NANO,
        Json.longAt(node, "Description", "Resources", "MemoryBytes"),
        Json.map(node, "Spec", "Labels"),
        Json.boolAt(node, "ManagerStatus", "Leader"),
        Json.textAt(node, "ManagerStatus", "Reachability"),
        Json.textAt(node, "ManagerStatus", "Addr"),
        Json.text(node, "CreatedAt"),
        Json.text(node, "UpdatedAt"));
  }
}

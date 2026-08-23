package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One row of `docker node ls`.
 *
 * <p>`self` is the field the client hangs its behaviour on: only this node's containers are
 * reachable, so it is what decides whether the row is a link or a note.
 *
 * <p>`role` is DERIVED and `address` is absent, because the CLI's list columns carry neither. A
 * node with any manager status is a manager — that is what the column means — and the address only
 * exists on the inspect document. Both are on {@link NodeDetail}, read from the daemon proper.
 */
public record NodeSummary(
    String id,
    String hostname,
    String role,
    String status,
    String availability,
    String managerStatus,
    String engineVersion,
    String address,
    boolean self) {

  public static NodeSummary from(JsonNode node) {
    String managerStatus = Json.text(node, "ManagerStatus");
    return new NodeSummary(
        Json.text(node, "ID"),
        Json.text(node, "Hostname"),
        managerStatus == null || managerStatus.isBlank() ? "worker" : "manager",
        Json.text(node, "Status"),
        Json.text(node, "Availability"),
        managerStatus,
        Json.text(node, "EngineVersion"),
        null,
        // The CLI prints Self as a real boolean in the json column on this version, and as the
        // string "true" on others; both are read.
        Json.boolAt(node, "Self") || "true".equalsIgnoreCase(Json.text(node, "Self")));
  }
}

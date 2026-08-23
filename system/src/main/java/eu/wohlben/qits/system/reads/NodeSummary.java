package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One row of `docker node ls`.
 *
 * <p>`self` is the field the client hangs its behaviour on: only this node's containers are
 * reachable in v1, so it is what decides whether the row is a link or a note.
 */
public record NodeSummary(
    String id,
    String hostname,
    String status,
    String availability,
    String managerStatus,
    String engineVersion,
    boolean self) {

  public static NodeSummary from(JsonNode node) {
    return new NodeSummary(
        Json.text(node, "ID"),
        Json.text(node, "Hostname"),
        Json.text(node, "Status"),
        Json.text(node, "Availability"),
        Json.text(node, "ManagerStatus"),
        Json.text(node, "EngineVersion"),
        // The CLI prints Self as a real boolean in the json column, but a string "true" on some
        // versions; asBoolean handles the first and this handles the second.
        Json.boolAt(node, "Self") || "true".equalsIgnoreCase(Json.text(node, "Self")));
  }
}

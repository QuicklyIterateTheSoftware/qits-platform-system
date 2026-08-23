package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** One row of `docker network ls`. */
public record NetworkSummary(
    String id,
    String name,
    String driver,
    String scope,
    boolean internal,
    boolean ipv6,
    String createdAt,
    Map<String, String> labels) {

  public static NetworkSummary from(JsonNode node) {
    return new NetworkSummary(
        Json.text(node, "ID"),
        Json.text(node, "Name"),
        Json.text(node, "Driver"),
        Json.text(node, "Scope"),
        // The CLI prints these two as the STRINGS "true"/"false" in its json column, not booleans.
        "true".equalsIgnoreCase(Json.text(node, "Internal")),
        "true".equalsIgnoreCase(Json.text(node, "IPv6")),
        Json.text(node, "CreatedAt"),
        Json.labelColumn(Json.text(node, "Labels")));
  }
}

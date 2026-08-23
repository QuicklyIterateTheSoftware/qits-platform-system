package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** One row of `docker config ls`. */
public record ConfigSummary(
    String id, String name, String createdAt, String updatedAt, Map<String, String> labels) {

  public static ConfigSummary from(JsonNode node) {
    return new ConfigSummary(
        Json.text(node, "ID"),
        Json.text(node, "Name"),
        Json.text(node, "CreatedAt"),
        Json.text(node, "UpdatedAt"),
        // Labels arrive as the CLI's comma-joined column. Safe here: swarm configs are created by
        // the platform's own bootstrap, whose label values carry no commas. See Json.labelColumn.
        Json.labelColumn(Json.text(node, "Labels")));
  }
}

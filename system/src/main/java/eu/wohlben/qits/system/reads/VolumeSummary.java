package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** One row of `docker volume ls`. */
public record VolumeSummary(
    String name, String driver, String scope, String mountpoint, Map<String, String> labels) {

  public static VolumeSummary from(JsonNode node) {
    return new VolumeSummary(
        Json.text(node, "Name"),
        Json.text(node, "Driver"),
        Json.text(node, "Scope"),
        Json.text(node, "Mountpoint"),
        Json.labelColumn(Json.text(node, "Labels")));
  }
}

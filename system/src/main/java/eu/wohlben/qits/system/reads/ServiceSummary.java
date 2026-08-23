package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One row of `docker service ls`. `replicas` is the CLI's own "1/1" string: running over desired,
 * which is the number an operator scans a list for.
 */
public record ServiceSummary(
    String id, String name, String mode, String replicas, String image, String ports) {

  public static ServiceSummary from(JsonNode node) {
    return new ServiceSummary(
        Json.text(node, "ID"),
        Json.text(node, "Name"),
        Json.text(node, "Mode"),
        Json.text(node, "Replicas"),
        Json.text(node, "Image"),
        Json.text(node, "Ports"));
  }
}

package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One row of `docker ps`.
 *
 * <p>NO LABELS HERE. The CLI joins them with commas and a label value may contain one — a real
 * example from this host is `maintainer=Red Hat, Inc.` — so a map parsed from that column would
 * invent entries. Container labels come from `inspect`, which returns a real map, and live on
 * {@link ContainerDetail}.
 *
 * @param status the CLI's own sentence: "Up 4 hours (healthy)", "Exited (137) 2 days ago"
 */
public record ContainerSummary(
    String id,
    String name,
    String image,
    String state,
    String status,
    String health,
    String command,
    String createdAt,
    String runningFor,
    String ports,
    String networks,
    String size) {

  public static ContainerSummary from(JsonNode node) {
    return new ContainerSummary(
        Json.text(node, "ID"),
        Json.text(node, "Names"),
        Json.text(node, "Image"),
        Json.text(node, "State"),
        Json.text(node, "Status"),
        Json.text(node, "HealthStatus"),
        Json.text(node, "Command"),
        Json.text(node, "CreatedAt"),
        Json.text(node, "RunningFor"),
        Json.text(node, "Ports"),
        Json.text(node, "Networks"),
        Json.text(node, "Size"));
  }
}

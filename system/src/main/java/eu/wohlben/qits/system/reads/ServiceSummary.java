package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One row of `docker service ls`.
 *
 * <p>`replicas` is the CLI's own string — "1/1" for a replicated service, "3/3" when it is scaled,
 * and the running-over-desired shape for a global one. Running over desired is the number an
 * operator scans a list for, and rendering it here rather than in the client keeps one spelling.
 *
 * <p>`ports` is ONE string, as the CLI prints it ("*:8080->8080/tcp"), and `updatedAt` is absent
 * from the list columns entirely — it is on {@link ServiceDetail}.
 */
public record ServiceSummary(
    String id, String name, String mode, String replicas, String image, String ports, String updatedAt) {

  public static ServiceSummary from(JsonNode node) {
    return new ServiceSummary(
        Json.text(node, "ID"),
        Json.text(node, "Name"),
        Json.text(node, "Mode"),
        Json.text(node, "Replicas"),
        Json.text(node, "Image"),
        Json.text(node, "Ports"),
        null);
  }
}

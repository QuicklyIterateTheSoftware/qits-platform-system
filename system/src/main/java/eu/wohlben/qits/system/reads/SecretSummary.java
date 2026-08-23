package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * One row of `docker secret ls` — METADATA, and there is no detail route beside it.
 *
 * <p>Not a policy this service enforces: swarm does not return a secret's value to anyone, ever,
 * including a manager. What an operator can learn is that the secret exists, when it was made and
 * what it is labelled — which is enough to answer "is the thing this service is missing even
 * there".
 */
public record SecretSummary(
    String id,
    String name,
    String driver,
    String createdAt,
    String updatedAt,
    Map<String, String> labels) {

  public static SecretSummary from(JsonNode node) {
    return new SecretSummary(
        Json.text(node, "ID"),
        Json.text(node, "Name"),
        Json.text(node, "Driver"),
        Json.text(node, "CreatedAt"),
        Json.text(node, "UpdatedAt"),
        Json.labelColumn(Json.text(node, "Labels")));
  }
}

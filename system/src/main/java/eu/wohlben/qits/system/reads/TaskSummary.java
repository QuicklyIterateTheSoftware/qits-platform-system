package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One task of a service, from `docker service ps --no-trunc`.
 *
 * <p>`--no-trunc` is why `error` is worth carrying: the default truncates it, and a failed task's
 * error ("No such image", "task: non-zero exit (1)") is the whole reason anybody opens this list.
 */
public record TaskSummary(
    String id,
    String name,
    String node,
    String desiredState,
    String currentState,
    String error,
    String image) {

  public static TaskSummary from(JsonNode node) {
    return new TaskSummary(
        Json.text(node, "ID"),
        Json.text(node, "Name"),
        Json.text(node, "Node"),
        Json.text(node, "DesiredState"),
        Json.text(node, "CurrentState"),
        Json.text(node, "Error"),
        Json.text(node, "Image"));
  }
}

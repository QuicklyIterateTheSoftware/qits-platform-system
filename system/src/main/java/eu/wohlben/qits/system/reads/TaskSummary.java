package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One task of a service, from `docker service ps --no-trunc`.
 *
 * <p>`--no-trunc` is why `error` is worth carrying: the default truncates it, and a failed task's
 * error ("No such image", "task: non-zero exit (1)") is the whole reason anybody opens this list.
 *
 * <p>`slot` is DERIVED from the task name — swarm names a task `<service>.<slot>` for a replicated
 * service and `<service>.<nodeId>` for a global one, so a non-numeric tail simply leaves it absent.
 * `nodeId` is not in the list columns at all; the HOSTNAME is, and that is what a person reads.
 */
public record TaskSummary(
    String id,
    String name,
    Integer slot,
    String nodeId,
    String nodeHostname,
    String state,
    String desiredState,
    String error,
    String image,
    String updatedAt) {

  public static TaskSummary from(JsonNode node) {
    String name = Json.text(node, "Name");
    return new TaskSummary(
        Json.text(node, "ID"),
        name,
        slotOf(name),
        null,
        Json.text(node, "Node"),
        Json.text(node, "CurrentState"),
        Json.text(node, "DesiredState"),
        Json.text(node, "Error"),
        Json.text(node, "Image"),
        null);
  }

  private static Integer slotOf(String name) {
    if (name == null) {
      return null;
    }
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      return null;
    }
    try {
      return Integer.valueOf(name.substring(dot + 1));
    } catch (NumberFormatException e) {
      // A global service's tasks are named for the node, not for a slot. Absent, not zero.
      return null;
    }
  }

  /** Whether this task is the one currently doing the work — the count behind a "2/3". */
  public boolean running() {
    return state != null && state.startsWith("Running");
  }
}

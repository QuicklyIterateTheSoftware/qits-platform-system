package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * One service, from `docker service inspect`, with its tasks alongside.
 *
 * <p>It carries every component {@link ServiceSummary} does — including `replicas` as the SAME
 * running-over-desired STRING, rebuilt here from the desired count and the tasks, so a detail page
 * and the list it was opened from never disagree about how many are up.
 *
 * <p>THE ENVIRONMENT IS KEYS ONLY. A platform service's env is where its injected database
 * password, its idp client secret and its machine audience live; swarm hands those over as plain
 * strings, so printing them here would be a credential dump behind one role check. The keys answer
 * the question an operator actually has ("is QITS_RESOURCE_DB_URL set at all?"). See
 * {@link Json#envKeys}.
 *
 * @param networks the attached networks, as swarm records them — an id and this service's VIP on it
 */
public record ServiceDetail(
    String id,
    String name,
    String image,
    String mode,
    String replicas,
    String ports,
    String createdAt,
    String updatedAt,
    Map<String, String> labels,
    List<String> envKeys,
    List<NetworkAttachment> networks,
    String updateOrder,
    int updateParallelism,
    String updateFailureAction,
    String restartCondition,
    List<String> healthcheck,
    List<TaskSummary> tasks) {

  /** A network this service is on, and the virtual address it answers on there. */
  public record NetworkAttachment(String networkId, String address) {}

  public static ServiceDetail from(JsonNode node, List<TaskSummary> tasks) {
    JsonNode spec = Json.at(node, "Spec");
    JsonNode container = Json.at(spec, "TaskTemplate", "ContainerSpec");
    boolean global = Json.at(spec, "Mode", "Global") != null;
    long running = tasks.stream().filter(TaskSummary::running).count();
    int desired = Json.intAt(spec, "Mode", "Replicated", "Replicas");
    List<NetworkAttachment> networks =
        Json.nodes(node, "Endpoint", "VirtualIPs").stream()
            .map(vip -> new NetworkAttachment(Json.text(vip, "NetworkID"), Json.text(vip, "Addr")))
            .toList();
    return new ServiceDetail(
        Json.text(node, "ID"),
        Json.text(spec, "Name"),
        Json.text(container, "Image"),
        global ? "global" : "replicated",
        // The list's own vocabulary: running over desired, or running over running for a global
        // service, which has no declared count.
        global ? running + "/" + tasks.size() : running + "/" + desired,
        portsOf(node),
        Json.text(node, "CreatedAt"),
        Json.text(node, "UpdatedAt"),
        Json.map(spec, "Labels"),
        Json.envKeys(container, "Env"),
        networks,
        Json.textAt(spec, "UpdateConfig", "Order"),
        Json.intAt(spec, "UpdateConfig", "Parallelism"),
        Json.textAt(spec, "UpdateConfig", "FailureAction"),
        Json.textAt(spec, "TaskTemplate", "RestartPolicy", "Condition"),
        Json.strings(container, "Healthcheck", "Test"),
        tasks);
  }

  /**
   * The published ports, rendered the way `docker service ls` renders them, so the string on a
   * detail page is the string on the list. A service that publishes nothing gets null rather than
   * an empty string — absent is a fact, "" is a value.
   */
  private static String portsOf(JsonNode node) {
    List<String> rendered =
        Json.nodes(node, "Endpoint", "Ports").stream()
            .map(
                port ->
                    "*:"
                        + Json.intAt(port, "PublishedPort")
                        + "->"
                        + Json.intAt(port, "TargetPort")
                        + "/"
                        + Json.text(port, "Protocol"))
            .toList();
    return rendered.isEmpty() ? null : String.join(", ", rendered);
  }
}

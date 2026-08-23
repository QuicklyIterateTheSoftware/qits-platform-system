package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * One service, from `docker service inspect`, with its tasks alongside.
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
    int replicas,
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
    List<NetworkAttachment> networks =
        Json.nodes(node, "Endpoint", "VirtualIPs").stream()
            .map(
                vip ->
                    new NetworkAttachment(
                        Json.text(vip, "NetworkID"), Json.text(vip, "Addr")))
            .toList();
    return new ServiceDetail(
        Json.text(node, "ID"),
        Json.text(spec, "Name"),
        Json.text(container, "Image"),
        // A service is replicated or global, and only the first has a replica count.
        Json.at(spec, "Mode", "Global") != null ? "global" : "replicated",
        Json.intAt(spec, "Mode", "Replicated", "Replicas"),
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
}

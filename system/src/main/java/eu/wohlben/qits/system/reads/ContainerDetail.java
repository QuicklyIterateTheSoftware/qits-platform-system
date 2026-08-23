package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * One container, from `docker container inspect` — the daemon's own document, which is where the
 * sandbox actually is: the limits, the capabilities, the mounts and the networks.
 *
 * <p>THE ENVIRONMENT IS KEYS ONLY, for the reason spelled out on {@link Json#envKeys} and
 * {@link ServiceDetail}: a container's env is where its injected credentials are.
 *
 * @param name docker prefixes a container name with a slash; it is stripped here so the value
 *     matches what `docker ps` prints and what a person would type back
 */
public record ContainerDetail(
    String id,
    String name,
    String image,
    String imageId,
    String state,
    boolean running,
    String health,
    int exitCode,
    String createdAt,
    String startedAt,
    String finishedAt,
    int restartCount,
    List<String> command,
    String user,
    Map<String, String> labels,
    List<String> envKeys,
    List<Mount> mounts,
    List<NetworkBinding> networks,
    String restartPolicy,
    long memoryBytes,
    long pidsLimit,
    int oomScoreAdj,
    boolean privileged,
    boolean readOnlyRootfs,
    List<String> capAdd,
    List<String> capDrop) {

  /** One mount, as the daemon reports it — a bind or a named volume. */
  public record Mount(String type, String source, String destination, String mode, boolean writable) {}

  /** One network the container is attached to, and its address there. */
  public record NetworkBinding(String name, String ipAddress) {}

  public static ContainerDetail from(JsonNode node) {
    JsonNode state = Json.at(node, "State");
    JsonNode config = Json.at(node, "Config");
    JsonNode host = Json.at(node, "HostConfig");
    List<String> command = new java.util.ArrayList<>();
    String path = Json.text(node, "Path");
    if (path != null) {
      command.add(path);
    }
    command.addAll(Json.strings(node, "Args"));
    List<Mount> mounts =
        Json.nodes(node, "Mounts").stream()
            .map(
                mount ->
                    new Mount(
                        Json.text(mount, "Type"),
                        // A named volume reports Name, a bind reports Source.
                        Json.text(mount, "Name") != null
                            ? Json.text(mount, "Name")
                            : Json.text(mount, "Source"),
                        Json.text(mount, "Destination"),
                        Json.text(mount, "Mode"),
                        Json.boolAt(mount, "RW")))
            .toList();
    JsonNode networks = Json.at(node, "NetworkSettings", "Networks");
    List<NetworkBinding> bindings = new java.util.ArrayList<>();
    if (networks != null && networks.isObject()) {
      networks
          .properties()
          .forEach(
              entry ->
                  bindings.add(
                      new NetworkBinding(entry.getKey(), Json.text(entry.getValue(), "IPAddress"))));
    }
    String name = Json.text(node, "Name");
    return new ContainerDetail(
        Json.text(node, "Id"),
        name != null && name.startsWith("/") ? name.substring(1) : name,
        Json.text(config, "Image"),
        Json.text(node, "Image"),
        Json.text(state, "Status"),
        Json.boolAt(state, "Running"),
        Json.textAt(state, "Health", "Status"),
        Json.intAt(state, "ExitCode"),
        Json.text(node, "Created"),
        Json.text(state, "StartedAt"),
        Json.text(state, "FinishedAt"),
        Json.intAt(node, "RestartCount"),
        command,
        Json.text(config, "User"),
        Json.map(config, "Labels"),
        Json.envKeys(config, "Env"),
        mounts,
        bindings,
        Json.textAt(host, "RestartPolicy", "Name"),
        Json.longAt(host, "Memory"),
        Json.longAt(host, "PidsLimit"),
        Json.intAt(host, "OomScoreAdj"),
        Json.boolAt(host, "Privileged"),
        Json.boolAt(host, "ReadonlyRootfs"),
        Json.strings(host, "CapAdd"),
        Json.strings(host, "CapDrop"));
  }
}

package eu.wohlben.qits.system.docker;

import java.util.List;

/**
 * Every non-interactive docker command line this service will ever run, as pure functions.
 *
 * <p>No I/O, no {@link ProcessBuilder}, no config, no clock — a reference goes in and a {@code
 * List<String>} comes out. That is what lets the argvs be asserted <b>element for element</b> in a
 * docker-free suite, and it is why they are assembled here rather than inside the runner: a lost
 * flag is invisible everywhere else until it is invisible in production.
 *
 * <p><b>Two output shapes, and the difference matters to every parser.</b>
 *
 * <ul>
 *   <li>{@code ls --format '{{json .}}'} prints ONE JSON OBJECT PER LINE, not an array. The fields
 *       are the CLI's own column set (a flattened, pre-formatted view: {@code "Status":"Up 4 hours
 *       (healthy)"}), not the API's.
 *   <li>{@code inspect} prints a JSON ARRAY of the API's own documents — nested, complete, and
 *       unformatted ({@code State.StartedAt} as an RFC-3339 string).
 * </ul>
 *
 * <p>Both are used deliberately: a list view wants the CLI's already-humanised columns, and a
 * detail view wants the daemon's real fields. A parser written against the wrong one gets nulls,
 * which is why the fixtures in the suite are real captures of both.
 *
 * <p><b>Nothing here writes.</b> The whole vocabulary is reads plus {@code rm -f} of containers
 * this service itself labelled, and {@code pull} of the one image it is configured with. There is
 * no {@code scale}, no {@code restart}, no {@code update}, no {@code config create}. Adding one is
 * a decision about what this console is, not a refactor.
 */
public final class DockerArgv {

  /** One JSON object per line — the {@code ls} shape. */
  private static final String JSON_LINES = "{{json .}}";

  private DockerArgv() {}

  // --- the host ---------------------------------------------------------------------------------

  /** Everything about the daemon and this machine, including the {@code Swarm} block. */
  public static List<String> info(String binary) {
    return List.of(binary, "info", "--format", JSON_LINES);
  }

  /**
   * Disk usage per class (images, containers, volumes, build cache) — one JSON object per line,
   * with sizes already rendered as {@code "308.3GB"}. The CLI does that formatting and the API does
   * not; showing the same string the operator would see in a shell is the point.
   */
  public static List<String> systemDf(String binary) {
    return List.of(binary, "system", "df", "--format", JSON_LINES);
  }

  /** The daemon liveness probe. Cheap, and it is the one call the boot sweep makes first. */
  public static List<String> version(String binary) {
    return List.of(binary, "version", "--format", "{{.Server.Version}}");
  }

  // --- the swarm --------------------------------------------------------------------------------

  public static List<String> nodeLs(String binary) {
    return List.of(binary, "node", "ls", "--format", JSON_LINES);
  }

  public static List<String> nodeInspect(String binary, String ref) {
    return List.of(binary, "node", "inspect", DockerIdentifiers.requireSwarmRef("node", ref));
  }

  public static List<String> serviceLs(String binary) {
    return List.of(binary, "service", "ls", "--format", JSON_LINES);
  }

  public static List<String> serviceInspect(String binary, String ref) {
    return List.of(binary, "service", "inspect", DockerIdentifiers.requireSwarmRef("service", ref));
  }

  /**
   * A service's tasks. {@code --no-trunc} because the default truncates the error column, and a
   * failed task's error is the entire reason anybody opens this view.
   */
  public static List<String> servicePs(String binary, String ref) {
    return List.of(
        binary,
        "service",
        "ps",
        DockerIdentifiers.requireSwarmRef("service", ref),
        "--no-trunc",
        "--format",
        JSON_LINES);
  }

  public static List<String> configLs(String binary) {
    return List.of(binary, "config", "ls", "--format", JSON_LINES);
  }

  public static List<String> configInspect(String binary, String ref) {
    return List.of(binary, "config", "inspect", DockerIdentifiers.requireSwarmRef("config", ref));
  }

  public static List<String> secretLs(String binary) {
    return List.of(binary, "secret", "ls", "--format", JSON_LINES);
  }

  // --- this node --------------------------------------------------------------------------------

  /**
   * The node's containers. {@code all} includes the stopped ones — which is what an operator wants
   * after something died, and why it is a query parameter rather than an assumption.
   */
  public static List<String> psAll(String binary, boolean all) {
    return all
        ? List.of(binary, "ps", "-a", "--no-trunc", "--format", JSON_LINES)
        : List.of(binary, "ps", "--no-trunc", "--format", JSON_LINES);
  }

  public static List<String> containerInspect(String binary, String ref) {
    return List.of(binary, "container", "inspect", DockerIdentifiers.requireContainerRef(ref));
  }

  /**
   * The three facts the exec path needs before it opens a terminal, in one call: the canonical id,
   * whether the container is running, and its name. A format string rather than a full inspect
   * because this runs on the request path of every terminal open.
   */
  public static List<String> containerProbe(String binary, String ref) {
    return List.of(
        binary,
        "container",
        "inspect",
        "--format",
        "{{.Id}}|{{.State.Running}}|{{.Name}}",
        DockerIdentifiers.requireContainerRef(ref));
  }

  /** A container's recent log tail. Timestamps off: the lines carry the workload's own format. */
  public static List<String> logsTail(String binary, String ref, int tail) {
    return List.of(
        binary,
        "logs",
        "--tail",
        String.valueOf(tail),
        DockerIdentifiers.requireContainerRef(ref));
  }

  /** {@code --no-trunc} so an image id is the id and not a prefix of it. */
  public static List<String> imageLs(String binary) {
    return List.of(binary, "image", "ls", "--no-trunc", "--format", JSON_LINES);
  }

  public static List<String> volumeLs(String binary) {
    return List.of(binary, "volume", "ls", "--format", JSON_LINES);
  }

  public static List<String> networkLs(String binary) {
    return List.of(binary, "network", "ls", "--format", JSON_LINES);
  }

  // --- this service's own containers --------------------------------------------------------------

  /**
   * The ids of every container this service owns, running or not — the boot sweep's first half.
   *
   * <p>The filter is the OWNER label, whose value is this application's name, so two platforms
   * sharing one daemon do not sweep each other's terminals. It is not a name prefix: a name can be
   * anything, a label is what this service put there.
   */
  public static List<String> psByOwner(String binary, String owner) {
    return List.of(binary, "ps", "-aq", "--filter", "label=qits.system.owner=" + owner);
  }

  /**
   * Remove containers by id — the boot sweep's second half, and the teardown of a glances terminal
   * whose CLI was killed (the container outlives it; {@code --rm} only acts on container exit).
   */
  public static List<String> rmForce(String binary, List<String> ids) {
    return java.util.stream.Stream.concat(
            java.util.stream.Stream.of(binary, "rm", "-f"), ids.stream())
        .toList();
  }

  /**
   * Fetch the glances image ahead of the first session. Best-effort and logged, never fatal: a
   * failed pull only means the first terminal shows docker's own pull output while it waits.
   */
  public static List<String> pull(String binary, String image) {
    return List.of(binary, "pull", image);
  }
}

package eu.wohlben.qits.system.docker;

import eu.wohlben.qits.system.terminal.ExecShell;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The two interactive docker command lines, as pure functions. Separate from {@link DockerArgv}
 * because these are not reads: they run on a pseudo-terminal with no deadline and no output bound,
 * and each of them is a privilege decision written as a flag list.
 *
 * <p><b>The argv IS the sandbox.</b> Every flag below is asserted element for element in the suite,
 * because a flag lost in a refactor is invisible until it is invisible in production.
 */
public final class TerminalArgv {

  /** The label namespace this service marks its own containers with. */
  public static final String LABEL_SESSION = "qits.system.session";

  public static final String LABEL_KIND = "qits.system.kind";

  /**
   * The label the boot sweep filters on. Its VALUE is the application name, so two platforms on one
   * daemon can each reap their own leftovers and neither reaps the other's.
   */
  public static final String LABEL_OWNER = "qits.system.owner";

  /** What every terminal child is told it is running under, so curses programs draw in colour. */
  public static final String TERM = "xterm-256color";

  private TerminalArgv() {}

  /** The container name a glances session runs under — derived, so the teardown can find it. */
  public static String glancesContainerName(UUID sessionId) {
    return "qits-system-glances-" + sessionId;
  }

  /**
   * The host monitor.
   *
   * <p>Every flag, and why:
   *
   * <ul>
   *   <li>{@code --rm} — a monitor is not a record; when it exits there is nothing to read back.
   *       (It only fires on CONTAINER exit, which is why an explicit teardown still does {@code rm
   *       -f} by the name above.)
   *   <li>{@code -it} — the whole point: a terminal on both ends.
   *   <li>{@code --name} + three labels — so the container can be found by a teardown that has only
   *       the session id, and by a boot sweep that has only the owner.
   *   <li>{@code --pid host} — glances reports PROCESSES. In its own pid namespace it would show
   *       one, itself, which is a monitor of nothing.
   *   <li>{@code --network host} — upstream's own recipe. Glances reads network counters from its
   *       netns, so in its own it reports the container's veth rather than the host's interfaces.
   *       Nothing is published: in terminal mode glances runs no listener.
   *   <li>the docker socket, READ-ONLY — the {@code -full} image variant carries the docker plugin,
   *       which is what makes the container list panel work. Read-only because a monitor never
   *       needs to write to it, and this is a container we hand a socket to.
   *   <li>{@code --cap-drop ALL} and {@code --security-opt no-new-privileges} — it reads {@code
   *       /proc} and a socket; it needs no capability at all.
   *   <li>{@code --memory}/{@code --memory-swap} equal, {@code --pids-limit} — a monitor that leaks
   *       must not take the host with it, and equal memory and memory-swap is what turns off swap
   *       rather than granting unlimited swap.
   *   <li>{@code --oom-score-adj 500} — if the host runs out, the kernel takes this before it takes
   *       a platform service. The same doctrine as the spawned-workload scores elsewhere on the
   *       platform (ci 1000, agents 800, workspaces 600); a monitor sits above all of them.
   * </ul>
   *
   * @param extraArgs glances' own flags from config, appended after the image — empty by default
   */
  public static List<String> runGlances(
      String binary, String image, List<String> extraArgs, UUID sessionId, String owner) {
    List<String> argv = new ArrayList<>();
    argv.add(binary);
    argv.add("run");
    argv.add("--rm");
    argv.add("-it");
    argv.add("--name");
    argv.add(glancesContainerName(sessionId));
    argv.add("--label");
    argv.add(LABEL_SESSION + "=" + sessionId);
    argv.add("--label");
    argv.add(LABEL_KIND + "=glances");
    argv.add("--label");
    argv.add(LABEL_OWNER + "=" + owner);
    argv.add("--pid");
    argv.add("host");
    argv.add("--network");
    argv.add("host");
    argv.add("-v");
    argv.add("/var/run/docker.sock:/var/run/docker.sock:ro");
    argv.add("--cap-drop");
    argv.add("ALL");
    argv.add("--security-opt");
    argv.add("no-new-privileges");
    argv.add("--memory");
    argv.add("512m");
    argv.add("--memory-swap");
    argv.add("512m");
    argv.add("--pids-limit");
    argv.add("256");
    argv.add("--oom-score-adj");
    argv.add("500");
    argv.add("-e");
    argv.add("TERM=" + TERM);
    argv.add(image);
    argv.addAll(extraArgs);
    return List.copyOf(argv);
  }

  /**
   * A shell inside one of this node's containers.
   *
   * <p>{@code containerId} is the CANONICAL 64-hex id the daemon printed when the reference was
   * resolved — never the string the caller sent. {@link DockerIdentifiers#requireFullId} is the
   * belt on that: if resolution ever produced anything else, no terminal opens.
   *
   * <p>{@code QITS_SYSTEM_SESSION} is in the child's environment so that a shell somebody finds
   * later in a {@code ps} can be traced back to the session that opened it.
   */
  public static List<String> execShell(
      String binary, String containerId, ExecShell shell, UUID sessionId) {
    return List.of(
        binary,
        "exec",
        "-it",
        "-e",
        "TERM=" + TERM,
        "-e",
        "QITS_SYSTEM_SESSION=" + sessionId,
        DockerIdentifiers.requireFullId(containerId),
        shell.binary());
  }
}

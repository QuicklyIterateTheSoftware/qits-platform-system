package eu.wohlben.qits.system.terminal;

/**
 * What a terminal was asked to run — the validated, resolved form of a POST body.
 *
 * <p>SEALED, because the two cases are the whole vocabulary and the registry switches on them: a
 * third one is a decision somebody makes here, not a string that arrives from outside. The wire
 * request is a FLAT record (kind, container, shell) and this is what it becomes once the container
 * reference has been checked against the daemon — by the time a launch exists, {@code containerId}
 * is the canonical 64-hex id the daemon printed and never the caller's string.
 */
public sealed interface TerminalLaunch {

  /** Which kind this is, for the wire and for the registry's find-or-create rule. */
  TerminalKind kind();

  /**
   * The host monitor. It carries nothing: there is one host, the image comes from config, and at
   * most one glances session exists at a time.
   */
  record Glances() implements TerminalLaunch {
    @Override
    public TerminalKind kind() {
      return TerminalKind.GLANCES;
    }
  }

  /**
   * A shell in a container of this node.
   *
   * @param containerId the CANONICAL id the daemon printed, never what the caller sent
   * @param containerName the name the daemon printed, for showing the session in a list
   * @param shell which shell to exec
   */
  record Exec(String containerId, String containerName, ExecShell shell) implements TerminalLaunch {
    @Override
    public TerminalKind kind() {
      return TerminalKind.EXEC;
    }
  }
}

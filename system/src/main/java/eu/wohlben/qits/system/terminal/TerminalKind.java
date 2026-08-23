package eu.wohlben.qits.system.terminal;

/** What a terminal is running. The wire value of a session's {@code kind}. */
public enum TerminalKind {
  /** The host monitor: a {@code glances} container on the host's pid and network namespaces. */
  GLANCES,
  /** A shell inside one of this node's containers. */
  EXEC
}

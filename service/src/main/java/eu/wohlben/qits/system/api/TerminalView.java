package eu.wohlben.qits.system.api;

import eu.wohlben.qits.system.terminal.TerminalKind;
import eu.wohlben.qits.system.terminal.TerminalLaunch;
import eu.wohlben.qits.system.terminal.TerminalSession;
import java.time.Instant;

/**
 * A terminal as the client sees it.
 *
 * <p>{@code socketPath} is spelled out rather than left for the client to build. The WebSocket path
 * is a LITERAL in {@code TerminalSocket} — `@WebSocket` does not follow `quarkus.rest.path` — so a
 * client composing it by hand would be the second place that literal lives, and the two would
 * drift the first time the segment moved.
 */
public record TerminalView(
    String id,
    TerminalKind kind,
    Container container,
    String shell,
    Instant createdAt,
    String createdBy,
    int attachedClients,
    String socketPath) {

  /** Which container an EXEC terminal is in; null for GLANCES. */
  public record Container(String id, String name) {}

  public static TerminalView of(TerminalSession session) {
    Container container = null;
    String shell = null;
    if (session.launch() instanceof TerminalLaunch.Exec exec) {
      container = new Container(exec.containerId(), exec.containerName());
      shell = exec.shell().binary();
    }
    return new TerminalView(
        session.id().toString(),
        session.kind(),
        container,
        shell,
        session.createdAt(),
        session.createdBy(),
        session.attachedClients(),
        TerminalSocket.PATH_PREFIX + session.id());
  }
}

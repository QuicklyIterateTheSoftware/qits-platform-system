package eu.wohlben.qits.system.terminal;

/**
 * A destination for a terminal session's output — the session fans every chunk of PTY output out to
 * all attached sinks. Kept framework-free (no websockets.next type) so the session can live in the
 * domain module; the service module's WebSocket adapts a connection to a sink.
 *
 * <p>Copied from qits-projects' {@code CommandOutputSink}, renamed for this context. It is a
 * two-method, dependency-free SPI, so copying it is cheaper and looser than depending on another
 * service's jar for it.
 */
public interface TerminalOutputSink {

  /**
   * Forward a chunk of already terminal-encoded output to the client (written verbatim to xterm).
   */
  void write(String data);

  /** Whether this sink can still receive output; the sender prunes sinks that report false. */
  boolean isOpen();
}

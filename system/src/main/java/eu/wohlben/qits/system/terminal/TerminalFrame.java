package eu.wohlben.qits.system.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What a browser sends up a terminal socket, parsed.
 *
 * <p>The protocol is the platform's existing one, unchanged so one xterm client works against every
 * terminal on it: {@code {"type":"data","data":"…"}} for keystrokes and {@code
 * {"type":"resize","cols":N,"rows":M}} for a window size. The server direction is not framed at
 * all — it is raw PTY text, because anything else would mean re-encoding a byte stream a terminal
 * emulator is about to decode itself.
 *
 * <p>Parsing lives here rather than in the socket so it can be tested without a server, and so an
 * unparseable frame has ONE answer: {@code null}, which the socket drops. A terminal is a stream;
 * closing it because one frame was malformed would lose the session over a client bug.
 */
public sealed interface TerminalFrame {

  /** Keystrokes. The text goes to the PTY as UTF-8 bytes, verbatim. */
  record Data(String text) implements TerminalFrame {}

  /** A window size. The registry turns it into a {@code TIOCSWINSZ} on the master. */
  record Resize(int cols, int rows) implements TerminalFrame {}

  /** The size a frame that names no size means — xterm's own default. */
  int DEFAULT_COLUMNS = 80;

  int DEFAULT_ROWS = 24;

  /**
   * Read one client frame.
   *
   * @return the frame, or null when the message is not JSON, names no known type, or is a
   *     {@code data} frame with no text
   */
  static TerminalFrame parse(ObjectMapper mapper, String message) {
    if (message == null || message.isEmpty()) {
      return null;
    }
    JsonNode node;
    try {
      node = mapper.readTree(message);
    } catch (Exception e) {
      return null;
    }
    if (node == null || !node.isObject()) {
      return null;
    }
    String type = node.path("type").asText("");
    return switch (type) {
      case "data" -> {
        JsonNode data = node.get("data");
        // A missing `data` is not an empty keystroke, it is a broken frame: writing "" to the PTY
        // would be a silent no-op that hides the client bug.
        yield data == null || !data.isTextual() ? null : new Data(data.asText());
      }
      case "resize" ->
          new Resize(
              positiveOr(node.path("cols").asInt(DEFAULT_COLUMNS), DEFAULT_COLUMNS),
              positiveOr(node.path("rows").asInt(DEFAULT_ROWS), DEFAULT_ROWS));
      default -> null;
    };
  }

  /**
   * A window size has to be positive: the ioctl is best-effort and would ignore a zero, but a
   * negative one cast to an unsigned short is a terminal 65000 columns wide.
   */
  private static int positiveOr(int value, int fallback) {
    return value > 0 && value <= 10_000 ? value : fallback;
  }
}

package eu.wohlben.qits.system.stories.support;

import eu.wohlben.qits.system.testdocker.TerminalClient;
import eu.wohlben.qits.userflows.NetworkCapture;
import eu.wohlben.qits.userflows.NetworkEdge;
import java.net.URI;
import java.util.Map;

/**
 * A terminal as a story drives it — {@link TerminalClient}, which is the suite's ordinary WebSocket
 * client, plus the <b>edges</b>.
 *
 * <h2>The plane the framework ships no tap for</h2>
 *
 * <p>{@code NetworkTaps.restAssured} sees a request and a status; a terminal is neither. It is one
 * dialled connection carrying a stream in both directions with no request boundary in it, and it
 * travels on the JDK's own WebSocket client, which no RestAssured filter is in front of. So this
 * plane is instrumented with {@link NetworkCapture#observe} at the call sites.
 *
 * <p><b>Every one of those calls is synchronous, on the story thread</b>, and that is the whole
 * design of this class rather than an implementation detail. {@link NetworkCapture#actor()} is a
 * sticky value the extension resets at every story border, and the framework's rule is that a tap
 * must read it on the thread that acted. A listener callback firing on the JDK's HTTP client
 * executor would read whatever actor is current when the frame lands, which is a different story's.
 * So nothing is observed from a callback: the story <i>waits</i> for the output it expects and the
 * edge is recorded where the waiting returned — the same discipline that makes a refused upgrade
 * recorded in a catch block incapable of lying.
 *
 * <p>It is also what bounds the observation. A frame arriving after {@link #close()} would land in
 * the next story's diagram; here there is no path by which a frame records anything at all unless a
 * story asked for it.
 *
 * <h2>Two kinds, and the split is the vocabulary's own</h2>
 *
 * <ul>
 *   <li><b>{@code socket}</b> — the dial. One edge for the connection the browser holds open, and
 *       the direction is who dialled: the operator's browser dials in, always.
 *   <li><b>{@code event}</b> — one per frame pushed over that connection, in whichever direction it
 *       was pushed. {@code data} and {@code resize} are the client's; the raw PTY text and the
 *       closing handshake are the server's.
 * </ul>
 *
 * <p>Labels carry the protocol's own vocabulary and values that cannot vary between runs — a window
 * size the story typed, a close code the protocol defines. The session id is not in any of them: it
 * is minted per terminal and would move the story's {@code networkHash} on every run, so the socket
 * label is written template-shaped in {@link StoryTarget#socketLabel}.
 */
public final class StoryTerminal implements AutoCloseable {

  /** What the socket edge's label says when the upgrade succeeded and the session was joined. */
  public static final String ATTACHED = "attached";

  /** …and when it did not. Deliberately a word: from a client a refused upgrade has no status. */
  public static final String REFUSED = "refused";

  /** The client's own frames, as the wire protocol names them. */
  public static final String DATA_FRAME = "data frame";

  public static final String OUTPUT = "pty output";

  private final TerminalClient client;

  /** Read once, on the story thread, at the moment of the dial. Never re-read from a callback. */
  private final String actor;

  private StoryTerminal(TerminalClient client, String actor) {
    this.client = client;
    this.actor = actor;
  }

  /**
   * Dial one terminal's socket as a browser would, and record the connection.
   *
   * <p>The edge is recorded only once the handshake has completed, which is what makes it evidence:
   * a refused upgrade throws out of {@code connect} and records nothing here — the story that
   * expects one observes {@link #refusedUpgrade()} from its own catch block instead.
   */
  public static StoryTerminal dial(URI httpBase, String socketPath, Map<String, String> headers) {
    String actor = NetworkCapture.actor();
    TerminalClient client =
        TerminalClient.connect(TerminalClient.socketUri(httpBase, socketPath), headers);
    NetworkCapture.observe(
        NetworkEdge.SOCKET, actor, StoryTarget.SERVICE, StoryTarget.socketLabel(ATTACHED));
    return new StoryTerminal(client, actor);
  }

  /**
   * Record an upgrade this service refused.
   *
   * <p>For the catch block of a story that attempted one, and it can never lie: an upgrade that
   * unexpectedly succeeded leaves the {@code try} by another path and this is not reached, so no
   * "refused" arrow is ever drawn for a handshake that was allowed.
   */
  public static void refusedUpgrade() {
    NetworkCapture.observe(
        NetworkEdge.SOCKET,
        NetworkCapture.actor(),
        StoryTarget.SERVICE,
        StoryTarget.socketLabel(REFUSED));
  }

  /** Type at the terminal: one {@code data} frame, and the edge for it. */
  public StoryTerminal type(String text) {
    send(() -> client.sendData(text), "typed " + text.strip());
    pushedByTheClient(DATA_FRAME);
    return this;
  }

  /**
   * Resize the terminal, as a browser does when its window changes.
   *
   * <p>The size is in the label because the story typed it: it is authored, not generated, and it
   * is the value the child reports back when the kernel signals it.
   */
  public StoryTerminal resize(int columns, int rows) {
    send(() -> client.sendResize(columns, rows), "resized to " + columns + "x" + rows);
    pushedByTheClient("resize frame " + columns + "x" + rows);
    return this;
  }

  /**
   * Send one frame, and turn "the socket was already gone" into a failure that says what the
   * terminal had printed before it went.
   *
   * <p>A dead session reports itself as {@code IOException: Output closed} out of the JDK's send
   * task, which names the encoder and nothing about the story. The transcript is the only thing
   * that says whether the child crashed, printed an error, or was never there.
   */
  private void send(Runnable frame, String what) {
    try {
      frame.run();
    } catch (RuntimeException gone) {
      throw new AssertionError(
          "the terminal was already closed when the story " + what + "; it had printed: <<<"
              + client.text() + ">>>",
          gone);
    }
  }

  /** Send something the server cannot read. No edge: a dropped frame is a step, not a dependency. */
  public StoryTerminal typeUnreadable(String frame) {
    client.sendRaw(frame);
    return this;
  }

  /**
   * Wait until the terminal has printed {@code needle}, then record that output came back.
   *
   * <p>One label for every wait, so the frames collapse into a single arrow: how many WebSocket
   * messages a stream of PTY output arrives in belongs to the kernel's read boundaries, not to the
   * story, and a diagram with one arrow per read would document the buffer size.
   */
  public StoryTerminal awaitOutput(String needle) throws InterruptedException {
    client.awaitText(needle);
    pushedByTheServer(OUTPUT);
    return this;
  }

  /** Everything the terminal has printed so far. */
  public String text() {
    return client.text();
  }

  /**
   * Wait for the server to close, and record the close it sent.
   *
   * <p>The code is in the label because it <b>is</b> the protocol: 1000 means final — the session
   * ended and the client must not reconnect — and anything else is the client's signal to reconnect
   * and take the scrollback replay. A story that awaits a close has, by the time this returns, also
   * waited out every frame the server had left to send, which is what keeps a late one out of the
   * next story's diagram.
   */
  public int awaitClose(String why) throws InterruptedException {
    int code = client.awaitClose();
    pushedByTheServer(closeLabel(code, why));
    return code;
  }

  /** The label a close renders as — spelled here so an assertion cannot word it differently. */
  public static String closeLabel(int code, String why) {
    return "close " + code + " (" + why + ")";
  }

  @Override
  public void close() {
    client.close();
  }

  private void pushedByTheClient(String label) {
    NetworkCapture.observe(NetworkEdge.EVENT, actor, StoryTarget.SERVICE, label);
  }

  private void pushedByTheServer(String label) {
    NetworkCapture.observe(NetworkEdge.EVENT, StoryTarget.SERVICE, actor, label);
  }
}

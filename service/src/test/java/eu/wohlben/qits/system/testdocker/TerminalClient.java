package eu.wohlben.qits.system.testdocker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A terminal client for the suite: the JDK's own WebSocket, so no test dependency is added for
 * something the platform already has.
 *
 * <p>It collects every text frame into one buffer rather than a queue of messages, because the
 * server direction is a STREAM — raw PTY output, split wherever a read happened to end — and a test
 * that waited for a whole "message" would be waiting for a boundary the protocol does not have.
 */
public final class TerminalClient implements AutoCloseable {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  private final WebSocket socket;
  private final StringBuilder received = new StringBuilder();
  private final CountDownLatch closed = new CountDownLatch(1);
  private final AtomicInteger closeCode = new AtomicInteger(-1);
  private final AtomicReference<String> closeReason = new AtomicReference<>("");

  private TerminalClient(URI uri, Map<String, String> headers) {
    WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
    headers.forEach(builder::header);
    this.socket = builder.buildAsync(uri, new Collector()).join();
  }

  /** Connect as a browser would, with whatever identity headers the caller needs. */
  public static TerminalClient connect(URI uri, Map<String, String> headers) {
    return new TerminalClient(uri, headers);
  }

  /** Turn an http test URI into the ws one for a terminal. */
  public static URI socketUri(URI httpBase, String socketPath) {
    String authority = httpBase.getAuthority();
    return URI.create("ws://" + authority + socketPath);
  }

  public void sendData(String text) {
    socket.sendText("{\"type\":\"data\",\"data\":" + quote(text) + "}", true).join();
  }

  public void sendResize(int cols, int rows) {
    socket.sendText("{\"type\":\"resize\",\"cols\":" + cols + ",\"rows\":" + rows + "}", true).join();
  }

  /** Send something the server cannot read, to prove it drops the frame rather than the session. */
  public void sendRaw(String message) {
    socket.sendText(message, true).join();
  }

  public synchronized String text() {
    return received.toString();
  }

  /** Wait until the output so far contains {@code needle}. */
  public void awaitText(String needle) throws InterruptedException {
    long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
    while (System.currentTimeMillis() < deadline) {
      if (text().contains(needle)) {
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError("never received '" + needle + "'; got: " + text());
  }

  /** Wait for the server to close, and answer the code it closed with. */
  public int awaitClose() throws InterruptedException {
    if (!closed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
      throw new AssertionError("the server never closed the socket; got: " + text());
    }
    return closeCode.get();
  }

  public String closeReason() {
    return closeReason.get();
  }

  @Override
  public void close() {
    try {
      socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    } catch (RuntimeException ignored) {
      // Already gone, which is the ordinary case after the server closed first.
    }
  }

  private static String quote(String text) {
    StringBuilder out = new StringBuilder("\"");
    for (char c : text.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        default -> out.append(c);
      }
    }
    return out.append('"').toString();
  }

  private final class Collector implements WebSocket.Listener {

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      synchronized (TerminalClient.this) {
        received.append(data);
      }
      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      closeCode.set(statusCode);
      closeReason.set(reason);
      closed.countDown();
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      closed.countDown();
    }
  }
}

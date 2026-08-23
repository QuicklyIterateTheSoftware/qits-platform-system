package eu.wohlben.qits.system.docker;

import eu.wohlben.qits.system.error.BadRequestException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates every caller-supplied string that reaches a docker argv — the {@code
 * ContainersIdentifiers} posture, applied to this service's much smaller vocabulary.
 *
 * <p>Everything checked here arrives in a URL path or a JSON body from a browser, so all of it is
 * attacker-reachable by design even behind {@code qits:admin} — a link is a thing somebody can be
 * sent.
 *
 * <p><b>Defence in depth, not the only guard.</b> An argv is assembled element by element for
 * {@link ProcessBuilder}, which never shell-splits. What these checks are really for is the
 * <b>element boundaries a value could forge</b>: a leading {@code -} would let a positional
 * argument be read as an option ({@code docker logs --tail=1 -f} is a different command), and a
 * {@code :} or {@code /} in a reference would let a container id turn into something with a
 * different shape. There are two checkpoints — the controller, and this class, always.
 *
 * <p><b>And there is a third, stronger one for exec.</b> The reference a caller sends is validated
 * here and then RESOLVED through {@code docker container inspect}; the argv that opens the shell
 * carries the canonical 64-hex id the daemon printed, never the caller's string. So the belt below
 * is what stops a malformed reference reaching the daemon at all, and the resolution is what stops
 * a well-formed one reaching the terminal as anything but an id.
 */
public final class DockerIdentifiers {

  private DockerIdentifiers() {}

  /**
   * A container reference as a person types it: an id, an id prefix, or a name. Docker's own name
   * charset is {@code [a-zA-Z0-9][a-zA-Z0-9_.-]+}, and a swarm task name — {@code
   * dev-qits-ci.1.k4g0nn1ld7o2} — is inside it, which is what this service is mostly asked for.
   * 128 is well past docker's longest generated name and short enough to log.
   */
  private static final Pattern CONTAINER_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

  /** What the daemon prints as an id: 64 hex characters, and that is the only exec target. */
  private static final Pattern FULL_ID = Pattern.compile("[0-9a-f]{64}");

  /**
   * A swarm object id — node, service, config, secret. Swarm ids are 25 lowercase alphanumerics;
   * a service or node may equally be addressed by NAME, so the charset is docker's name charset
   * and the length cap is the same 128 as a container reference.
   */
  private static final Pattern SWARM_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

  /** How much of a rejected value is echoed back: enough to recognise, short enough to log. */
  private static final int ECHO_MAX = 64;

  /** A container reference from a path or a body. */
  public static String requireContainerRef(String value) {
    return require("container", CONTAINER_REF, value);
  }

  /** A node, service, config or secret reference from a path. */
  public static String requireSwarmRef(String field, String value) {
    return require(field, SWARM_REF, value);
  }

  /**
   * The canonical id the daemon printed. Called on the way OUT of resolution rather than on the way
   * in: if the daemon ever answered something that is not an id, the argv must not carry it.
   */
  public static String requireFullId(String value) {
    return require("container id", FULL_ID, value);
  }

  /** A log tail count. Docker takes any integer; a caller does not get to ask for a heap. */
  public static int requireTail(int value, int max) {
    if (value < 1 || value > max) {
      throw new BadRequestException("tail must be between 1 and " + max + ", got " + value);
    }
    return value;
  }

  /**
   * A terminal session id from a path. It is this service's own UUID, so anything else is a 404
   * rather than a 400 — the caller named a session, it just does not exist.
   */
  public static UUID parseSessionId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      return null;
    }
  }

  private static String require(String field, Pattern pattern, String value) {
    if (value == null || !pattern.matcher(value).matches()) {
      throw new BadRequestException("not a valid " + field + ": " + echo(value));
    }
    return value;
  }

  /**
   * A rejected value goes into a log line and an HTTP body, so control characters are stripped: one
   * that carried a newline could forge a second log line.
   */
  private static String echo(String value) {
    if (value == null) {
      return "(none)";
    }
    String cleaned = value.replaceAll("\\p{Cntrl}", "?");
    return cleaned.length() <= ECHO_MAX ? cleaned : cleaned.substring(0, ECHO_MAX) + "…";
  }
}

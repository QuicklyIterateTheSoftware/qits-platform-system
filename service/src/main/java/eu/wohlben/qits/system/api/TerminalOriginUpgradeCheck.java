package eu.wohlben.qits.system.api;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Refuses a WebSocket handshake that came from another site.
 *
 * <p><b>Why a check of our own.</b> The same-origin policy does not apply to WebSockets: a page on
 * any origin may open one to this service, and the browser will attach the operator's cookies to
 * the handshake. Every other route here is protected by the fact that a cross-site {@code fetch}
 * cannot read the answer; a socket has no such protection, because the attacker's page reads the
 * frames directly. So a terminal reachable from any origin is a shell on the platform host handed
 * to whichever site an operator happened to visit while logged in.
 *
 * <p><b>The rule.</b> When the handshake carries an {@code Origin}, its host must be the host this
 * request was addressed to. Behind the edge that is {@code X-Forwarded-Host}; direct, it is {@code
 * Host}. The rule survives this service having a host of its own: the page is served from {@code
 * system.<env>.<domain>}, so the socket it opens carries that origin and the edge forwards that same
 * name — the two agree, exactly as they did when the page came from the environment host. A handshake with NO {@code Origin} is allowed: browsers always send one, so the
 * absence means a non-browser client — a machine, a test, {@code websocat} — and those are not
 * subject to the attack this exists to stop (nobody's ambient credential is being borrowed).
 *
 * <p>{@code appliesTo} is not narrowed: this service has one socket, and a check that had to be
 * remembered when a second is added is a check that will be forgotten.
 */
@ApplicationScoped
public class TerminalOriginUpgradeCheck implements HttpUpgradeCheck {

  private static final Logger LOG = Logger.getLogger(TerminalOriginUpgradeCheck.class);

  /** What a refused handshake answers. Not 401: the credential was fine, the origin was not. */
  private static final int FORBIDDEN = 403;

  @Override
  public Uni<CheckResult> perform(HttpUpgradeContext context) {
    String origin = context.httpRequest().getHeader("Origin");
    if (origin == null || origin.isBlank()) {
      return CheckResult.permitUpgrade();
    }
    String expected = expectedHost(context);
    String actual = hostOf(origin);
    if (expected != null && expected.equalsIgnoreCase(actual)) {
      return CheckResult.permitUpgrade();
    }
    LOG.warnf(
        "Refusing a terminal handshake from origin %s; this service answers as %s", origin, expected);
    return CheckResult.rejectUpgrade(FORBIDDEN);
  }

  /**
   * The host this request was addressed to. The gateway rewrites {@code Host} to the internal
   * service name, so its forwarded header is the one that matches what the browser typed — and it
   * may carry a list, of which the FIRST entry is the original client's.
   */
  private static String expectedHost(HttpUpgradeContext context) {
    String forwarded = context.httpRequest().getHeader("X-Forwarded-Host");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return context.httpRequest().getHeader("Host");
  }

  /** The authority of an origin — {@code https://qits.example:8443} becomes {@code qits.example:8443}. */
  private static String hostOf(String origin) {
    String value = origin.trim();
    int scheme = value.indexOf("://");
    if (scheme >= 0) {
      value = value.substring(scheme + 3);
    }
    int slash = value.indexOf('/');
    return slash >= 0 ? value.substring(0, slash) : value;
  }
}

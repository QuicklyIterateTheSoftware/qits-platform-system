package eu.wohlben.qits.system.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.servicemock.idp.MockIdp;
import eu.wohlben.qits.system.testdocker.FakeDocker;
import eu.wohlben.qits.system.testdocker.TerminalClient;
import eu.wohlben.qits.userflows.Interactions;
import eu.wohlben.qits.userflows.UserStory;
import eu.wohlben.qits.userflows.UserStoryDescription;
import eu.wohlben.qits.userflows.report.ReportAssertions;
import eu.wohlben.qits.userflows.report.UserflowReport;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;

/**
 * The whole service as it is <b>packaged</b> — like {@link PackagedSurfaceIT} beside it, but with
 * the OIDC tenant <b>on</b>, which no {@code @QuarkusTest} here can prove.
 *
 * <p>The shipped tenant is {@code quarkus.oidc.tenant-enabled=${qits.auth.machine.required:false}},
 * and <b>no suite in this repository turns that gate on at all</b> — the test
 * application.properties says so in as many words ("the OIDC tenant is disabled because
 * qits.auth.machine.required defaults false"), which is what keeps a clone-alone {@code ./mvnw
 * verify} free of an issuer. The consequence is that the entire shipped {@code quarkus.oidc.*}
 * block — auth-server-url with {@code discovery-enabled=false} and {@code jwks-path=jwks} joined
 * onto it, the boot-time fetch that {@code connection-delay} retries, audience enforcement,
 * groups→roles mapping — is exercised NOWHERE. This is the one place it runs. The far side is
 * {@link MockIdp}, whose recordings make the interaction assertable on <b>both ends</b>.
 *
 * <p>It is also this repo's first <b>userflow</b>: the proof doubles as documentation, emitted
 * under {@code target/userstories/} with the interactions drawn as a sequence diagram. The stories
 * are browserless (no {@code Flow} parameter), so no Chromium is involved anywhere.
 *
 * <p><b>The route both stories drive is {@code GET /system/api/overview}</b>, and it is chosen as
 * the least side-effectful read this service has. Every REST route here is {@code
 * @RolesAllowed({"qits:admin", "qits:system"})}, so the machine role reaches all of them and the
 * choice is about what the request DOES, not about what it takes:
 *
 * <ul>
 *   <li>it names nothing. It is the only guarded read with no path parameter — every other one
 *       carries a node id (and a foreign one is a deliberate {@code 409 NODE_REMOTE}), a container
 *       id, a service id, or a config id whose DATA is decoded into the answer;
 *   <li>it is introspection of THIS machine and no peer: one {@code docker info} plus one {@code
 *       docker system df}, and even the swarm block comes out of the daemon's own {@code info}
 *       document rather than from a cluster query. Nothing here answers about a host this IT would
 *       then have to stand in for — the qits-platform-deployments pin-ledger property;
 *   <li>and it cannot write, because nothing in this service can. The one call that is not a read
 *       is {@code POST /system/api/terminals}, which forks a real child on a real pseudo-terminal
 *       and is the obvious other candidate and worse on every count.
 * </ul>
 *
 * <p><b>The daemon is a recording stand-in, not a mock.</b> {@link FakeDocker} is the same shell
 * script the surefire suite points {@code qits.system.docker.binary} at: a real {@code
 * ProcessBuilder} forks a real child and the argv it receives is the argv the builders produced.
 * That is why the accepted story can assert the read reached the machine as well as that it was
 * served — {@code calls.log} is the daemon's end of the interaction, exactly as {@code
 * recordedRequests()} is the idp's.
 *
 * <p><b>ITs are skipped by default here and this one does NOT flip that.</b> {@code skipITs} is
 * {@code true} in the root pom because {@link PackagedSurfaceIT} is this module's other integration
 * test and a good half of it is about the CLIENT — the base href at the root, the deep links, the
 * fallback that must not swallow {@code /system} — which the userflow pipeline deliberately does
 * not build ({@code -Dquarkus.quinoa=false}, since the qits-platform-spa-system submodule arrives
 * EMPTY in a step container). A blanket {@code -DskipITs=false} would make that run red on a test
 * that is right. {@code .config/qits/ci-event-userflows.yml} names this class instead ({@code
 * -DskipITs=false "-Dit.test=TokenValidationBootstrapIT"}), which is also what keeps the userflow
 * pipeline about these stories and nothing else — and keeps the property's own meaning ("run
 * everything") intact for the {@code native} profile in service/pom.xml that sets it.
 */
@QuarkusIntegrationTest
@TestProfile(TokenValidationBootstrapIT.PackagedWithMockIdp.class)
public class TokenValidationBootstrapIT {

  static final String CATEGORY = "authentication";
  static final String ACCEPTED_SLUG =
      "on-start-the-system-panel-fetches-the-platform-s-signing-keys";
  static final String DENIED_SLUG = "a-stranger-s-token-never-reads-the-platform-s-machine";

  private static final String OVERVIEW = "/system/api/overview";

  /** A terminal that does not exist, so nothing but the door is ever reached. */
  private static final String SOCKET_PATH =
      "/system/api/terminals/11111111-2222-3333-4444-555555555555";

  @TestHTTPResource("/")
  URI base;

  /**
   * {@link PackagedSurfaceIT.PackagedUnderTarget} — the stand-in docker binary and the silenced
   * glances prefetch, resolved in THIS JVM because the launched artifact does not have this
   * module's test classpath and so never sees the path itself — <b>plus the two things these
   * stories are about</b>: the gate that turns the shipped OIDC tenant on, and where the idp is.
   *
   * <p>Extending rather than copying is deliberate. What a launched qits-platform-system needs in
   * order to boot at all is one answer, it is written out at length over there, and a second copy
   * of it would be a second place for it to drift. What is added here is only the seams these
   * stories move.
   *
   * <p>The mock idp starts <b>before</b> the application, via {@link MockIdp#ensureStarted()},
   * which parks its coordinates (and its keypair) in system properties — a test profile is
   * instantiated in more than one classloader and the property table is the one thing every copy
   * shares. That is also how a story method's {@link MockIdp#attach()} reaches the very server the
   * launched process fetched its keys from.
   *
   * <p><b>Every key here is a RUNTIME key.</b> A packaged process takes its configuration as {@code
   * -D} arguments on a jar that was already built, so a build-time key would be silently ignored
   * and these tests would prove the opposite of what they say.
   *
   * <p><b>There is no telemetry or event-bus line to darken, and that is a fact about this
   * repository rather than an omission.</b> This service has no datasource, no qits-eventstream and
   * no opentelemetry extension on its classpath — the root pom's own comment says so — so the only
   * two things a launched process here would dial out for are the idp, which is pointed at {@link
   * MockIdp} below, and the glances image pull, which the inherited {@code
   * qits.system.glances.pull-at-startup=false} already switches off at its own source. The boot
   * sweep's remaining calls go to the stand-in binary like every other docker call.
   */
  public static class PackagedWithMockIdp extends PackagedSurfaceIT.PackagedUnderTarget {

    /**
     * The audience this service enforces, and it is a LITERAL rather than a variable name — the
     * difference from qits-githost's IT, which hands its launched process {@code
     * QITS_AUTH_MACHINE_AUDIENCE} because the shipped expression there reads that variable. Here
     * {@code qits.auth.machine.audience=qits-platform-system} is spelled out in {@code
     * application.properties} and {@code quarkus.oidc.token.audience} references it, so the
     * audience under test is the shipped one and there is no expression to feed. A deployment still
     * overrides it by environment.
     */
    static final String AUDIENCE = "qits-platform-system";

    @Override
    public Map<String, String> getConfigOverrides() {
      MockIdp idp = MockIdp.ensureStarted();
      Map<String, String> overrides = new LinkedHashMap<>(super.getConfigOverrides());
      // THE GATE, and turning it on is the point: the shipped tenant is
      // quarkus.oidc.tenant-enabled=${qits.auth.machine.required:false}, so this one key is the
      // difference between a service that validates machine bearers and one that does not. The
      // application.properties block says what flipping it implies — with it on there IS a tenant,
      // and the tenant fetches a JWKS at boot — and this is where that is proved rather than
      // described. It also says there is no third state, which is why nothing else is set with it.
      overrides.put("qits.auth.machine.required", "true");
      // The one seam these stories move: where the idp is. Runtime key, so the packaged artifact is
      // otherwise exactly what ships — discovery stays off and jwks-path stays `jwks`, joined onto
      // this URL.
      overrides.put("quarkus.oidc.auth-server-url", idp.baseUrl());
      return overrides;
    }
  }

  @UserStory(
      value = "On start, the system panel fetches the platform's signing keys",
      category = "authentication")
  @UserStoryDescription(
      """
      A freshly deployed qits-platform-system must validate service bearers before any caller
      arrives: at startup it fetches the signing keys (JWKS) from qits-platform-idp — discovery
      stays off, the path is configured — so the very first machine request is accepted. What
      that bearer then buys is the whole of this service's read surface, answered live off the
      host's docker daemon; and what it does NOT buy is a shell. Only `qits:admin` may open a
      terminal socket, because reading the host's shape is something a machine may do and
      holding a shell on the platform host is not.
      """)
  void serviceBootFetchesJwksAndAcceptsPlatformTokens(Interactions story) {
    MockIdp idp = MockIdp.attach();

    story.note(
        "qits-platform-system starts with the OIDC tenant on, beside a reachable"
            + " qits-platform-idp");
    given().get("/system/q/health/ready").then().statusCode(200);

    // End (a), the idp side: the JWKS was served during startup — before this story presented any
    // token at all. Readiness above is deliberately independent of that fetch — the shipped config
    // explains why: tying it to another service's would make a cold boot a question of ordering —
    // so a 200 there is not the claim. The recording is.
    assertTrue(
        idp.recordedRequests().stream().anyMatch(r -> "/idp/jwks".equals(r.path())),
        "the packaged service never fetched /idp/jwks at startup");
    story
        .happened("qits-platform-system", "qits-platform-idp", "GET /idp/jwks (at startup)")
        .as("jwks-fetched");

    // End (b), this service's side: those keys are what token validation now runs on. A platform
    // peer's bearer (aud = this service, roles in `groups`) opens the landing read — no path
    // parameter, nothing named, nothing written.
    String peerToken =
        idp.token()
            .subject("a-platform-service")
            .audience(PackagedWithMockIdp.AUDIENCE)
            .groups("qits:system")
            .mint();
    given()
        .header("Authorization", "Bearer " + peerToken)
        .get(OVERVIEW)
        .then()
        .statusCode(200)
        .body("host", notNullValue())
        .body("host.hostname", equalTo("fake-host"))
        .body("swarm.state", equalTo("active"));
    story
        .happened(
            "a platform service",
            "qits-platform-system",
            "GET /system/api/overview (Bearer, groups=[qits:system])")
        .as("overview-served");

    // End (c), the machine's own side, and it is what makes this service's answer different from a
    // row out of a table: there is no store here, so the body above can only have come from a
    // `docker info` this request itself forked. The stand-in records every argv, and `info` is the
    // one call nothing else makes — the boot sweep probes with `version` and reaps with `ps`/`rm`.
    assertTrue(
        FakeDocker.called("info --format"),
        "the overview answered without ever asking the daemon: " + FakeDocker.calls());
    story
        .happened(
            "qits-platform-system",
            "the host's docker daemon",
            "docker info / docker system df (read at the moment it was asked)")
        .as("daemon-read");

    // And the ceiling of that same credential. The socket is @RolesAllowed("qits:admin") at class
    // level, which is what secures the HTTP UPGRADE itself, so a bearer that opens every read is
    // still refused a terminal.
    //
    // WHAT IS ASSERTED IS THE REFUSAL, NOT ITS CODE, and deliberately: from a WebSocket client a
    // rejected upgrade is one failed handshake whether the server decided 401 or 403, and the claim
    // this story makes is only that the machine role does not open a shell.
    try {
      TerminalClient.connect(
          TerminalClient.socketUri(base, SOCKET_PATH),
          Map.of("Authorization", "Bearer " + peerToken));
      throw new AssertionError("a machine bearer must never open a terminal on the platform host");
    } catch (RuntimeException refused) {
      // A refused upgrade, which is what the class-level @RolesAllowed on the endpoint arranges.
    }
    story
        .happened(
            "a platform service",
            "qits-platform-system",
            "WS upgrade /system/api/terminals/{id} (Bearer, groups=[qits:system]) -> refused")
        .as("shell-refused");
  }

  @UserStory(
      value = "A stranger's token never reads the platform's machine",
      category = "authentication")
  @UserStoryDescription(
      """
      The flip side of trusting the platform's keys. A token signed by a key the published JWKS
      never carried, or minted for another service's audience, is refused at the door — however
      well-formed it looks: both are 401 and not 403, because the credential never became an
      identity and there is no caller to have been forbidden. A token addressed here and signed
      correctly but carrying a role this service has never heard of gets the other answer, 403 —
      it authenticated and covers nothing. There is no anonymous route in this service and there
      must never be one: what it reads is the whole machine.
      """)
  void aStrangersTokenIsRefused(Interactions story) {
    MockIdp idp = MockIdp.attach();

    String strangersToken =
        idp.token()
            .audience(PackagedWithMockIdp.AUDIENCE)
            .groups("qits:system")
            .signedByUnknownKey()
            .mint();
    given()
        .header("Authorization", "Bearer " + strangersToken)
        .get(OVERVIEW)
        .then()
        .statusCode(401);
    story
        .happened(
            "an impostor",
            "qits-platform-system",
            "GET /system/api/overview (token signed by an unknown key) -> 401")
        .as("unknown-key-refused");

    String wrongAudienceToken =
        idp.token().audience("some-other-service").groups("qits:system").mint();
    given()
        .header("Authorization", "Bearer " + wrongAudienceToken)
        .get(OVERVIEW)
        .then()
        .statusCode(401);
    story
        .happened(
            "an impostor",
            "qits-platform-system",
            "GET /system/api/overview (another service's audience) -> 401")
        .as("wrong-audience-refused");

    // The third door, and the one that proves the groups→roles mapping really ran rather than being
    // waved through: `qits:reader` is a real platform role and it is not one of the two this
    // service's routes name. Minted into a token addressed here it authenticates perfectly and
    // still covers nothing.
    String readerToken =
        idp.token()
            .subject("somebody-elses-service")
            .audience(PackagedWithMockIdp.AUDIENCE)
            .groups("qits:reader")
            .mint();
    given().header("Authorization", "Bearer " + readerToken).get(OVERVIEW).then().statusCode(403);
    story
        .happened(
            "a caller with the wrong role",
            "qits-platform-system",
            "GET /system/api/overview (Bearer, groups=[qits:reader]) -> 403")
        .as("wrong-role-refused");
  }

  @AfterAll
  static void bothStoryReportsAreComplete() {
    // The extension emits each report in its afterEach, so both are on disk before @AfterAll runs.
    ReportAssertions.assertComplete(CATEGORY, ACCEPTED_SLUG, UserflowReport.PASSED);
    ReportAssertions.assertInteraction(
        CATEGORY,
        ACCEPTED_SLUG,
        "qits-platform-system",
        "qits-platform-idp",
        "GET /idp/jwks (at startup)");
    ReportAssertions.assertStepId(CATEGORY, ACCEPTED_SLUG, "jwks-fetched");
    ReportAssertions.assertStepId(CATEGORY, ACCEPTED_SLUG, "overview-served");
    ReportAssertions.assertStepId(CATEGORY, ACCEPTED_SLUG, "daemon-read");
    ReportAssertions.assertStepId(CATEGORY, ACCEPTED_SLUG, "shell-refused");

    ReportAssertions.assertComplete(CATEGORY, DENIED_SLUG, UserflowReport.PASSED);
    ReportAssertions.assertStepId(CATEGORY, DENIED_SLUG, "unknown-key-refused");
    ReportAssertions.assertStepId(CATEGORY, DENIED_SLUG, "wrong-audience-refused");
    ReportAssertions.assertStepId(CATEGORY, DENIED_SLUG, "wrong-role-refused");
  }
}

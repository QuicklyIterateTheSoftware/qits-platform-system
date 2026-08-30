package eu.wohlben.qits.system.stories.terminals;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.system.api.TokenValidationBootstrapIT;
import eu.wohlben.qits.system.stories.support.StoryDocker;
import eu.wohlben.qits.system.stories.support.StoryIdentities;
import eu.wohlben.qits.system.stories.support.StoryProfile;
import eu.wohlben.qits.system.stories.support.StoryTarget;
import eu.wohlben.qits.system.stories.support.StoryTerminal;
import eu.wohlben.qits.system.testdocker.TerminalClient;
import eu.wohlben.qits.userflows.Interactions;
import eu.wohlben.qits.userflows.NetworkCapture;
import eu.wohlben.qits.userflows.NetworkEdge;
import eu.wohlben.qits.userflows.NetworkTaps;
import eu.wohlben.qits.userflows.UserStory;
import eu.wohlben.qits.userflows.UserStoryDescription;
import eu.wohlben.qits.userflows.UserflowRunsAfter;
import eu.wohlben.qits.userflows.report.ReportAssertions;
import eu.wohlben.qits.userflows.report.Slugs;
import eu.wohlben.qits.userflows.report.UserflowReport;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * <b>The ceiling</b>: who may hold an interactive shell on the platform host, and — the part a
 * presence check cannot say — what a refused one costs the machine.
 *
 * <p>Every REST route in this service takes {@code qits:admin} <i>or</i> {@code qits:system},
 * because reading the shape of the host is something a machine may ordinarily do. The terminal
 * socket takes {@code qits:admin} alone. That asymmetry is the rule this story exists for, and it
 * is enforced at the HTTP <b>UPGRADE</b> — WebSockets Next reads the class-level
 * {@code @RolesAllowed} through its own {@code SecurityHttpUpgradeCheck} — so an unauthorised
 * client is refused the handshake rather than connected and then quietly ignored.
 *
 * <p><b>This is why the catalogue runs against the packaged artifact.</b> A {@code @QuarkusTest}
 * runs under the {@code test} profile, where qits-auth-core ships a synthetic dev user that answers
 * every path policy; the launched process runs as a deployment does, so the roles have to arrive
 * the way the platform edge sends them or not arrive at all. The guard on this upgrade was found
 * exactly that way.
 *
 * <p><b>The paying assertion is the absence.</b> Each of the three callers below is a valid,
 * authenticated identity — every one of them gets a real answer out of the REST resource in the
 * same breath — and not one of them reaches the daemon. {@code assertNoEdgesTo} is what states
 * that from the diagram's own side: no process edge, no socket behind it, nothing spawned. A
 * refusal that had already forked a child would still have refused, and no presence check would
 * have noticed.
 */
@QuarkusIntegrationTest
@TestProfile(StoryProfile.class)
public class TerminalRefusalIT {

  static final String CATEGORY = "terminals";

  static final String CATEGORY_SLUG = Slugs.slug(CATEGORY);

  static final String REFUSED = "A shell on the platform host is refused to everyone but an admin";

  static final String REFUSED_SLUG = Slugs.slug(REFUSED);

  /** How the diagram names a request that arrived with no identity on it at all. */
  private static final String NOBODY = "a caller with no identity";

  /** …and a person the edge authenticated, holding a role this service has never heard of. */
  private static final String WITHOUT_ADMIN = "a person without qits:admin";

  /** A real platform role that is not one of the two this service's routes name. */
  private static final String OTHER_ROLE = "qits:reader";

  /**
   * A terminal that does not exist, so nothing but the door is ever reached. Written out because
   * the point is that the id never matters: the upgrade is refused before any session is looked up.
   */
  private static final String SOCKET_PATH =
      StoryTarget.socketPath("11111111-2222-3333-4444-555555555555");

  private static String peerBearer;

  @TestHTTPResource("/")
  URI base;

  @BeforeAll
  static void tapEveryPlane() {
    NetworkTaps.restAssured(StoryTarget.SERVICE);
    StoryDocker.installSource();
  }

  @UserStory(value = REFUSED, category = CATEGORY)
  @UserStoryDescription(
      """
      Reading the host is something a machine may do; holding a shell on it is not. Three callers
      try the terminal socket and all three are refused at the handshake: one with no identity at
      all, one platform service holding a perfectly valid bearer this run's idp minted, and one
      logged-in person whose roles do not include qits:admin. Each of them is shown, in the same
      story, getting a real answer out of the REST resource beside it — a 401, a 200 and a 403,
      which are three different facts — so the refusals are the ceiling of a working credential
      rather than the failure of a broken one. And nothing any of them did reached the docker
      daemon: a refused shell is refused before anything is spawned.
      """)
  @UserflowRunsAfter(TokenValidationBootstrapIT.class)
  void onlyAnAdminHoldsAShellOnThePlatformHost(Interactions story) {
    int before = StoryDocker.mark();

    // --- nobody at all ------------------------------------------------------------------------------
    NetworkCapture.actor(NOBODY);
    given().get(StoryTarget.TERMINALS).then().statusCode(401);
    refuseUpgrade(Map.of());
    story
        .note(
            "with no identity there is no surface at all: the resource answers 401 and the socket"
                + " refuses the handshake")
        .as("anonymous-refused");

    // --- a platform service, on a bearer that works ------------------------------------------------
    NetworkCapture.actor(StoryIdentities.PEER);
    peerBearer = StoryIdentities.machineToken();
    StoryIdentities.bearer(given(), peerBearer).get(StoryTarget.TERMINALS).then().statusCode(200);
    refuseUpgrade(Map.of("Authorization", "Bearer " + peerBearer));
    story
        .note(
            "a platform service's bearer LISTS the terminals — the read routes take qits:system —"
                + " and is still refused a shell, which is the ceiling this service draws")
        .as("machine-refused");

    // --- a logged-in person who is not an admin ----------------------------------------------------
    NetworkCapture.actor(WITHOUT_ADMIN);
    StoryIdentities.withRole(given(), OTHER_ROLE).get(StoryTarget.TERMINALS).then().statusCode(403);
    refuseUpgrade(StoryIdentities.headersWithRole(OTHER_ROLE));
    story
        .note(
            "and a person the edge authenticated but who holds neither role gets the OTHER answer,"
                + " 403 — they are somebody, and they cover nothing here")
        .as("wrong-role-refused");

    // The claim the diagram makes, made again from the recording's own side: three refusals, and
    // the stand-in was not asked for anything at all in between.
    List<String> calls = StoryDocker.callsSince(before);
    assertTrue(calls.isEmpty(), "a refused shell must spawn nothing: " + calls);
  }

  /**
   * Attempt the upgrade and record the refusal.
   *
   * <p>The observation sits in the catch block on purpose, and it is what makes this edge
   * incapable of lying: an upgrade that unexpectedly SUCCEEDED leaves by the {@code AssertionError}
   * above without recording anything, so a "refused" arrow can never be drawn for a handshake this
   * service allowed.
   */
  private void refuseUpgrade(Map<String, String> headers) {
    try {
      TerminalClient.connect(TerminalClient.socketUri(base, SOCKET_PATH), headers);
      throw new AssertionError(
          "the terminal handshake must not succeed for " + NetworkCapture.actor());
    } catch (RuntimeException refused) {
      // A refused upgrade, which is what the class-level @RolesAllowed on the endpoint arranges.
      // What is recorded is the REFUSAL and not its code: from a WebSocket client a rejected
      // upgrade is one failed handshake whether the server decided 401 or 403, and a label carrying
      // a number would put something in the document this story does not actually assert.
      StoryTerminal.refusedUpgrade();
    }
  }

  @AfterAll
  static void theRefusalStoryIsComplete() {
    ReportAssertions.assertComplete(CATEGORY_SLUG, REFUSED_SLUG, UserflowReport.PASSED);
    ReportAssertions.assertStepId(CATEGORY_SLUG, REFUSED_SLUG, "anonymous-refused");
    ReportAssertions.assertStepId(CATEGORY_SLUG, REFUSED_SLUG, "machine-refused");
    ReportAssertions.assertStepId(CATEGORY_SLUG, REFUSED_SLUG, "wrong-role-refused");

    http(NOBODY, "GET " + StoryTarget.TERMINALS + " -> 401");
    http(StoryIdentities.PEER, "GET " + StoryTarget.TERMINALS + " -> 200");
    http(WITHOUT_ADMIN, "GET " + StoryTarget.TERMINALS + " -> 403");
    refused(NOBODY);
    refused(StoryIdentities.PEER);
    refused(WITHOUT_ADMIN);

    // THE TITLE, ASSERTED AS A SHAPE. Six edges, three of them refusals — and nothing left this
    // process. No docker call, no socket behind it, no peer asked who anybody was.
    ReportAssertions.assertEdgeCount(CATEGORY_SLUG, REFUSED_SLUG, 6);
    ReportAssertions.assertNoEdgesTo(CATEGORY_SLUG, REFUSED_SLUG, StoryDocker.DOCKER);
    ReportAssertions.assertNoEdgesTo(CATEGORY_SLUG, REFUSED_SLUG, StoryDocker.DAEMON);
    ReportAssertions.assertNoEdgesFrom(CATEGORY_SLUG, REFUSED_SLUG, StoryTarget.SERVICE);
    ReportAssertions.assertOnlyEdgesFrom(
        CATEGORY_SLUG, REFUSED_SLUG, List.of(NOBODY, StoryIdentities.PEER, WITHOUT_ADMIN));
    // The bearer that worked and was still not enough is nowhere in the bundle.
    ReportAssertions.assertNotLeaked(CATEGORY_SLUG, REFUSED_SLUG, peerBearer);
  }

  private static void http(String actor, String label) {
    ReportAssertions.assertEdge(
        CATEGORY_SLUG, REFUSED_SLUG, NetworkEdge.HTTP, actor, StoryTarget.SERVICE, label);
  }

  private static void refused(String actor) {
    ReportAssertions.assertEdge(
        CATEGORY_SLUG,
        REFUSED_SLUG,
        NetworkEdge.SOCKET,
        actor,
        StoryTarget.SERVICE,
        StoryTarget.socketLabel(StoryTerminal.REFUSED));
  }
}

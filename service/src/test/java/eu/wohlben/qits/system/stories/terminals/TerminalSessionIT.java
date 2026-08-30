package eu.wohlben.qits.system.stories.terminals;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.system.api.TokenValidationBootstrapIT;
import eu.wohlben.qits.system.stories.support.StoryDocker;
import eu.wohlben.qits.system.stories.support.StoryIdentities;
import eu.wohlben.qits.system.stories.support.StoryProfile;
import eu.wohlben.qits.system.stories.support.StoryTarget;
import eu.wohlben.qits.system.stories.support.StoryTerminal;
import eu.wohlben.qits.userflows.Interactions;
import eu.wohlben.qits.userflows.Network;
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
import io.restassured.http.ContentType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * <b>A terminal, end to end</b> — the one thing this service does that is not a read, played
 * against the packaged artifact with a real pseudo-terminal and a real child on the far end of it.
 *
 * <p>The two stories are the two kinds of terminal, and they differ in more than their argv:
 *
 * <ul>
 *   <li>a <b>shell</b> in one of this node's containers. It is created as a REST resource, attached
 *       to over a WebSocket, typed into, resized, and it ends when the shell does — with close code
 *       1000, which is the protocol's way of saying "do not reconnect".
 *   <li>the <b>host monitor</b>, which is find-or-create because there is one host, and whose
 *       ending is two acts rather than one: {@code --rm} fires when a CONTAINER exits and killing
 *       the CLI attached to it is not that, so the teardown removes the container by the name the
 *       argv builder derived from the session id.
 * </ul>
 *
 * <p><b>Splitting create from attach is what these stories are drawn around.</b> A POST answers an
 * id and a socket path before any socket exists, which is what lets the client list what is running
 * and reconnect to it by address after a reload — and it is why the diagram has an HTTP arrow and a
 * socket arrow rather than one of either.
 *
 * <p><b>The socket plane is instrumented at its own call sites</b>, in {@link StoryTerminal}: the
 * framework ships a RestAssured tap and nothing for a WebSocket, and every observation there is
 * made synchronously on the story thread, where the actor the extension resets at each story border
 * is the story's own. Nothing is recorded from a listener callback.
 *
 * <p><b>The linger reaper is deliberately not exercised.</b> Both windows keep their shipped values
 * — 60 seconds for a shell, 3 for the monitor — and every story here ends its own session
 * explicitly. A story that waited out a linger window would be indistinguishable from a story that
 * hung, and a session reaped by a timer would put its {@code docker rm -f} in whichever diagram was
 * open when it fired. {@code TerminalLingerTest} is where that behaviour is proved, on a profile
 * that shortens both windows on purpose.
 */
@QuarkusIntegrationTest
@TestProfile(StoryProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TerminalSessionIT {

  static final String CATEGORY = "terminals";

  static final String CATEGORY_SLUG = Slugs.slug(CATEGORY);

  static final String SHELL = "An operator opens a shell in a container and works in it";

  static final String SHELL_SLUG = Slugs.slug(SHELL);

  static final String MONITOR = "The host monitor is one monitor, and ending it takes its container";

  static final String MONITOR_SLUG = Slugs.slug(MONITOR);

  /** What the close edge's label says happened. Authored, so the assertion cannot word it twice. */
  static final String ENDED = "the shell exited";

  /** Every session id this class created — generated per run, so every one is pinned as not leaked. */
  private static final List<String> SESSION_IDS = new ArrayList<>();

  @TestHTTPResource("/")
  URI base;

  @BeforeAll
  static void tapEveryPlane() {
    NetworkTaps.restAssured(StoryTarget.SERVICE);
    StoryDocker.installSource();
  }

  @UserStory(value = SHELL, category = CATEGORY)
  @UserStoryDescription(
      """
      Something on the host is misbehaving and an operator wants a prompt inside it. They open a
      terminal against one of this node's containers — the reference they typed is resolved
      against the daemon first, so a name that means nothing is a 404 and a container that is not
      running is a 409, rather than a shell that opens and dies with a message a browser cannot
      act on. What comes back is an id and a socket address; the browser dials that address, and
      from then on it is a stream in both directions with no request boundary in it: keystrokes
      down, whatever the far side printed back up, and a window size that reaches the child as a
      real SIGWINCH. Typing `exit` ends the shell, and the server says so in band and closes with
      1000 — which means final, so the client shows the note and does not reconnect.
      """)
  @UserflowRunsAfter(TokenValidationBootstrapIT.class)
  @Order(1)
  void anOperatorOpensAShellAndWorksInIt(Interactions story, Network network) throws Exception {
    NetworkCapture.actor(StoryIdentities.OPERATOR);
    int before = StoryDocker.mark();

    // --- create ------------------------------------------------------------------------------------
    String socketPath =
        StoryIdentities.operator(given())
            .contentType(ContentType.JSON)
            .body("{\"kind\":\"EXEC\",\"container\":\"" + StoryTarget.CONTAINER + "\",\"shell\":\"sh\"}")
            .post(StoryTarget.TERMINALS)
            .then()
            .statusCode(201)
            .body("kind", equalTo("EXEC"))
            .body("shell", equalTo("sh"))
            .body("container.name", equalTo(StoryTarget.CONTAINER))
            // THE CANONICAL ID, and it is the whole of what the exec argv is allowed to carry: the
            // reference was handed to the daemon and only what the daemon printed came back.
            .body("container.id", hasLength(64))
            .body("attachedClients", equalTo(0))
            .body("socketPath", startsWith(StoryTarget.TERMINALS + "/"))
            .extract()
            .path("socketPath");
    String sessionId = socketPath.substring(socketPath.lastIndexOf('/') + 1);
    SESSION_IDS.add(sessionId);
    story
        .note(
            "the terminal is created before any socket exists: the reference is resolved against"
                + " the daemon, and what comes back is an id and the address to dial")
        .as("terminal-created");

    // The argv is the sandbox, and this is the half a summary cannot show: the caller's string
    // reached no command line, and the 64-hex id the daemon printed is what did.
    List<String> exec = StoryDocker.argvOf("exec -it " + StoryTarget.DIGEST + " sh");
    assertTrue(
        exec.contains("-it") && exec.contains("sh"),
        "the shell was not spawned on a terminal: " + StoryDocker.callsSince(before));
    assertTrue(
        exec.stream().noneMatch(argument -> argument.equals(StoryTarget.CONTAINER)),
        "the caller's reference must not reach the exec argv: " + exec);
    assertTrue(
        exec.stream().anyMatch(argument -> argument.startsWith("QITS_SYSTEM_SESSION=")),
        "the child must carry its session id, so a stray shell can be traced back: " + exec);
    story
        .note(
            "and the shell it spawned carries the canonical id and its own session in the child's"
                + " environment — never the string the caller typed")
        .as("argv-is-the-sandbox");

    // --- attach and work ---------------------------------------------------------------------------
    try (StoryTerminal terminal =
        StoryTerminal.dial(base, socketPath, StoryIdentities.operatorHeaders())) {
      terminal.awaitOutput("fake-terminal ready");
      story.note("the browser dials the address it was given and the far side is already there")
          .as("socket-attached");

      terminal.type("hello\n").awaitOutput("echo:hello");
      story.note("a keystroke goes down the pseudo-terminal and what the child printed comes back")
          .as("round-trip");

      // THE RESIZE CHAIN, and every link of it is invisible on its own: a JSON frame, an ioctl on
      // the master, the kernel signalling the terminal's foreground process group, and that group
      // being the child only because `setsid --ctty` made it one. `stty size` prints rows then
      // columns — the winsize struct's own order — so a swapped field would print "132 50" here.
      terminal.resize(132, 50).type("nudge\n").awaitOutput("50 132");
      story
          .note(
              "resizing the browser window reaches the child as a real SIGWINCH: it reports the new"
                  + " size back, rows before columns, which is the kernel struct's own order")
          .as("resize-reached-the-child");

      terminal.type("exit\n").awaitOutput("[terminal exited (code 0)]");
      // 1000 is FINAL. Any other code is the client's own signal to reconnect, which for a session
      // that has ended would be a reconnect loop.
      assertEquals(1000, terminal.awaitClose(ENDED));
      story
          .note(
              "and when the shell exits the server says so in band and closes 1000 — final, so the"
                  + " client shows the note instead of reconnecting")
          .as("closed-final");
    }

    StoryIdentities.operator(given())
        .get(StoryTarget.TERMINALS + "/" + sessionId)
        .then()
        .statusCode(404);
    story
        .note("the session is gone with its child: this service's only state is meant to be lost")
        .as("session-gone");

    network.declare(
        NetworkEdge.SOCKET, StoryDocker.DOCKER, StoryDocker.DAEMON, StoryDocker.SOCKET_LABEL);
  }

  @UserStory(value = MONITOR, category = CATEGORY)
  @UserStoryDescription(
      """
      The other kind of terminal: a live view of the host itself, a `glances` container this
      service owns, on a terminal of the same shape. There is one host, so asking for its monitor
      twice is asking for the same monitor — the second request answers 200 with the live session
      rather than 201 with a second container fighting it for the screen. Ending it is two acts,
      and the second one is the one that is easy to forget: `--rm` fires when a CONTAINER exits,
      and killing the CLI attached to it is not that, so a teardown that only killed the client
      would leave a container holding the host's pid namespace and the docker socket with nobody
      watching it.
      """)
  @UserflowRunsAfter(TokenValidationBootstrapIT.class)
  @Order(2)
  void theHostMonitorIsOneMonitor(Interactions story, Network network) {
    NetworkCapture.actor(StoryIdentities.OPERATOR);
    int before = StoryDocker.mark();

    String sessionId =
        StoryIdentities.operator(given())
            .contentType(ContentType.JSON)
            .body("{\"kind\":\"GLANCES\"}")
            .post(StoryTarget.TERMINALS)
            .then()
            .statusCode(201)
            .body("kind", equalTo("GLANCES"))
            // No container reference: a host monitor is not inside anything.
            .body("container", org.hamcrest.Matchers.nullValue())
            .extract()
            .path("id");
    SESSION_IDS.add(sessionId);
    story.note("an operator opens the host monitor and one container is started for it")
        .as("monitor-started");

    // THE SANDBOX, asserted element for element. Every one of these is a decision written as a
    // flag, and a flag lost in a refactor is invisible everywhere else until it is invisible in
    // production — this is a container that is handed the host's pid namespace and its socket.
    List<String> run = StoryDocker.argvOf("run -it --rm --name " + StoryDocker.glancesContainer() + " glances");
    assertTrue(run.contains("--rm"), "a monitor is not a record: " + run);
    assertTrue(
        run.contains("/var/run/docker.sock:/var/run/docker.sock:ro"),
        "the socket a monitor is given must be READ-ONLY: " + run);
    assertTrue(run.contains("--cap-drop") && run.contains("ALL"), "it reads /proc and a socket: " + run);
    assertTrue(
        run.contains("no-new-privileges") && run.contains("--pids-limit"),
        "a monitor that leaks must not take the host with it: " + run);
    assertTrue(
        run.contains("--oom-score-adj") && run.contains("500"),
        "if the host runs out, the kernel takes this before it takes a platform service: " + run);
    story
        .note(
            "the container it started is the argv, element for element: the host's pid namespace,"
                + " the docker socket READ-ONLY, every capability dropped, and an oom score that"
                + " puts it above every platform service")
        .as("monitor-is-sandboxed");

    String again =
        StoryIdentities.operator(given())
            .contentType(ContentType.JSON)
            .body("{\"kind\":\"GLANCES\"}")
            .post(StoryTarget.TERMINALS)
            .then()
            // 200 rather than 201 is how the client is told it did not just start something.
            .statusCode(200)
            .extract()
            .path("id");
    assertEquals(sessionId, again, "there is one host, so there is one monitor of it");
    StoryIdentities.operator(given())
        .get(StoryTarget.TERMINALS)
        .then()
        .statusCode(200)
        .body("$", hasSize(1));
    story
        .note(
            "asking again gets the SAME session back with a 200 — one host, one monitor, and the"
                + " list still holds exactly one")
        .as("found-not-created-twice");

    StoryIdentities.operator(given())
        .delete(StoryTarget.TERMINALS + "/" + sessionId)
        .then()
        .statusCode(204);
    StoryIdentities.operator(given())
        .get(StoryTarget.TERMINALS + "/" + sessionId)
        .then()
        .statusCode(404);
    List<String> calls = StoryDocker.callsSince(before);
    assertTrue(
        calls.contains(
            StoryDocker.label("rm -f " + StoryDocker.glancesContainer(), "0")),
        "the glances container outlived the session that started it: " + calls);
    story
        .note(
            "ending it kills the CLI AND removes the container by the name the argv builder derived"
                + " — `--rm` fires on container exit, and killing the client is not that")
        .as("container-removed-too");

    network.declare(
        NetworkEdge.SOCKET, StoryDocker.DOCKER, StoryDocker.DAEMON, StoryDocker.SOCKET_LABEL);
  }

  @AfterAll
  static void bothTerminalStoriesAreComplete() {
    // --- the shell ---------------------------------------------------------------------------------
    ReportAssertions.assertComplete(CATEGORY_SLUG, SHELL_SLUG, UserflowReport.PASSED);
    ReportAssertions.assertStepId(CATEGORY_SLUG, SHELL_SLUG, "terminal-created");
    ReportAssertions.assertStepId(CATEGORY_SLUG, SHELL_SLUG, "argv-is-the-sandbox");
    ReportAssertions.assertStepId(CATEGORY_SLUG, SHELL_SLUG, "socket-attached");
    ReportAssertions.assertStepId(CATEGORY_SLUG, SHELL_SLUG, "round-trip");
    ReportAssertions.assertStepId(CATEGORY_SLUG, SHELL_SLUG, "resize-reached-the-child");
    ReportAssertions.assertStepId(CATEGORY_SLUG, SHELL_SLUG, "closed-final");
    ReportAssertions.assertStepId(CATEGORY_SLUG, SHELL_SLUG, "session-gone");

    http(SHELL_SLUG, "POST " + StoryTarget.TERMINALS + " -> 201");
    http(SHELL_SLUG, "GET " + StoryTarget.TERMINALS + "/" + StoryTarget.ID + " -> 404");
    // The dial — the browser's, always: nothing in this service ever dials a browser.
    socket(SHELL_SLUG, StoryTarget.socketLabel(StoryTerminal.ATTACHED));
    // The frames, in whichever direction they were pushed.
    pushedByTheOperator(SHELL_SLUG, StoryTerminal.DATA_FRAME);
    pushedByTheOperator(SHELL_SLUG, "resize frame 132x50");
    pushedByTheService(SHELL_SLUG, StoryTerminal.OUTPUT);
    pushedByTheService(SHELL_SLUG, StoryTerminal.closeLabel(1000, ENDED));
    // The daemon's end: one probe that resolved the reference, one child on a pseudo-terminal.
    docker(SHELL_SLUG, "container inspect " + StoryTarget.CONTAINER, "0");
    docker(SHELL_SLUG, "exec -it " + StoryTarget.DIGEST + " sh", StoryDocker.RUNNING);
    ReportAssertions.assertDeclaredEdge(
        CATEGORY_SLUG,
        SHELL_SLUG,
        NetworkEdge.SOCKET,
        StoryDocker.DOCKER,
        StoryDocker.DAEMON,
        StoryDocker.SOCKET_LABEL);
    // Ten edges and no eleventh. The one that would matter is a second docker call: a shell that
    // re-resolved its container on every frame, or a service that asked a peer who the operator is.
    ReportAssertions.assertEdgeCount(CATEGORY_SLUG, SHELL_SLUG, 10);
    ReportAssertions.assertOnlyEdgesFrom(
        CATEGORY_SLUG,
        SHELL_SLUG,
        List.of(StoryIdentities.OPERATOR, StoryTarget.SERVICE, StoryDocker.DOCKER));

    // --- the monitor -------------------------------------------------------------------------------
    ReportAssertions.assertComplete(CATEGORY_SLUG, MONITOR_SLUG, UserflowReport.PASSED);
    ReportAssertions.assertStepId(CATEGORY_SLUG, MONITOR_SLUG, "monitor-started");
    ReportAssertions.assertStepId(CATEGORY_SLUG, MONITOR_SLUG, "monitor-is-sandboxed");
    ReportAssertions.assertStepId(CATEGORY_SLUG, MONITOR_SLUG, "found-not-created-twice");
    ReportAssertions.assertStepId(CATEGORY_SLUG, MONITOR_SLUG, "container-removed-too");

    http(MONITOR_SLUG, "POST " + StoryTarget.TERMINALS + " -> 201");
    // The second POST is its own edge and not a duplicate: same route, DIFFERENT status, and the
    // status is the entire answer — 200 is how a client is told it did not just start something.
    http(MONITOR_SLUG, "POST " + StoryTarget.TERMINALS + " -> 200");
    http(MONITOR_SLUG, "GET " + StoryTarget.TERMINALS + " -> 200");
    http(MONITOR_SLUG, "DELETE " + StoryTarget.TERMINALS + "/" + StoryTarget.ID + " -> 204");
    http(MONITOR_SLUG, "GET " + StoryTarget.TERMINALS + "/" + StoryTarget.ID + " -> 404");
    docker(
        MONITOR_SLUG,
        "run -it --rm --name " + StoryDocker.glancesContainer() + " glances",
        StoryDocker.RUNNING);
    docker(MONITOR_SLUG, "rm -f " + StoryDocker.glancesContainer(), "0");
    ReportAssertions.assertDeclaredEdge(
        CATEGORY_SLUG,
        MONITOR_SLUG,
        NetworkEdge.SOCKET,
        StoryDocker.DOCKER,
        StoryDocker.DAEMON,
        StoryDocker.SOCKET_LABEL);
    // THE SECOND REQUEST COST NOTHING, and a count is what makes that checkable: two docker calls
    // for five requests, one to start the monitor and one to take its container away. A third
    // would be a second glances container the operator never asked for.
    ReportAssertions.assertEdgeCount(CATEGORY_SLUG, MONITOR_SLUG, 8);
    ReportAssertions.assertOnlyEdgesFrom(
        CATEGORY_SLUG,
        MONITOR_SLUG,
        List.of(StoryIdentities.OPERATOR, StoryTarget.SERVICE, StoryDocker.DOCKER));

    // No session id this class minted is anywhere in either bundle: every one of them is generated
    // per run, and a label carrying one would move the story's networkHash on every run.
    for (String slug : List.of(SHELL_SLUG, MONITOR_SLUG)) {
      for (String sessionId : SESSION_IDS) {
        ReportAssertions.assertNotLeaked(CATEGORY_SLUG, slug, sessionId);
      }
    }
  }

  private static void http(String slug, String label) {
    ReportAssertions.assertEdge(
        CATEGORY_SLUG, slug, NetworkEdge.HTTP, StoryIdentities.OPERATOR, StoryTarget.SERVICE, label);
  }

  private static void socket(String slug, String label) {
    ReportAssertions.assertEdge(
        CATEGORY_SLUG, slug, NetworkEdge.SOCKET, StoryIdentities.OPERATOR, StoryTarget.SERVICE, label);
  }

  private static void pushedByTheOperator(String slug, String label) {
    ReportAssertions.assertEdge(
        CATEGORY_SLUG, slug, NetworkEdge.EVENT, StoryIdentities.OPERATOR, StoryTarget.SERVICE, label);
  }

  private static void pushedByTheService(String slug, String label) {
    ReportAssertions.assertEdge(
        CATEGORY_SLUG, slug, NetworkEdge.EVENT, StoryTarget.SERVICE, StoryIdentities.OPERATOR, label);
  }

  private static void docker(String slug, String summary, String exitCode) {
    ReportAssertions.assertEdge(
        CATEGORY_SLUG,
        slug,
        StoryDocker.KIND,
        StoryTarget.SERVICE,
        StoryDocker.DOCKER,
        StoryDocker.label(summary, exitCode));
  }
}

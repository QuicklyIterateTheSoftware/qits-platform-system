package eu.wohlben.qits.system.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.system.error.BadRequestException;
import eu.wohlben.qits.system.terminal.ExecShell;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The two interactive command lines, element for element.
 *
 * <p><b>The argv IS the sandbox.</b> Nothing else constrains what a glances container may do — no
 * seccomp profile of ours, no runtime policy — so every flag below is a security decision and a
 * lost one is a privilege granted silently. That is why this test asserts presence flag by flag
 * rather than comparing a whole list somebody would then "fix" by pasting the new one in.
 */
class TerminalArgvTest {

  private static final UUID SESSION = UUID.fromString("11111111-2222-3333-4444-555555555555");

  private static final String IMAGE = "mirror.dev.localhost:8080/hub/nicolargo/glances:4.5.6-full";

  @Test
  void theGlancesRunIsSandboxed() {
    List<String> argv =
        TerminalArgv.runGlances("docker", IMAGE, List.of(), SESSION, "qits-platform-system");

    assertEquals(List.of("docker", "run", "--rm", "-it"), argv.subList(0, 4));
    assertTrue(argv.contains("--cap-drop"), argv.toString());
    assertEquals("ALL", argv.get(argv.indexOf("--cap-drop") + 1));
    assertEquals("no-new-privileges", argv.get(argv.indexOf("--security-opt") + 1));
    // Equal memory and memory-swap is what turns swap OFF. A missing --memory-swap would grant
    // unlimited swap, which is the opposite of the intent.
    assertEquals("512m", argv.get(argv.indexOf("--memory") + 1));
    assertEquals("512m", argv.get(argv.indexOf("--memory-swap") + 1));
    assertEquals("256", argv.get(argv.indexOf("--pids-limit") + 1));
    // The kernel takes a monitor before it takes a platform service.
    assertEquals("500", argv.get(argv.indexOf("--oom-score-adj") + 1));
    // Read-only, because this is a container we hand the host's docker socket to.
    assertTrue(argv.contains("/var/run/docker.sock:/var/run/docker.sock:ro"), argv.toString());
    assertFalse(
        argv.contains("/var/run/docker.sock:/var/run/docker.sock"),
        "the socket must never be mounted writable");
  }

  @Test
  void glancesSeesTheHostRatherThanItsOwnContainer() {
    List<String> argv =
        TerminalArgv.runGlances("docker", IMAGE, List.of(), SESSION, "qits-platform-system");

    // In its own pid namespace glances would report one process, itself.
    assertEquals("host", argv.get(argv.indexOf("--pid") + 1));
    // And in its own netns it would report the container's veth rather than the host's interfaces.
    // Nothing is published: in terminal mode glances runs no listener.
    assertEquals("host", argv.get(argv.indexOf("--network") + 1));
  }

  @Test
  void theGlancesContainerCanBeFoundAgainByASweepAndByATeardown() {
    List<String> argv =
        TerminalArgv.runGlances("docker", IMAGE, List.of(), SESSION, "qits-platform-system");

    // By name, for the teardown that has only the session id — `--rm` fires on container exit, and
    // killing the CLI is not that.
    assertEquals("qits-system-glances-" + SESSION, argv.get(argv.indexOf("--name") + 1));
    assertEquals("qits-system-glances-" + SESSION, TerminalArgv.glancesContainerName(SESSION));
    // And by label, for the boot sweep that has only the owner.
    assertTrue(argv.contains("qits.system.session=" + SESSION), argv.toString());
    assertTrue(argv.contains("qits.system.kind=glances"), argv.toString());
    assertTrue(argv.contains("qits.system.owner=qits-platform-system"), argv.toString());
  }

  @Test
  void theImageIsTheLastElementUnlessGlancesWasGivenFlags() {
    List<String> plain =
        TerminalArgv.runGlances("docker", IMAGE, List.of(), SESSION, "owner");
    assertEquals(IMAGE, plain.get(plain.size() - 1));

    List<String> flagged =
        TerminalArgv.runGlances("docker", IMAGE, List.of("--disable-plugin", "cloud"), SESSION, "owner");
    assertEquals(
        List.of(IMAGE, "--disable-plugin", "cloud"),
        flagged.subList(flagged.size() - 3, flagged.size()),
        "glances' own flags go AFTER the image, or docker reads them as its own");
  }

  @Test
  void theExecArgvCarriesTheCanonicalIdAndAShellFromTheEnum() {
    String id = "b248472f7a8e00bba8cd036232373baed8856d90eb77b06c53f3eb0b321fdb17";

    List<String> argv = TerminalArgv.execShell("docker", id, ExecShell.SH, SESSION);

    assertEquals(
        List.of(
            "docker",
            "exec",
            "-it",
            "-e",
            "TERM=xterm-256color",
            "-e",
            "QITS_SYSTEM_SESSION=" + SESSION,
            id,
            "sh"),
        argv);
  }

  @Test
  void anExecTargetThatIsNotACanonicalIdNeverOpensATerminal() {
    // The belt on the resolution step. The reference a caller sent is resolved through the daemon
    // first, and only what the daemon printed may reach this argv — so a name arriving here at all
    // means the resolution was bypassed.
    assertThrows(
        BadRequestException.class,
        () -> TerminalArgv.execShell("docker", "dev-qits-ci", ExecShell.BASH, SESSION));
    assertThrows(
        BadRequestException.class,
        () -> TerminalArgv.execShell("docker", "b248472f7a8e", ExecShell.BASH, SESSION));
  }

  @Test
  void theShellIsAClosedSet() {
    assertEquals(ExecShell.BASH, ExecShell.parse("bash"));
    assertEquals(ExecShell.SH, ExecShell.parse("SH"));
    // Everything else is a 400 at the boundary rather than a program in an argv.
    assertEquals(null, ExecShell.parse("zsh"));
    assertEquals(null, ExecShell.parse("/bin/sh"));
    assertEquals(null, ExecShell.parse(""));
    assertEquals(null, ExecShell.parse(null));
  }
}

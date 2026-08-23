package eu.wohlben.qits.system.terminal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** How a child is put onto a pseudo-terminal. One method, and it is a fix rather than a helper. */
public final class TerminalProcesses {

  private TerminalProcesses() {}

  /** Where the parent's own stdio goes: anywhere that can never become a controlling terminal. */
  private static final File NOT_A_TERMINAL = new File("/dev/null");

  /**
   * The child process that runs {@code argv} on the terminal's slave device — and, just as
   * important, <b>opens that device itself</b>.
   *
   * <p>COPIED VERBATIM from qits-projects' {@code RemoteLoginSessions.terminalProcess}. The comment
   * below is the incident it was written for; it is reproduced because the shape only looks
   * baroque until you know why.
   *
   * <p>This used to be {@code new ProcessBuilder("setsid", "--ctty", …)} with {@code
   * redirectInput/Output/Error(new File(slavePath))}, and that is what killed the service in
   * production. Those redirects are not a request the child carries out: {@code ProcessBuilder}
   * opens the file <em>in the calling process</em> and passes the descriptors down. The call has no
   * {@code O_NOCTTY}, and opening a pts from a <b>session leader with no controlling terminal makes
   * that pts its controlling terminal</b> — which the service is, because it runs as PID 1 in its
   * container. So the service adopted the terminal, and closing the master at the end of the
   * session hung it up: SIGHUP to the terminal's session, which was now the service's own. Quarkus
   * treats SIGHUP as a stop signal, so the container shut down gracefully and exited 129, ~20-30
   * seconds into every life somebody opened a terminal in.
   *
   * <p>It never reproduced anywhere else because the JVM running the suite is not a session leader,
   * and for a process that is not one the same {@code open} is harmless.
   *
   * <p>So the open moves into the child, where it is safe: a child is not a session leader either
   * when {@code sh} runs the redirect, and by the time {@code setsid --ctty} has made it one, {@code
   * TIOCSCTTY} is claiming the terminal deliberately and for the child's own new session. {@code
   * exec} throughout, so no shell lingers and the pid this returns is still the docker CLI's — which
   * is what keeps {@code destroy()} aimed at the right process.
   *
   * <p>{@code setsid --ctty} is also what makes SIGWINCH work: the kernel sends it to the foreground
   * process group of the terminal's session, and the docker CLI is that group leader only because
   * of this. Without it a browser resize sets the master's window size and nothing downstream ever
   * hears about it. ({@code util-linux-core} in the runtime image is what supplies {@code setsid};
   * busybox's has no {@code --ctty}.)
   *
   * <p>The parent's own stdio goes to {@code /dev/null}: it has to point somewhere, and that is the
   * one destination that can never become anybody's terminal.
   *
   * <p>Public so a test can assert the shape rather than the symptom — the symptom needs a session
   * leader, and a test JVM is not one.
   */
  public static ProcessBuilder terminalProcess(String slavePath, List<String> argv) {
    List<String> command = new ArrayList<>();
    command.add("sh");
    command.add("-c");
    // $0 is the slave device, "$@" is the command. `0<>` opens it read-write, then 1 and 2 are
    // duplicates of it — one interleaved stream, as on a real terminal, and stderr is not merged in
    // Java. Quoting "$@" is what keeps an argument with spaces one argument.
    command.add("exec 0<>\"$0\" 1>&0 2>&0; exec \"$@\"");
    command.add(slavePath);
    command.add("setsid");
    command.add("--ctty");
    command.addAll(argv);
    return new ProcessBuilder(command)
        .redirectInput(NOT_A_TERMINAL)
        .redirectOutput(NOT_A_TERMINAL)
        .redirectError(NOT_A_TERMINAL);
  }
}

package eu.wohlben.qits.system.error;

/**
 * The base of this service's own failures — the ones that have an HTTP answer rather than a stack
 * trace. Every subclass carries the status it becomes and, where the client needs to branch on it,
 * a short machine code; the service module's exception mapper turns them into {@code
 * {"message": "…"}} (plus {@code "code"} when there is one) and nothing else.
 *
 * <p>Why a hierarchy rather than throwing JAX-RS' WebApplicationException: this jar has no JAX-RS
 * on its classpath, and the domain is where the decisions are made — "no such container" is known
 * by the code that asked the daemon, not by the controller that will render it.
 */
public abstract class SystemException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  protected SystemException(String message) {
    super(message);
  }

  /** The HTTP status this failure is. */
  public abstract int status();

  /**
   * A stable machine code, or null when the message is the whole answer. Only one exists today —
   * {@code NODE_REMOTE} — because it is the only refusal a client changes its behaviour on.
   */
  public String code() {
    return null;
  }
}

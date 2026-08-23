package eu.wohlben.qits.system.error;

/**
 * The thing exists but is not in a state the request can be served in — a container that is not
 * running, the session limit already reached, a node that is not this one.
 */
public class ConflictException extends SystemException {

  private static final long serialVersionUID = 1L;

  private final String code;

  public ConflictException(String message) {
    this(null, message);
  }

  public ConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  @Override
  public int status() {
    return 409;
  }

  @Override
  public String code() {
    return code;
  }
}

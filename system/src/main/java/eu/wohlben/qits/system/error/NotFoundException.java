package eu.wohlben.qits.system.error;

/** The daemon does not know the thing that was named. */
public class NotFoundException extends SystemException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(message);
  }

  @Override
  public int status() {
    return 404;
  }
}

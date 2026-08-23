package eu.wohlben.qits.system.error;

/** The caller asked for something malformed — an unknown shell, a reference that is not one. */
public class BadRequestException extends SystemException {

  private static final long serialVersionUID = 1L;

  public BadRequestException(String message) {
    super(message);
  }

  @Override
  public int status() {
    return 400;
  }
}

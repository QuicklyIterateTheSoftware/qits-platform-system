package eu.wohlben.qits.system.error;

/**
 * The docker daemon could not be asked, or refused to answer.
 *
 * <p>503 AND NEVER 404, which is the rule this class exists to keep. A read that cannot reach the
 * daemon knows nothing about whether the thing it was asked for exists, and answering "not found"
 * would tell an operator the container is gone when what is gone is the socket. The message
 * carries docker's own last words, truncated, because that sentence is the whole diagnosis nine
 * times out of ten ("permission denied while trying to connect", "Cannot connect to the Docker
 * daemon", "This node is not a swarm manager").
 */
public class DockerUnavailableException extends SystemException {

  private static final long serialVersionUID = 1L;

  public DockerUnavailableException(String message) {
    super(message);
  }

  @Override
  public int status() {
    return 503;
  }
}

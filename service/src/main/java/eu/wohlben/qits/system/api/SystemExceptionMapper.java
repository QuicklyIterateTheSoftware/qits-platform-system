package eu.wohlben.qits.system.api;

import eu.wohlben.qits.system.error.SystemException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Turns the domain's failures into the wire's. One place, so a status and a message shape cannot
 * differ between two routes that fail for the same reason.
 *
 * <p>Nothing else is mapped. A bug in this service is a 500 with a stack trace in the log and no
 * detail on the wire, which is Quarkus' own default and the right one.
 */
@Provider
public class SystemExceptionMapper implements ExceptionMapper<SystemException> {

  @Override
  public Response toResponse(SystemException failure) {
    return Response.status(failure.status())
        .type(MediaType.APPLICATION_JSON)
        .entity(new ErrorBody(failure.code(), failure.getMessage()))
        .build();
  }
}

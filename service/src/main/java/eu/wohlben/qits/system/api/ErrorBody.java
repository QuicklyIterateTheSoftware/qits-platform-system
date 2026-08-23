package eu.wohlben.qits.system.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Every failure this service reports: {@code {"message": "…"}}, plus a {@code code} on the one
 * refusal a client changes its behaviour on ({@code NODE_REMOTE}).
 *
 * <p>The code is omitted rather than sent as null, so an ordinary error stays the two-word document
 * every other qits service answers with.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(String code, String message) {

  public static ErrorBody of(String message) {
    return new ErrorBody(null, message);
  }
}

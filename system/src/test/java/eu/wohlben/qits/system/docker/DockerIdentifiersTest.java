package eu.wohlben.qits.system.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.wohlben.qits.system.error.BadRequestException;
import org.junit.jupiter.api.Test;

/** The belts on every caller-supplied string that reaches a docker argv. */
class DockerIdentifiersTest {

  @Test
  void acceptsTheReferencesDockerItselfPrints() {
    // A swarm task name is what an operator copies out of `docker ps` on this platform, so it has
    // to pass: dots and digits and dashes.
    assertEquals(
        "dev-qits-ci.1.k4g0nn1ld7o272jl7fciatg6m",
        DockerIdentifiers.requireContainerRef("dev-qits-ci.1.k4g0nn1ld7o272jl7fciatg6m"));
    assertEquals("b248472f7a8e", DockerIdentifiers.requireContainerRef("b248472f7a8e"));
    assertEquals("qits_shared_m2", DockerIdentifiers.requireContainerRef("qits_shared_m2"));
  }

  @Test
  void refusesWhatCouldForgeAnElementBoundary() {
    // A leading dash is read as an option, not as a positional argument.
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireContainerRef("-f"));
    // A space, a slash, a colon and a newline each move a boundary docker or a log line relies on.
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireContainerRef("a b"));
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireContainerRef("a/b"));
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireContainerRef("a:b"));
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireContainerRef("a\nb"));
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireContainerRef(""));
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireContainerRef(null));
    assertThrows(
        BadRequestException.class, () -> DockerIdentifiers.requireContainerRef("x".repeat(200)));
  }

  @Test
  void aRefusalNamesTheFieldAndCannotForgeASecondLogLine() {
    // The rejected value ends up in a log and in an HTTP body, so its control characters are gone.
    BadRequestException refused =
        assertThrows(
            BadRequestException.class,
            () -> DockerIdentifiers.requireContainerRef("bad\nlevel=fatal msg=owned"));

    assertTrue(refused.getMessage().contains("container"), refused.getMessage());
    assertTrue(refused.getMessage().contains("bad?level"), refused.getMessage());
    assertEquals(1, refused.getMessage().lines().count(), refused.getMessage());
  }

  @Test
  void onlyAFullIdMayReachAnExec() {
    String id = "b248472f7a8e00bba8cd036232373baed8856d90eb77b06c53f3eb0b321fdb17";
    assertEquals(id, DockerIdentifiers.requireFullId(id));
    // A prefix is a valid REFERENCE and not a valid exec target: the exec argv carries only what
    // the daemon printed when the reference was resolved.
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireFullId("b248472f7a8e"));
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireFullId(id.toUpperCase()));
  }

  @Test
  void aTailIsBounded() {
    assertEquals(200, DockerIdentifiers.requireTail(200, 5000));
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireTail(0, 5000));
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireTail(-1, 5000));
    // A log panel is a glance at the last few hundred lines, not a log store.
    assertThrows(BadRequestException.class, () -> DockerIdentifiers.requireTail(100_000, 5000));
  }

  @Test
  void aSessionIdIsThisServicesOwnUuid() {
    assertNotNull(DockerIdentifiers.parseSessionId("11111111-2222-3333-4444-555555555555"));
    // Not a 400: the caller named a session, it just does not exist. The controller answers 404.
    assertNull(DockerIdentifiers.parseSessionId("nonsense"));
    assertNull(DockerIdentifiers.parseSessionId(null));
  }
}

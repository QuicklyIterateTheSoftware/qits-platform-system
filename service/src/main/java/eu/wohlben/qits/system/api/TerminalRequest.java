package eu.wohlben.qits.system.api;

/**
 * The POST body that opens a terminal — FLAT, on purpose.
 *
 * <p>A polymorphic body (`{"kind":"EXEC", …}` deserialised into a sealed hierarchy) would need
 * Jackson subtype resolution, which the native-image build cannot see and which turns a typo in a
 * discriminator into a 500. Three nullable fields and a switch in the controller is the shape that
 * survives the binary and gives a caller a 400 with a sentence in it.
 *
 * @param kind {@code GLANCES} or {@code EXEC}
 * @param container for EXEC: an id, an id prefix or a name; resolved against the daemon before any
 *     argv is built
 * @param shell for EXEC: {@code bash} or {@code sh}
 */
public record TerminalRequest(String kind, String container, String shell) {}

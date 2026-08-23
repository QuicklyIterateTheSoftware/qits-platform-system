package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * One swarm config, WITH ITS CONTENT.
 *
 * <p>Showing the data is a decision, and it is the opposite of the one made for secrets. A config
 * IS configuration — that is what the object is for, and swarm returns it to anybody who can list
 * it. An operator looking at a misbehaving service needs to see what it was actually handed. A
 * secret is the other thing, and swarm does not return those values at all, so there is nothing
 * here that could leak one by accident.
 *
 * @param data the config's bytes as text; swarm base64-encodes them on the wire. Empty, never
 *     null, so a client renders a document rather than the word "null" — and empty with
 *     {@code binary} set is the honest answer for bytes that are not text
 * @param binary true when the bytes are not valid UTF-8 — the client shows a note rather than mojibake
 */
public record ConfigDetail(
    String id,
    String name,
    String createdAt,
    String updatedAt,
    Map<String, String> labels,
    String data,
    boolean binary) {

  public static ConfigDetail from(JsonNode node) {
    String encoded = Json.textAt(node, "Spec", "Data");
    String decoded = "";
    boolean binary = false;
    if (encoded != null && !encoded.isBlank()) {
      try {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        decoded = new String(bytes, StandardCharsets.UTF_8);
        // A round trip that does not come back is a file that is not text. Saying so beats sending
        // a page of replacement characters and calling it the config.
        binary = !java.util.Arrays.equals(bytes, decoded.getBytes(StandardCharsets.UTF_8));
        if (binary) {
          decoded = "";
        }
      } catch (IllegalArgumentException e) {
        binary = true;
      }
    }
    return new ConfigDetail(
        Json.text(node, "ID"),
        Json.textAt(node, "Spec", "Name"),
        Json.text(node, "CreatedAt"),
        Json.text(node, "UpdatedAt"),
        Json.map(node, "Spec", "Labels"),
        decoded,
        binary);
  }
}

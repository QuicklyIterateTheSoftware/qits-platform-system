package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * One row of `docker volume ls`.
 *
 * <p>`createdAt` is absent: the list columns do not carry it, and a `volume inspect` per row would
 * be one docker fork per volume for a column nobody sorts on. The component is here so the client
 * holds one shape whether or not a later version fills it.
 */
public record VolumeSummary(
    String name,
    String driver,
    String scope,
    String mountpoint,
    String createdAt,
    Map<String, String> labels) {

  public static VolumeSummary from(JsonNode node) {
    return new VolumeSummary(
        Json.text(node, "Name"),
        Json.text(node, "Driver"),
        Json.text(node, "Scope"),
        Json.text(node, "Mountpoint"),
        null,
        Json.labelColumn(Json.text(node, "Labels")));
  }
}

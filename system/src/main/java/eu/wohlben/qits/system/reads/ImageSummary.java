package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/** One row of `docker image ls --no-trunc`. */
public record ImageSummary(
    String id,
    String repository,
    String tag,
    String digest,
    String size,
    String createdAt,
    String createdSince,
    String containers) {

  public static ImageSummary from(JsonNode node) {
    return new ImageSummary(
        Json.text(node, "ID"),
        Json.text(node, "Repository"),
        Json.text(node, "Tag"),
        Json.text(node, "Digest"),
        Json.text(node, "Size"),
        Json.text(node, "CreatedAt"),
        Json.text(node, "CreatedSince"),
        Json.text(node, "Containers"));
  }
}

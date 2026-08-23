package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One row of `docker image ls --no-trunc`.
 *
 * <p>`size` is docker's own rendering ("325MB") and `sizeBytes` is that rendering read back — the
 * client sorts and totals on the number, an operator recognises the string. See {@link HumanSize}
 * for why there is no better source for the number through the CLI.
 */
public record ImageSummary(
    String id,
    String repository,
    String tag,
    String digest,
    String size,
    long sizeBytes,
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
        HumanSize.toBytes(Json.text(node, "Size")),
        Json.text(node, "CreatedAt"),
        Json.text(node, "CreatedSince"),
        Json.text(node, "Containers"));
  }
}

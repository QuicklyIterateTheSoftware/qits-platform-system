package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * What is on the disk, per class — images, containers, local volumes, build cache.
 *
 * <p>THE SIZES ARE STRINGS, deliberately. `docker system df` renders them ("308.3GB",
 * "286.8GB (93%)") and the API does not; re-deriving bytes would mean a second, much more expensive
 * call, and the operator's mental model is the string they would have seen in a shell. Reclaimable
 * carries its own percentage for the same reason.
 */
public record DiskUsage(List<Entry> entries) {

  /** One row of `docker system df`. */
  public record Entry(String type, String totalCount, String active, String size, String reclaimable) {

    public static Entry from(JsonNode node) {
      return new Entry(
          Json.text(node, "Type"),
          Json.text(node, "TotalCount"),
          Json.text(node, "Active"),
          Json.text(node, "Size"),
          Json.text(node, "Reclaimable"));
    }
  }

  public static DiskUsage from(List<JsonNode> rows) {
    return new DiskUsage(rows.stream().map(Entry::from).toList());
  }
}

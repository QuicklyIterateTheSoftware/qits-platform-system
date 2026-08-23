package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;

/**
 * What is on the disk, per class — images, containers, local volumes, build cache.
 *
 * <p><b>BOTH FORMS, on purpose.</b> The four totals are bytes, because the client draws a bar and
 * sorts on them. The entries carry docker's own rendered strings ("308.3GB", "286.8GB (93%)"),
 * because that is what an operator would have seen in a shell and it is the honest record of what
 * this service was told.
 *
 * <p>The bytes are read back OUT of the rendering — see {@link HumanSize} for why there is no
 * better source through the CLI, and why the numbers are decimal.
 */
public record DiskUsage(
    long imagesBytes,
    long containersBytes,
    long volumesBytes,
    long buildCacheBytes,
    long reclaimableBytes,
    List<Entry> entries) {

  /** One row of `docker system df`, exactly as it was printed. */
  public record Entry(
      String type, String totalCount, String active, String size, String reclaimable) {

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
    List<Entry> entries = rows.stream().map(Entry::from).toList();
    long reclaimable = 0;
    for (Entry entry : entries) {
      reclaimable += HumanSize.toBytes(entry.reclaimable());
    }
    return new DiskUsage(
        bytesOf(entries, "images"),
        bytesOf(entries, "containers"),
        bytesOf(entries, "local volumes"),
        bytesOf(entries, "build cache"),
        reclaimable,
        entries);
  }

  /**
   * The size of one class. Matched on the TYPE column rather than on row order: docker prints the
   * four in a fixed order today, and a positional read would silently attribute the wrong number
   * the day it prints a fifth.
   */
  private static long bytesOf(List<Entry> entries, String type) {
    return entries.stream()
        .filter(entry -> entry.type() != null && entry.type().toLowerCase(Locale.ROOT).equals(type))
        .findFirst()
        .map(entry -> HumanSize.toBytes(entry.size()))
        .orElse(0L);
  }
}

package eu.wohlben.qits.system.reads;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the sizes the docker CLI renders back into bytes.
 *
 * <p><b>Why this exists rather than a better call.</b> `docker system df` and `docker image ls`
 * print sizes as text — "308.3GB", "1.492GB (4%)", "325MB" — and there is no CLI flag that returns
 * the numbers behind them. The daemon's HTTP API has them; the CLI does not expose it. The client
 * needs numbers (it draws bars and sorts columns), so the rendering is read back here.
 *
 * <p><b>Decimal, not binary.</b> Docker renders with go-units' HumanSize, which divides by 1000:
 * "1.5GB" is 1_500_000_000 bytes, not 1_610_612_736. Reading it as binary would overstate every
 * figure on the page by 7% at GB and 10% at TB.
 *
 * <p><b>It is a rendering, so it is lossy, and that is fine for what it is for.</b> "308.3GB" was
 * some number between 308.25 and 308.35 GB; a disk-usage panel does not need the digit that was
 * dropped. The original string travels beside the number for anyone who wants exactly what a shell
 * would have shown.
 */
public final class HumanSize {

  /** A number, optional space, an optional unit — with anything in parentheses already gone. */
  private static final Pattern SIZE = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z]*)");

  private HumanSize() {}

  /**
   * Parse one rendered size.
   *
   * @param rendered what docker printed, with or without a trailing "(93%)"
   * @return the size in bytes, or 0 for anything unparseable — a panel showing zero is better than
   *     a panel that failed, and the original string is still on the record beside it
   */
  public static long toBytes(String rendered) {
    if (rendered == null || rendered.isBlank()) {
      return 0L;
    }
    // "286.8GB (93%)" — the percentage is docker's own derivation, not part of the size.
    int paren = rendered.indexOf('(');
    String value = paren < 0 ? rendered : rendered.substring(0, paren);
    Matcher matched = SIZE.matcher(value);
    if (!matched.find()) {
      return 0L;
    }
    double number = Double.parseDouble(matched.group(1));
    return Math.round(number * multiplier(matched.group(2)));
  }

  private static double multiplier(String unit) {
    return switch (unit.toLowerCase(Locale.ROOT)) {
      case "", "b" -> 1d;
      case "kb" -> 1_000d;
      case "mb" -> 1_000_000d;
      case "gb" -> 1_000_000_000d;
      case "tb" -> 1_000_000_000_000d;
      case "pb" -> 1_000_000_000_000_000d;
      // An unknown unit is a docker release that started rendering differently. Zero, and the
      // string beside it, beats a number that is wrong by a factor of a thousand.
      default -> 0d;
    };
  }
}

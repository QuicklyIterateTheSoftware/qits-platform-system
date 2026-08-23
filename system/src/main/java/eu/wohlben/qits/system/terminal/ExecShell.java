package eu.wohlben.qits.system.terminal;

import java.util.Locale;

/**
 * The shells a caller may ask for inside a container. An ENUM rather than a string, because the
 * value reaches a {@code docker exec} argv: an enum is a closed set the argv builder cannot be
 * talked out of, and "unknown shell" becomes a 400 at the boundary instead of a command line
 * assembled from whatever arrived.
 *
 * <p>Two entries, and no more without a decision. {@code bash} is what a debian-based image has and
 * {@code sh} is what every image has; anything else is a program, and running arbitrary programs in
 * a container is not what this service offers.
 */
public enum ExecShell {
  BASH("bash"),
  SH("sh");

  private final String binary;

  ExecShell(String binary) {
    this.binary = binary;
  }

  /** The word that goes into the argv. */
  public String binary() {
    return binary;
  }

  /**
   * Read a caller's value, case-insensitively.
   *
   * @return the shell, or null when the value names none — the caller answers 400
   */
  public static ExecShell parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "bash" -> BASH;
      case "sh" -> SH;
      default -> null;
    };
  }
}

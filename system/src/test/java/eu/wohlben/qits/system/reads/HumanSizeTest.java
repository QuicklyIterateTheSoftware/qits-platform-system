package eu.wohlben.qits.system.reads;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Reading docker's rendered sizes back into bytes. */
class HumanSizeTest {

  @Test
  void readsTheUnitsDockerActuallyPrints() {
    assertEquals(0L, HumanSize.toBytes("0B"));
    assertEquals(512L, HumanSize.toBytes("512B"));
    assertEquals(110_600L, HumanSize.toBytes("110.6kB"));
    assertEquals(325_000_000L, HumanSize.toBytes("325MB"));
    assertEquals(308_300_000_000L, HumanSize.toBytes("308.3GB"));
    assertEquals(2_000_000_000_000L, HumanSize.toBytes("2TB"));
  }

  @Test
  void isDecimalBecauseGoUnitsIs() {
    // 1000, not 1024. Reading it as binary would overstate every figure on the page by 7% at GB
    // and 10% at TB — a bar that is wrong and looks right.
    assertEquals(1_500_000_000L, HumanSize.toBytes("1.5GB"));
  }

  @Test
  void stripsTheReclaimablePercentage() {
    // `docker system df` renders reclaimable as "<size> (<percent>)"; the percentage is docker's
    // own derivation, not part of the size.
    assertEquals(286_800_000_000L, HumanSize.toBytes("286.8GB (93%)"));
    assertEquals(103_500_000_000L, HumanSize.toBytes("103.5GB"));
  }

  @Test
  void anythingUnreadableIsZeroRatherThanAFailure() {
    // A panel showing zero beats a panel that failed — and the original string travels beside the
    // number, so nothing is actually lost.
    assertEquals(0L, HumanSize.toBytes("N/A"));
    assertEquals(0L, HumanSize.toBytes(""));
    assertEquals(0L, HumanSize.toBytes(null));
    // An unknown unit is a docker release that renders differently. Zero beats being wrong by a
    // factor of a thousand.
    assertEquals(0L, HumanSize.toBytes("12ZB"));
  }
}

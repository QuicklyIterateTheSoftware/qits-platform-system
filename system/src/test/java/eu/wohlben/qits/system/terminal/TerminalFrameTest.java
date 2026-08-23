package eu.wohlben.qits.system.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * The client half of the wire protocol. It is the platform's existing one, unchanged so that one
 * xterm client works against every terminal on the platform.
 */
class TerminalFrameTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void readsKeystrokes() {
    TerminalFrame frame = TerminalFrame.parse(mapper, "{\"type\":\"data\",\"data\":\"ls -la\\n\"}");

    assertEquals("ls -la\n", assertInstanceOf(TerminalFrame.Data.class, frame).text());
  }

  @Test
  void readsAResize() {
    TerminalFrame frame =
        TerminalFrame.parse(mapper, "{\"type\":\"resize\",\"cols\":132,\"rows\":50}");

    TerminalFrame.Resize resize = assertInstanceOf(TerminalFrame.Resize.class, frame);
    assertEquals(132, resize.cols());
    assertEquals(50, resize.rows());
  }

  @Test
  void anImplausibleSizeFallsBackRatherThanReachingTheIoctl() {
    // struct winsize is unsigned shorts: a negative value cast into one is a terminal 65000
    // columns wide, and a zero is an ioctl that silently does nothing.
    TerminalFrame.Resize negative =
        assertInstanceOf(
            TerminalFrame.Resize.class,
            TerminalFrame.parse(mapper, "{\"type\":\"resize\",\"cols\":-1,\"rows\":0}"));
    assertEquals(80, negative.cols());
    assertEquals(24, negative.rows());

    TerminalFrame.Resize huge =
        assertInstanceOf(
            TerminalFrame.Resize.class,
            TerminalFrame.parse(mapper, "{\"type\":\"resize\",\"cols\":999999,\"rows\":24}"));
    assertEquals(80, huge.cols());
  }

  @Test
  void aMissingSizeIsTheDefaultOne() {
    TerminalFrame.Resize resize =
        assertInstanceOf(
            TerminalFrame.Resize.class, TerminalFrame.parse(mapper, "{\"type\":\"resize\"}"));

    assertEquals(80, resize.cols());
    assertEquals(24, resize.rows());
  }

  @Test
  void anythingUnreadableIsNullSoTheSocketDropsItRatherThanClosing() {
    // A terminal is a stream. Closing it because one frame was malformed would lose a live session
    // to a client bug.
    assertNull(TerminalFrame.parse(mapper, "not json"));
    assertNull(TerminalFrame.parse(mapper, "[]"));
    assertNull(TerminalFrame.parse(mapper, "{\"type\":\"detach\"}"));
    assertNull(TerminalFrame.parse(mapper, "{\"type\":\"data\"}"));
    assertNull(TerminalFrame.parse(mapper, "{\"type\":\"data\",\"data\":42}"));
    assertNull(TerminalFrame.parse(mapper, ""));
    assertNull(TerminalFrame.parse(mapper, null));
  }
}

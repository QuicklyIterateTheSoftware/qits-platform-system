package eu.wohlben.qits.system.reads;

/**
 * A container's recent output.
 *
 * @param truncated whether the bound dropped the FRONT of what docker printed. A workload can put
 *     a megabyte on one line, so the tail is what is kept and the flag is how the client says so.
 */
public record LogChunk(String text, boolean truncated) {}

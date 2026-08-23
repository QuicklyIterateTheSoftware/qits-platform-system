package eu.wohlben.qits.system.reads;

/**
 * The landing answer: one call that fills the whole first screen, so the client does not open three
 * connections to draw a header.
 */
public record Overview(HostInfo host, DiskUsage usage, SwarmInfo swarm) {}

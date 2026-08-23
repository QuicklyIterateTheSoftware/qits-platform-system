package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The swarm as this node sees it, read out of `docker info`'s own `Swarm` block rather than with a
 * second call — the block carries every number the overview needs and is free with the host read.
 *
 * @param state docker's `LocalNodeState`: `active`, `inactive`, `pending`, `locked`, `error`
 * @param nodeId THIS node's id — the one the whole node-scoped half of the API is keyed on
 * @param controlAvailable whether this node is a manager; swarm-wide reads need one
 * @param error the daemon's own swarm error, which is blank in the ordinary case
 */
public record SwarmInfo(
    String state,
    String nodeId,
    String nodeAddress,
    boolean controlAvailable,
    int managers,
    int nodes,
    String clusterId,
    String error) {

  public static SwarmInfo from(JsonNode info) {
    JsonNode swarm = Json.at(info, "Swarm");
    return new SwarmInfo(
        Json.textAt(swarm, "LocalNodeState"),
        Json.textAt(swarm, "NodeID"),
        Json.textAt(swarm, "NodeAddr"),
        Json.boolAt(swarm, "ControlAvailable"),
        Json.intAt(swarm, "Managers"),
        Json.intAt(swarm, "Nodes"),
        Json.textAt(swarm, "Cluster", "ID"),
        Json.textAt(swarm, "Error"));
  }

  /** Whether this daemon is in a swarm at all — an `inactive` one has no nodes to list. */
  public boolean active() {
    return "active".equalsIgnoreCase(state);
  }
}

package eu.wohlben.qits.system.reads;

import com.fasterxml.jackson.databind.JsonNode;
import eu.wohlben.qits.system.docker.DockerArgv;
import eu.wohlben.qits.system.docker.DockerCli;
import eu.wohlben.qits.system.error.DockerUnavailableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/** The host: what this machine is, what is on its disk, and which node of the swarm it is. */
@ApplicationScoped
public class HostReads {

  @Inject DockerCli docker;

  /** `docker info`, read once — the host facts and the swarm block both come out of it. */
  public JsonNode info() {
    List<JsonNode> rows = docker.jsonLines(DockerArgv.info(docker.binary()));
    if (rows.isEmpty()) {
      throw new DockerUnavailableException("docker printed no info document");
    }
    return rows.get(0);
  }

  public HostInfo host() {
    return HostInfo.from(info());
  }

  public DiskUsage usage() {
    return DiskUsage.from(docker.jsonLines(DockerArgv.systemDf(docker.binary())));
  }

  /** The swarm as this node sees it. */
  public SwarmInfo swarm() {
    return SwarmInfo.from(info());
  }

  /**
   * The whole first screen in one call, and one `docker info` between the two halves of it —
   * calling {@link #host()} and {@link #swarm()} separately would fork docker twice for one
   * document.
   */
  public Overview overview() {
    JsonNode info = info();
    return new Overview(HostInfo.from(info), usage(), SwarmInfo.from(info));
  }

  /**
   * This node's swarm id, or null when the daemon is not in a swarm.
   *
   * <p>It is read live rather than cached at boot: a daemon can join or leave a swarm under a
   * running service, and a cached id would then key every node-scoped read on a node that no
   * longer exists.
   */
  public String localNodeId() {
    String id = SwarmInfo.from(info()).nodeId();
    return id == null || id.isBlank() ? null : id;
  }
}

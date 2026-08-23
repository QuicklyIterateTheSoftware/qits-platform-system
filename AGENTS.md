# qits-platform-system — working notes

Read `README.md` first: it defines what this service reads, the two docker output shapes, the
terminal lifecycle and the config keys. This file is the working conventions on top of it.

## The rules that shape everything

**This service READS; it does not act.** No scale, no restart, no rm of anything it did not itself
create, no config or secret write. The vocabulary is reads plus `run`/`exec` of a terminal and
`rm -f` of this service's own labelled containers. Adding a write is a decision about what this
console IS, and it belongs in `qits-system-plan.md` before it belongs in a commit here.

**The contract is pinned by `qits-system-plan.md` in the qits-qits wrapper.** The route shapes, the
wire names, the config keys and the terminal protocol are written down there and two repositories
build against them. Changing one of those shapes is a plan edit and a conversation.

**A clone of this repo alone builds and tests green** — no monorepo, no docker, no prior
`mvn install` elsewhere, no credentials. That is why the poms duplicate versions instead of
inheriting them, and why the suite forks a shell script instead of a daemon.

**The one thing it needs besides Maven Central** is the platform's own Maven repository, for
`qits-auth-core` and `qits-arch-rules`. `<repositories>` in the root pom points at
`${qits.maven.repository.url}`; the image build overrides it through `.qits-maven-settings.xml`,
which mirrors the exact repository id `qits-maven` — an exact id match is what gets past Maven's
`external:http:*` blocker.

**The gate is `./mvnw clean verify -Dquarkus.http.test-port=0`**, and it needs BOTH a node on PATH
and `git submodule update --init`. Always `clean`. Port 0 is not optional on the deployment host.

## Docker

**Every call goes through `DockerCli`, and every argv through `DockerArgv` or `TerminalArgv`.** The
builders are pure functions with no I/O, which is what lets the suite assert them element for
element — and the argv IS the sandbox, so a flag lost in a refactor is invisible everywhere else
until it is invisible in production.

**A failed call is a 503 carrying docker's own last words. Never a 404, never an empty list.** A
read that could not reach the daemon knows nothing. The three failures an operator actually meets —
socket not mounted, group not added, node is not a manager — all arrive as a non-zero exit with one
clear sentence, and passing that sentence through is worth more than anything this service could
invent. The two callers that must tell "docker said no" from "docker could not be asked" use
`attempt` and match on docker's wording; they are `SwarmReads.inspectOne` and `NodeReads`.

**Two checkpoints on every caller string, and neither is trusted alone.** The controller validates,
and `DockerIdentifiers` validates again inside the argv builder. What they are really for is the
element boundaries a value could forge — a leading `-` read as an option, a `:` moving a mount's
fields.

**The exec target is resolved, not passed through.** `NodeReads.requireRunningContainer` hands the
reference to the daemon and keeps only the canonical 64-hex id it printed;
`DockerIdentifiers.requireFullId` in the argv builder is the belt that makes bypassing it fail.

**Never add a captured call without a timeout and a bound.** Both are required parameters of
`DockerProcess.run` on purpose: a convenience overload is the regression.

## Parsers

**Fixtures are real captures, and a new field needs a new capture.** `system/src/test/resources/
fixtures/README.md` says which files are captured and which two are hand-written because this host
has no swarm configs. A parser written from docker's API documentation rather than from its CLI
output reads field names that are not there.

**Every field is optional.** Docker renames and drops columns between releases, and one absent
field must cost one blank value, not a panel. `Json` is where that tolerance lives; nothing else
should be reaching into a `JsonNode` by hand.

**Wire names are the CLIENT's vocabulary, not docker's.** `hostname` not `Name`, `os` not
`OperatingSystem`, `dockerVersion` not `ServerVersion`. The parser is where the two meet, and that
is the only place they should.

**Environment values never leave this service.** `Json.envKeys` is the only way an `Env` array is
read, and two tests assert that the fixtures' scrubbed secret marker reaches no record. If a future
detail view needs more of a container, it still does not need this.

## The terminal subsystem

**It is copied from qits-projects, incident and all.** `ForeignPty`, its reachability metadata and
`TerminalProcesses.terminalProcess` are that repository's, and the comment on the last one is the
production outage it was written for: `ProcessBuilder`'s file redirects are opened by the CALLING
process, and a session leader opening a pts adopts it as its controlling terminal. Do not simplify
that launch. `HangupImmunity` is the backstop under it.

**The reader is a PLATFORM thread, not a virtual one.** The read is a blocking FFM downcall, which
pins its carrier for the life of the session.

**One stateful decoder per session.** A multi-byte character straddles a 4 KiB read boundary about
once a second when a full-screen program is drawing; decoding each read on its own turns every one
of them into a replacement character that never heals. Replay decodes the ring snapshot separately —
feeding the live decoder the scrollback would corrupt both.

**Create is separate from attach, and the linger window is armed at CREATION.** A POST whose browser
never connects must not leave a container running with nothing pointing at it.

**Glances stops when the last viewer leaves; a shell does not.** The last detach arms
`terminals.glances-linger` (3s) for GLANCES and `terminals.linger` (60s) for EXEC. A host monitor
holds nothing worth coming back to, so navigating away from the Overview must stop it — but not
instantly: a reload closes and reopens the socket within a second or two, and the session is shared.
The CREATION window stays the long one for both, because nobody has attached yet.

**Ending a glances session is two acts.** `--rm` fires when the CONTAINER exits; killing the CLI
attached to it is not that. The second act is `docker rm -f` by the name the argv builder derived
from the session id, and the boot sweep is what catches the ones a killed service left behind.

**The sweep filters on the owner label's VALUE.** Two platforms can share one docker daemon, and a
sweep that reaped every `qits.system.owner` container would kill the other platform's live terminals
on every restart of this one.

## The wire

**Anything returned as `Response.entity(...)` is invisible to the build-time Jackson analysis**,
which is what `api/ApiWireReflection` exists for — including nested records, which registering the
outer one does not reach. A new response type joins that list in the commit that adds it; the
failure is a 500 in the native binary while every JVM test stays green.

**`{"message": "…"}` and nothing else**, plus `"code"` on the one refusal a client branches on
(`NODE_REMOTE`). One exception mapper, so two routes failing for the same reason cannot answer
differently.

**The WebSocket path is a literal.** `@WebSocket` does not follow `quarkus.rest.path`, so
`/system/api/terminals/{id}` is spelled in `TerminalSocket.PATH_PREFIX` and moves with that key by
hand. `TerminalView` builds `socketPath` from the same constant so the client never spells it twice.

**Add a literal route under `/system`, add its prefix to `quarkus.quinoa.ignored-path-prefixes` in
the same commit.** Without an entry the SPA fallback answers the page with 200 text/html, and a
JSON parser gets an HTML document.

## Tests

**No mocks.** The suite substitutes the docker BINARY, not a class: `qits.system.docker.binary`
points at `service/src/test/resources/fake-docker/docker`, which answers the reads in docker's exact
shapes, refuses an unknown container in docker's own words, and turns `run -it`/`exec -it` into a
real echo loop. Everything between the HTTP request and that child is the shipped code, including a
real pseudo-terminal.

**`FakeDockerConfigSource` is a config source rather than a per-test profile** so nobody can forget
it and silently run against the developer's real docker.

**A test must leave no session behind.** Each terminal is a PTY and a child; the `@AfterEach` in the
terminal suites ends every one.

**`PackagedSurfaceIT` is the only proof of three things**: the FFM metadata and the run-time
initialisation of `ForeignPty`, `setsid` being present in the runtime image, and the client being
served with a matching `<base href>`. Run it with `-DskipITs=false`, or with `-Dnative`, which flips
the flag itself.

**Never run two native builds on this platform's host at once.** They OOM a 16 GB machine and take
it down with them.

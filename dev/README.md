# Local development

A Paper server running Grim, in offline mode, and a client that hot-reloads
code while it runs. Nothing here needs a Minecraft account.

| Run configuration | Gradle equivalent       | What it does                                              |
|-------------------|-------------------------|-----------------------------------------------------------|
| **Server (Debug)** | `gradlew runServer`     | Paper (same version as the mod) + Grim, offline, on `localhost:25565` |
| **Client (Debug)** | `gradlew runClientDebug` | The client on JetBrains Runtime, as `Dev`, with hot reload |

Start the server, start the client, then join `localhost` from Multiplayer.

## The server (`dev/server`)

- Built by [run-paper](https://github.com/jpenilla/run-task): it downloads Paper
  for `minecraft_version` and Grim (`grim_version`, a Modrinth version id; see
  `gradle.properties`) into Gradle's cache.
- `dev/server/run/` is the live server: world, logs, plugin configs. Ignored by
  git. Delete it for a fresh world.
- `dev/server/config/server.properties` is copied in only when the run folder
  has none. `online-mode` is forced off by the task's arguments either way.
- The player named `dev_username` (default `Dev`) is opped on first start, so
  `/grim alerts`, `/gamemode` and `/tp` work immediately. Grim's own settings are
  in `dev/server/run/plugins/GrimAC/`.
- The task agrees to the [Minecraft EULA](https://aka.ms/MinecraftEULA) for
  this local server with `-Dcom.mojang.eula.agree=true`.
- Requires Gradle 8.14.3+ (run-paper 3.x); the wrapper is pinned to 8.14.3.

## Hot reload

`Client (Debug)` runs on **JetBrains Runtime 21** with:

- `-XX:+AllowEnhancedClassRedefinition` &mdash; DCEVM: reload structural changes
  (new/removed methods and fields, changed signatures, new classes), not only
  method bodies.
- `-XX:HotswapAgent=fatjar` &mdash; HotswapAgent, from JBR's `lib/hotswap/`.
- `-Dfabric.debug.disableClassPathIsolation=true` &mdash; required with
  HotswapAgent: it patches classes the game loads (Log4j) to call back into
  itself, and Fabric otherwise hides the agent from them.
- `-javaagent:.gradle/dev-agents/sponge-mixin.jar -Dmixin.hotSwap=true` &mdash;
  Mixin's hot-swap agent, so a changed mixin method body is re-applied.
  `prepareDevClient` copies the jar there before each launch.

What still needs a restart: new mixins or new injectors, changes to a mixin's
`@At` target, `fabric.mod.json`, and access wideners. Static initialisers do not
re-run on reload.

### Installing JetBrains Runtime

The run configuration expects `~/.jdks/jbr-21.0.11`:

1. Download `jbrsdk-21.0.11-windows-x64-b1163.116.zip` from the
   [JBR releases](https://github.com/JetBrains/JetBrainsRuntime/releases/tag/jbr-release-21.0.11b1163.116)
   and extract it to `%USERPROFILE%\.jdks\jbr-21.0.11`.
2. Download `hotswap-agent-2.0.3.jar` from the
   [HotswapAgent releases](https://github.com/HotswapProjects/HotswapAgent/releases/tag/RELEASE-2.0.3)
   and save it as `%USERPROFILE%\.jdks\jbr-21.0.11\lib\hotswap\hotswap-agent.jar`.

`runClientDebug` finds it as a Gradle toolchain (vendor JetBrains, version 21).
A different JBR location means editing the JRE in `Client (Debug)`.

## IntelliJ settings

1. **Reload the Gradle project** (Gradle tool window, reload), so the
   `dev:server` module and the new tasks appear.
2. **Settings, Build, Execution, Deployment, Build Tools, Gradle:**
   *Build and run using* = **Gradle** (the default; keep it). The mod's
   `fabric.mod.json` is filled in by Gradle's `processResources`, so IntelliJ's
   own builder would ship it with a literal `${mod_id}` and the game would refuse
   to load the mod.
3. **Settings, Build, Execution, Deployment, Debugger, HotSwap:**
   - *Reload classes after compilation:* **Always** (or *Ask*).
   - *Enable HotSwap agent for Groovy code:* off (not used).
   - Leave *Show the "Code changed" floating button* on.
4. Optional, **Settings, Build, Execution, Deployment, Compiler:**
   *Build project automatically* on, and in **Help, Find Action, Registry**
   enable `compiler.automake.allow.when.app.running`, to reload on save instead of on
   build.
5. **Lombok:** the bundled Lombok plugin must be enabled. Annotation processing
   runs in Gradle, so nothing else is needed.

### The loop

1. **Debug** (not Run) `Server (Debug)`, then **Debug** `Client (Debug)`. Hot reload
   only works with the debugger attached.
2. Edit code.
3. **Build, Build Project** (`Ctrl+F9`), or click the *Code changed* button. The
   changed classes are reloaded into the running client.

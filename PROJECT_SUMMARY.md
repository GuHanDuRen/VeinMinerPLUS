# VeinMinerPlus Project Handoff

Last updated: 2026-09-13

## Read First

This repository contains three independent builds of the same Mod. Do not merge their Gradle files, source roots, or output artifacts.

- Project root: `D:\VeinMinerPLUS`. The project was moved here from `G:\Codex项目\连锁mod`.
- NeoForge build: Minecraft `1.21.1`, NeoForge `21.1.233`, Java `21` at `C:\Program Files\Java\jdk-21.0.10`. Root project.
- Forge 1.20.1 build: Minecraft `1.20.1`, Forge `47.4.13`, Java `17` at `C:\Program Files\Java\jdk-17`. Project root `forge-1.20.1`.
- Forge 1.12.2 build: Minecraft `1.12.2`, Forge `14.23.5.2860`, Java `8` at `C:\Program Files\Java\jdk1.8.0_271`. Project root `forge-1.12.2`.
- Mod ID: `veinminerplus`.
- Java package: `com.extrarawstyle.veinminerplus`.
- Author: `Extra_RawStyle`.
- License: `MIT`.
- Gradle: use each project's own `gradlew` wrapper. Distributions and dependencies are cached under `C:\Users\<user>\.gradle` — Gradle `8.8` for the NeoForge and Forge 1.20.1 builds, Gradle `4.9` for the Forge 1.12.2 build. The previously documented `G:\gradle-8.8`, `G:\java21`, `G:\java17`, and `G:\GradleCache` paths no longer exist.

The user manually installs JARs into test instances. Do not start Minecraft or copy artifacts into a test instance unless the user explicitly changes this instruction.

## Current Artifacts

Each build writes to its own `build\libs` directory. Historical JARs are retained there and must not be overwritten.

| Target | Current version | Artifact | SHA-256 |
| --- | --- | --- | --- |
| NeoForge 1.21.1 | `1.2.8` | `build\libs\veinminerplusG-1.21.1-neoforge-1.2.8.jar` | `D335F825BA19BE6590E5822C2F9E471DF3479EDB1B0DA2EFF60A68B82C298E68` |
| Forge 1.20.1 | `1.2.10` | `forge-1.20.1\build\libs\veinminerplusG-1.20.1-forge-1.2.10.jar` | `CF31A4EB280000B42F121ED095312C503E90F100345CBCE5A019327F3206BE4C` |
| Forge 1.12.2 | `1.2.8-forge1122` | `forge-1.12.2\build\libs\veinminerplusG-1.12.2-forge-1.2.8.jar` | `3D94E61071E8E66643E1ACE4E8F6FE8E1981BB945FC26B67A8134BD6017F4AD5` |

Increment the relevant `mod_version` for every further functional or metadata change. Preserve old JARs and do not overwrite them.

## Project Layout

| Target | Project root | Server source | Client source |
| --- | --- | --- | --- |
| NeoForge | `D:\VeinMinerPLUS` | `src\main\java\com\extrarawstyle\veinminerplus\ChainEvents.java` | `VeinMinerPlusClient.java` in the same package |
| Forge 1.20.1 | `D:\VeinMinerPLUS\forge-1.20.1` | `src\main\java\com\extrarawstyle\veinminerplus\ChainEvents.java` | `VeinMinerPlusClient.java` plus `VeinMinerPlusClientEvents.java` |
| Forge 1.12.2 | `D:\VeinMinerPLUS\forge-1.12.2` | `src\main\java\com\extrarawstyle\veinminerplus\ChainEvents.java` | `VeinMinerPlusClient.java` |

All builds also contain `ChainMode.java`, `Config.java`, `NetworkHandler.java`, and `VeinMinerPlus.java` under their respective source roots.

Config limits are enforced in four places per build. When a limit changes, update all of them or the config definition and the runtime clamp will disagree: `Config.java` (definition), `NetworkHandler.java` (server-side clamp when a config packet is applied), `VeinMinerConfigScreen.java` (GUI input bounds; Forge 1.12.2 keeps them in the `FIELD_MAXES` array), and `CommandEvents.java` (command argument bounds). Range text in `en_us` / `zh_cn` descriptions must be updated too. Forge 1.12.2 has no hardcoded range text in its `.lang` files — it builds the range string from `FIELD_MAXES` at runtime and uses `%s-%s` placeholders in command messages.

Loader API difference: NeoForge `ModConfigSpec.IntValue` exposes `getAsInt()`; Forge `ForgeConfigSpec.IntValue` only exposes `get()`. Do not copy one build's accessor into the other.

## Shared Behavior

- Normal chain mining searches all 26 neighboring directions. Default limit: `1024` blocks.
- Area modes are `1x1`, `3x3`, and `5x5`; `2x2` was deliberately removed.
- Area mining determines the plane from the hit face and advances away from the player into the target.
- Blast modes: same block, ores, any harvestable block, and logs only.
- Logs mode targets `minecraft:logs` and does not chain leaves.
- Containers, menu blocks, and blocks exposing an item-handler capability are excluded.
- A chain starts only after the player actually breaks the first eligible block while the grave-accent key is held.
- Releasing the key ends the current job and settles its buffered drops.
- Hold `Shift + ~` and use the mouse wheel to select a mode. The selected mode is shown in the top-left HUD.
- All chained blocks use `player.gameMode.destroyBlock(pos)` to preserve the normal player-break path, including drops, enchantments, durability, experience, and events.
- Searches and destruction are tick-bounded to avoid blocking server TPS.
- Normal, area, and blast modes each cap blocks broken per server tick. Normal and area modes share `maxNormalBlocksPerTick`; blast modes use `maxBlastBlocksPerTick`. Area mode previously used a hardcoded `BLOCK_BREAKS_PER_TICK = 8` constant (named `AREA_BREAKS_PER_TICK` in the 1.12.2 build), which throttled it to 8 blocks per tick regardless of configuration and made it roughly four times slower than normal chaining. It now uses the configurable normal per-tick budget, matching the fact that it already shared `maxNormalBlocks` as its total limit.

### Empty Hand and Wood Changes

- Empty hand may now start and continue normal, area, and blast chain modes.
- This does not bypass normal harvest checks. A block still needs to be breakable by the player under the active loader's normal rules.
- Blast logs no longer requires an axe. Any tool, including an empty hand, can chain a log when it can normally break that log.

### Drop Settlement

- Drops and experience are buffered during a chain and settle at the player's current position when the job finishes, reaches its limit, the key is released, or the player leaves.
- Item stacks are merged by item plus components/tags before spawning. This avoids item entities first appearing at remote block positions on map mods.
- NeoForge captures drops with `BlockDropsEvent` before they join the world.
- Forge captures item entities and experience orbs with `EntityJoinLevelEvent`; active chained breaks use a thread-local capture context and the initial block uses a pending origin window.

## Ore Tags

| Target | Blast ores tags |
| --- | --- |
| NeoForge 1.21.1 | `minecraft:ores`, `c:ores` |
| Forge 1.20.1 | `minecraft:ores`, `forge:ores`, `c:ores` |

The Forge support for `forge:ores` was added for Monifactory compatibility.

## Configuration

The common config file is `config/veinminerplus-common.toml` in each running instance.

| Key | Meaning | Range | Default |
| --- | --- | --- | --- |
| `maxNormalBlocks` | normal and area total limit | `32-2147483647` | `1024` |
| `maxNormalBlocksPerTick` | normal and area blocks broken per tick | `1-2147483647` | `32` |
| `maxBlastBlocks` | blast total limit | `32-2147483647` | `32767` |
| `maxBlastBlocksPerTick` | blast blocks broken per tick | `1-2147483647` | `64` |
| `blastSearchDistance` | blast search distance from each found block | `3-2147483647` | `48` |

Upper bounds were raised from `32767` / `384` / `32767` / `512` / `128` to `Integer.MAX_VALUE` (`2147483647`). Defaults were left unchanged.

Practical caveats on the raised bounds — a value being accepted does not make it safe to use:

- `blastSearchDistance` feeds `createBlastOffsets`, which prebuilds a `(2 * distance + 1)^3` offset list and sorts it. Large values will exhaust heap or hang the server before any block is broken. Keep this in the low hundreds.
- `maxNormalBlocksPerTick` and `maxBlastBlocksPerTick` are hard per-tick break budgets. Setting them very high lets a single tick break an unbounded number of blocks through `destroyBlock` and will collapse server TPS.
- `maxNormalBlocks` and `maxBlastBlocks` cap the `examined` set and the total break count. A very large value allows one chain to run for a very long time and hold its chunk references.

The blast search distance is cached per `distance + ":" + manhattan` key in `BLAST_OFFSETS`, so each distinct distance builds its own offset list.

## Forge 1.20.1 Notes

- The target pack was documented as `G:\MC\PCL\.minecraft\versions\Monifactory`. That path is not present on the current machine and no `Monifactory` instance was found under `D:\`, so the pack location needs to be confirmed before the next manual test.
- Its confirmed runtime is Minecraft `1.20.1`, Forge `47.4.13`, and Java `17`.
- Forge client key registration is on the Mod bus. Input, mouse-scroll, and HUD listeners are on the Forge game bus. Do not combine the two listener sets in one `@EventBusSubscriber` class.
- `src\main\resources\pack.mcmeta` is required. It uses pack format `15` so Forge loads `zh_cn.json` and `en_us.json` as a valid Mod resource pack.
- Version `1.1.2-forge` failed to load because client input listeners were wrongly scanned on the Mod bus. This was fixed in `1.1.3-forge`.
- Version `1.1.3-forge` lacked `pack.mcmeta`, causing missing translations and an invalid `ResourcePackInfo` warning. This was fixed in `1.1.4-forge`.
- `1.1.5-forge` adds empty-hand chaining and removes the axe-only restriction for blast logs.
- FTB Ultimine is installed in Monifactory. The user will disable it or change its keybinding; do not add compatibility handling unless requested.

## Build Commands

All three builds use their own Gradle wrapper and must be run from the target's project root. The commands below were verified from Git Bash on Windows using the cached toolchain described in Read First.

### NeoForge 1.21.1

Run from `D:\VeinMinerPLUS`:

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"
export PATH="$JAVA_HOME/bin:$PATH"
bash gradlew --no-daemon --console=plain build
```

Output: `build\libs\veinminerplusG-1.21.1-neoforge-<version>.jar`.

### Forge 1.20.1

Run from `D:\VeinMinerPLUS\forge-1.20.1`:

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
bash gradlew --no-daemon --console=plain build
```

Output: `build\libs\veinminerplusG-1.20.1-forge-<version>.jar`.

The build must complete `compileJava`, `processResources`, `jar`, and `reobfJar`. `reobfJar` is what produces a loadable Forge artifact; a run that stops earlier leaves an unusable JAR.

### Forge 1.12.2

Run from `D:\VeinMinerPLUS\forge-1.12.2`:

```bash
export JAVA_HOME='C:\Program Files\Java\jdk1.8.0_271'
D='C:\Users\<user>\.gradle\wrapper\dists\gradle-4.9-bin\<hash>\gradle-4.9'
java -classpath "$D\lib\gradle-launcher-4.9.jar" org.gradle.launcher.GradleMain --no-daemon --console=plain build
```

Output: `build\libs\veinminerplusG-1.12.2-forge-<version>.jar`.

This build cannot use its bundled `gradlew` from Git Bash. The wrapper's sh launcher assembles a POSIX classpath, and the Windows JVM then fails with `Could not find or load main class org.gradle.wrapper.GradleWrapperMain` — or `...org.gradle.launcher.GradleMain` if the distribution's own `gradle` script is invoked the same way. Passing a Windows-style `-classpath` to the launcher JAR, as shown, works.

The first Forge 1.12.2 build in a session runs the ForgeGradle deobfuscation setup and can run for ten minutes or more with no console output. Later builds reuse that cache and finish in under a minute.

After any build, compare the new JAR's SHA-256 against the source artifact before publishing. Do not copy artifacts into a test instance or modpack automatically.

## Validation Status

- All three builds compile and produce JARs from the current source: `1.2.8` (NeoForge 1.21.1), `1.2.10` (Forge 1.20.1), and `1.2.8-forge1122` (Forge 1.12.2).
- Artifact contents were checked by unpacking each JAR. `mods.toml` / `mcmod.info` report the expected version, and the language files carry the updated range text where that build uses it.
- Bytecode was checked with `javap -c` on each `Config.class`. All five upper bounds are the constant `2147483647`, and the defaults remain `1024` / `32` / `32767` / `64` / `48`.
- A successful build is not evidence of in-game correctness. None of these versions has been launched or tested in an instance.

The next manual test should use only the latest JAR for the target and verify:

1. Mod loads without a client event-bus or resource-pack error.
2. Chinese mode HUD and progress text render as translations.
3. Normal, area, blast same, blast ores, blast any, and blast logs work with an empty hand where the block is normally breakable.
4. Blast logs works with non-axe tools and with an empty hand.
5. Blast ores detects Monifactory ores tagged `forge:ores`.
6. Releasing the key, reaching a limit, and natural search completion settle merged drops and experience at the player without source-location item markers.
7. FTB Ultimine is disabled or rebound before key-conflict conclusions are drawn.
8. The config screen accepts `2147483647` in all five numeric fields, and lower input still clamps up to each field's minimum.
9. Area modes break at the configured `maxNormalBlocksPerTick` rate instead of a fixed 8 blocks per tick.
10. The values persisted to `config/veinminerplus-common.toml` match what the GUI and the commands reported.

## Development Constraints

- State assumptions and ask when a requirement is ambiguous.
- Modify only files required by the request; do not refactor unrelated code.
- Prefer public Minecraft, Forge, and NeoForge APIs. Do not introduce Mixins, reflection, or direct third-party Mod internals without first explaining why they are necessary.
- Keep the three loader implementations separate and preserve their Mod ID, package, versions, and historical JARs.
- Keep config limits and their range text in sync across all three builds. A limit that is raised in one build but not another is a defect, not a platform difference.
- Build and artifact-hash verification are separate from gameplay validation. Report both honestly.

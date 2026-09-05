# BubblesOnChunkGen — Bedrock Edition add-on

A native Bedrock behavior pack (plus a tiny resource pack) that does the water-freeze job of the Java plugin/Terra addon for a **Java-built CHIMERA map that has been converted to Bedrock**. It places no loot, no chests and no books — only the river-lift protection.

## What it does

Same contract as [`common/BubblesLogic.java`](../common/src/main/java/com/bubbleschunkgen/common/BubblesLogic.java), same Y range (45–165):

1. For every loaded chunk near a player, find the **lowest soul sand** in each x/z column of the Y range.
2. **Protect that soul sand** — non-operator players cannot break it (`beforeEvents.playerBreakBlock` is cancelled).
3. Walk upward through water / bubble column / waterlogged blocks until the first non-water block.
4. Every **level-1 water** block on the way (Java `water[level=1]`, which converts to Bedrock `liquid_depth: 1`) is **frozen**.

### How "frozen" works on Bedrock

Bedrock's Script API has no cancellable liquid-flow or source-formation event, so the Java trick (cancel `BlockFromToEvent` / `BlockFormEvent`) is impossible. Instead the pack ships an invisible custom block, `bubbles:flow_wall`:

- empty geometry, no collision, no selection box → players, boats, mobs and items pass straight through; it cannot be targeted, mined or blown up
- `minecraft:liquid_detection` → `on_liquid_touches: "blocking"` so water can never enter it
- light passes through it

For each frozen water block the pack fills every **air** neighbour it could spread into (4 sides + below, never up) with a flow wall. The water is left as vanilla `flowing_water[liquid_depth=1]`, fed by its neighbouring source, and simply has nowhere to go. Optionally (`healLeaks`, on by default) a neighbour that is already flowing water *lower* than level 1 — i.e. a leak that began before the pack was installed — is walled too, which cuts the feed so the leak downstream dries up on its own.

Discovery does not depend on a chunk-load event (it is not reliably available across Script API versions). Every 10 ticks the pack looks at chunks within 6 of each player and queues any it has not processed this session; a background job drains the queue two chunks per tick using the native `Dimension.getBlocks` filter, so an all-water chunk costs one native query. Because view distance is larger than simulation distance, chunks are normally processed before their liquids start ticking. A slow maintenance pass (every 10 s) re-walks known frozen blocks, and `playerBreakBlock` / `explosion` re-wall immediately when a neighbour is opened.

## Differences from the Java plugin — read these

| Java plugin | Bedrock pack |
|---|---|
| Chunk is held frozen (no ticks) until the scan finishes | Not possible. There is a short window between a chunk becoming *ticking* and it being processed. In practice liquids in a freshly imported chunk are inert until something updates them, and the scan radius exceeds simulation distance, so this has not been an issue — but it is not a hard guarantee. |
| Flow cancelled by coordinate; the world is untouched | **Physical invisible blocks are placed** in the air around frozen water. Players cannot build in those exact positions. Removing the pack leaves them behind as "unknown" blocks — run `/scriptevent bubbles:clear` first (see Uninstall). |
| `BlockFormEvent` stops frozen water becoming a source | **Cannot be prevented.** If a frozen block has two horizontally adjacent sources and water or a solid block beneath, vanilla Bedrock will convert it to a source. The walls still contain it — the river cannot grow past the wall ring — but that edge block renders as a full block instead of a 7/8 step. Straight bank-to-bank steps (one upstream source per edge block) never trigger this; it needs a convex corner. |
| Water can still flow into short grass, snow layers, etc. and is cancelled | Only **air** neighbours are walled. A replaceable plant next to a frozen block would be washed out once, then the position is air and gets walled on the next maintenance pass. |
| Works only in Terra worlds | Works only in `minecraft:overworld` (`CONFIG.dimensions` in `scripts/main.js`). |
| Dedication chest loot / written book | Not included. |

## Commands

All via `/scriptevent` (requires operator / cheats, same as `/bubblesdebug` on Java):

| Command | Effect |
|---|---|
| `/scriptevent bubbles:debug` | Toggle per-chunk debug messages (content log + chat) |
| `/scriptevent bubbles:status` | Counts: chunks processed, queue, protected soul sand, frozen water, walls placed |
| `/scriptevent bubbles:rescan` | Forget which chunks were processed this session and redo loaded ones |
| `/scriptevent bubbles:pause` / `bubbles:resume` | Stop / restart scanning, walling and soul sand protection |
| `/scriptevent bubbles:clear` | **Uninstall helper.** Removes every flow wall in loaded chunks near you and pauses. Water will flow freely there afterwards. |

## Install

1. Build (below) or download `BubblesOnChunkGen-Bedrock-<version>.mcaddon` from the GitHub Actions artifacts.
2. Open the `.mcaddon` (or import it in the game). Both packs are registered; the behavior pack depends on the resource pack so adding the BP to a world pulls the RP in.
3. In the world settings, add **BubblesOnChunkGen** under Behavior Packs. On a Bedrock Dedicated Server add both to `world_behavior_packs.json` / `world_resource_packs.json` and set `texturepack-required=true` so clients receive the empty geometry (otherwise the wall renders as a missing-texture cube for them).
4. Requires Minecraft 1.21.90+ (`@minecraft/server` 2.0.0). No experimental toggles are needed.

Convert the Java map first (e.g. with Chunker), then add the pack; the pack assumes soul sand and `level=1` water are already in the world exactly as CHIMERA generated them.

## Uninstall

1. Walk/fly the affected rivers and run `/scriptevent bubbles:clear` so the invisible walls are removed while the pack is still present (unloaded chunks keep their walls — repeat as needed).
2. Remove the behavior pack from the world, then the resource pack.

## Build

```sh
bedrock/build.sh          # -> bedrock/build/BubblesOnChunkGen-Bedrock-<version>.mcaddon
node --test bedrock/test/core.test.mjs
```

`build.sh` needs `jq`, `zip` and `node`. It syntax-checks the scripts, validates the JSON and stamps the pack version from `gradle.properties`. For development, copy `BubblesOnChunkGen_BP` and `BubblesOnChunkGen_RP` straight into `development_behavior_packs` / `development_resource_packs`.

## Layout

```
bedrock/
├── BubblesOnChunkGen_BP/
│   ├── manifest.json
│   ├── blocks/flow_wall.json          # bubbles:flow_wall definition
│   └── scripts/
│       ├── core.js                    # pure column/wall logic (unit-tested)
│       └── main.js                    # Script API glue: scanning, events, commands
├── BubblesOnChunkGen_RP/
│   ├── manifest.json
│   └── models/blocks/bubbles_flow_wall.geo.json   # empty geometry = invisible
├── test/core.test.mjs
└── build.sh
```

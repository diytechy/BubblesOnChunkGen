// BubblesOnChunkGen - Bedrock Edition behavior pack entry point.
//
// The Java plugin cancels BlockFromToEvent / BlockFormEvent for frozen water.
// Bedrock's Script API has no cancellable liquid event, so this port instead:
//   1. discovers loaded chunks around players (no chunkLoad event dependency),
//   2. finds the lowest soul sand per x/z column in the Java Y range,
//   3. protects that soul sand from non-operator breaks, and
//   4. walls off every air neighbour of level-1 water above it with the
//      invisible bubbles:flow_wall block, so the water has nowhere to flow.
//
// All world API access goes through the namespace import so a missing export
// on an older/newer @minecraft/server never fails module linking.
import * as mc from "@minecraft/server";
import {
    MIN_Y, MAX_Y, WALL_ID, AIR_ID, SOUL_SAND_ID,
    processColumn, wallNeighbors, lowestSoulSand, chunksAround,
    chunkKey, blockKey, toChunk,
} from "./core.js";

// ---------------------------------------------------------------- config ----
const CONFIG = {
    /** Only these dimensions are scanned (Java: Terra worlds only). */
    dimensions: ["minecraft:overworld"],
    /** Chunk radius around each player that is kept processed. */
    scanRadiusChunks: 6,
    /** Ticks between player-proximity scans. */
    scanIntervalTicks: 10,
    /** Ticks between re-walking known frozen blocks to re-wall newly opened neighbours. */
    maintainIntervalTicks: 200,
    /** Chunks processed per tick by the background job. */
    chunksPerTick: 2,
    /** Also wall off flowing water that already leaked out of a frozen block. */
    healLeaks: true,
};

// ----------------------------------------------------------------- state ----
const world = mc.world;
const system = mc.system;

let ready = false;
let paused = false;
let debug = false;

/** chunkKey -> true once processed this session (walls are physical, so this is only a memo). */
const scanned = new Set();
/** chunkKey queue awaiting processing. */
const queue = [];
const queued = new Set();
/** blockKey -> {dimId,x,y,z} of protected soul sand. */
const protectedSoulSand = new Map();
/** blockKey -> {dimId,x,y,z} of frozen level-1 water. */
const frozen = new Map();

const stats = { chunks: 0, wallsPlaced: 0 };

// --------------------------------------------------------------- helpers ----
function log(msg) {
    console.warn(`[Bubbles] ${msg}`);
    if (debug) {
        try { world.sendMessage(`§7[Bubbles] ${msg}`); } catch { /* not in a tick */ }
    }
}

function isOperator(player) {
    try {
        if (typeof player.commandPermissionLevel === "number") {
            const threshold = mc.CommandPermissionLevel?.GameDirectors ?? 1;
            return player.commandPermissionLevel >= threshold;
        }
    } catch { /* fall through */ }
    try {
        if (typeof player.isOp === "function") return player.isOp();
    } catch { /* fall through */ }
    return false;
}

function reply(entity, msg) {
    if (entity && typeof entity.sendMessage === "function") {
        entity.sendMessage(`§b[Bubbles]§r ${msg}`);
    } else {
        console.warn(`[Bubbles] ${msg}`);
    }
}

/** BlockAccess (see core.js) over a Dimension using world coordinates. */
function accessorFor(dim) {
    return {
        get(x, y, z) {
            let b;
            try { b = dim.getBlock({ x, y, z }); } catch { return undefined; }
            if (!b) return undefined;
            const typeId = b.typeId;
            let depth = -1;
            if (typeId === "minecraft:water" || typeId === "minecraft:flowing_water") {
                try {
                    const d = b.permutation.getState("liquid_depth");
                    if (typeof d === "number") depth = d;
                } catch { /* leave -1 */ }
            }
            return { typeId, depth, waterlogged: b.isWaterlogged === true, air: b.isAir === true };
        },
        set(x, y, z, typeId) {
            try {
                const b = dim.getBlock({ x, y, z });
                if (b) {
                    b.setType(typeId);
                    if (typeId === WALL_ID) stats.wallsPlaced++;
                }
            } catch (e) {
                if (debug) log(`setType ${typeId} @ ${x},${y},${z} failed: ${e}`);
            }
        },
    };
}

function isChunkLoaded(dim, cx, cz) {
    try {
        return dim.getBlock({ x: cx * 16, y: MIN_Y, z: cz * 16 }) !== undefined;
    } catch {
        return false;
    }
}

// ------------------------------------------------------- chunk processing ----
/** Returns false if the chunk was not loaded (caller will retry later). */
function processChunk(dim, cx, cz) {
    if (!isChunkLoaded(dim, cx, cz)) return false;

    const volume = new mc.BlockVolume(
        { x: cx * 16, y: MIN_Y - 1, z: cz * 16 },
        { x: cx * 16 + 15, y: MAX_Y, z: cz * 16 + 15 },
    );

    let soulSand;
    try {
        const list = dim.getBlocks(volume, { includeTypes: [SOUL_SAND_ID] }, false);
        soulSand = [];
        for (const loc of list.getBlockLocationIterator()) {
            soulSand.push({ x: loc.x, y: loc.y, z: loc.z });
        }
    } catch {
        return false; // UnloadedChunksError - chunk went away between checks
    }

    const acc = accessorFor(dim);
    let protectedCount = 0;
    let frozenCount = 0;
    let wallCount = 0;

    for (const ss of lowestSoulSand(soulSand)) {
        protectedSoulSand.set(blockKey(dim.id, ss.x, ss.y, ss.z), { dimId: dim.id, ...ss });
        protectedCount++;

        const result = processColumn(acc, ss.x, ss.y, ss.z, CONFIG.healLeaks);
        for (const f of result.frozen) {
            frozen.set(blockKey(dim.id, f.x, f.y, f.z), { dimId: dim.id, ...f });
        }
        frozenCount += result.frozen.length;
        wallCount += result.walls;
    }

    stats.chunks++;
    if (debug && (protectedCount > 0 || frozenCount > 0)) {
        log(`Chunk [${cx}, ${cz}]: protected ${protectedCount} soul sand, froze ${frozenCount} water, placed ${wallCount} walls`);
    }
    return true;
}

function enqueueAroundPlayers() {
    for (const player of world.getAllPlayers()) {
        let dim;
        try { dim = player.dimension; } catch { continue; }
        if (!CONFIG.dimensions.includes(dim.id)) continue;

        const loc = player.location;
        for (const { cx, cz } of chunksAround(toChunk(loc.x), toChunk(loc.z), CONFIG.scanRadiusChunks)) {
            const key = chunkKey(dim.id, cx, cz);
            if (scanned.has(key) || queued.has(key)) continue;
            queued.add(key);
            queue.push({ dim, cx, cz, key });
        }
    }
}

/** Long-lived job: drains the chunk queue a few chunks per tick. */
function* chunkWorker() {
    while (true) {
        if (paused || queue.length === 0) {
            yield;
            continue;
        }
        for (let i = 0; i < CONFIG.chunksPerTick && queue.length > 0; i++) {
            const item = queue.shift();
            queued.delete(item.key);
            try {
                if (processChunk(item.dim, item.cx, item.cz)) scanned.add(item.key);
                // else: not loaded yet; the next proximity scan re-queues it.
            } catch (e) {
                log(`Error processing chunk ${item.key}: ${e}`);
            }
        }
        yield;
    }
}

// ------------------------------------------------------------ maintenance ----
function rewallAt(entry) {
    const dim = world.getDimension(entry.dimId);
    const acc = accessorFor(dim);
    const b = acc.get(entry.x, entry.y, entry.z);
    if (!b) return 0; // unloaded
    if (!(b.typeId === "minecraft:water" || b.typeId === "minecraft:flowing_water" || b.typeId === "minecraft:bubble_column")) return 0;
    return wallNeighbors(acc, entry.x, entry.y, entry.z, CONFIG.healLeaks);
}

/** Slow backstop: re-walk every tracked frozen block in loaded chunks. */
function* maintenanceJob() {
    let placed = 0;
    let i = 0;
    for (const entry of [...frozen.values()]) {
        try { placed += rewallAt(entry); } catch { /* ignore */ }
        if (++i % 64 === 0) yield;
    }
    if (debug && placed > 0) log(`Maintenance re-walled ${placed} neighbour(s)`);
}

/** Fast path: a block next to a frozen coordinate just changed - re-wall immediately. */
function onBlockOpened(dimId, x, y, z) {
    const around = [[0, 0, 0], [1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]];
    for (const [dx, dy, dz] of around) {
        const entry = frozen.get(blockKey(dimId, x + dx, y + dy, z + dz));
        if (entry) rewallAt(entry);
    }
}

// ---------------------------------------------------------------- events ----
world.beforeEvents.playerBreakBlock.subscribe((ev) => {
    if (!ready || paused) return;
    const b = ev.block;
    if (b.typeId !== SOUL_SAND_ID) return;
    if (!protectedSoulSand.has(blockKey(ev.dimension.id, b.location.x, b.location.y, b.location.z))) return;
    if (isOperator(ev.player)) return;
    ev.cancel = true;
    const player = ev.player;
    system.run(() => reply(player, "That soul sand holds a river lift together and cannot be broken."));
});

world.afterEvents.playerBreakBlock.subscribe((ev) => {
    if (!ready || paused) return;
    const { x, y, z } = ev.block.location;
    const dimId = ev.dimension.id;
    system.run(() => onBlockOpened(dimId, x, y, z));
});

if (world.afterEvents.explosion) {
    world.afterEvents.explosion.subscribe((ev) => {
        if (!ready || paused) return;
        const dimId = ev.dimension.id;
        let impacted = [];
        try { impacted = ev.getImpactedBlocks().map((b) => b.location); } catch { return; }
        system.run(() => {
            for (const { x, y, z } of impacted) onBlockOpened(dimId, x, y, z);
        });
    });
}

// -------------------------------------------------------------- commands ----
// /scriptevent bubbles:<command>   (requires operator / cheats, like /bubblesdebug on Java)
function clearWallsNear(entity) {
    let dim;
    try { dim = entity?.dimension ?? world.getDimension(CONFIG.dimensions[0]); } catch { return 0; }
    const center = entity?.location ?? { x: 0, z: 0 };
    let removed = 0;
    for (const { cx, cz } of chunksAround(toChunk(center.x), toChunk(center.z), CONFIG.scanRadiusChunks)) {
        if (!isChunkLoaded(dim, cx, cz)) continue;
        const volume = new mc.BlockVolume(
            { x: cx * 16, y: MIN_Y - 2, z: cz * 16 },
            { x: cx * 16 + 15, y: MAX_Y + 1, z: cz * 16 + 15 },
        );
        try {
            const list = dim.getBlocks(volume, { includeTypes: [WALL_ID] }, false);
            for (const loc of list.getBlockLocationIterator()) {
                dim.getBlock(loc)?.setType(AIR_ID);
                removed++;
            }
        } catch { /* chunk unloaded mid-way */ }
    }
    return removed;
}

system.afterEvents.scriptEventReceive.subscribe((ev) => {
    if (!ev.id.startsWith("bubbles:")) return;
    const cmd = ev.id.slice("bubbles:".length).toLowerCase();
    const src = ev.sourceEntity;

    switch (cmd) {
        case "debug":
            debug = !debug;
            reply(src, `debug ${debug ? "ENABLED" : "DISABLED"}`);
            break;
        case "status":
            reply(src, `${paused ? "PAUSED" : "running"} | chunks processed: ${stats.chunks} | queued: ${queue.length} | protected soul sand: ${protectedSoulSand.size} | frozen water: ${frozen.size} | walls placed this session: ${stats.wallsPlaced}`);
            break;
        case "rescan":
            scanned.clear();
            reply(src, "chunk memo cleared; loaded chunks near players will be re-processed");
            break;
        case "pause":
            paused = true;
            reply(src, "paused - no scanning, walling or soul sand protection until bubbles:resume");
            break;
        case "resume":
            paused = false;
            reply(src, "resumed");
            break;
        case "clear": {
            // Uninstall helper: strip every flow wall near the caller. Also pauses,
            // otherwise the next scan would put them straight back.
            paused = true;
            const removed = clearWallsNear(src);
            frozen.clear();
            protectedSoulSand.clear();
            scanned.clear();
            reply(src, `removed ${removed} flow wall(s) in loaded chunks nearby and paused. Water will now flow freely there. Run bubbles:resume to re-arm.`);
            break;
        }
        default:
            reply(src, "commands: debug | status | rescan | pause | resume | clear");
    }
}, { namespaces: ["bubbles"] });

// ----------------------------------------------------------------- start ----
function onReady() {
    if (ready) return;
    ready = true;
    system.runJob(chunkWorker());
    system.runInterval(() => {
        if (!paused) {
            try { enqueueAroundPlayers(); } catch (e) { log(`scan error: ${e}`); }
        }
    }, CONFIG.scanIntervalTicks);
    system.runInterval(() => {
        if (!paused && frozen.size > 0) system.runJob(maintenanceJob());
    }, CONFIG.maintainIntervalTicks);
    log(`enabled - Y ${MIN_Y}..${MAX_Y}, radius ${CONFIG.scanRadiusChunks} chunks, dimensions ${CONFIG.dimensions.join(", ")}`);
}

const loadEvent = world.afterEvents.worldLoad ?? world.afterEvents.worldInitialize;
if (loadEvent) {
    loadEvent.subscribe(onReady);
} else {
    system.run(onReady);
}

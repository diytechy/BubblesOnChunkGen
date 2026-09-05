// Pure column logic for the Bedrock port. No @minecraft/server imports here so
// this file can be unit-tested with plain node (see bedrock/test/).
//
// Mirrors common/BubblesLogic.java + BubblesConstants.java: scan each x/z column
// for the lowest soul sand in the Y range, then walk up through water and
// "freeze" every level-1 water block. On Bedrock there is no cancellable flow
// event, so freezing means placing an invisible, collision-less bubbles:flow_wall
// block in every air neighbour the water could otherwise spread into.

export const MIN_Y = 45;
export const MAX_Y = 165;

/** Java `water[level=1]` converts to Bedrock `liquid_depth: 1`. */
export const FROZEN_DEPTH = 1;

export const WALL_ID = "bubbles:flow_wall";
export const AIR_ID = "minecraft:air";
export const SOUL_SAND_ID = "minecraft:soul_sand";

const WATER_ID = "minecraft:water";
const FLOWING_WATER_ID = "minecraft:flowing_water";
const BUBBLE_COLUMN_ID = "minecraft:bubble_column";

/** Directions water can spread from a block: sideways and down (never up). */
export const WALL_DIRS = [
    [1, 0, 0], [-1, 0, 0],
    [0, 0, 1], [0, 0, -1],
    [0, -1, 0],
];

/**
 * Block snapshot shape returned by a BlockAccess:
 *   { typeId: string, depth: number (-1 if not water), waterlogged: boolean, air: boolean }
 * `get` returns undefined for an unloaded chunk.
 */

function isWaterBlock(b) {
    return !!b && (b.typeId === WATER_ID || b.typeId === FLOWING_WATER_ID);
}

/** Anything the upward walk should pass through (the Java walk stops at non-WATER). */
export function isWaterLike(b) {
    return isWaterBlock(b) || (!!b && (b.typeId === BUBBLE_COLUMN_ID || b.waterlogged === true));
}

export function isFrozenCandidate(b) {
    return isWaterBlock(b) && b.depth === FROZEN_DEPTH;
}

/** Flowing water strictly lower than the frozen level: something already leaked out. */
export function isLeak(b) {
    return isWaterBlock(b) && b.depth > FROZEN_DEPTH;
}

/**
 * Places flow walls in every neighbour that water at (x,y,z) could spread into.
 * Returns the number of walls placed. Skips neighbours in unloaded chunks.
 */
export function wallNeighbors(acc, x, y, z, healLeaks = true) {
    let placed = 0;
    for (const [dx, dy, dz] of WALL_DIRS) {
        const nx = x + dx, ny = y + dy, nz = z + dz;
        const n = acc.get(nx, ny, nz);
        if (!n) continue;
        if (n.air || (healLeaks && isLeak(n))) {
            acc.set(nx, ny, nz, WALL_ID);
            placed++;
        }
    }
    return placed;
}

/**
 * Walks up from the soul sand at (x, soulY, z) until the first non-water block,
 * freezing every level-1 water block on the way (BubblesLogic.freezeLevelOneWaterAbove).
 */
export function processColumn(acc, x, soulY, z, healLeaks = true) {
    const frozen = [];
    let walls = 0;
    for (let y = soulY + 1; y <= MAX_Y; y++) {
        const b = acc.get(x, y, z);
        if (!isWaterLike(b)) break;
        if (isFrozenCandidate(b)) {
            frozen.push({ x, y, z });
            walls += wallNeighbors(acc, x, y, z, healLeaks);
        }
    }
    return { frozen, walls };
}

/**
 * Given every soul sand location in a chunk, keep only the lowest one per x/z
 * column (the Java scan breaks at the first soul sand walking up from MIN_Y-1).
 */
export function lowestSoulSand(locations) {
    const byColumn = new Map();
    for (const loc of locations) {
        if (loc.y < MIN_Y - 1 || loc.y > MAX_Y) continue;
        const k = `${loc.x}|${loc.z}`;
        const cur = byColumn.get(k);
        if (!cur || loc.y < cur.y) byColumn.set(k, { x: loc.x, y: loc.y, z: loc.z });
    }
    return [...byColumn.values()];
}

/** Chunk coordinates within `radius` chunks of (cx, cz), nearest first. */
export function chunksAround(cx, cz, radius) {
    const out = [];
    for (let dx = -radius; dx <= radius; dx++) {
        for (let dz = -radius; dz <= radius; dz++) {
            out.push({ cx: cx + dx, cz: cz + dz, d: dx * dx + dz * dz });
        }
    }
    out.sort((a, b) => a.d - b.d);
    return out.map(({ cx, cz }) => ({ cx, cz }));
}

export const chunkKey = (dimId, cx, cz) => `${dimId}|${cx}|${cz}`;
export const blockKey = (dimId, x, y, z) => `${dimId}|${x}|${y}|${z}`;
export const toChunk = (v) => Math.floor(v / 16);

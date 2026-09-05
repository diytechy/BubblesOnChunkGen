// Run with: node --test bedrock/test/core.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import {
    MIN_Y, MAX_Y, WALL_ID, FROZEN_DEPTH,
    processColumn, wallNeighbors, lowestSoulSand, chunksAround, toChunk,
} from "../BubblesOnChunkGen_BP/scripts/core.js";

const AIR = { typeId: "minecraft:air", depth: -1, waterlogged: false, air: true };
const SOUL = { typeId: "minecraft:soul_sand", depth: -1, waterlogged: false, air: false };
const STONE = { typeId: "minecraft:stone", depth: -1, waterlogged: false, air: false };
const BUBBLE = { typeId: "minecraft:bubble_column", depth: -1, waterlogged: false, air: false };
const water = (depth, flowing = depth > 0) => ({
    typeId: flowing ? "minecraft:flowing_water" : "minecraft:water", depth, waterlogged: false, air: false,
});

/** In-memory world: unknown positions are air; positions set to undefined are "unloaded". */
function fakeWorld(blocks = {}) {
    const map = new Map(Object.entries(blocks));
    const k = (x, y, z) => `${x},${y},${z}`;
    const sets = [];
    return {
        get(x, y, z) {
            const key = k(x, y, z);
            return map.has(key) ? map.get(key) : AIR;
        },
        set(x, y, z, typeId) {
            sets.push({ x, y, z, typeId });
            map.set(k(x, y, z), { typeId, depth: -1, waterlogged: false, air: false });
        },
        sets,
        map,
    };
}

test("column: soul sand, sources, level-1 top gets walled on 4 sides only", () => {
    const soulY = 60;
    const w = fakeWorld({
        [`0,${soulY},0`]: SOUL,
        [`0,${soulY + 1},0`]: water(0),
        [`0,${soulY + 2},0`]: BUBBLE,
        [`0,${soulY + 3},0`]: water(FROZEN_DEPTH),
        // one side of the top block is a neighbouring source (upstream pool)
        [`1,${soulY + 3},0`]: water(0),
    });
    const { frozen, walls } = processColumn(w, 0, soulY, 0);
    assert.deepEqual(frozen, [{ x: 0, y: soulY + 3, z: 0 }]);
    assert.equal(walls, 3, "three air sides walled; source side and water-below untouched");
    const placed = w.sets.map(({ x, y, z }) => `${x},${y},${z}`).sort();
    assert.deepEqual(placed, [`-1,${soulY + 3},0`, `0,${soulY + 3},-1`, `0,${soulY + 3},1`].sort());
    assert.ok(w.sets.every((s) => s.typeId === WALL_ID));
});

test("column: walk stops at the first non-water block", () => {
    const w = fakeWorld({
        "0,50,0": SOUL,
        "0,51,0": water(FROZEN_DEPTH),
        "0,52,0": STONE,
        "0,53,0": water(FROZEN_DEPTH), // above the stone - must not be reached
    });
    const { frozen } = processColumn(w, 0, 50, 0);
    assert.deepEqual(frozen, [{ x: 0, y: 51, z: 0 }]);
});

test("column: waterlogged blocks are walked through", () => {
    const w = fakeWorld({
        "0,50,0": SOUL,
        "0,51,0": { typeId: "minecraft:seagrass", depth: -1, waterlogged: true, air: false },
        "0,52,0": water(FROZEN_DEPTH),
    });
    const { frozen } = processColumn(w, 0, 50, 0);
    assert.deepEqual(frozen, [{ x: 0, y: 52, z: 0 }]);
});

test("column: walk never exceeds MAX_Y", () => {
    const blocks = { [`0,${MAX_Y - 1},0`]: SOUL };
    for (let y = MAX_Y; y <= MAX_Y + 5; y++) blocks[`0,${y},0`] = water(FROZEN_DEPTH);
    const w = fakeWorld(blocks);
    const { frozen } = processColumn(w, 0, MAX_Y - 1, 0);
    assert.deepEqual(frozen, [{ x: 0, y: MAX_Y, z: 0 }]);
});

test("wallNeighbors: air below is walled, source below is not", () => {
    const overAir = fakeWorld({ "0,70,0": water(1) });
    assert.equal(wallNeighbors(overAir, 0, 70, 0), 5);

    const overSource = fakeWorld({ "0,70,0": water(1), "0,69,0": water(0) });
    assert.equal(wallNeighbors(overSource, 0, 70, 0), 4);
});

test("wallNeighbors: existing leaks are cut when healLeaks is on, kept when off", () => {
    const mk = () => fakeWorld({ "0,70,0": water(1), "1,70,0": water(2), "0,69,0": water(8) });
    const heal = mk();
    assert.equal(wallNeighbors(heal, 0, 70, 0, true), 5);
    const keep = mk();
    assert.equal(wallNeighbors(keep, 0, 70, 0, false), 3);
});

test("wallNeighbors: neighbouring frozen (depth 1) water and bubble columns are left alone", () => {
    const w = fakeWorld({
        "0,70,0": water(1), "1,70,0": water(1), "-1,70,0": { ...BUBBLE },
    });
    assert.equal(wallNeighbors(w, 0, 70, 0), 3);
});

test("wallNeighbors: unloaded neighbours are skipped", () => {
    const w = fakeWorld({ "0,70,0": water(1) });
    w.map.set("1,70,0", undefined);
    assert.equal(wallNeighbors(w, 0, 70, 0), 4);
});

test("lowestSoulSand keeps the lowest per x/z within the Java scan range", () => {
    const picked = lowestSoulSand([
        { x: 1, y: 80, z: 1 }, { x: 1, y: 60, z: 1 }, { x: 1, y: 90, z: 1 },
        { x: 2, y: MIN_Y - 2, z: 2 }, // below range, ignored
        { x: 2, y: MIN_Y - 1, z: 2 }, // Java starts at MIN_Y - 1
        { x: 3, y: MAX_Y + 1, z: 3 }, // above range, ignored
    ]);
    picked.sort((a, b) => a.x - b.x);
    assert.deepEqual(picked, [{ x: 1, y: 60, z: 1 }, { x: 2, y: MIN_Y - 1, z: 2 }]);
});

test("chunksAround is nearest-first and complete", () => {
    const list = chunksAround(10, -3, 2);
    assert.equal(list.length, 25);
    assert.deepEqual(list[0], { cx: 10, cz: -3 });
    const last = list[list.length - 1];
    assert.equal(Math.max(Math.abs(last.cx - 10), Math.abs(last.cz + 3)), 2);
});

test("toChunk floors negatives", () => {
    assert.equal(toChunk(-1), -1);
    assert.equal(toChunk(-16), -1);
    assert.equal(toChunk(-17), -2);
    assert.equal(toChunk(15.9), 0);
});

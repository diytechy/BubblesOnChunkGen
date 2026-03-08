Key fixes:

Switched from ChunkPopulateEvent to ChunkLoadEvent with isNewChunk() — ChunkPopulateEvent likely wasn't firing on Paper 1.21. This matches what Terra's own post-gen listeners use.
Changed update method — Instead of BlockState.update() on the water, now re-sets the soul_sand block with setBlockData(data, true) where true = apply physics. This triggers Minecraft's neighbor update which should propagate the bubble column upward through the water.
Debug command:

/bubblesdebug — toggles debug logging on/off (op-only)
When enabled, logs to console:
Every new chunk detected (coordinates)
Per chunk: how many soul_sand blocks found, how many updates triggered
What to check with debug on:

If you see soul_sand found: 0 — the Terra feature isn't placing soul_sand at all (config issue)
If you see soul_sand found: N, updates triggered: 0 — soul_sand exists but no water above it
If you see updates triggered: N but no bubble columns — the update method needs a different approach
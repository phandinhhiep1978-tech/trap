package com.trapbuilder;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/** Non-air blocks in local coordinates, normalized so the minimum corner is (0,0,0). */
public final class Schematic {
	public final String name;
	public final Map<BlockPos, BlockState> blocks = new HashMap<>();
	public int sx, sy, sz;

	public Schematic(String name) { this.name = name; }

	/** Shift everything so min corner = 0,0,0 and compute size. */
	public void normalize() {
		if (blocks.isEmpty()) return;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : blocks.keySet()) {
			minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
			minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
		}
		Map<BlockPos, BlockState> shifted = new HashMap<>();
		for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
			shifted.put(e.getKey().add(-minX, -minY, -minZ), e.getValue());
		}
		blocks.clear();
		blocks.putAll(shifted);
		sx = maxX - minX + 1; sy = maxY - minY + 1; sz = maxZ - minZ + 1;
	}
}

package com.trapbuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;

import java.util.List;
import java.util.Set;

public final class Classifier {
	public enum Kind { DIG, DROP, TRIGGER, REDSTONE, STRUCTURE }

	private static final Set<Block> STONE = Set.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.ANDESITE,
		Blocks.DIORITE, Blocks.GRANITE, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.STONE_BRICKS,
		Blocks.SMOOTH_STONE, Blocks.TUFF);
	private static final Set<Block> DIRT = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT,
		Blocks.PODZOL, Blocks.MYCELIUM);
	private static final Set<Block> METAL = Set.of(Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK, Blocks.CUT_COPPER,
		Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER);

	private Classifier() {}

	public static Kind classify(BlockState s) {
		if (s.isIn(BlockTags.PRESSURE_PLATES) || s.isOf(Blocks.TRIPWIRE_HOOK) || s.isOf(Blocks.TRIPWIRE)
			|| s.isOf(Blocks.OBSERVER) || s.isOf(Blocks.TRAPPED_CHEST)) return Kind.TRIGGER;
		if (s.isOf(Blocks.PISTON) || s.isOf(Blocks.STICKY_PISTON) || s.isOf(Blocks.REDSTONE_WIRE)
			|| s.isOf(Blocks.REPEATER) || s.isOf(Blocks.COMPARATOR) || s.isOf(Blocks.DISPENSER)
			|| s.isOf(Blocks.DROPPER) || s.isOf(Blocks.TNT) || s.isOf(Blocks.HOPPER)
			|| s.isOf(Blocks.REDSTONE_TORCH) || s.isOf(Blocks.REDSTONE_WALL_TORCH) || s.isOf(Blocks.LEVER)
			|| s.isOf(Blocks.REDSTONE_BLOCK) || s.isOf(Blocks.SLIME_BLOCK) || s.isOf(Blocks.HONEY_BLOCK)
			|| s.isIn(BlockTags.BUTTONS)) return Kind.REDSTONE;
		return Kind.STRUCTURE;
	}

	/** Strict kinds must match the exact block (and facing). */
	public static boolean strict(Kind k) { return k == Kind.TRIGGER || k == Kind.REDSTONE; }

	public static boolean whitelisted(BlockState s) {
		Block b = s.getBlock();
		return (Config.stone && STONE.contains(b))
			|| (Config.dirt && DIRT.contains(b))
			|| (Config.metal && METAL.contains(b))
			|| (Config.stairsSlabs && (s.isIn(BlockTags.STAIRS) || s.isIn(BlockTags.SLABS)));
	}

	/** Does the block currently in the world satisfy what the schematic wants? */
	public static boolean matches(BlockState want, BlockState cur, Kind k) {
		if (cur.isOf(want.getBlock())) return !strict(k) || facingOk(want, cur);
		return !strict(k) && whitelisted(want) && whitelisted(cur);
	}

	/** Is the held block acceptable for this ghost? */
	public static boolean heldOk(Block held, BlockState want, Kind k) {
		if (held == want.getBlock()) return true;
		return !strict(k) && whitelisted(want) && whitelisted(held.getDefaultState());
	}

	private static boolean facingOk(BlockState a, BlockState b) {
		for (Property<?> p : List.<Property<?>>of(Properties.FACING, Properties.HORIZONTAL_FACING)) {
			if (a.contains(p) && b.contains(p) && !a.get(p).equals(b.get(p))) return false;
		}
		return true;
	}
}

package com.trapbuilder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** Holds the placed schematic (world space) and a cached diff against the real world. */
public final class Plan {
	public record Entry(BlockPos pos, Classifier.Kind kind, BlockState want) {}

	public static Schematic schem;
	public static BlockPos anchor = BlockPos.ORIGIN;
	public static int rot = 0; // 0..3, x90 degrees clockwise

	public static List<Entry> dig = List.of(), drop = List.of(), mech = List.of(), struct = List.of();

	private static Map<BlockPos, BlockState> placed = Map.of();
	private static Set<BlockPos> dropCells = Set.of();
	private static BlockPos wmin = BlockPos.ORIGIN, wmax = BlockPos.ORIGIN;
	private static int tick;

	private Plan() {}

	public static void place(Schematic s, BlockPos at) {
		schem = s;
		anchor = at;
		Config.layerY = at.getY();
		rebuild();
	}

	public static void clear() {
		schem = null;
		placed = Map.of();
		dig = drop = mech = struct = List.of();
	}

	private static BlockPos rotate(BlockPos p) {
		return switch (rot & 3) {
			case 1 -> new BlockPos(-p.getZ(), p.getY(), p.getX());
			case 2 -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
			case 3 -> new BlockPos(p.getZ(), p.getY(), -p.getX());
			default -> p;
		};
	}

	public static void rebuild() {
		if (schem == null) return;
		BlockRotation br = BlockRotation.values()[rot & 3];
		Map<BlockPos, BlockState> map = new HashMap<>();
		for (Map.Entry<BlockPos, BlockState> e : schem.blocks.entrySet()) {
			map.put(anchor.add(rotate(e.getKey())), e.getValue().rotate(br));
		}
		placed = map;

		BlockPos a = anchor.add(rotate(BlockPos.ORIGIN));
		BlockPos b = anchor.add(rotate(new BlockPos(schem.sx - 1, schem.sy - 1, schem.sz - 1)));
		wmin = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		wmax = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));

		// Drop / pitfall cells: empty cells straight below a trigger block until something solid
		Set<BlockPos> cells = new HashSet<>();
		for (Map.Entry<BlockPos, BlockState> e : map.entrySet()) {
			if (Classifier.classify(e.getValue()) != Classifier.Kind.TRIGGER) continue;
			BlockPos p = e.getKey().down();
			while (p.getY() >= wmin.getY() && !map.containsKey(p)) { cells.add(p); p = p.down(); }
		}
		dropCells = cells;
		tick = 100; // force recompute next tick
	}

	public static void tick(MinecraftClient mc) {
		if (schem == null || mc.world == null || mc.player == null) return;
		if (++tick < 10) return;
		tick = 0;
		ClientWorld world = mc.world;
		BlockPos pp = mc.player.getBlockPos();
		int R = 48;
		int x0 = Math.max(wmin.getX(), pp.getX() - R), x1 = Math.min(wmax.getX(), pp.getX() + R);
		int y0 = Math.max(wmin.getY(), pp.getY() - R), y1 = Math.min(wmax.getY(), pp.getY() + R);
		int z0 = Math.max(wmin.getZ(), pp.getZ() - R), z1 = Math.min(wmax.getZ(), pp.getZ() + R);

		List<Entry> d = new ArrayList<>(), dr = new ArrayList<>(), m = new ArrayList<>(), s = new ArrayList<>();
		BlockPos.Mutable mp = new BlockPos.Mutable();
		for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
			mp.set(x, y, z);
			BlockState cur = world.getBlockState(mp);
			BlockState want = placed.get(mp);
			boolean curEmpty = cur.isAir() || cur.isReplaceable();
			if (want == null) {
				BlockPos ip = mp.toImmutable();
				if (!curEmpty) d.add(new Entry(ip, Classifier.Kind.DIG, cur));
				else if (dropCells.contains(ip)) dr.add(new Entry(ip, Classifier.Kind.DROP, cur));
				continue;
			}
			Classifier.Kind k = Classifier.classify(want);
			if (Classifier.matches(want, cur, k)) continue;
			BlockPos ip = mp.toImmutable();
			if (!curEmpty) { d.add(new Entry(ip, Classifier.Kind.DIG, cur)); continue; } // wrong block in the way
			Entry e = new Entry(ip, k, want);
			if (k == Classifier.Kind.STRUCTURE) s.add(e); else m.add(e);
		}
		dig = d; drop = dr; mech = m; struct = s;
	}
}

package com.trapbuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class SchematicLoader {
	private SchematicLoader() {}

	public static Schematic load(Path file) throws IOException {
		NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
		Schematic s = new Schematic(file.getFileName().toString());
		String n = file.getFileName().toString().toLowerCase();
		if (n.endsWith(".litematic")) readLitematic(root, s);
		else readSponge(root, s);
		s.normalize();
		return s;
	}

	// ---------------------------------------------------------------- .litematic
	private static void readLitematic(NbtCompound root, Schematic out) {
		NbtCompound regions = root.getCompoundOrEmpty("Regions");
		for (String key : regions.getKeys()) {
			NbtCompound r = regions.getCompoundOrEmpty(key);
			NbtCompound pos = r.getCompoundOrEmpty("Position");
			NbtCompound size = r.getCompoundOrEmpty("Size");
			int sx = size.getInt("x", 0), sy = size.getInt("y", 0), sz = size.getInt("z", 0);
			int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
			// negative size => region extends backwards from Position
			int ox = pos.getInt("x", 0) + (sx < 0 ? sx + 1 : 0);
			int oy = pos.getInt("y", 0) + (sy < 0 ? sy + 1 : 0);
			int oz = pos.getInt("z", 0) + (sz < 0 ? sz + 1 : 0);

			NbtList pal = r.getListOrEmpty("BlockStatePalette");
			if (pal.isEmpty()) continue;
			BlockState[] states = new BlockState[pal.size()];
			for (int i = 0; i < states.length; i++) {
				NbtCompound c = pal.getCompoundOrEmpty(i);
				Map<String, String> props = new HashMap<>();
				NbtCompound pc = c.getCompoundOrEmpty("Properties");
				for (String k : pc.getKeys()) props.put(k, pc.getString(k, ""));
				states[i] = state(c.getString("Name", "minecraft:air"), props);
			}

			long[] data = r.getLongArray("BlockStates").orElse(new long[0]);
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(pal.size() - 1));
			long mask = (1L << bits) - 1;

			for (int y = 0; y < ay; y++) for (int z = 0; z < az; z++) for (int x = 0; x < ax; x++) {
				long index = ((long) y * az + z) * ax + x;
				long startBit = index * bits;
				int startArr = (int) (startBit >>> 6);
				int endArr = (int) (((index + 1) * bits - 1) >>> 6);
				int startOff = (int) (startBit & 63);
				if (endArr >= data.length) continue;
				long v;
				if (startArr == endArr) v = (data[startArr] >>> startOff) & mask;
				else v = ((data[startArr] >>> startOff) | (data[endArr] << (64 - startOff))) & mask;
				if (v >= states.length) continue;
				BlockState st = states[(int) v];
				if (st.isAir() || st.getBlock() == net.minecraft.block.Blocks.STRUCTURE_VOID) continue;
				out.blocks.put(new BlockPos(ox + x, oy + y, oz + z), st);
			}
		}
	}

	// ---------------------------------------------------------------- Sponge .schem (v2 / v3)
	private static void readSponge(NbtCompound root, Schematic out) {
		NbtCompound base = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
		int w = base.getShort("Width", (short) 0) & 0xFFFF;
		int h = base.getShort("Height", (short) 0) & 0xFFFF;
		int l = base.getShort("Length", (short) 0) & 0xFFFF;

		NbtCompound palTag;
		byte[] data;
		if (base.contains("Blocks")) { // v3
			NbtCompound b = base.getCompoundOrEmpty("Blocks");
			palTag = b.getCompoundOrEmpty("Palette");
			data = b.getByteArray("Data").orElse(new byte[0]);
		} else { // v2
			palTag = base.getCompoundOrEmpty("Palette");
			data = base.getByteArray("BlockData").orElse(new byte[0]);
		}

		Map<Integer, BlockState> palette = new HashMap<>();
		for (String key : palTag.getKeys()) palette.put(palTag.getInt(key, 0), parseStateString(key));

		int i = 0, idx = 0;
		while (i < data.length && idx < w * h * l) {
			int value = 0, shift = 0;
			byte b;
			do {
				b = data[i++];
				value |= (b & 0x7F) << shift;
				shift += 7;
			} while ((b & 0x80) != 0 && i < data.length);
			int x = idx % w, z = (idx / w) % l, y = idx / (w * l);
			BlockState st = palette.get(value);
			if (st != null && !st.isAir()) out.blocks.put(new BlockPos(x, y, z), st);
			idx++;
		}
	}

	// ---------------------------------------------------------------- state helpers
	private static BlockState parseStateString(String s) {
		int br = s.indexOf('[');
		String name = br < 0 ? s : s.substring(0, br);
		Map<String, String> props = new HashMap<>();
		if (br >= 0) {
			String inner = s.substring(br + 1, s.length() - 1);
			for (String pair : inner.split(",")) {
				int eq = pair.indexOf('=');
				if (eq > 0) props.put(pair.substring(0, eq), pair.substring(eq + 1));
			}
		}
		return state(name, props);
	}

	private static BlockState state(String name, Map<String, String> props) {
		Block block = Registries.BLOCK.get(Identifier.of(name));
		BlockState st = block.getDefaultState();
		for (Map.Entry<String, String> e : props.entrySet()) {
			Property<?> p = block.getStateManager().getProperty(e.getKey());
			if (p != null) st = apply(st, p, e.getValue());
		}
		return st;
	}

	private static <T extends Comparable<T>> BlockState apply(BlockState s, Property<T> p, String v) {
		return p.parse(v).map(val -> s.with(p, val)).orElse(s);
	}
}

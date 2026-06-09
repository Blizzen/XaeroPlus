package xaeroplus.module.impl;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.lenni0451.lambdaevents.EventHandler;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import xaeroplus.Globals;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.feature.highlights.SavableHighlightCacheInstance;
import xaeroplus.feature.render.DrawFeatureFactory;
import xaeroplus.module.Module;
import xaeroplus.module.ModuleManager;
import xaeroplus.settings.Settings;
import xaeroplus.util.ChunkScanner;
import xaeroplus.util.ColorHelper;

import static xaeroplus.util.ChunkUtils.getActualDimension;

public class SettledLava extends Module {
    public final SavableHighlightCacheInstance settledLavaCache = new SavableHighlightCacheInstance("XaeroPlusSettledLava");
    private int color = ColorHelper.getColor(255, 100, 0, 200);

    // v1 heuristic tuning constants
    private static final int MIN_SOURCES_PER_LEVEL = 6;
    private static final int MIN_BOUNDING_BOX_DIM_SUM = 4;
    // nether's natural lava sea sits below ~Y 32; reject candidates at/under it
    private static final int NETHER_NATURAL_LAVA_Y_CUTOFF = 32;

    private static final ReferenceSet<Block> lavaFilter = ReferenceSet.of(Blocks.LAVA);

    public void setDiskCache(boolean disk) {
        settledLavaCache.setDiskCache(disk, isEnabled());
    }

    @EventHandler
    public void onChunkData(final ChunkDataEvent event) {
        if (event.seenChunk()) return;
        var level = mc.level;
        if (level == null || mc.levelRenderer.viewArea == null) return;
        var chunk = event.chunk();
        var chunkPos = chunk.getPos();
        ResourceKey<Level> dim = getActualDimension();
        if (settledLavaCache.get().isHighlighted(chunkPos.x, chunkPos.z, dim)) return;
        // reject chunks already flagged as natural lava-lake by LavaColumns
        var lavaColumns = ModuleManager.getModule(LavaColumns.class);
        if (lavaColumns != null && lavaColumns.lavaColumnsCache.get().isHighlighted(chunkPos.x, chunkPos.z, dim)) return;

        boolean isNether = dim == Level.NETHER;

        // y -> packed (relX, relZ) source positions, packed as ((relX & 0xF) << 4) | (relZ & 0xF)
        Int2ObjectMap<int[]> sourcesByY = new Int2ObjectOpenHashMap<>();
        // we accumulate counts in [0] of each array; array grows lazily
        ChunkScanner.chunkScanBlockstatePredicate(chunk, lavaFilter, (c, state, relX, y, relZ) -> {
            var fluid = state.getFluidState();
            if (fluid.isEmpty() || !fluid.isSource()) return false;
            if (isNether && y < NETHER_NATURAL_LAVA_Y_CUTOFF) return false;
            int[] arr = sourcesByY.get(y);
            if (arr == null) {
                arr = new int[9]; // [0] = count, [1..] = positions
                sourcesByY.put(y, arr);
            }
            int count = arr[0];
            if (count + 1 >= arr.length) {
                int[] grown = new int[arr.length * 2];
                System.arraycopy(arr, 0, grown, 0, arr.length);
                arr = grown;
                sourcesByY.put(y, arr);
            }
            int packed = ((relX & 0xF) << 4) | (relZ & 0xF);
            arr[count + 1] = packed;
            arr[0] = count + 1;
            return false;
        }, 0);

        for (var entry : sourcesByY.int2ObjectEntrySet()) {
            int[] arr = entry.getValue();
            int count = arr[0];
            if (count < MIN_SOURCES_PER_LEVEL) continue;
            int minX = 16, maxX = -1, minZ = 16, maxZ = -1;
            for (int i = 1; i <= count; i++) {
                int p = arr[i];
                int rx = (p >> 4) & 0xF;
                int rz = p & 0xF;
                if (rx < minX) minX = rx;
                if (rx > maxX) maxX = rx;
                if (rz < minZ) minZ = rz;
                if (rz > maxZ) maxZ = rz;
            }
            if ((maxX - minX) + (maxZ - minZ) >= MIN_BOUNDING_BOX_DIM_SUM) {
                settledLavaCache.get().addHighlight(chunkPos.x, chunkPos.z);
                return;
            }
        }
    }

    @Override
    public void onEnable() {
        Globals.drawManager.registry().register(
            DrawFeatureFactory.chunkHighlights(
                "SettledLava",
                this::getHighlightsState,
                this::getSettledLavaColor,
                250
            )
        );
        settledLavaCache.onEnable();
    }

    @Override
    public void onDisable() {
        settledLavaCache.onDisable();
        Globals.drawManager.registry().unregister("SettledLava");
    }

    public Long2LongMap getHighlightsState(final ResourceKey<Level> dimension) {
        return settledLavaCache.get().getCacheMap(dimension);
    }

    public int getSettledLavaColor() {
        return color;
    }

    public void setRgbColor(final int color) {
        this.color = ColorHelper.getColorWithAlpha(color, Settings.REGISTRY.settledLavaAlphaSetting.getAsInt());
    }

    public void setAlpha(final double a) {
        this.color = ColorHelper.getColorWithAlpha(this.color, (int) a);
    }
}

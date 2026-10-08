package me.liwk.karhu.world.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import me.liwk.karhu.util.gui.Callback;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Map;

public interface IChunkManager {

    void getChunk(Location location, Callback<Chunk> chunkCallback);

    Block getChunkBlockAt(Location location);

    /**
     * @return the cached chunk if it is loaded, null otherwise
     */
    Chunk getCachedChunk(World world, int chunkX, int chunkZ);

    Block getBlockInChunk(World world, Chunk chunk, int x, int y, int z);

    void onChunkUnload(final Chunk chunk);

    void onChunkLoad(final Chunk chunk);

    void addWorld(final World world);

    void removeWorld(final World world);

    boolean isChunkLoaded(Location loc);

    void unloadAll();

    int getCacheSize(World world);

    Map<World, Long2ObjectMap<Chunk>> getLoadedChunks();

}

package me.liwk.karhu.world.chunk;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.util.Conditions;
import me.liwk.karhu.util.gui.Callback;
import me.liwk.karhu.util.player.BlockUtil;
import me.liwk.karhu.util.task.Tasker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class WorldChunkManager implements IChunkManager {

    private final ConcurrentHashMap<World, Long2ObjectMap<Chunk>> loadedChunks =
            new ConcurrentHashMap<>();
    private final Set<Long> pendingRegistrations = ConcurrentHashMap.newKeySet();

    @Override
    public void getChunk(Location location, Callback<Chunk> chunkCallback) {

        synchronized (this.loadedChunks) {

            World world = location.getWorld();

            Conditions.notNull(world, "location world cannot be null");

            final Long2ObjectMap<Chunk> chunkMap = this.loadedChunks.computeIfAbsent(world, k -> new Long2ObjectOpenHashMap<>());

            if (chunkMap.isEmpty()) {
                this.somethingTriedDoingSomethingStupidErrorMessage(world);
            } else {

                final Chunk chunk = chunkMap.get(BlockUtil.getChunkPair(location));

                //Chunk was found
                if (chunk != null) {
                    chunkCallback.call(chunk);
                }
            }
        }
    }

    public Block getChunkBlockAt(Location location) {
        synchronized (this.loadedChunks) {

            World world = location.getWorld();

            Conditions.notNull(world, "location world cannot be null");

            final Long2ObjectMap<Chunk> chunkMap = this.loadedChunks.computeIfAbsent(world, k -> new Long2ObjectOpenHashMap<>());

            if (chunkMap.isEmpty()) {
                return null;
            } else {

                final Chunk chunk = chunkMap.get(BlockUtil.getChunkPair(location));

                //Chunk was found
                if (chunk != null) {
                    return getBlockInChunk(world, chunk, location.getBlockX(), location.getBlockY(), location.getBlockZ());
                } else {
                    requestChunkRegistration(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
                    return null;
                }
            }
        }
    }

    @Override
    public Chunk getCachedChunk(World world, int chunkX, int chunkZ) {
        synchronized (this.loadedChunks) {
            final Long2ObjectMap<Chunk> chunkMap = this.loadedChunks.get(world);

            if (chunkMap == null) {
                return null;
            }

            final Chunk chunk = chunkMap.get(BlockUtil.getChunkPair(chunkX, chunkZ));
            return chunk != null && chunk.isLoaded() ? chunk : null;
        }
    }

    @Override
    public Block getBlockInChunk(World world, Chunk chunk, int x, int y, int z) {
        // 1.17+ worlds can go below y=0
        int minHeight = Karhu.SERVER_VERSION.isNewerThanOrEquals(ServerVersion.V_1_17) ? world.getMinHeight() : 0;
        boolean invalidCoord = y >= world.getMaxHeight() || y < minHeight;

        if (invalidCoord && Karhu.SERVER_VERSION.isNewerThanOrEquals(ServerVersion.V_1_13)) {
            return world.getBlockAt(x, y, z);
        }

        return chunk.getBlock(x & 0xF, y, z & 0xF);
    }

    /**
     * Registers a single chunk that is loaded on the server but missing from the cache,
     * instead of rescanning every loaded chunk of the world.
     */
    private void requestChunkRegistration(World world, int chunkX, int chunkZ) {
        final long key = BlockUtil.getChunkPair(chunkX, chunkZ);

        if (!this.pendingRegistrations.add(key)) {
            return;
        }

        Tasker.run(() -> {
            try {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    this.onChunkLoad(world.getChunkAt(chunkX, chunkZ));
                }
            } finally {
                this.pendingRegistrations.remove(key);
            }
        });
    }

    public void onChunkLoad(final Chunk chunk) {
        synchronized (this.loadedChunks) {
            this.loadedChunks.computeIfAbsent(chunk.getWorld(),
                    k -> new Long2ObjectOpenHashMap<>()).put(BlockUtil.getChunkPair(chunk), chunk);
        }
    }

    public void onChunkUnload(final Chunk chunk) {
        synchronized (this.loadedChunks) {
            final Map<Long, Chunk> chunkMap = this.loadedChunks.get(chunk.getWorld());
            if (chunkMap != null) {
                chunkMap.remove(BlockUtil.getChunkPair(chunk));
            }
        }
    }

    public boolean isChunkLoaded(Location l) {
        World world = l.getWorld();
        if (world == null) return false;

        // Ensure world's chunk map exists
        Long2ObjectMap<Chunk> chunkMap = loadedChunks.computeIfAbsent(world,
                k -> new Long2ObjectOpenHashMap<>());

        // Synchronize on the specific world's chunk map
        synchronized (chunkMap) {
            if (chunkMap.isEmpty()) {
                return world.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4);
            }

            long chunkKey = BlockUtil.getChunkPair(l);
            Chunk chunk = chunkMap.get(chunkKey);

            return chunk != null && chunk.isLoaded();
        }
    }

    public void addWorld(final World world) {
        synchronized (this.loadedChunks) {
            this.loadedChunks.computeIfAbsent(world, k -> new Long2ObjectOpenHashMap<>());
        }
    }

    public void removeWorld(final World world) {
        synchronized (this.loadedChunks) {
            this.loadedChunks.remove(world);
        }
    }

    @Override
    public void unloadAll() {
        synchronized (this.loadedChunks) {
            this.loadedChunks.clear();
        }
    }

    public int getCacheSize(final World world) {
        synchronized (this.loadedChunks) {
            return this.loadedChunks.get(world).size();
        }
    }

    public Map<World, Long2ObjectMap<Chunk>> getLoadedChunks() {
        return loadedChunks;
    }

    private void somethingTriedDoingSomethingStupidErrorMessage(World world) {
        //if this happens idk what to tell you lol
        if(world == null) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "Karhu attempted to access a chunk in a non-existent world, this should never happen null");
        } else {
            Bukkit.getLogger().log(Level.SEVERE,
                    "Karhu attempted to access a chunk in a non-existent world, this should never happen " +
                    world.getName());
        }
    }
}

package me.liwk.karhu.util.mc.boundingbox;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import lombok.Getter;
import lombok.SneakyThrows;
import me.liwk.karhu.Karhu;
import me.liwk.karhu.data.EntityData;
import me.liwk.karhu.data.KarhuPlayer;
import me.liwk.karhu.handler.collision.type.MaterialChecks;
import me.liwk.karhu.util.Draw;
import me.liwk.karhu.util.KarhuStream;
import me.liwk.karhu.util.MathUtil;
import me.liwk.karhu.util.mc.MathHelper;
import me.liwk.karhu.util.mc.axisalignedbb.AxisAlignedBB;
import me.liwk.karhu.util.player.BlockUtil;
import me.liwk.karhu.util.set.ConcurrentSet;
import me.liwk.karhu.world.CachedBlock;
import me.liwk.karhu.world.chunk.IChunkManager;
import org.apache.commons.math3.util.FastMath;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Getter
public final class BoundingBox implements Cloneable {

    private final int MAX_BLOCKS_TO_CHECK = 1024;

    public double minX, minY, minZ;
    public double maxX, maxY, maxZ;

    private final long timestamp = System.currentTimeMillis();

    private final KarhuPlayer data;

    private ConcurrentSet<Entity> nearbyEntities = new ConcurrentSet<>();

    public Chunk chunk;

    public BoundingBox(KarhuPlayer data, double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);

        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);

        this.data = data;
    }

    public double getVolume() {
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
    }

    public double getWidth() {
        return maxX - minX;
    }
    public double distance(final Location location) {
        return Math.sqrt(Math.min(FastMath.pow(location.getX() - this.minX, 2), FastMath.pow(location.getX() - this.maxX, 2)) + Math.min(FastMath.pow(location.getZ() - this.minZ, 2), FastMath.pow(location.getZ() - this.maxZ, 2)));
    }

    public double distance(final double x, final double z) {
        final double dx = Math.min(FastMath.pow(x - minX, 2), FastMath.pow(x - maxX, 2));
        final double dz = Math.min(FastMath.pow(z - minZ, 2), FastMath.pow(z - maxZ, 2));
        return Math.sqrt(dx + dz);
    }

    public double distance(final BoundingBox box) {
        final double dx = Math.min(FastMath.pow(box.minX - minX, 2), FastMath.pow(box.maxX - maxX, 2));
        final double dz = Math.min(FastMath.pow(box.minZ - minZ, 2), FastMath.pow(box.maxZ - maxZ, 2));
        return Math.sqrt(dx + dz);
    }

    public double distance(final AxisAlignedBB box) {
        final double dx = Math.min(FastMath.pow(box.minX - minX, 2), FastMath.pow(box.maxX - maxX, 2));
        final double dz = Math.min(FastMath.pow(box.minZ - minZ, 2), FastMath.pow(box.maxZ - maxZ, 2));
        return Math.sqrt(dx + dz);
    }

    public double distanceToHitbox(final AxisAlignedBB box) {
        double cornerX = MathUtil.clamp(this.getCenterX(), box.getCenterX() - 0.4, box.getCenterX() + 0.4);
        double cornerZ = MathUtil.clamp(this.getCenterZ(), box.getCenterZ() - 0.4, box.getCenterZ() + 0.4);

        double distanceX = this.getCenterX() - cornerX;
        double distanceZ = this.getCenterZ() - cornerZ;

        return Math.hypot(distanceX, distanceZ);
    }

    public double distance(Vector3d other) {
        return Math.sqrt(distanceSquared(other)) - 0.4;
    }

    public double distanceSquared(Vector3d other) {
        double distX = (this.getCenterX() - other.x) * (this.getCenterX() - other.x);
        double distZ = (this.getCenterZ() - other.z) * (this.getCenterZ() - other.z);
        return distX + distZ;
    }

    public Vector getDirection(final World world) {
        final double centerX = (minX + maxX) / 2.0;
        final double centerY = (minY + maxY) / 2.0;
        final double centerZ = (minZ + maxZ) / 2.0;
        return new Location(world, centerX, centerY, centerZ).getDirection();
    }

    public boolean hasPoint(Vector point) {
        return point.getX() >= minX && point.getX() <= maxX && point.getY() >= minY && point.getY() <= maxY && point.getZ() >= minZ && point.getZ() <= maxZ;
    }

    public BoundingBox add(final BoundingBox box) {
        this.minX += box.minX;
        this.minY += box.minY;
        this.minZ += box.minZ;
        this.maxX += box.maxX;
        this.maxY += box.maxY;
        this.maxZ += box.maxZ;
        return this;
    }

    public BoundingBox translate(final double x, final double y, final double z) {
        this.minX += x;
        this.minY += y;
        this.minZ += z;
        this.maxX += x;
        this.maxY += y;
        this.maxZ += z;
        return this;
    }

    public BoundingBox expand(final double val) {
        this.minX -= val;
        this.minY -= val;
        this.minZ -= val;
        this.maxX += val;
        this.maxY += val;
        this.maxZ += val;
        return this;
    }

    public BoundingBox expand(final double x, final double y, final double z) {
        this.minX -= x;
        this.minY -= y;
        this.minZ -= z;
        this.maxX += x;
        this.maxY += y;
        this.maxZ += z;
        return this;
    }

    public BoundingBox expand(Vector vec) {
        if (vec.getX() < 0D)
            this.minX += vec.getX();
        else
            this.maxX += vec.getX();

        if (vec.getY() < 0D)
            this.minY += vec.getY();
        else
            this.maxY += vec.getY();

        if (vec.getZ() < 0D)
            this.minZ += vec.getZ();
        else
            this.maxZ += vec.getZ();

        return this;
    }

    public BoundingBox expandMin(final double x, final double y, final double z) {
        this.minX += x;
        this.minY += y;
        this.minZ += z;
        return this;
    }

    public BoundingBox expandMax(final double x, final double y, final double z) {
        this.maxX += x;
        this.maxY += y;
        this.maxZ += z;
        return this;
    }

    public BoundingBox subtractMin(final double x, final double y, final double z) {
        this.minX -= x;
        this.minY -= y;
        this.minZ -= z;
        return this;
    }

    public BoundingBox subtractMax(final double x, final double y, final double z) {
        this.maxX -= x;
        this.maxY -= y;
        this.maxZ -= z;
        return this;
    }

    public BoundingBox initBox(BoundingBox pastBox) {
        final double minX = Math.min(this.minX, pastBox.minX);
        final double minY = Math.min(this.minY, pastBox.minY);
        final double minZ = Math.min(this.minZ, pastBox.minZ);
        final double maxX = Math.max(this.maxX, pastBox.maxX);
        final double maxY = Math.max(this.maxY, pastBox.maxY);
        final double maxZ = Math.max(this.maxZ, pastBox.maxZ);

        return new BoundingBox(pastBox.getData(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void setBounds(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public List<EntityType> getCollidingEntitiesNew() {
        final List<EntityType> list = new ArrayList<>();
        final AxisAlignedBB bbThis = this.toBB();
        final AxisAlignedBB searchArea = bbThis.expand(3, 3, 3); // Use max distance for search area

        for (EntityData edata : data.getEntityData().values()) {
            if (edata.getEid() == data.getBukkitPlayer().getEntityId()) continue;

            AxisAlignedBB bb = edata.getEntityBoundingBox();
            if (bb != null && searchArea.intersectsWith(bb)) {
                double distance = bb.distanceXYZ(bbThis);
                double allowedDistance = (edata.getType() == EntityTypes.HAPPY_GHAST) ? 3.0 : 2.0;

                if (bbThis.intersectsWith(bb) || distance <= allowedDistance) {
                    list.add(edata.getType());
                }
            }
        }
        return list;
    }
    public AxisAlignedBB toBB() {
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public List<CachedBlock> getCollidingBlocks() {
        final List<CachedBlock> blocks = new ArrayList<>();

        final int xFloor = MathHelper.floor_double(minX);
        final int xCeil = MathHelper.floor_double(maxX);
        final int yFloor = MathHelper.floor_double(minY);
        final int yCeil = MathHelper.floor_double(maxY);
        final int zFloor = MathHelper.floor_double(minZ);
        final int zCeil = MathHelper.floor_double(maxZ);

        int totalBlocks = ((xCeil - xFloor) + 1) * ((yCeil - yFloor) + 1) * ((zCeil - zFloor) + 1);
        if (totalBlocks > MAX_BLOCKS_TO_CHECK) {
            return blocks;
        }

        final World world = data.getWorld();
        final IChunkManager chunkManager = Karhu.getInstance().getChunkManager();

        if (world == null) {
            return blocks;
        }

        for (int x = xFloor; x <= xCeil; x++) {
            for (int z = zFloor; z <= zCeil; z++) {
                // One cache lookup per column instead of one per block
                final Chunk chunk = chunkManager.getCachedChunk(world, x >> 4, z >> 4);

                if (chunk != null) {
                    for (int y = yFloor - 1; y <= yCeil; y++) {
                        Block block = chunkManager.getBlockInChunk(world, chunk, x, y, z);

                        if (block != null) {

                            Material material = block.getType();

                            boolean[] water = isWater(block, material, y, yCeil);

                            blocks.add(new CachedBlock(material, block.getLocation(), water, null));

                            // Optional: Add a hard limit to prevent excessive memory usage
                            if (blocks.size() >= 1000) {
                                break;
                            }
                        }
                    }
                }
            }
        }

        return blocks;
    }

    public List<CachedBlock> getCollidingBlocksModern(KarhuPlayer karhuPlayer) {
        final List<CachedBlock> blocks = new ArrayList<>();

        final int xFloor = MathHelper.floor_double(minX);
        final int xCeil = MathHelper.floor_double(maxX);
        final int yFloor = MathHelper.floor_double(minY);
        final int yCeil = MathHelper.floor_double(maxY);
        final int zFloor = MathHelper.floor_double(minZ);
        final int zCeil = MathHelper.floor_double(maxZ);

        int totalBlocks = ((xCeil - xFloor) + 1) * ((yCeil - yFloor) + 1) * ((zCeil - zFloor) + 1);
        if (totalBlocks > MAX_BLOCKS_TO_CHECK) {
            return blocks;
        }

        final World world = data.getWorld();
        final IChunkManager chunkManager = Karhu.getInstance().getChunkManager();

        if (world == null) {
            return blocks;
        }

        for (int x = xFloor; x <= xCeil; x++) {
            for (int z = zFloor; z <= zCeil; z++) {
                // One cache lookup per column instead of one per block
                final Chunk chunk = chunkManager.getCachedChunk(world, x >> 4, z >> 4);

                if (chunk != null) {
                    for (int y = yFloor - 1; y <= yCeil; y++) {
                        Block block = chunkManager.getBlockInChunk(world, chunk, x, y, z);

                        if (block != null) {
                            boolean[] water = isWaterModern(block, y, minY, karhuPlayer);
                            boolean[] lava = isLavaModern(block, y, minY, karhuPlayer);

                            blocks.add(new CachedBlock(block.getType(), block.getLocation(), water, lava));

                            // Optional: Add a hard limit to prevent excessive memory usage

                            if (blocks.size() >= 1000) {
                                break;
                            }
                        }
                    }
                }
            }
        }

        return blocks;
    }

    private boolean[] isWater(Block block, Material material, int y, double yCeil) {
        boolean water = false;
        boolean push = false;

        boolean extraCheck = false;

        if (Karhu.SERVER_VERSION.isNewerThanOrEquals(ServerVersion.V_1_13)) {
            if (block.getBlockData() instanceof Waterlogged) {
                extraCheck = true;
            }
        }

        if (MaterialChecks.LIQUIDS.contains(material)
                || MaterialChecks.WATER.contains(material)
                || MaterialChecks.SEASHIT.contains(material)
                || extraCheck) {
            int data = block.getData();
            float height = getWaterHeight(data);
            double d0 = (float) (y + 1) - height;

            if (yCeil >= d0) {
                water = true;
            }

            if (data > 0) {
                push = true;
            }
        }
        return new boolean[]{water, push, false};
    }

    private boolean[] isWaterModern(Block block, int y, double minY, KarhuPlayer karhuPlayer) {
        boolean water = false;
        boolean push = false;

        Material material = block.getType();

        boolean waterLogged = false;

        if (Karhu.SERVER_VERSION.isNewerThanOrEquals(ServerVersion.V_1_13)) {
            BlockData blockData = block.getBlockData();
            if (blockData instanceof Waterlogged) {
                Waterlogged waterloggedData = (Waterlogged) blockData;
                waterLogged = waterloggedData.isWaterlogged();
            }
        }

        if (MaterialChecks.LIQUIDS.contains(material)
                || MaterialChecks.WATER.contains(material)
                || MaterialChecks.SEASHIT.contains(material)
                || waterLogged) {
            int data = block.getData();
            double height = getWaterHeight(data);

            if (karhuPlayer.getClientVersion().isOlderThan(ClientVersion.V_1_14))
                height = Math.min(height, 8 / 9D);

            if (height != 0 || (y + height) >= minY) {
                water = true;
            }

            if (data > 0) {
                push = true;
            }
        }
        return new boolean[]{water, push, waterLogged};
    }

    private boolean[] isLavaModern(Block block, int y, double minY, KarhuPlayer karhuPlayer) {
        boolean lava = false;
        boolean push = false;

        Material material = block.getType();

        if (MaterialChecks.LAVA.contains(material)) {
            int data = block.getData();
            double height = getWaterHeight(data);

            if (karhuPlayer.getClientVersion().isOlderThan(ClientVersion.V_1_14))
                height = Math.min(height, 8 / 9D);

            if (height != 0 || (y + height) >= minY) {
                lava = true;
            }

            if (data > 0) {
                push = true;
            }
        }
        return new boolean[]{lava, push};
    }

    public List<Block> getCollidingBlocksWithAir() {

        final List<Block> blocks = new ArrayList<>();

        final int xFloor = MathHelper.floor_double(minX);
        final int xCeil = MathHelper.floor_double(maxX);

        final int yFloor = MathHelper.floor_double(minY);
        final int yCeil = MathHelper.floor_double(maxY);

        final int zFloor = MathHelper.floor_double(minZ);
        final int zCeil = MathHelper.floor_double(maxZ);

        int totalBlocks = ((xCeil - xFloor) + 1) * ((yCeil - yFloor) + 1) * ((zCeil - zFloor) + 1);
        if (totalBlocks > MAX_BLOCKS_TO_CHECK) {
            return blocks;
        }

        for (int x = xFloor; x <= xCeil; x++) {
            for (int z = zFloor; z <= zCeil; z++) {

                final Location chunkLocation = new Location(data.getWorld(), x, 64, z);

                boolean chunkLoaded = Karhu.getInstance().getChunkManager().isChunkLoaded(chunkLocation);

                if (chunkLoaded) {
                    for (int y = yFloor - 1; y <= yCeil; y++) {

                        chunkLocation.setY(y);

                        final Block block2 = Karhu.getInstance().getChunkManager().getChunkBlockAt(chunkLocation);

                        if (block2 != null)
                            blocks.add(block2);
                    }
                }

            }
        }


        return blocks;
    }

    public List<Material> getCachedCollidingBlocks(List<CachedBlock> blocks) {

        final List<Material> blocksWanted = new ArrayList<>();

        for (CachedBlock cachedBlock : blocks) {

            Material blockMat = cachedBlock.getMaterial();

            float expandY = MaterialChecks.FENCES.contains(blockMat)
                    || MaterialChecks.MOVABLE.contains(blockMat)
                    ? 1.5F
                    : 1.0F;

            AxisAlignedBB blockAABB = new AxisAlignedBB(cachedBlock.getPosition(), 1F, expandY);

            if (intersectsWith(blockAABB)) {

                blocksWanted.add(blockMat);
            }

        }


        return blocksWanted;
    }

    public List<Material> getCachedCollidingBlocksDebug(List<CachedBlock> blocks) {

        final List<Material> blocksWanted = new ArrayList<>();

        Draw.drawBox(data.getBukkitPlayer(),
                this.getMinX(), this.getMaxX(),
                this.getMinY(), this.getMaxY(),
                this.getMinZ(), this.getMaxZ(), 0.2F);

        for (CachedBlock cachedBlock : blocks) {

            Material blockMat = cachedBlock.getMaterial();

            float expandY = MaterialChecks.FENCES.contains(blockMat)
                    || MaterialChecks.MOVABLE.contains(blockMat)
                    ? 1.5F
                    : 1.0F;

            AxisAlignedBB blockAABB = new AxisAlignedBB(cachedBlock.getPosition(), 1F, expandY);

            if (intersectsWith(blockAABB)) {

                blocksWanted.add(blockMat);
            }

        }


        return blocksWanted;
    }

    public List<CachedBlock> getCachedCollidingBlocks2(List<CachedBlock> blocks) {

        final List<CachedBlock> blocksWanted = new ArrayList<>();

        for (CachedBlock cachedBlock : blocks) {

            Material blockMat = cachedBlock.getMaterial();

            if (MaterialChecks.AIR.contains(blockMat)) continue;

            float expandY = MaterialChecks.FENCES.contains(blockMat)
                    || MaterialChecks.MOVABLE.contains(blockMat)
                    ? 1.5F
                    : 1.0F;

            AxisAlignedBB blockAABB = new AxisAlignedBB(cachedBlock.getPosition(), 1F, expandY);

            if (intersectsWithModern(blockAABB)) {
                blocksWanted.add(cachedBlock);
            }

        }


        return blocksWanted;
    }

    public List<Material> getCollidingOnLanded(List<CachedBlock> blocks, double posX, double posY, double posZ) {

        final List<Material> blocksWanted = new ArrayList<>();

        for(CachedBlock cachedBlock : blocks) {
            AxisAlignedBB blockAABB = new AxisAlignedBB(cachedBlock.getPosition(), 1F);

            if (intersectsWith(posX, posY, posZ, blockAABB)) {

                blocksWanted.add(cachedBlock.getMaterial());
            }

        }


        return blocksWanted;
    }

    public List<Material> getCollidingMaterialAccel(List<CachedBlock> blocks) {


        final List<Material> blocksWanted = new ArrayList<>();


        for (CachedBlock cachedBlock : blocks) {
            AxisAlignedBB blockAABB = new AxisAlignedBB(cachedBlock.getPosition(), 1F);

            boolean[] water = cachedBlock.getWater();

            boolean[] lava = cachedBlock.getLava();

            boolean intersects = intersectsWith(blockAABB);

            if (intersects) {
                if ((water[0])) {
                    boolean isWaterlogged = water[2];

                    if (!isWaterlogged) {
                        blocksWanted.add(cachedBlock.getMaterial());
                    } else {
                        blocksWanted.add(Material.WATER);
                    }

                    if (water[1]) {
                        blocksWanted.add(Material.AIR);
                    }
                } else if (lava != null && lava[0]) {
                    blocksWanted.add(cachedBlock.getMaterial());
                }
            }
        }
        return blocksWanted;
    }

    public float getWaterHeight(int i) {
        if (i >= 8) {
            i = 0;
        }
        return (i + 1) / 9.0F;
    }

    public boolean getAnyLiquid(List<CachedBlock> blocks) {

        int i = MathHelper.floor_double(minX);
        int j = MathHelper.floor_double(maxX);
        int k = MathHelper.floor_double(minY);
        int l = MathHelper.floor_double(maxY);
        int i1 = MathHelper.floor_double(minZ);
        int j1 = MathHelper.floor_double(maxZ);

        for(CachedBlock cachedBlock : blocks) {
            AxisAlignedBB blockAABB = new AxisAlignedBB(cachedBlock.getPosition(), 1F);

            for (int k1 = i; k1 < j; ++k1) {
                for (int l1 = k; l1 < l; ++l1) {
                    for (int i2 = i1; i2 < j1; ++i2) {

                        if (intersectsWith(k1, l1, i2, blockAABB)) {

                            return MaterialChecks.LIQUIDS.contains(cachedBlock.getMaterial());
                        }
                    }
                }
            }

        }


        return false;
    }

    public List<Block> getCollidingAir() {

        final List<Block> blocks = new ArrayList<>();

        final int xFloor = MathHelper.floor(minX);
        final int xCeil = MathHelper.ceiling_double_int(maxX);

        final int yFloor = MathHelper.floor(minY);
        final int yCeil = MathHelper.ceiling_double_int(maxY);

        final int zFloor = MathHelper.floor(minZ);
        final int zCeil = MathHelper.ceiling_double_int(maxZ);

        for (int x = xFloor; x <= xCeil; x++) {

            for (int z = zFloor; z <= zCeil; z++) {

                final Location chunkLocation = new Location(data.getWorld(), x, 64, z);

                Block block = Karhu.getInstance().getChunkManager().getChunkBlockAt(chunkLocation);

                if (block != null) {
                    for (int y = yFloor; y <= yCeil; y++) {

                        chunkLocation.setY(y);

                        final Block block2 = Karhu.getInstance().getChunkManager().getChunkBlockAt(chunkLocation);

                        if (block2.getType() == Material.AIR) blocks.add(block2);
                    }
                }
            }
        }
        return blocks;
    }

    public boolean checkBlocks(Predicate<Material> p) {

        final List<Block> blocks = new ArrayList<>();

        final int xFloor = (int) Math.floor(minX);
        final int xCeil = (int) Math.ceil(maxX);

        final int yFloor = (int) Math.floor(minY);
        final int yCeil = (int) Math.ceil(maxY);

        final int zFloor = (int) Math.floor(minZ);
        final int zCeil = (int) Math.ceil(maxZ);

        for (int x = xFloor; x < xCeil; x++) {
            for (int y = yFloor; y < yCeil; y++) {
                for (int z = zFloor; z < zCeil; z++) {
                    final Location loc = new Location(data.getWorld(), x, y, z);
                    if (BlockUtil.chunkLoaded(loc)) {
                        final Block b = loc.getBlock();
                        blocks.add(b);
                    }
                }

            }

        }

        return new KarhuStream<>(blocks).any(t -> p.test(t.getType()));
    }

    public boolean intersectsWith(AxisAlignedBB other, int floorX, int floorY, int floorZ, int ceilX, int ceilY, int ceilZ)
    {
        return other.minX < ceilX && other.maxX > floorX &&
                other.minY < ceilY && other.maxY > floorY &&
                other.minZ < ceilZ && other.maxZ > floorZ;
    }

    public boolean intersectsWith(double posX, double posY, double posZ, AxisAlignedBB other)
    {
        return other.maxX >= posX && other.minX <= posX
                && other.maxY >= posY && other.minY <= posY
                && other.maxZ >= posZ && other.minZ <= posZ;
    }

    public boolean intersectsWith(AxisAlignedBB other)
    {
        return other.maxX >= this.minX && other.minX <= this.maxX
                && other.maxY >= this.minY && other.minY <= this.maxY
                && other.maxZ >= this.minZ && other.minZ <= this.maxZ;
    }
    public boolean intersectsWithModern(AxisAlignedBB other) {
        double epsilon = 1.0E-7;
        return this.maxX - epsilon > other.minX && this.minX + epsilon < other.maxX
                && this.maxY - epsilon > other.minY && this.minY + epsilon < other.maxY
                && this.maxZ - epsilon > other.minZ && this.minZ + epsilon < other.maxZ;
    }

    public boolean intersectsWithTest(AxisAlignedBB other)
    {
        return other.minX <= maxX && other.maxX >= minX &&
                other.minY <= maxY && other.maxY >= minY &&
                other.minZ <= maxZ && other.maxZ >= minZ;
    }

    @Override
    public String toString()
    {
        return "box[" + this.minX + ", " + this.minY + ", " + this.minZ + " -> " + this.maxX + ", " + this.maxY + ", " + this.maxZ + "]";
    }

    @SneakyThrows
    public BoundingBox clone() {
        return (BoundingBox) super.clone();
    }

    public double getCenterX() {
        return (minX + maxX) / 2.0;
    }

    public double getCenterY() {
        return (minY + maxY) / 2.0;
    }

    public double getCenterZ() {
        return (minZ + maxZ) / 2.0;
    }

    public long getTimestamp() {
        return timestamp;
    }

}

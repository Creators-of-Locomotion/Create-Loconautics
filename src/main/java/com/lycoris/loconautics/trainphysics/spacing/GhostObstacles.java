package com.lycoris.loconautics.trainphysics.spacing;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.observer.TrackObserverBlock;
import com.simibubi.create.content.trains.signal.SignalBlock;
import com.simibubi.create.content.trains.station.StationBlock;
import com.simibubi.create.content.trains.track.FakeTrackBlock;
import com.simibubi.create.content.trains.track.ITrackBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// unloaded chunks cannot be read, so blocked rail cells are saved at unload
@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class GhostObstacles {

    private static final double HALF_WIDTH = 0.8;
    private static final double FLOOR = 0.3;
    private static final double CEILING = 2.6;
    private static final double SAMPLE = 0.5;
    private static final double MARGIN = 1.5;

    private static final Map<ServerLevel, Long2ObjectMap<ChunkAccess>> PENDING = new HashMap<>();

    private GhostObstacles() {
    }

    // unloads queue up and are scanned together once per tick
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkAccess chunk = event.getChunk();
        if (level.getServer().isRunning()) {
            PENDING.computeIfAbsent(level, key -> new Long2ObjectOpenHashMap<>()).put(chunk.getPos().toLong(), chunk);
            return;
        }
        Long2ObjectMap<ChunkAccess> now = new Long2ObjectOpenHashMap<>();
        now.put(chunk.getPos().toLong(), chunk);
        rescan(level, now);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Map.Entry<ServerLevel, Long2ObjectMap<ChunkAccess>>> batches = new ArrayList<>(PENDING.entrySet());
        PENDING.clear();
        for (Map.Entry<ServerLevel, Long2ObjectMap<ChunkAccess>> batch : batches) {
            rescan(batch.getKey(), batch.getValue());
        }
    }

    static boolean any(ServerLevel level) {
        return !GhostObstacleStore.get(level).isEmpty();
    }

    static boolean isBlocked(ServerLevel level, Vec3 at) {
        BlockPos cell = BlockPos.containing(at);
        ChunkPos chunk = new ChunkPos(cell);
        if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
            return blockedBox(level, null, at);
        }
        return GhostObstacleStore.get(level).has(chunk.toLong(), cell.asLong());
    }

    private record EdgeRef(TrackGraph graph, TrackEdge edge) {
    }

    private static void rescan(ServerLevel level, Long2ObjectMap<ChunkAccess> chunks) {
        Map<UUID, TrackGraph> networks = Create.RAILWAYS == null ? null : Create.RAILWAYS.trackNetworks;
        Long2ObjectMap<List<EdgeRef>> byChunk = networks == null ? new Long2ObjectOpenHashMap<>()
                : indexEdges(level, networks, chunks);
        GhostObstacleStore store = GhostObstacleStore.get(level);
        for (ChunkAccess chunk : chunks.values()) {
            ChunkPos pos = chunk.getPos();
            Long2ObjectMap<LongSet> found = new Long2ObjectOpenHashMap<>();
            List<EdgeRef> refs = byChunk.get(pos.toLong());
            if (refs != null) {
                for (EdgeRef ref : refs) {
                    sampleEdge(level, chunk, chunks, ref, found);
                }
            }
            int before = store.size(pos.toLong());
            store.replace(pos.toLong(), found.remove(pos.toLong()));
            for (Long2ObjectMap.Entry<LongSet> entry : found.long2ObjectEntrySet()) {
                store.add(entry.getLongKey(), entry.getValue());
            }
            int after = store.size(pos.toLong());
            if (before != 0 || after != 0) {
                LoconauticsConstants.LOGGER.info("[ghostblock] chunk {} {} cells {} to {} neighborsTouched={}", pos.x,
                        pos.z, before, after, found.size());
            }
        }
    }

    private static Long2ObjectMap<List<EdgeRef>> indexEdges(ServerLevel level, Map<UUID, TrackGraph> networks,
                                                            Long2ObjectMap<ChunkAccess> chunks) {
        Long2ObjectMap<List<EdgeRef>> byChunk = new Long2ObjectOpenHashMap<>();
        for (TrackGraph graph : networks.values()) {
            for (TrackNodeLocation location : graph.getNodes()) {
                if (location.getDimension() != level.dimension()) {
                    continue;
                }
                TrackNode node = graph.locateNode(location);
                Map<TrackNode, TrackEdge> edges = node == null ? null : graph.getConnectionsFrom(node);
                if (edges == null) {
                    continue;
                }
                for (Map.Entry<TrackNode, TrackEdge> link : edges.entrySet()) {
                    TrackNode other = link.getKey();
                    TrackEdge edge = link.getValue();
                    if (node.getNetId() > other.getNetId() || edge.isInterDimensional()) {
                        continue;
                    }
                    register(byChunk, chunks, new EdgeRef(graph, edge), node, other);
                }
            }
        }
        return byChunk;
    }

    private static void register(Long2ObjectMap<List<EdgeRef>> byChunk, Long2ObjectMap<ChunkAccess> chunks,
                                 EdgeRef ref, TrackNode a, TrackNode b) {
        Vec3 from = a.getLocation().getLocation();
        Vec3 to = b.getLocation().getLocation();
        double chord = from.distanceTo(to);
        double length = ref.edge().getLength();
        double slack = Math.sqrt(Math.max(0.0, length * length - chord * chord)) / 2.0 + 1.0;
        int minCx = Mth.floor((Math.min(from.x, to.x) - slack - MARGIN) / 16.0) - 1;
        int maxCx = Mth.floor((Math.max(from.x, to.x) + slack + MARGIN) / 16.0);
        int minCz = Mth.floor((Math.min(from.z, to.z) - slack - MARGIN) / 16.0) - 1;
        int maxCz = Mth.floor((Math.max(from.z, to.z) + slack + MARGIN) / 16.0);
        boolean hostChecked = false;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                if (!chunks.containsKey(key)) {
                    continue;
                }
                if (!hostChecked) {
                    if (RailSeams.hostIdOf(a.getLocation()) != null) {
                        return;
                    }
                    hostChecked = true;
                }
                byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(ref);
            }
        }
    }

    private static void sampleEdge(ServerLevel level, ChunkAccess chunk, Long2ObjectMap<ChunkAccess> chunks,
                                   EdgeRef ref, Long2ObjectMap<LongSet> found) {
        ChunkPos pos = chunk.getPos();
        double minX = pos.getMinBlockX() - MARGIN;
        double maxX = pos.getMaxBlockX() + 1 + MARGIN;
        double minZ = pos.getMinBlockZ() - MARGIN;
        double maxZ = pos.getMaxBlockZ() + 1 + MARGIN;
        TrackEdge edge = ref.edge();
        int guard = (int) (edge.getLength() / SAMPLE) * 2 + 8;
        double t = 0.0;
        while (t <= 1.0 && guard-- > 0) {
            Vec3 at = edge.getPosition(ref.graph(), t);
            if (at.x >= minX && at.x <= maxX && at.z >= minZ && at.z <= maxZ && blockedBox(level, chunks, at)) {
                BlockPos cell = BlockPos.containing(at);
                found.computeIfAbsent(new ChunkPos(cell).toLong(), key -> new LongOpenHashSet()).add(cell.asLong());
            }
            double next = edge.incrementT(t, SAMPLE);
            if (next <= t) {
                return;
            }
            t = next;
        }
    }

    // never goes through level.getblockstate, that would load the chunk it asks about
    private static boolean blockedBox(ServerLevel level, Long2ObjectMap<ChunkAccess> extras, Vec3 at) {
        AABB box = new AABB(at.x - HALF_WIDTH, at.y + FLOOR, at.z - HALF_WIDTH, at.x + HALF_WIDTH, at.y + CEILING,
                at.z + HALF_WIDTH);
        VoxelShape boxShape = null;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                ChunkAccess source = chunkOf(level, extras, x >> 4, z >> 4);
                if (source == null) {
                    continue;
                }
                for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
                    if (source.isOutsideBuildHeight(y)) {
                        continue;
                    }
                    p.set(x, y, z);
                    BlockState state = source.getBlockState(p);
                    if (state.isAir() || ignored(state.getBlock())) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(source, p);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    if (boxShape == null) {
                        boxShape = Shapes.create(box);
                    }
                    if (Shapes.joinIsNotEmpty(shape.move(x, y, z), boxShape, BooleanOp.AND)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static ChunkAccess chunkOf(ServerLevel level, Long2ObjectMap<ChunkAccess> extras, int cx, int cz) {
        ChunkAccess queued = extras == null ? null : extras.get(ChunkPos.asLong(cx, cz));
        if (queued != null) {
            return queued;
        }
        return level.getChunkSource().getChunkNow(cx, cz);
    }

    private static boolean ignored(Block block) {
        return block instanceof ITrackBlock || block instanceof FakeTrackBlock || block instanceof SignalBlock
                || block instanceof StationBlock || block instanceof TrackObserverBlock;
    }
}

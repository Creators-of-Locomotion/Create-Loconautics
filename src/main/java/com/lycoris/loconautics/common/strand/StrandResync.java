package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.hose.HoseStrand;
import com.lycoris.loconautics.content.hose.HoseStrandManager;
import com.lycoris.loconautics.content.screwlink.ScrewLinkStrand;
import com.lycoris.loconautics.content.screwlink.ScrewLinkStrandManager;
import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class StrandResync {

    private StrandResync() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendAll(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendAll(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendAll(player);
        }
    }

    private static void sendAll(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        for (SteelCableStrand strand : SteelCableStrandManager.get(server).all()) {
            if (StrandEndpoints.drivenBy(level, strand.subA, strand.subB, strand.dimension)) {
                send(player, strand.type.kind(), strand.id, strand.points,
                        new StrandEnd(strand.subA, strand.localA), new StrandEnd(strand.subB, strand.localB));
            }
        }
        for (ScrewLinkStrand strand : ScrewLinkStrandManager.get(server).all()) {
            if (StrandEndpoints.drivenBy(level, strand.subA, strand.subB)) {
                send(player, StrandKind.SCREW_LINK, strand.id, strand.points, new StrandEnd(strand.subA, strand.localA),
                        strand.localB == null ? null : new StrandEnd(strand.subB, strand.localB));
            }
        }
        for (HoseStrand strand : HoseStrandManager.get(server).all()) {
            if (strand.dimension.equals(level.dimension())) {
                send(player, StrandKind.WINCH_HOSE, strand.id, strand.points, null, null);
            }
        }
    }

    private static void send(ServerPlayer player, StrandKind kind, UUID id, @Nullable List<Vector3d> points,
                             @Nullable StrandEnd start, @Nullable StrandEnd end) {
        if (points == null || points.size() < 2) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new StrandPointsPacket(kind, id, 0, true, points, start, end));
    }
}

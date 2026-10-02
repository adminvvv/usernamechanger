package adminvvv.usernamechanger.compat;

import adminvvv.usernamechanger.mixin.ChunkMapAccessor;
import adminvvv.usernamechanger.mixin.TrackedEntityAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Packet/tracker dependencies are kept here to make version ports small. */
public final class PlayerPresentation {
    private static final ThreadLocal<Boolean> LITERAL_TEAM_ENTRY = ThreadLocal.withInitial(() -> false);

    private PlayerPresentation() {}

    public static boolean literalTeamEntry() { return LITERAL_TEAM_ENTRY.get(); }

    public static void refresh(MinecraftServer server, ServerPlayer player, String oldName) {
        var service = adminvvv.usernamechanger.Usernamechanger.service();
        refreshTeam(server, player.getGameProfile().name(), oldName, service.visibleName(player));
        Object tracked = ((ChunkMapAccessor) player.level().getChunkSource().chunkMap)
                .usernamechanger$entities().get(player.getId());
        var tracker = tracked == null ? null : (TrackedEntityAccessor) tracked;
        var removeInfo = new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID()));
        var addInfo = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
            boolean visible = viewer != player && tracker != null && tracker.usernamechanger$viewers().contains(viewer.connection);
            if (visible) packets.add(new ClientboundRemoveEntitiesPacket(player.getId()));
            packets.add(removeInfo);
            packets.add(addInfo);
            if (visible) tracker.usernamechanger$entity().sendPairingData(viewer, packets::add);
            viewer.connection.send(new ClientboundBundlePacket(packets));
        }
    }

    public static void refreshTeam(MinecraftServer server, String account, String oldName, String newName) {
        var team = server.getScoreboard().getPlayersTeam(account);
        if (team == null || oldName.equals(newName)) return;
        LITERAL_TEAM_ENTRY.set(true);
        try {
            server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, oldName, ClientboundSetPlayerTeamPacket.Action.REMOVE));
            server.getPlayerList().broadcastAll(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, newName, ClientboundSetPlayerTeamPacket.Action.ADD));
        } finally {
            LITERAL_TEAM_ENTRY.remove();
        }
    }
}

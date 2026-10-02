package adminvvv.usernamechanger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MixinCompatibilityTest {
    @Test void modLoadsForDedicatedAndIntegratedServers() {
        var metadata = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("usernamechanger").orElseThrow().getMetadata();
        assertTrue(metadata.getEnvironment().matches(net.fabricmc.api.EnvType.SERVER));
        assertTrue(metadata.getEnvironment().matches(net.fabricmc.api.EnvType.CLIENT));
    }

    @Test void allMinecraftInjectionTargetsApply() throws Exception {
        for (String name : new String[]{
                "net.minecraft.world.entity.player.Player",
                "net.minecraft.server.level.ServerPlayer",
                "net.minecraft.server.players.PlayerList",
                "net.minecraft.commands.CommandSourceStack",
                "net.minecraft.commands.arguments.GameProfileArgument",
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry",
                "net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket",
                "net.minecraft.server.level.ChunkMap",
                "net.minecraft.server.level.ChunkMap$TrackedEntity"}) {
            assertNotNull(Class.forName(name, false, getClass().getClassLoader()));
        }
        assertTrue(adminvvv.usernamechanger.mixin.ChunkMapAccessor.class.isAssignableFrom(
                Class.forName("net.minecraft.server.level.ChunkMap", false, getClass().getClassLoader())));
    }
}

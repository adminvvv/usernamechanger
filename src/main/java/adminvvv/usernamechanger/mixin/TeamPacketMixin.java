package adminvvv.usernamechanger.mixin;

import adminvvv.usernamechanger.Usernamechanger;
import adminvvv.usernamechanger.compat.PlayerPresentation;
import java.util.Collection;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientboundSetPlayerTeamPacket.class)
public abstract class TeamPacketMixin {
    @ModifyVariable(method = "<init>(Ljava/lang/String;ILjava/util/Optional;Ljava/util/Collection;)V",
            at = @At("HEAD"), argsOnly = true)
    private static Collection<String> usernamechanger$members(Collection<String> players) {
        var service = Usernamechanger.service();
        if (service == null || PlayerPresentation.literalTeamEntry()) return players;
        return players.stream().map(service::teamEntry).toList();
    }
}

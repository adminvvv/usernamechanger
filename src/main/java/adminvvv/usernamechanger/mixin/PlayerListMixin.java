package adminvvv.usernamechanger.mixin;

import adminvvv.usernamechanger.Usernamechanger;
import java.io.IOException;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void usernamechanger$join(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (Usernamechanger.service() == null) return;
        try {
            Usernamechanger.service().joining(player);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot persist nickname account record", e);
        }
    }

    @Inject(method = "getPlayerByName", at = @At("RETURN"), cancellable = true)
    private void usernamechanger$alias(String name, CallbackInfoReturnable<ServerPlayer> cir) {
        if (cir.getReturnValue() == null && Usernamechanger.service() != null) {
            cir.setReturnValue(Usernamechanger.service().onlineAlias(name));
        }
    }

}

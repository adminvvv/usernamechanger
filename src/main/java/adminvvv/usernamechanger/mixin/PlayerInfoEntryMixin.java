package adminvvv.usernamechanger.mixin;

import adminvvv.usernamechanger.Usernamechanger;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientboundPlayerInfoUpdatePacket.Entry.class)
public abstract class PlayerInfoEntryMixin {
    @Redirect(method = "<init>(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getGameProfile()Lcom/mojang/authlib/GameProfile;"))
    private static GameProfile usernamechanger$profile(ServerPlayer player) {
        var service = Usernamechanger.service();
        return service == null ? player.getGameProfile() : service.clientProfile(player.getGameProfile());
    }
}

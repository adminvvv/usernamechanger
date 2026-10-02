package adminvvv.usernamechanger.mixin;

import adminvvv.usernamechanger.Usernamechanger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDisplayMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void usernamechanger$display(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof ServerPlayer player && Usernamechanger.service() != null) {
            String nickname = Usernamechanger.service().nickname(player.getUUID());
            if (nickname != null) {
                cir.setReturnValue(PlayerTeam.formatNameForTeam(player.getTeam(), Component.literal(nickname))
                        .withStyle(cir.getReturnValue().getStyle()));
            }
        }
    }
}

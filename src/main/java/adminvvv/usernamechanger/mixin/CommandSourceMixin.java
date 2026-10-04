package adminvvv.usernamechanger.mixin;

import adminvvv.usernamechanger.Usernamechanger;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandSourceStack.class)
public abstract class CommandSourceMixin {
    @Inject(method = "getOnlinePlayerNames", at = @At("RETURN"), cancellable = true)
    private void usernamechanger$suggestions(CallbackInfoReturnable<Collection<String>> cir) {
        if (Usernamechanger.service() != null) {
            cir.setReturnValue(List.of(Usernamechanger.service().suggestions()));
        }
    }
}

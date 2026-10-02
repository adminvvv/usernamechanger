package adminvvv.usernamechanger.testmixin;

import adminvvv.usernamechanger.NicknameGameTests;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrimaryLevelData.class)
public abstract class CheatsDisabledTestMixin {
    @Inject(method = "isAllowCommands", at = @At("HEAD"), cancellable = true)
    private void usernamechanger$disableCheats(CallbackInfoReturnable<Boolean> cir) {
        if (NicknameGameTests.worldOwner != null) cir.setReturnValue(false);
    }
}

package adminvvv.usernamechanger.testmixin;

import adminvvv.usernamechanger.NicknameGameTests;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies a world owner to the headless server for integrated-server permission tests. */
@Mixin(GameTestServer.class)
public abstract class WorldOwnerTestMixin {
    @Inject(method = "isSingleplayerOwner", at = @At("HEAD"), cancellable = true)
    private void usernamechanger$owner(NameAndId identity, CallbackInfoReturnable<Boolean> cir) {
        if (NicknameGameTests.worldOwner != null) {
            cir.setReturnValue(NicknameGameTests.worldOwner.equals(identity.id()));
        }
    }
}

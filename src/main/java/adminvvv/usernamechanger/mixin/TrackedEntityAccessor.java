package adminvvv.usernamechanger.mixin;

import java.util.Set;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {
    @Accessor("serverEntity")
    ServerEntity usernamechanger$entity();

    @Accessor("seenBy")
    Set<ServerPlayerConnection> usernamechanger$viewers();
}

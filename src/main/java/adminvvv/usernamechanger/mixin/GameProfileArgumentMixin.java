package adminvvv.usernamechanger.mixin;

import adminvvv.usernamechanger.Usernamechanger;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.commands.arguments.GameProfileArgument;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameProfileArgument.class)
public abstract class GameProfileArgumentMixin {
    @WrapMethod(method = "parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/GameProfileArgument$Result;")
    private static GameProfileArgument.Result usernamechanger$account(StringReader reader, boolean allowSelectors,
            Operation<GameProfileArgument.Result> original) throws CommandSyntaxException {
        int start = reader.getCursor();
        var result = original.call(reader, allowSelectors);
        String name = reader.getString().substring(start, reader.getCursor());
        if (name.startsWith("@")) return result;
        // Resolve at execution time, including commands parsed earlier in a data-pack function.
        return source -> {
            var service = Usernamechanger.service();
            var identity = service == null ? null : service.profileTarget(name);
            return identity == null ? result.getNames(source) : List.of(identity);
        };
    }
}

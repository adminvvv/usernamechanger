package adminvvv.usernamechanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

public final class NicknameCommands {
    private NicknameCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("usernamechange")
                .requires(source -> allowed(source, "change"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Usernamechanger.service().knownNames(), builder))
                        .then(Commands.argument("nickname", StringArgumentType.word())
                                .executes(context -> Usernamechanger.service().change(context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "nickname"))))));
        dispatcher.register(Commands.literal("usernamereset")
                .requires(source -> allowed(source, "change"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Usernamechanger.service().knownNames(), builder))
                        .executes(context -> Usernamechanger.service().change(context.getSource(),
                                StringArgumentType.getString(context, "player"), null))));
        dispatcher.register(Commands.literal("usernamechanger")
                .requires(source -> allowed(source, "reload"))
                .then(Commands.literal("reload").executes(context -> Usernamechanger.service().reload(context.getSource()))));
    }

    private static boolean allowed(CommandSourceStack source, String action) {
        return Usernamechanger.service() != null && Usernamechanger.service().allowed(source, action);
    }
}

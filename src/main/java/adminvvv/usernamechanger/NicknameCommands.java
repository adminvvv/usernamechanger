package adminvvv.usernamechanger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class NicknameCommands {
    private NicknameCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("usernamechange")
                .requires(source -> allowed(source, "change"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .then(Commands.argument("nickname", StringArgumentType.word())
                                .executes(context -> Usernamechanger.service().change(context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "nickname"))))));
        dispatcher.register(Commands.literal("usernamereset")
                .requires(source -> allowed(source, "change"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((context, builder) -> suggestPlayers(builder))
                        .executes(context -> Usernamechanger.service().change(context.getSource(),
                                StringArgumentType.getString(context, "player"), null))));
        dispatcher.register(Commands.literal("usernamechanger")
                .requires(source -> allowed(source, "reload"))
                .then(Commands.literal("reload").executes(context -> Usernamechanger.service().reload(context.getSource()))));
    }

    private static boolean allowed(CommandSourceStack source, String action) {
        return Usernamechanger.service() != null && Usernamechanger.service().allowed(source, action);
    }

    private static CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (prefix.startsWith("\"")) prefix = prefix.substring(1);
        for (String name : Usernamechanger.service().knownNames()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(StringArgumentType.escapeIfRequired(name));
            }
        }
        return builder.buildFuture();
    }
}

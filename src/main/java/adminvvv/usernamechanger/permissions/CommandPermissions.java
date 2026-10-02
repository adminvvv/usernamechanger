package adminvvv.usernamechanger.permissions;

import adminvvv.usernamechanger.config.ModConfig;
import java.util.UUID;
import java.util.function.BiFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;

public final class CommandPermissions {
    private final BiFunction<UUID, String, Boolean> luckPerms = FabricLoader.getInstance().isModLoaded("luckperms")
            ? LuckPermsBridge::check : (id, node) -> null;

    public boolean allowed(CommandSourceStack source, ModConfig config, String action) {
        if (source.isPlayer()) {
            var identity = new NameAndId(source.getPlayer().getGameProfile());
            if (source.getServer().isSingleplayerOwner(identity)
                    || source.getServer().getPlayerList().isOp(identity)) return true;
        }
        if (source.isPlayer() && config.useLuckPerms) {
            Boolean result = luckPerms.apply(source.getPlayer().getUUID(), "usernamechanger." + action);
            if (result != null) return result;
        }
        if (source.isPlayer() && config.allowNonOperators && !action.equals("reload")) return true;
        return source.permissions().hasPermission(switch (config.requiredOpLevel) {
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        });
    }
}

package com.gargara.flanestate;

import com.mojang.brigadier.CommandDispatcher;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import static net.minecraft.server.command.CommandManager.literal;

public class CommandManager {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(CommandManager::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, RegistrationEnvironment environment) {
        dispatcher.register(literal("emlak")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    GuiHandler.openEmlakGui(player);
                }
                return 1;
            })
            .then(literal("isim").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player != null) {
                    ClaimStorage storage = ClaimStorage.get(player.getServerWorld());
                    BlockPos pos = player.getBlockPos();
                    Claim claim = storage.getClaimAt(pos);
                    
                    if (claim != null && claim.getOwner() != null && claim.getOwner().equals(player.getUuid())) {
                        GuiHandler.openNameGui(player, claim);
                    } else {
                        player.sendMessage(Text.literal("§cBu işlem için kendi arazinizin içinde olmalısınız!"), false);
                    }
                }
                return 1;
            }))
        );
    }
}

package com.gargara.flanestate;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlanEstate implements ModInitializer {
    public static final String MOD_ID = "flanestate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("FlanEstate Mod Initialized!");
        SignEventHandler.register();
        RentManager.register();
        CommandManager.register();
        com.gargara.flanestate.integration.TabIntegration.register();
        
        // Otomatik isim ekranı için takip haritası
        java.util.Map<java.util.UUID, Integer> claimCounts = new java.util.HashMap<>();
        
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                io.github.flemmli97.flan.claim.ClaimStorage storage = io.github.flemmli97.flan.claim.ClaimStorage.get(player.getServerWorld());
                java.util.Collection<io.github.flemmli97.flan.claim.Claim> claims = storage.allClaimsFromPlayer(player.getUuid());
                
                int currentCount = claims.size();
                Integer oldCount = claimCounts.get(player.getUuid());
                
                if (oldCount != null && currentCount > oldCount) {
                    // Yeni claim alındı! Son claim'i bul
                    io.github.flemmli97.flan.claim.Claim newestClaim = null;
                    for (io.github.flemmli97.flan.claim.Claim c : claims) {
                        newestClaim = c; // Genelde en son eklenen en sonda olur, ya da hepsi isimsizdir
                    }
                    
                    if (newestClaim != null) {
                        GuiHandler.openNameGui(player, newestClaim);
                    }
                }
                
                claimCounts.put(player.getUuid(), currentCount);
            }
        });
    }
}

package com.gargara.flanestate.integration;

import com.gargara.flanestate.EstateState;
import com.gargara.flanestate.FlanEstate;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import me.neznamy.tab.api.TabAPI;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TabIntegration {

    public static class EmlakInfo {
        public String baslik = "";
        public String satir1 = "";
        public String satir2 = "";

        public EmlakInfo(String baslik, String satir1, String satir2) {
            this.baslik = baslik;
            this.satir1 = satir1;
            this.satir2 = satir2;
        }
    }

    private static final Map<UUID, EmlakInfo> cache = new HashMap<>();
    private static int tickCounter = 0;

    public static void register() {
        try {
            // Register server tick event to update cache every second
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                tickCounter++;
                if (tickCounter >= 20) { // 1 second
                    tickCounter = 0;
                    updateCache(server);
                }
            });

            // Register TAB placeholders
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%emlak_baslik%", 1000, tabPlayer -> {
                EmlakInfo info = cache.get(tabPlayer.getUniqueId());
                return info != null ? info.baslik : "";
            });

            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%emlak_satir1%", 1000, tabPlayer -> {
                EmlakInfo info = cache.get(tabPlayer.getUniqueId());
                return info != null ? info.satir1 : "";
            });

            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%emlak_satir2%", 1000, tabPlayer -> {
                EmlakInfo info = cache.get(tabPlayer.getUniqueId());
                return info != null ? info.satir2 : "";
            });

            FlanEstate.LOGGER.info("TAB Integration registered successfully!");
        } catch (Throwable t) {
            FlanEstate.LOGGER.error("Failed to register TAB integration. Is TAB installed?", t);
        }
    }

    private static void updateCache(MinecraftServer server) {
        EstateState estateState = EstateState.getServerState(server);
        long currentTime = server.getOverworld().getTimeOfDay();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ClaimStorage storage = ClaimStorage.get(player.getServerWorld());
            if (storage == null) continue;

            UUID pUuid = player.getUuid();
            Claim currentClaim = storage.getClaimAt(player.getBlockPos());
            
            // 1. Is player standing inside a claim they rent or own?
            if (currentClaim != null) {
                if (currentClaim.getOwner().equals(pUuid)) {
                    cache.put(pUuid, getOwnedInfo(currentClaim));
                    continue;
                }
                
                EstateState.RentedProperty rp = estateState.getRentedProperties().get(currentClaim.getClaimID());
                if (rp != null && rp.renterUuid.equals(pUuid)) {
                    cache.put(pUuid, getRentedInfo(currentClaim, rp, currentTime));
                    continue;
                }
            }
            
            // 2. Not standing in their claim. Find rented properties with nearest rent payment.
            EstateState.RentedProperty nearestRent = null;
            Claim nearestClaim = null;
            long minRemaining = Long.MAX_VALUE;

            for (Map.Entry<UUID, EstateState.RentedProperty> entry : estateState.getRentedProperties().entrySet()) {
                EstateState.RentedProperty prop = entry.getValue();
                if (prop.renterUuid.equals(pUuid)) {
                    long remaining = 24000 - (currentTime - prop.lastPaidTime);
                    if (remaining < minRemaining) {
                        minRemaining = remaining;
                        nearestRent = prop;
                        nearestClaim = storage.getFromUUID(entry.getKey());
                    }
                }
            }

            if (nearestRent != null && nearestClaim != null) {
                cache.put(pUuid, getRentedInfo(nearestClaim, nearestRent, currentTime));
                continue;
            }

            // 3. Optional: If no rented properties, could show owned property (but skipped to save performance, and user usually knows their house unless inside it).
            // Clear if nothing applies
            cache.put(pUuid, new EmlakInfo("&6&l🏠 Emlak Varlıkları", "&7Ev Durumu: &cYok", "&7Kira: &cYok"));
        }
    }

    private static EmlakInfo getOwnedInfo(Claim claim) {
        String name = getClaimNameSafe(claim);
        // Calculate volume or area safely if possible
        String size = getClaimSizeSafe(claim);
        return new EmlakInfo(
            "&6&l🏠 Emlak Varlıkları",
            "&aSahip: &f" + name,
            "&7" + size
        );
    }

    private static EmlakInfo getRentedInfo(Claim claim, EstateState.RentedProperty prop, long currentTime) {
        String name = getClaimNameSafe(claim);
        long remainingTicks = 24000 - (currentTime - prop.lastPaidTime);
        if (remainingTicks < 0) remainingTicks = 0;
        
        long remainingSeconds = remainingTicks / 20;
        long mins = remainingSeconds / 60;
        long secs = remainingSeconds % 60;
        String timeStr = String.format("%02d:%02d", mins, secs);

        return new EmlakInfo(
            "&6&l🏠 Emlak Varlıkları",
            "&eKira: &f" + name,
            "&c" + (int)prop.price + "₺ &7- Sonraki: &a" + timeStr
        );
    }

    private static String getClaimNameSafe(Claim claim) {
        try {
            String name = claim.getClaimName();
            if (name != null && !name.trim().isEmpty()) {
                return name;
            }
        } catch (Throwable t) {
            // Ignored
        }
        return "Arazi";
    }

    private static String getClaimSizeSafe(Claim claim) {
        return "Mülkünüz";
    }
}

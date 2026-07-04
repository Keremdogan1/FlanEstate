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
    private static boolean tabPlaceholdersRegistered = false;

    public static void register() {
        // Register server tick event - both for cache updates AND lazy TAB registration
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= 20) { // 1 second
                tickCounter = 0;
                updateCache(server);
            }

            // Lazy TAB registration - wait until TAB API is actually ready
            if (!tabPlaceholdersRegistered) {
                try {
                    if (TabAPI.getInstance() != null) {
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

                        tabPlaceholdersRegistered = true;
                        FlanEstate.LOGGER.info("TAB Integration registered successfully!");
                    }
                } catch (Throwable t) {
                    FlanEstate.LOGGER.error("Failed to register TAB integration. Is TAB installed?", t);
                }
            }
        });
    }

    private static void updateCache(MinecraftServer server) {
        EstateState estateState = EstateState.getServerState(server);
        long currentTime = server.getOverworld().getTimeOfDay();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ClaimStorage storage = ClaimStorage.get(player.getServerWorld());
            if (storage == null) continue;

            UUID pUuid = player.getUuid();
            java.util.Collection<Claim> ownedClaims = storage.allClaimsFromPlayer(pUuid);
            int ownedCount = ownedClaims.size();
            String singleOwnedName = ownedCount == 1 ? getClaimNameSafe(ownedClaims.iterator().next(), player) : "";

            int rentedCount = 0;
            EstateState.RentedProperty nearestRent = null;
            long minRemaining = Long.MAX_VALUE;
            Claim singleRentedClaim = null;

            for (java.util.Map.Entry<java.util.UUID, EstateState.RentedProperty> entry : estateState.getRentedProperties().entrySet()) {
                EstateState.RentedProperty prop = entry.getValue();
                if (prop.renterUuid.equals(pUuid)) {
                    Claim rClaim = storage.getFromUUID(entry.getKey());
                    if (rClaim != null) {
                        rentedCount++;
                        singleRentedClaim = rClaim;
                        
                        long elapsedTime = currentTime - prop.lastPaidTime;
                        long remainingTicks = 24000 - elapsedTime;
                        if (remainingTicks < 0) remainingTicks = 0;
                        
                        if (remainingTicks < minRemaining) {
                            minRemaining = remainingTicks;
                            nearestRent = prop;
                        }
                    }
                }
            }
            
            String singleRentedName = rentedCount == 1 && singleRentedClaim != null ? getClaimNameSafe(singleRentedClaim, player) : "";

            Claim currentClaim = storage.getClaimAt(player.getBlockPos());
            
            String satir1 = "&7Ev Durumu: &cYok";
            String satir2 = "&7Kira: &cYok";

            // İçindeyse durumu belirt
            boolean isInsideOwned = currentClaim != null && currentClaim.getOwner() != null && currentClaim.getOwner().equals(pUuid);
            boolean isInsideRented = false;
            EstateState.RentedProperty currentRentProp = null;
            
            if (currentClaim != null) {
                for (java.util.Map.Entry<java.util.UUID, EstateState.RentedProperty> entry : estateState.getRentedProperties().entrySet()) {
                    EstateState.RentedProperty prop = entry.getValue();
                    if (entry.getKey().equals(currentClaim.getClaimID()) && prop.renterUuid.equals(pUuid)) {
                        isInsideRented = true;
                        currentRentProp = prop;
                        break;
                    }
                }
            }

            // Satır 1 mantığı (Sahiplik)
            if (isInsideOwned) {
                satir1 = "&a📍 İçinde: &f" + getClaimNameSafe(currentClaim, player);
            } else if (ownedCount > 1) {
                satir1 = "&aSahip: &f" + ownedCount + " Mülk";
            } else if (ownedCount == 1) {
                satir1 = "&aSahip: &f" + singleOwnedName;
            }

            // Satır 2 mantığı (Kira)
            if (isInsideRented && currentRentProp != null) {
                long elapsedTime = currentTime - currentRentProp.lastPaidTime;
                long remainingTicks = 24000 - elapsedTime;
                if (remainingTicks < 0) remainingTicks = 0;
                long mins = (remainingTicks / 20) / 60;
                satir2 = "&e📍 Kira: &f" + getClaimNameSafe(currentClaim, player) + " (&c" + currentRentProp.price + "₺ &7- &a" + mins + "m&f)";
            } else if (rentedCount > 0 && nearestRent != null) {
                long mins = (minRemaining / 20) / 60;
                String namePrefix = rentedCount > 1 ? rentedCount + " Mülk" : singleRentedName;
                satir2 = "&eKira: &f" + namePrefix + " (&c" + nearestRent.price + "₺ &7- &a" + mins + "m&f)";
            }
            
            // Eğer kirada olduğu bir evdeyse ve hiç evi yoksa 1. satırı da Kira olarak göster (daha temiz gözükür)
            if (isInsideRented && ownedCount == 0) {
                satir1 = satir2;
                satir2 = "&7Ev Durumu: &cYok";
            }

            cache.put(pUuid, new EmlakInfo("&6&l🏠 Emlak Varlıkları", satir1, satir2));
        }
    }

    private static String getClaimNameSafe(Claim claim, ServerPlayerEntity player) {
        try {
            String name = claim.getClaimName();
            if (name != null) {
                name = name.replace(" " + player.getName().getString(), "");
                if (name.equals("Admin") || name.trim().isEmpty()) return "Arazi";
                return name.trim();
            }
        } catch (Throwable t) {
        }
        return "Arazi";
    }
}

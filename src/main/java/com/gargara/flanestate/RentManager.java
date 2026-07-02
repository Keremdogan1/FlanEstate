package com.gargara.flanestate;

import com.example.secretid.PlayerDataState;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

public class RentManager {

    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= 1200) { // Check every 1 minute (1200 ticks)
                tickCounter = 0;
                
                EstateState estateState = EstateState.getServerState(server);
                PlayerDataState playerData = PlayerDataState.getServerState(server);
                ClaimStorage claimStorage = ClaimStorage.get(server.getOverworld());
                
                long currentTime = server.getOverworld().getTimeOfDay();
                
                for (Map.Entry<UUID, EstateState.RentedProperty> entry : estateState.getRentedProperties().entrySet()) {
                    EstateState.RentedProperty prop = entry.getValue();
                    UUID claimUuid = entry.getKey();
                    
                    // Only process if the renter is online
                    ServerPlayerEntity renter = server.getPlayerManager().getPlayer(prop.renterUuid);
                    if (renter != null) {
                        // Check if an in-game day (24000 ticks) has passed since last payment
                        if (currentTime - prop.lastPaidTime >= 24000) {
                            
                            double balance = playerData.getBalance(prop.renterUuid);
                            Claim claim = claimStorage.getFromUUID(claimUuid);
                            
                            if (claim != null) {
                                if (balance >= prop.price) {
                                    // Pay rent
                                    if (playerData.removeBalance(prop.renterUuid, prop.price)) {
                                        playerData.addBalance(prop.ownerUuid, prop.price);
                                        prop.lastPaidTime = currentTime;
                                        estateState.markDirty();
                                        
                                        renter.sendMessage(Text.literal("§a[Emlak] Kiranız başarıyla ödendi: " + prop.price + " AK Lira."), false);

                                        // Unlock if it was locked
                                        if (prop.lockedOut) {
                                            prop.lockedOut = false;
                                            claim.setPlayerGroup(prop.renterUuid, "Co-Owner", true);
                                            renter.sendMessage(Text.literal("§a[Emlak] Evinizin kilidi açıldı!"), false);
                                            estateState.markDirty();
                                        }
                                    }
                                } else {
                                    // Lock out due to insufficient funds
                                    if (!prop.lockedOut) {
                                        prop.lockedOut = true;
                                        claim.setPlayerGroup(prop.renterUuid, null, true); // Remove from Co-Owner
                                        
                                        // Teleport out if they are inside the claim
                                        Claim currentClaim = ClaimStorage.get((net.minecraft.server.world.ServerWorld) renter.getWorld()).getClaimAt(renter.getBlockPos());
                                        if (currentClaim != null && currentClaim.getClaimID().equals(claim.getClaimID())) {
                                            if (prop.signPos != null) {
                                                renter.teleport((net.minecraft.server.world.ServerWorld) renter.getWorld(), prop.signPos.getX() + 0.5, prop.signPos.getY(), prop.signPos.getZ() + 0.5, renter.getYaw(), renter.getPitch());
                                                renter.sendMessage(Text.literal("§c[Emlak] Kirayı ödeyemediğiniz için evden tahliye edildiniz (Tabelaya Işınlandınız)!"), false);
                                            } else {
                                                net.minecraft.util.math.BlockPos spawnPos = ((net.minecraft.server.world.ServerWorld) renter.getWorld()).getSpawnPos();
                                                renter.teleport((net.minecraft.server.world.ServerWorld) renter.getWorld(), spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, renter.getYaw(), renter.getPitch());
                                                renter.sendMessage(Text.literal("§c[Emlak] Kirayı ödeyemediğiniz için evden tahliye edildiniz (Başlangıca Işınlandınız)!"), false);
                                            }
                                        } else {
                                            renter.sendMessage(Text.literal("§c[Emlak] Kirayı ödeyemediğiniz için evinize girişiniz kilitlendi!"), false);
                                        }
                                        estateState.markDirty();
                                    } else {
                                        renter.sendMessage(Text.literal("§c[Emlak] Kilitli evinizin kirası gecikti. Lütfen para yükleyin."), false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }
}

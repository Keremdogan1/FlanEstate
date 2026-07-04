package com.gargara.flanestate;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;

public class GuiHandler {

    public static void openEmlakGui(ServerPlayerEntity player) {
        SimpleGui gui = new SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false);
        gui.setTitle(Text.literal("§6Emlaklarım ve Kiralarım"));
        
        ClaimStorage storage = ClaimStorage.get(player.getServerWorld());
        Collection<Claim> ownedClaims = storage.allClaimsFromPlayer(player.getUuid());
        
        int slot = 0;
        
        // Sahip olunan mülkler
        for (Claim claim : ownedClaims) {
            if (slot >= 27) break; // İlk 3 satır sahip olunan mülkler
            
            String claimName = claim.getClaimName();
            if (claimName == null || claimName.isEmpty() || claimName.equals("Admin")) {
                claimName = "İsimsiz Arazi";
            } else {
                claimName = claimName.replace(" " + player.getName().getString(), "");
            }
            
            // Boyut
            int size = (claim.getDimensions().maxX() - claim.getDimensions().minX()) * (claim.getDimensions().maxZ() - claim.getDimensions().minZ());
            
            // Kira durumu kontrolü
            EstateState state = EstateState.getServerState(player.getServer());
            boolean isRented = false;
            double rentPrice = 0;
            String renterName = "Yok";
            
            for (java.util.Map.Entry<java.util.UUID, EstateState.RentedProperty> entry : state.getRentedProperties().entrySet()) {
                EstateState.RentedProperty p = entry.getValue();
                if (entry.getKey().equals(claim.getClaimID())) {
                    isRented = true;
                    rentPrice = p.price;
                    java.util.Optional<com.mojang.authlib.GameProfile> prof = player.getServer().getUserCache().getByUuid(p.renterUuid);
                    if (prof.isPresent()) renterName = prof.get().getName();
                    break;
                }
            }
            
            GuiElementBuilder element = new GuiElementBuilder(Items.OAK_DOOR)
                    .setName(Text.literal("§a[Mülk] §f" + claimName))
                    .addLoreLine(Text.literal("§7Konum: §f" + claim.getDimensions().minX() + ", " + claim.getDimensions().minZ()))
                    .addLoreLine(Text.literal("§7Boyut: §f" + size + " blok²"));
            
            if (isRented) {
                element.addLoreLine(Text.literal("§7Durum: §eKirada"));
                element.addLoreLine(Text.literal("§7Kiracı: §f" + renterName));
                element.addLoreLine(Text.literal("§7Kira Bedeli: §f" + rentPrice + " AK Lira"));
            } else {
                element.addLoreLine(Text.literal("§7Durum: §aBoşta / Sizin Kullanımınızda"));
            }
            
            gui.setSlot(slot++, element);
        }
        
        slot = 27; // 4. satırdan itibaren kiralanan mülkler
        
        EstateState state = EstateState.getServerState(player.getServer());
        for (java.util.Map.Entry<java.util.UUID, EstateState.RentedProperty> entry : state.getRentedProperties().entrySet()) {
            EstateState.RentedProperty p = entry.getValue();
            java.util.UUID claimId = entry.getKey();
            if (p.renterUuid.equals(player.getUuid())) {
                if (slot >= 54) break;
                
                Claim claim = storage.getFromUUID(claimId);
                if (claim == null) continue;
                
                String claimName = claim.getClaimName();
                if (claimName == null || claimName.isEmpty() || claimName.equals("Admin")) {
                    claimName = "İsimsiz Arazi";
                }
                
                int size = (claim.getDimensions().maxX() - claim.getDimensions().minX()) * (claim.getDimensions().maxZ() - claim.getDimensions().minZ());
                
                String ownerName = "Bilinmiyor";
                java.util.Optional<com.mojang.authlib.GameProfile> prof = player.getServer().getUserCache().getByUuid(p.ownerUuid);
                if (prof.isPresent()) ownerName = prof.get().getName();
                
                // Kalan süre
                long currentTime = player.getServerWorld().getTimeOfDay();
                long elapsedTime = currentTime - p.lastPaidTime;
                long ticksInDay = 24000;
                long remainingTicks = ticksInDay - elapsedTime;
                long remainingMins = (remainingTicks / 20) / 60;
                if (remainingMins < 0) remainingMins = 0;
                
                GuiElementBuilder element = new GuiElementBuilder(Items.IRON_DOOR)
                        .setName(Text.literal("§c[Kira] §f" + claimName))
                        .addLoreLine(Text.literal("§7Konum: §f" + claim.getDimensions().minX() + ", " + claim.getDimensions().minZ()))
                        .addLoreLine(Text.literal("§7Boyut: §f" + size + " blok²"))
                        .addLoreLine(Text.literal("§7Ev Sahibi: §f" + ownerName))
                        .addLoreLine(Text.literal("§7Kira Bedeli: §f" + p.price + " AK Lira"))
                        .addLoreLine(Text.literal("§7Sonraki Ödeme: §f" + remainingMins + " dk sonra"));
                
                gui.setSlot(slot++, element);
            }
        }
        
        gui.open();
    }

    public static void openNameGui(ServerPlayerEntity player, Claim claim) {
        AnvilInputGui gui = new AnvilInputGui(player, false);
        gui.setTitle(Text.literal("Mülk İsmi Belirle"));
        
        String currentName = claim.getClaimName();
        if (currentName != null) {
             currentName = currentName.replace(" " + player.getName().getString(), "");
             if (currentName.isEmpty() || currentName.equals("Admin")) currentName = "Arazi";
        } else {
             currentName = "Arazi";
        }
        
        gui.setDefaultInputValue(currentName);
        
        GuiElementBuilder confirmButton = new GuiElementBuilder(Items.NAME_TAG)
                .setName(Text.literal("§aOnayla"))
                .addLoreLine(Text.literal("§7İsmi kaydetmek için tıklayın."))
                .setCallback((index, type, action, guiObj) -> {
                    String newName = gui.getInput();
                    if (newName != null && !newName.trim().isEmpty()) {
                        claim.setClaimName(newName.trim());
                        player.sendMessage(Text.literal("§aMülk ismi başarıyla güncellendi: §f" + newName.trim()), false);
                        gui.close();
                    } else {
                        player.sendMessage(Text.literal("§cGeçersiz isim!"), false);
                    }
                });
        
        gui.setSlot(2, confirmButton);
        gui.open();
    }
}

package com.gargara.flanestate;

import com.example.secretid.PlayerDataState;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

public class SignEventHandler {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity)) {
                return ActionResult.PASS;
            }
            
            BlockPos pos = hitResult.getBlockPos();
            BlockEntity blockEntity = world.getBlockEntity(pos);
            
            if (blockEntity instanceof SignBlockEntity) {
                SignBlockEntity sign = (SignBlockEntity) blockEntity;
                String line1 = sign.getFrontText().getMessage(0, false).getString().trim();
                
                if (line1.equalsIgnoreCase("[Emlak]") || line1.equalsIgnoreCase("[FlanEstate]")) {
                    String type = sign.getFrontText().getMessage(1, false).getString().trim();
                    String priceStr = sign.getFrontText().getMessage(2, false).getString().trim();
                    String ownerName = sign.getFrontText().getMessage(3, false).getString().trim();
                    
                    if (type.equalsIgnoreCase("Satilik") || type.equalsIgnoreCase("Kiralik")) {
                        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
                        try {
                            double price = Double.parseDouble(priceStr);
                            
                            PlayerDataState state = PlayerDataState.getServerState(serverPlayer.getServer());
                            double balance = state.getBalance(serverPlayer.getUuid());
                            
                            ClaimStorage storage = ClaimStorage.get(serverPlayer.getServerWorld());
                            
                            // Check the block the sign is attached to if it's a wall sign
                            net.minecraft.block.BlockState blockState = world.getBlockState(pos);
                            BlockPos claimPos = pos;
                            if (blockState.getBlock() instanceof net.minecraft.block.WallSignBlock) {
                                claimPos = pos.offset(blockState.get(net.minecraft.block.WallSignBlock.FACING).getOpposite());
                            }
                            
                            Claim claim = storage.getClaimAt(claimPos);
                            
                            if (claim == null) {
                                serverPlayer.sendMessage(Text.literal("§cBu tabela bir koruma alanının (Claim) içinde değil!"), false);
                                return ActionResult.SUCCESS;
                            }
                            
                            java.util.Optional<com.mojang.authlib.GameProfile> profile = serverPlayer.getServer().getUserCache().findByName(ownerName);
                            if (profile.isEmpty()) {
                                serverPlayer.sendMessage(Text.literal("§cGeçersiz Ev Sahibi (Böyle bir oyuncu bulunamadı)!"), false);
                                return ActionResult.SUCCESS;
                            }
                            UUID ownerUuid = profile.get().getId();
                            
                            if (claim.getOwner().equals(serverPlayer.getUuid())) {
                                serverPlayer.sendMessage(Text.literal("§cKendi evini satın alamazsın!"), false);
                                return ActionResult.SUCCESS;
                            }
                            
                            if (type.equalsIgnoreCase("Satilik")) {
                                if (balance >= price) {
                                    if (state.removeBalance(serverPlayer.getUuid(), price)) {
                                        state.addBalance(ownerUuid, price);
                                        storage.transferOwner(claim, serverPlayer.getUuid());
                                        world.breakBlock(pos, false);
                                        serverPlayer.sendMessage(Text.literal("§aEv başarıyla satın alındı! " + price + " AK Lira kesildi."), false);
                                    }
                                } else {
                                    serverPlayer.sendMessage(Text.literal("§cYetersiz bakiye!"), false);
                                }
                            } else if (type.equalsIgnoreCase("Kiralik")) {
                                if (balance >= price) {
                                    if (state.removeBalance(serverPlayer.getUuid(), price)) {
                                        state.addBalance(ownerUuid, price);
                                        claim.setPlayerGroup(serverPlayer.getUuid(), "Co-Owner", true);
                                        EstateState.getServerState(serverPlayer.getServer()).addRentedProperty(claim.getClaimID(), serverPlayer.getUuid(), ownerUuid, price, world.getTimeOfDay(), pos);
                                        serverPlayer.sendMessage(Text.literal("§aEv başarıyla kiralandı! İlk kiranız (" + price + " AK Lira) kesildi."), false);
                                    }
                                } else {
                                    serverPlayer.sendMessage(Text.literal("§cYetersiz bakiye!"), false);
                                }
                            }
                            return ActionResult.SUCCESS;
                        } catch (Exception e) {
                            serverPlayer.sendMessage(Text.literal("§cHatalı Emlak Tabelası Formatı!"), false);
                        }
                    }
                }
            }
            return ActionResult.PASS;
        });
    }
}

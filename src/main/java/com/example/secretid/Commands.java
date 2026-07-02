package com.example.secretid;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.enums.ChestType;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class Commands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // /myid
        dispatcher.register(CommandManager.literal("myid")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                    String id = state.getSecretId(player.getUuid());
                    player.sendMessage(Text.literal("§aSenin gizli ID numaran: §e" + id), false);
                    return 1;
                }));

        // /balance
        dispatcher.register(CommandManager.literal("balance")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                    double bal = state.getBalance(player.getUuid());
                    player.sendMessage(Text.literal("§aMevcut bakiyen: §e" + bal), false);
                    return 1;
                }));

        // /pay <id> <amount>
        dispatcher.register(CommandManager.literal("pay")
                .then(CommandManager.argument("targetId", StringArgumentType.word())
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.1))
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    String targetId = StringArgumentType.getString(context, "targetId");
                                    double amount = DoubleArgumentType.getDouble(context, "amount");
                                    
                                    PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                                    String myId = state.getSecretId(player.getUuid());
                                    
                                    if (myId.equals(targetId)) {
                                        player.sendMessage(Text.literal("§cKendine para gonderemezsin!"), false);
                                        return 0;
                                    }
                                    
                                    UUID targetUuid = state.getUuidFromId(targetId);
                                    if (targetUuid == null) {
                                        player.sendMessage(Text.literal("§cBu ID'ye sahip bir oyuncu bulunamadi!"), false);
                                        return 0;
                                    }
                                    
                                    if (state.removeBalance(player.getUuid(), amount)) {
                                        state.addBalance(targetUuid, amount);
                                        player.sendMessage(Text.literal("§aBasariyla §e" + targetId + " §aID'li kisiye §e" + amount + " §agonderdin!"), false);
                                        
                                        ServerPlayerEntity targetPlayer = context.getSource().getServer().getPlayerManager().getPlayer(targetUuid);
                                        if (targetPlayer != null) {
                                            targetPlayer.sendMessage(Text.literal("§aBirisinden hesabina §e" + amount + " §ageldi!"), false);
                                        }
                                        return 1;
                                    } else {
                                        player.sendMessage(Text.literal("§cYetersiz bakiye!"), false);
                                        return 0;
                                    }
                                }))));

        // /setid <player> <new_id> (requires permission level 2)
        dispatcher.register(CommandManager.literal("setid")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .then(CommandManager.argument("newId", StringArgumentType.word())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "target");
                                    String newId = StringArgumentType.getString(context, "newId").toUpperCase();
                                    
                                    if (newId.length() != 6) {
                                        source.sendMessage(Text.literal("§cID tam olarak 6 haneli olmalidir!"));
                                        return 0;
                                    }
                                    
                                    PlayerDataState state = PlayerDataState.getServerState(source.getServer());
                                    
                                    if (state.setSecretId(targetPlayer.getUuid(), newId)) {
                                        source.sendMessage(Text.literal("§a" + targetPlayer.getName().getString() + " §aoyuncusunun yeni ID'si §e" + newId + " §aolarak ayarlandi."));
                                        targetPlayer.sendMessage(Text.literal("§aAdmin tarafindan ID'niz degistirildi. Yeni ID'niz: §e" + newId), false);
                                        return 1;
                                    } else {
                                        source.sendMessage(Text.literal("§cBu ID baska bir oyuncu tarafindan kullaniliyor!"));
                                        return 0;
                                    }
                                }))));

        // /setrole <id> <role> (requires permission level 2)
        dispatcher.register(CommandManager.literal("setrole")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("targetId", StringArgumentType.word())
                        .then(CommandManager.argument("role", StringArgumentType.word())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    String targetId = StringArgumentType.getString(context, "targetId");
                                    String roleStr = StringArgumentType.getString(context, "role").toUpperCase();
                                    
                                    PlayerDataState state = PlayerDataState.getServerState(source.getServer());
                                    UUID targetUuid = state.getUuidFromId(targetId);
                                    
                                    if (targetUuid == null) {
                                        source.sendMessage(Text.literal("§cBu ID'ye sahip bir oyuncu bulunamadi!"));
                                        return 0;
                                    }
                                    
                                    try {
                                        Role role = Role.valueOf(roleStr);
                                        state.setRole(targetUuid, role);
                                        source.sendMessage(Text.literal("§aID §e" + targetId + " §aicin rol basariyla §e" + role.name() + " §aolarak ayarlandi."));
                                        
                                        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(targetUuid);
                                        if (targetPlayer != null) {
                                            targetPlayer.sendMessage(Text.literal("§aRolun §e" + role.name() + " §aolarak degistirildi!"), false);
                                        }
                                        return 1;
                                    } catch (IllegalArgumentException e) {
                                        source.sendMessage(Text.literal("§cGecersiz rol! Gecerli roller: NONE, PRESIDENT, PRIME_MINISTER, MAYOR, MP"));
                                        return 0;
                                    }
                                }))));
                                
        // /govdata <my_secret_id> [date]
        dispatcher.register(CommandManager.literal("govdata")
                .then(CommandManager.argument("myid", StringArgumentType.word())
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            String inputId = StringArgumentType.getString(context, "myid");
                            
                            PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                            String realId = state.getSecretId(player.getUuid());
                            
                            if (realId == null || !realId.equals(inputId)) {
                                player.sendMessage(Text.literal("§cHATALI ID! Erisim reddedildi."), false);
                                return 0;
                            }
                            
                            Role role = state.getRole(player.getUuid());
                            if (role == Role.PRESIDENT || role == Role.PRIME_MINISTER) {
                                player.sendMessage(Text.literal("§b--- CUMHURBASKANLIGI VERI SISTEMI ---"), false);
                                player.sendMessage(Text.literal("§eBuluttan veri getiriliyor (En Guncel)... Lutfen bekleyin."), false);
                                
                                CloudDataFetcher.fetchData(player, null);
                                
                                return 1;
                            } else {
                                player.sendMessage(Text.literal("§cBu komutu kullanmak icin CUMHURBASKANI veya BASBAKAN olmalisiniz!"), false);
                                return 0;
                            }
                        })
                        .then(CommandManager.argument("date", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    String inputId = StringArgumentType.getString(context, "myid");
                                    String date = StringArgumentType.getString(context, "date");
                                    
                                    PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                                    String realId = state.getSecretId(player.getUuid());
                                    
                                    if (realId == null || !realId.equals(inputId)) {
                                        player.sendMessage(Text.literal("§cHATALI ID! Erisim reddedildi."), false);
                                        return 0;
                                    }
                                    
                                    Role role = state.getRole(player.getUuid());
                                    if (role == Role.PRESIDENT || role == Role.PRIME_MINISTER) {
                                        player.sendMessage(Text.literal("§b--- CUMHURBASKANLIGI VERI SISTEMI ---"), false);
                                        player.sendMessage(Text.literal("§eBuluttan " + date + " verisi getiriliyor... Lutfen bekleyin."), false);
                                        
                                        CloudDataFetcher.fetchData(player, date);
                                        
                                        return 1;
                                    } else {
                                        player.sendMessage(Text.literal("§cBu komutu kullanmak icin CUMHURBASKANI veya BASBAKAN olmalisiniz!"), false);
                                        return 0;
                                    }
                                }))));

        // /govexchangerate
        dispatcher.register(CommandManager.literal("govexchangerate")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    player.sendMessage(Text.literal("§b--- DOVIZ VE RISK PRIMI SISTEMI ---"), false);
                    player.sendMessage(Text.literal("§eBuluttan canli veriler aliniyor... Lutfen bekleyin."), false);
                    CloudDataFetcher.fetchExchangeRateData(player);
                    return 1;
                }));

        // /hazine
        dispatcher.register(CommandManager.literal("hazine")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                    Role role = state.getRole(player.getUuid());
                    
                    if (role == Role.PRESIDENT || role == Role.PRIME_MINISTER || context.getSource().hasPermissionLevel(2)) {
                        double tb = state.getTreasuryBalance();
                        player.sendMessage(Text.literal("§6--- DEVLET HAZINESI ---"), false);
                        player.sendMessage(Text.literal("§eToplam Bakiye: §a" + tb + " AK Lirasi"), false);
                        return 1;
                    } else {
                        player.sendMessage(Text.literal("§cBu komutu sadece Cumhurbaskani veya Basbakan kullanabilir!"), false);
                        return 0;
                    }
                })
                .then(CommandManager.literal("gonder")
                        .then(CommandManager.argument("targetId", StringArgumentType.word())
                                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                            PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                                            Role role = state.getRole(player.getUuid());
                                            
                                            if (role != Role.PRESIDENT && role != Role.PRIME_MINISTER && !context.getSource().hasPermissionLevel(2)) {
                                                player.sendMessage(Text.literal("§cYetkiniz yok!"), false);
                                                return 0;
                                            }
                                            
                                            String targetId = StringArgumentType.getString(context, "targetId");
                                            double amount = DoubleArgumentType.getDouble(context, "amount");
                                            
                                            UUID targetUuid = state.getUuidFromId(targetId);
                                            if (targetUuid == null) {
                                                player.sendMessage(Text.literal("§cBu ID'ye sahip bir oyuncu bulunamadi!"), false);
                                                return 0;
                                            }
                                            
                                            if (state.removeTreasuryBalance(amount)) {
                                                state.addBalance(targetUuid, amount);
                                                player.sendMessage(Text.literal("§aHazineden §e" + targetId + " §aID'li kisiye §e" + amount + " AK Lirasi §aaktarildi."), false);
                                                
                                                ServerPlayerEntity targetPlayer = context.getSource().getServer().getPlayerManager().getPlayer(targetUuid);
                                                if (targetPlayer != null) {
                                                    targetPlayer.sendMessage(Text.literal("§aDevlet Hazinesi tarafindan hesabina §e" + amount + " AK Lirasi §aaktarildi!"), false);
                                                }
                                                return 1;
                                            } else {
                                                player.sendMessage(Text.literal("§cHazinede yeterli bakiye yok!"), false);
                                                return 0;
                                            }
                                        }))))
                .then(CommandManager.literal("cek")
                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.1))
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                                    Role role = state.getRole(player.getUuid());
                                    
                                    if (role != Role.PRESIDENT && role != Role.PRIME_MINISTER && !context.getSource().hasPermissionLevel(2)) {
                                        player.sendMessage(Text.literal("§cYetkiniz yok!"), false);
                                        return 0;
                                    }
                                    
                                    double amount = DoubleArgumentType.getDouble(context, "amount");
                                    
                                    if (state.removeTreasuryBalance(amount)) {
                                        state.addBalance(player.getUuid(), amount);
                                        player.sendMessage(Text.literal("§aHazineden kendi hesabina §e" + amount + " AK Lirasi §acektin."), false);
                                        return 1;
                                    } else {
                                        player.sendMessage(Text.literal("§cHazinede yeterli bakiye yok!"), false);
                                        return 0;
                                    }
                                }))));

        // /vergi ekle <ad> <tutar> ve /vergi ode <ad> <tutar>
        dispatcher.register(CommandManager.literal("vergi")
                .then(CommandManager.literal("ekle")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                            PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                                            Role role = state.getRole(player.getUuid());
                                            
                                            if (role != Role.PRESIDENT && role != Role.PRIME_MINISTER && !context.getSource().hasPermissionLevel(2)) {
                                                player.sendMessage(Text.literal("§cYetkiniz yok!"), false);
                                                return 0;
                                            }
                                            
                                            String taxName = StringArgumentType.getString(context, "name");
                                            if (taxName.length() != 3) {
                                                player.sendMessage(Text.literal("§cVergi adi tam olarak 3 harfli olmalidir! (Orn: KDV)"), false);
                                                return 0;
                                            }
                                            
                                            double amount = DoubleArgumentType.getDouble(context, "amount");
                                            state.setGlobalTax(taxName, amount);
                                            player.sendMessage(Text.literal("§aYeni vergi eklendi/guncellendi: §e" + taxName.toUpperCase() + " - " + amount + " AK Lirasi"), false);
                                            
                                            context.getSource().getServer().getPlayerManager().broadcast(
                                                    Text.literal("§6[DEVLET DUYURUSU] §eYeni bir vergi yasa tasarisi onaylandi: §c" + taxName.toUpperCase() + " §eTutar: §a" + amount + " AK Lirasi"), false);
                                            
                                            return 1;
                                        }))))
                .then(CommandManager.literal("ode")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.1))
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                            String taxName = StringArgumentType.getString(context, "name");
                                            double amount = DoubleArgumentType.getDouble(context, "amount");
                                            
                                            PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                                            double debt = state.getDebtFor(player.getUuid(), taxName);
                                            
                                            if (debt <= 0) {
                                                player.sendMessage(Text.literal("§aBu vergi turu icin borcunuz bulunmamaktadir."), false);
                                                return 0;
                                            }
                                            
                                            if (state.payTax(player.getUuid(), taxName, amount)) {
                                                double newDebt = state.getDebtFor(player.getUuid(), taxName);
                                                player.sendMessage(Text.literal("§aVergi odemesi basarili. Odenen: §e" + amount + " AK Lirasi"), false);
                                                if (newDebt > 0) {
                                                    player.sendMessage(Text.literal("§eKalan borc: §c" + newDebt + " AK Lirasi"), false);
                                                } else {
                                                    player.sendMessage(Text.literal("§aBu vergi turundeki tum borcunuzu kapattiniz!"), false);
                                                }
                                                return 1;
                                            } else {
                                                player.sendMessage(Text.literal("§cBakiye yetersiz! Veya odenmek istenen tutar hatali."), false);
                                                return 0;
                                            }
                                        })))));
                                        
        // /borc
        dispatcher.register(CommandManager.literal("borc")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    PlayerDataState state = PlayerDataState.getServerState(context.getSource().getServer());
                    
                    Map<String, Double> debts = state.getAllDebts(player.getUuid());
                    
                    player.sendMessage(Text.literal("§b--- VERGI BORCLARINIZ ---"), false);
                    if (debts.isEmpty()) {
                        player.sendMessage(Text.literal("§aHicbir vergi borcunuz bulunmamaktadir. Tesekkurler!"), false);
                    } else {
                        double total = 0;
                        for (Map.Entry<String, Double> entry : debts.entrySet()) {
                            player.sendMessage(Text.literal("§c" + entry.getKey() + ": §e" + entry.getValue() + " AK Lirasi"), false);
                            total += entry.getValue();
                        }
                        player.sendMessage(Text.literal("§6Toplam Borc: §c" + total + " AK Lirasi"), false);
                    }
                    return 1;
                }));

        // /shop commands
        dispatcher.register(CommandManager.literal("shop")
                .then(CommandManager.literal("create")
                        .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(0.1))
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    double price = DoubleArgumentType.getDouble(context, "price");
                                    
                                    BlockPos targetPos = getLookedAtBlock(player);
                                    if (targetPos == null) {
                                        player.sendMessage(Text.literal("§cDukkan kurmak icin bir sandiga bakmalisiniz!"), false);
                                        return 0;
                                    }
                                    
                                    BlockState blockState = player.getWorld().getBlockState(targetPos);
                                    Block block = blockState.getBlock();
                                    if (!(block instanceof ChestBlock || block instanceof BarrelBlock)) {
                                        player.sendMessage(Text.literal("§cDukkan sadece Sandik (Chest) veya Varil (Barrel) olabilir!"), false);
                                        return 0;
                                    }
                                    
                                    if (block instanceof ChestBlock && blockState.contains(ChestBlock.CHEST_TYPE) && blockState.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                                        player.sendMessage(Text.literal("§cDukkan sandigi sadece tekli (single) sandik olabilir! Cift sandik kullanamazsiniz."), false);
                                        return 0;
                                    }
                                    
                                    PlayerDataState state = PlayerDataState.getServerState(player.getServer());
                                    String posKey = getPosKey(player.getWorld(), targetPos);
                                    
                                    if (state.getShop(posKey) != null) {
                                        player.sendMessage(Text.literal("§cBu sandik zaten bir dukkan!"), false);
                                        return 0;
                                    }
                                    
                                    state.addShop(posKey, player.getUuid(), player.getName().getString(), price);
                                    player.sendMessage(Text.literal("§aDukkaniniz basariyla olusturuldu! Fiyat: §e" + price + " AK Lirasi"), false);
                                    return 1;
                                })))
                .then(CommandManager.literal("remove")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            BlockPos targetPos = getLookedAtBlock(player);
                            if (targetPos == null) {
                                player.sendMessage(Text.literal("§cKaldirmak istediginiz dukkan sandigina bakmalisiniz!"), false);
                                return 0;
                            }
                            
                            PlayerDataState state = PlayerDataState.getServerState(player.getServer());
                            String posKey = getPosKey(player.getWorld(), targetPos);
                            PlayerDataState.ShopInfo shop = state.getShop(posKey);
                            
                            if (shop == null) {
                                player.sendMessage(Text.literal("§cBu sandik bir dukkan degil!"), false);
                                return 0;
                            }
                            
                            if (!shop.ownerUuid.equals(player.getUuid()) && !context.getSource().hasPermissionLevel(2)) {
                                player.sendMessage(Text.literal("§cBu dukkanin sahibi siz degilsiniz!"), false);
                                return 0;
                            }
                            
                            state.removeShop(posKey);
                            player.sendMessage(Text.literal("§aDukkan basariyla kaldirildi."), false);
                            return 1;
                        }))
                .then(CommandManager.literal("price")
                        .then(CommandManager.argument("newPrice", DoubleArgumentType.doubleArg(0.1))
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    double newPrice = DoubleArgumentType.getDouble(context, "newPrice");
                                    BlockPos targetPos = getLookedAtBlock(player);
                                    if (targetPos == null) {
                                        player.sendMessage(Text.literal("§cFiyatini guncellemek istediginiz dukkan sandigina bakmalisiniz!"), false);
                                        return 0;
                                    }
                                    
                                    PlayerDataState state = PlayerDataState.getServerState(player.getServer());
                                    String posKey = getPosKey(player.getWorld(), targetPos);
                                    PlayerDataState.ShopInfo shop = state.getShop(posKey);
                                    
                                    if (shop == null) {
                                        player.sendMessage(Text.literal("§cBu sandik bir dukkan degil!"), false);
                                        return 0;
                                    }
                                    
                                    if (!shop.ownerUuid.equals(player.getUuid()) && !context.getSource().hasPermissionLevel(2)) {
                                        player.sendMessage(Text.literal("§cBu dukkanin sahibi siz degilsiniz!"), false);
                                        return 0;
                                    }
                                    
                                    state.updateShopPrice(posKey, newPrice);
                                    player.sendMessage(Text.literal("§aDukkan fiyati basariyla guncellendi: §e" + newPrice + " AK Lirasi"), false);
                                    return 1;
                                }))));

        // /yasa commands
        dispatcher.register(CommandManager.literal("yasa")
                .then(CommandManager.literal("olustur")
                        .then(CommandManager.argument("secret_id", StringArgumentType.word())
                                .then(CommandManager.argument("baslik", StringArgumentType.string())
                                        .then(CommandManager.argument("icerik", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                                    PlayerDataState state = PlayerDataState.getServerState(player.getServer());
                                                    String providedId = StringArgumentType.getString(context, "secret_id");
                                                    
                                                    if (!providedId.equals(state.getSecretId(player.getUuid()))) {
                                                        player.sendMessage(Text.literal("§cGirdiğiniz gizli kimlik (ID) size ait değil! Sahtecilik engellendi."), false);
                                                        return 0;
                                                    }
                                                    
                                                    Role role = state.getRole(player.getUuid());
                                                    if (role != Role.PRIME_MINISTER && role != Role.MP) {
                                                        player.sendMessage(Text.literal("§cBu işlemi sadece Başbakan veya Milletvekili gerçekleştirebilir!"), false);
                                                        return 0;
                                                    }

                                                    String baslik = StringArgumentType.getString(context, "baslik");
                                                    String icerik = StringArgumentType.getString(context, "icerik");

                                                    LawManager.LawInfo newLaw = LawManager.getInstance().createLaw(baslik, icerik, providedId);
                                                    
                                                    player.getServer().getPlayerManager().broadcast(Text.literal("§6[MECLIS] §eYeni yasa tasarisi sunuldu! Yasa Kodu: §a" + newLaw.displayId + " §e- " + baslik), false);
                                                    return 1;
                                                })))))
                .then(CommandManager.literal("duzenle")
                        .then(CommandManager.argument("secret_id", StringArgumentType.word())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .then(CommandManager.argument("icerik", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                                    PlayerDataState state = PlayerDataState.getServerState(player.getServer());
                                                    String providedId = StringArgumentType.getString(context, "secret_id");
                                                    
                                                    if (!providedId.equals(state.getSecretId(player.getUuid()))) {
                                                        player.sendMessage(Text.literal("§cGirdiğiniz gizli kimlik (ID) size ait değil!"), false);
                                                        return 0;
                                                    }
                                                    
                                                    Role role = state.getRole(player.getUuid());
                                                    if (role != Role.PRIME_MINISTER) {
                                                        player.sendMessage(Text.literal("§cBu komutu sadece Başbakan kullanabilir!"), false);
                                                        return 0;
                                                    }

                                                    String id = StringArgumentType.getString(context, "id");
                                                    String icerik = StringArgumentType.getString(context, "icerik");
                                                    
                                                    LawManager.LawInfo law = LawManager.getInstance().getLaw(id);
                                                    if (law == null) {
                                                        player.sendMessage(Text.literal("§cGeçersiz yasa kodu!"), false);
                                                        return 0;
                                                    }
                                                    
                                                    if (law.status != LawManager.LawStatus.VOTING) {
                                                        player.sendMessage(Text.literal("§cSadece oylamada olan yasaları düzenleyebilirsiniz!"), false);
                                                        return 0;
                                                    }

                                                    law.description = icerik;
                                                    LawManager.getInstance().save();
                                                    player.sendMessage(Text.literal("§aYasa içeriği başarıyla güncellendi!"), false);
                                                    return 1;
                                                })))))
                .then(CommandManager.literal("oyla")
                        .then(CommandManager.argument("secret_id", StringArgumentType.word())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .then(CommandManager.argument("oy", StringArgumentType.word())
                                                .executes(context -> {
                                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                                    PlayerDataState state = PlayerDataState.getServerState(player.getServer());
                                                    String providedId = StringArgumentType.getString(context, "secret_id");
                                                    
                                                    if (!providedId.equals(state.getSecretId(player.getUuid()))) {
                                                        player.sendMessage(Text.literal("§cGirdiğiniz gizli kimlik (ID) size ait değil!"), false);
                                                        return 0;
                                                    }
                                                    
                                                    Role role = state.getRole(player.getUuid());
                                                    if (role != Role.PRIME_MINISTER && role != Role.MP) {
                                                        player.sendMessage(Text.literal("§cBu komutu sadece Başbakan veya Milletvekili kullanabilir!"), false);
                                                        return 0;
                                                    }

                                                    String id = StringArgumentType.getString(context, "id");
                                                    String oyStr = StringArgumentType.getString(context, "oy").toLowerCase();
                                                    
                                                    if (!oyStr.equals("evet") && !oyStr.equals("hayir")) {
                                                        player.sendMessage(Text.literal("§cOy sadece 'evet' veya 'hayir' olabilir!"), false);
                                                        return 0;
                                                    }

                                                    LawManager.LawInfo law = LawManager.getInstance().getLaw(id);
                                                    if (law == null) {
                                                        player.sendMessage(Text.literal("§cGeçersiz yasa kodu!"), false);
                                                        return 0;
                                                    }
                                                    
                                                    if (law.status != LawManager.LawStatus.VOTING) {
                                                        player.sendMessage(Text.literal("§cBu yasa oylamaya kapalıdır! Durum: " + law.status.name()), false);
                                                        return 0;
                                                    }

                                                    law.votes.put(providedId, oyStr.equals("evet"));
                                                    LawManager.getInstance().save();
                                                    
                                                    player.sendMessage(Text.literal("§aOy başarıyla kaydedildi! (Yasa: " + id + ", Oy: " + oyStr.toUpperCase() + ")"), false);
                                                    return 1;
                                                })))))
                .then(CommandManager.literal("onayla")
                        .then(CommandManager.argument("secret_id", StringArgumentType.word())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                            PlayerDataState state = PlayerDataState.getServerState(player.getServer());
                                            String providedId = StringArgumentType.getString(context, "secret_id");
                                            
                                            if (!providedId.equals(state.getSecretId(player.getUuid()))) {
                                                player.sendMessage(Text.literal("§cGirdiğiniz gizli kimlik (ID) size ait değil!"), false);
                                                return 0;
                                            }
                                            
                                            Role role = state.getRole(player.getUuid());
                                            if (role != Role.PRESIDENT) {
                                                player.sendMessage(Text.literal("§cBu komutu sadece Cumhurbaşkanı kullanabilir!"), false);
                                                return 0;
                                            }

                                            String id = StringArgumentType.getString(context, "id");
                                            LawManager.LawInfo law = LawManager.getInstance().getLaw(id);
                                            if (law == null) {
                                                player.sendMessage(Text.literal("§cGeçersiz yasa kodu!"), false);
                                                return 0;
                                            }
                                            
                                            if (law.status != LawManager.LawStatus.VOTING) {
                                                player.sendMessage(Text.literal("§cBu yasa oylamaya açık değil veya zaten karara bağlanmış!"), false);
                                                return 0;
                                            }

                                            law.status = LawManager.LawStatus.APPROVED;
                                            LawManager.getInstance().save();
                                            
                                            player.getServer().getPlayerManager().broadcast(Text.literal("§a[CUMHURBAŞKANLIĞI] §e" + id + " kodlu '" + law.title + "' yasa tasarısı §aONAYLANDI!"), false);
                                            return 1;
                                        }))))
                .then(CommandManager.literal("reddet")
                        .then(CommandManager.argument("secret_id", StringArgumentType.word())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                            PlayerDataState state = PlayerDataState.getServerState(player.getServer());
                                            String providedId = StringArgumentType.getString(context, "secret_id");
                                            
                                            if (!providedId.equals(state.getSecretId(player.getUuid()))) {
                                                player.sendMessage(Text.literal("§cGirdiğiniz gizli kimlik (ID) size ait değil!"), false);
                                                return 0;
                                            }
                                            
                                            Role role = state.getRole(player.getUuid());
                                            if (role != Role.PRESIDENT) {
                                                player.sendMessage(Text.literal("§cBu komutu sadece Cumhurbaşkanı kullanabilir!"), false);
                                                return 0;
                                            }

                                            String id = StringArgumentType.getString(context, "id");
                                            LawManager.LawInfo law = LawManager.getInstance().getLaw(id);
                                            if (law == null) {
                                                player.sendMessage(Text.literal("§cGeçersiz yasa kodu!"), false);
                                                return 0;
                                            }
                                            
                                            if (law.status != LawManager.LawStatus.VOTING) {
                                                player.sendMessage(Text.literal("§cBu yasa oylamaya açık değil veya zaten karara bağlanmış!"), false);
                                                return 0;
                                            }

                                            law.status = LawManager.LawStatus.REJECTED;
                                            LawManager.getInstance().save();
                                            
                                            player.getServer().getPlayerManager().broadcast(Text.literal("§c[CUMHURBAŞKANLIĞI] §e" + id + " kodlu '" + law.title + "' yasa tasarısı §cREDDEDİLDİ!"), false);
                                            return 1;
                                        }))))
                .then(CommandManager.literal("liste")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            Collection<LawManager.LawInfo> laws = LawManager.getInstance().getAllLaws();
                            if (laws.isEmpty()) {
                                player.sendMessage(Text.literal("§cHiç yasa bulunamadı."), false);
                                return 1;
                            }
                            player.sendMessage(Text.literal("§b--- YASALAR ---"), false);
                            for (LawManager.LawInfo law : laws) {
                                String color = law.status == LawManager.LawStatus.APPROVED ? "§a" : (law.status == LawManager.LawStatus.REJECTED ? "§c" : "§e");
                                player.sendMessage(Text.literal(color + "[" + law.displayId + "] " + law.title + " (" + law.status.name() + ")"), false);
                            }
                            return 1;
                        }))
                .then(CommandManager.literal("detay")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    String id = StringArgumentType.getString(context, "id");
                                    LawManager.LawInfo law = LawManager.getInstance().getLaw(id);
                                    
                                    if (law == null) {
                                        player.sendMessage(Text.literal("§cGeçersiz yasa kodu!"), false);
                                        return 0;
                                    }
                                    
                                    String color = law.status == LawManager.LawStatus.APPROVED ? "§a" : (law.status == LawManager.LawStatus.REJECTED ? "§c" : "§e");
                                    player.sendMessage(Text.literal("§b--- YASA DETAYI ---"), false);
                                    player.sendMessage(Text.literal("§3Kod: §e" + law.displayId), false);
                                    player.sendMessage(Text.literal("§3Başlık: §e" + law.title), false);
                                    player.sendMessage(Text.literal("§3Durum: " + color + law.status.name()), false);
                                    player.sendMessage(Text.literal("§3Sunan ID: §e" + law.creatorSecretId), false);
                                    player.sendMessage(Text.literal("§3İçerik: §f" + law.description), false);
                                    player.sendMessage(Text.literal("§3Oylar: §aEvet (" + law.getYesVotes() + ") §f- §cHayır (" + law.getNoVotes() + ")"), false);
                                    return 1;
                                }))));
    }
    
    private static BlockPos getLookedAtBlock(ServerPlayerEntity player) {
        HitResult hit = player.raycast(5.0, 0.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        return null;
    }
    
    public static String getPosKey(net.minecraft.world.World world, BlockPos pos) {
        return world.getRegistryKey().getValue().toString() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}

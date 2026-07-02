package com.example.secretid;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SecretIdMod implements ModInitializer {
	public static final String MOD_ID = "secretid";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Secret ID Mod is initializing...");

		// Init LawManager on server start
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			LawManager.init(server);
		});

		// Save LawManager on server stop
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (LawManager.getInstance() != null) {
				LawManager.getInstance().save();
			}
		});

		// Register Player Join Event
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			PlayerDataState state = PlayerDataState.getServerState(server);
			String secretId = state.getOrCreateSecretId(player.getUuid());
		});

		// Register Commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			Commands.register(dispatcher);
		});

		// Register Block Break Event to protect shops
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (world.isClient()) return true;

			PlayerDataState pState = PlayerDataState.getServerState(world.getServer());
			String posKey = Commands.getPosKey(world, pos);
			PlayerDataState.ShopInfo shop = pState.getShop(posKey);

			if (shop != null) {
				if (player.getUuid().equals(shop.ownerUuid) || player.hasPermissionLevel(2)) {
					pState.removeShop(posKey);
					player.sendMessage(Text.literal("§aDukkan sandigi kirildi, dukkan kaldirildi."), false);
					return true;
				} else {
					player.sendMessage(Text.literal("§cBu sandik bir dukkan ve sahibi siz degilsiniz! Kiramazsiniz."), false);
					return false;
				}
			}
			return true;
		});

		// Map to track last clicks for shop purchases
		Map<UUID, LastClickInfo> lastClicks = new HashMap<>();

		// Register Block Use Event for shop interaction
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient() || hand != Hand.MAIN_HAND || player.isSpectator()) {
				return ActionResult.PASS;
			}

			net.minecraft.util.math.BlockPos pos = hitResult.getBlockPos();
			String posKey = Commands.getPosKey(world, pos);
			PlayerDataState pState = PlayerDataState.getServerState(world.getServer());
			PlayerDataState.ShopInfo shop = pState.getShop(posKey);

			if (shop != null) {
				UUID buyerUuid = player.getUuid();
				
				// Owner can access normally
				if (buyerUuid.equals(shop.ownerUuid)) {
					return ActionResult.PASS;
				}

				// Buyer interacting
				BlockEntity blockEntity = world.getBlockEntity(pos);
				if (!(blockEntity instanceof Inventory inventory)) {
					return ActionResult.PASS; // Safety check
				}

				ItemStack targetStack = null;
				for (int i = 0; i < inventory.size(); i++) {
					ItemStack stack = inventory.getStack(i);
					if (!stack.isEmpty()) {
						targetStack = stack;
						break;
					}
				}

				if (targetStack == null) {
					player.sendMessage(Text.literal("§cBu dukkanin stogu tukenmis!"), false);
					return ActionResult.SUCCESS; // Cancel chest opening
				}

				long now = System.currentTimeMillis();
				LastClickInfo lastClick = lastClicks.get(buyerUuid);
				
				if (lastClick != null && lastClick.pos.equals(pos) && (now - lastClick.time) < 5000) {
					// Confirm buy!
					lastClicks.remove(buyerUuid);

					double balance = pState.getBalance(buyerUuid);
					if (balance < shop.price) {
						player.sendMessage(Text.literal("§cYetersiz bakiye! Gerekli: §e" + shop.price + " AK Lirasi§c, Senin bakiyen: §e" + balance + " AK Lirasi"), false);
						return ActionResult.SUCCESS;
					}

					ItemStack purchaseStack = targetStack.copy();
					purchaseStack.setCount(1);

					if (player instanceof ServerPlayerEntity serverPlayer) {
						if (serverPlayer.getInventory().insertStack(purchaseStack)) {
							// Success! Decrement inventory stack
							targetStack.decrement(1);
							inventory.markDirty();

							// Transfer money
							pState.removeBalance(buyerUuid, shop.price);
							pState.addBalance(shop.ownerUuid, shop.price);

							serverPlayer.sendMessage(Text.literal("§aBasariyla §e1x " + purchaseStack.getName().getString() + " §asatin aldin! Fiyat: §e" + shop.price + " AK Lirasi"), false);

							// Notify owner if online
							ServerPlayerEntity ownerPlayer = world.getServer().getPlayerManager().getPlayer(shop.ownerUuid);
							if (ownerPlayer != null) {
								ownerPlayer.sendMessage(Text.literal("§6[Dukkan] §e" + serverPlayer.getName().getString() + " §aadli oyuncu dukkaninizdan §e1x " + purchaseStack.getName().getString() + " §asatin aldi! Hesabiniza §e" + shop.price + " AK Lirasi §aaktarildi."), false);
							}
						} else {
							serverPlayer.sendMessage(Text.literal("§cEnvanteriniz dolu!"), false);
						}
					}
					return ActionResult.SUCCESS;
				} else {
					// First click
					lastClicks.put(buyerUuid, new LastClickInfo(pos, now));

					int stockCount = 0;
					String itemName = targetStack.getName().getString();
					for (int i = 0; i < inventory.size(); i++) {
						ItemStack stack = inventory.getStack(i);
						if (!stack.isEmpty() && stack.getName().getString().equals(itemName)) {
							stockCount += stack.getCount();
						}
					}

					player.sendMessage(Text.literal("§b--- DUKKAN BILGISI ---"), false);
					player.sendMessage(Text.literal("§aSahibi: §e" + shop.ownerName), false);
					player.sendMessage(Text.literal("§aUrun: §e" + itemName), false);
					player.sendMessage(Text.literal("§aFiyat: §e" + shop.price + " AK Lirasi"), false);
					player.sendMessage(Text.literal("§aStok: §e" + stockCount + " adet"), false);
					player.sendMessage(Text.literal("§6Satin almak icin 5 saniye icinde sandiga TEKRAR sag tiklayin!"), false);

					return ActionResult.SUCCESS;
				}
			}

			return ActionResult.PASS;
		});
	}

	private static class LastClickInfo {
		public final net.minecraft.util.math.BlockPos pos;
		public final long time;

		public LastClickInfo(net.minecraft.util.math.BlockPos pos, long time) {
			this.pos = pos;
			this.time = time;
		}
	}
}

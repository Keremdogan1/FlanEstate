package com.example.secretid;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class PlayerDataState extends PersistentState {

    public static class ShopInfo {
        public final UUID ownerUuid;
        public final String ownerName;
        public double price;

        public ShopInfo(UUID ownerUuid, String ownerName, double price) {
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.price = price;
        }
    }
    
    private final Map<UUID, String> uuidToSecretId = new HashMap<>();
    private final Map<String, UUID> secretIdToUuid = new HashMap<>();
    private final Map<UUID, Double> uuidToBalance = new HashMap<>();
    private final Map<UUID, Role> uuidToRole = new HashMap<>();
    
    private double treasuryBalance = 1000000.0;
    private final Map<String, Double> globalTaxes = new HashMap<>();
    private final Map<UUID, Map<String, Double>> playerTaxPayments = new HashMap<>();
    private final Map<String, ShopInfo> shops = new HashMap<>();
    
    private final Random random = new Random();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList playersList = new NbtList();
        
        for (UUID uuid : uuidToSecretId.keySet()) {
            NbtCompound playerCompound = new NbtCompound();
            playerCompound.putUuid("uuid", uuid);
            playerCompound.putString("secretId", uuidToSecretId.get(uuid));
            playerCompound.putDouble("balance", uuidToBalance.getOrDefault(uuid, 0.0));
            playerCompound.putString("role", uuidToRole.getOrDefault(uuid, Role.NONE).name());
            playersList.add(playerCompound);
        }
        
        nbt.put("players", playersList);
        
        NbtCompound taxesCompound = new NbtCompound();
        for (Map.Entry<String, Double> entry : globalTaxes.entrySet()) {
            taxesCompound.putDouble(entry.getKey(), entry.getValue());
        }
        nbt.put("globalTaxes", taxesCompound);
        
        NbtList paymentsList = new NbtList();
        for (Map.Entry<UUID, Map<String, Double>> playerEntry : playerTaxPayments.entrySet()) {
            NbtCompound pCompound = new NbtCompound();
            pCompound.putUuid("uuid", playerEntry.getKey());
            NbtCompound pTaxes = new NbtCompound();
            for (Map.Entry<String, Double> taxEntry : playerEntry.getValue().entrySet()) {
                pTaxes.putDouble(taxEntry.getKey(), taxEntry.getValue());
            }
            pCompound.put("payments", pTaxes);
            paymentsList.add(pCompound);
        }
        nbt.put("playerTaxPayments", paymentsList);
        
        nbt.putDouble("treasuryBalance", treasuryBalance);
        
        NbtCompound shopsCompound = new NbtCompound();
        for (Map.Entry<String, ShopInfo> entry : shops.entrySet()) {
            NbtCompound shopData = new NbtCompound();
            shopData.putUuid("ownerUuid", entry.getValue().ownerUuid);
            shopData.putString("ownerName", entry.getValue().ownerName);
            shopData.putDouble("price", entry.getValue().price);
            shopsCompound.put(entry.getKey(), shopData);
        }
        nbt.put("shops", shopsCompound);
        
        return nbt;
    }

    public static PlayerDataState createFromNbt(NbtCompound tag) {
        PlayerDataState state = new PlayerDataState();
        
        NbtList playersList = tag.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < playersList.size(); i++) {
            NbtCompound playerCompound = playersList.getCompound(i);
            UUID uuid = playerCompound.getUuid("uuid");
            String secretId = playerCompound.getString("secretId");
            double balance = playerCompound.getDouble("balance");
            Role role = Role.valueOf(playerCompound.getString("role"));
            
            state.uuidToSecretId.put(uuid, secretId);
            state.secretIdToUuid.put(secretId, uuid);
            state.uuidToBalance.put(uuid, balance);
            state.uuidToRole.put(uuid, role);
        }
        
        if (tag.contains("treasuryBalance")) {
            state.treasuryBalance = tag.getDouble("treasuryBalance");
        }
        
        if (tag.contains("globalTaxes")) {
            NbtCompound taxesCompound = tag.getCompound("globalTaxes");
            for (String key : taxesCompound.getKeys()) {
                state.globalTaxes.put(key, taxesCompound.getDouble(key));
            }
        }
        
        if (tag.contains("playerTaxPayments")) {
            NbtList paymentsList = tag.getList("playerTaxPayments", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < paymentsList.size(); i++) {
                NbtCompound pCompound = paymentsList.getCompound(i);
                UUID uuid = pCompound.getUuid("uuid");
                NbtCompound pTaxes = pCompound.getCompound("payments");
                Map<String, Double> payments = new HashMap<>();
                for (String key : pTaxes.getKeys()) {
                    payments.put(key, pTaxes.getDouble(key));
                }
                state.playerTaxPayments.put(uuid, payments);
            }
        }
        
        if (tag.contains("shops")) {
            NbtCompound shopsCompound = tag.getCompound("shops");
            for (String key : shopsCompound.getKeys()) {
                NbtCompound shopData = shopsCompound.getCompound(key);
                UUID ownerUuid = shopData.getUuid("ownerUuid");
                String ownerName = shopData.getString("ownerName");
                double price = shopData.getDouble("price");
                state.shops.put(key, new ShopInfo(ownerUuid, ownerName, price));
            }
        }
        
        return state;
    }

    public static PlayerDataState getServerState(MinecraftServer server) {
        PersistentStateManager persistentStateManager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        
        return persistentStateManager.getOrCreate(
                PlayerDataState::createFromNbt,
                PlayerDataState::new,
                "secret_id_player_data"
        );
    }
    
    public String getOrCreateSecretId(UUID uuid) {
        if (uuidToSecretId.containsKey(uuid)) {
            return uuidToSecretId.get(uuid);
        }
        
        String newId;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            newId = sb.toString();
        } while (secretIdToUuid.containsKey(newId));
        
        uuidToSecretId.put(uuid, newId);
        secretIdToUuid.put(newId, uuid);
        
        uuidToBalance.put(uuid, 0.0);
        uuidToRole.put(uuid, Role.NONE);
        
        this.markDirty();
        return newId;
    }
    
    public boolean setSecretId(UUID uuid, String newId) {
        if (secretIdToUuid.containsKey(newId)) {
            return false;
        }
        if (uuidToSecretId.containsKey(uuid)) {
            String oldId = uuidToSecretId.get(uuid);
            secretIdToUuid.remove(oldId);
        }
        uuidToSecretId.put(uuid, newId);
        secretIdToUuid.put(newId, uuid);
        this.markDirty();
        return true;
    }
    
    public String getSecretId(UUID uuid) { return uuidToSecretId.get(uuid); }
    public UUID getUuidFromId(String secretId) { return secretIdToUuid.get(secretId); }
    
    public double getBalance(UUID uuid) { return uuidToBalance.getOrDefault(uuid, 0.0); }
    public void setBalance(UUID uuid, double amount) { uuidToBalance.put(uuid, amount); this.markDirty(); }
    public boolean addBalance(UUID uuid, double amount) { setBalance(uuid, getBalance(uuid) + amount); return true; }
    public boolean removeBalance(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current >= amount) { setBalance(uuid, current - amount); return true; }
        return false;
    }
    
    public Role getRole(UUID uuid) { return uuidToRole.getOrDefault(uuid, Role.NONE); }
    public void setRole(UUID uuid, Role role) { uuidToRole.put(uuid, role); this.markDirty(); }

    public double getTreasuryBalance() { return treasuryBalance; }
    public void setTreasuryBalance(double amount) { this.treasuryBalance = amount; this.markDirty(); }
    public void addTreasuryBalance(double amount) { setTreasuryBalance(treasuryBalance + amount); }
    public boolean removeTreasuryBalance(double amount) {
        if (treasuryBalance >= amount) { setTreasuryBalance(treasuryBalance - amount); return true; }
        return false;
    }

    public void setGlobalTax(String name, double amount) {
        globalTaxes.put(name.toUpperCase(), amount);
        this.markDirty();
    }
    
    public Map<String, Double> getAllDebts(UUID uuid) {
        Map<String, Double> debts = new HashMap<>();
        Map<String, Double> payments = playerTaxPayments.getOrDefault(uuid, new HashMap<>());
        
        for (Map.Entry<String, Double> tax : globalTaxes.entrySet()) {
            double totalTax = tax.getValue();
            double paid = payments.getOrDefault(tax.getKey(), 0.0);
            if (totalTax > paid) {
                debts.put(tax.getKey(), totalTax - paid);
            }
        }
        return debts;
    }
    
    public double getDebtFor(UUID uuid, String taxName) {
        taxName = taxName.toUpperCase();
        if (!globalTaxes.containsKey(taxName)) return 0.0;
        
        double totalTax = globalTaxes.get(taxName);
        double paid = playerTaxPayments.getOrDefault(uuid, new HashMap<>()).getOrDefault(taxName, 0.0);
        return Math.max(0, totalTax - paid);
    }
    
    public boolean payTax(UUID uuid, String taxName, double amountToPay) {
        taxName = taxName.toUpperCase();
        double currentDebt = getDebtFor(uuid, taxName);
        
        if (currentDebt <= 0 || amountToPay <= 0) return false;
        
        if (amountToPay > currentDebt) {
            amountToPay = currentDebt;
        }
        
        if (removeBalance(uuid, amountToPay)) {
            addTreasuryBalance(amountToPay);
            
            playerTaxPayments.putIfAbsent(uuid, new HashMap<>());
            Map<String, Double> payments = playerTaxPayments.get(uuid);
            payments.put(taxName, payments.getOrDefault(taxName, 0.0) + amountToPay);
            
            this.markDirty();
            return true;
        }
        
        
        return false;
    }

    public Map<String, ShopInfo> getShops() {
        return shops;
    }

    public ShopInfo getShop(String posKey) {
        return shops.get(posKey);
    }

    public void addShop(String posKey, UUID ownerUuid, String ownerName, double price) {
        shops.put(posKey, new ShopInfo(ownerUuid, ownerName, price));
        this.markDirty();
    }

    public boolean removeShop(String posKey) {
        if (shops.remove(posKey) != null) {
            this.markDirty();
            return true;
        }
        return false;
    }
    
    public void updateShopPrice(String posKey, double newPrice) {
        ShopInfo info = shops.get(posKey);
        if (info != null) {
            info.price = newPrice;
            this.markDirty();
        }
    }
}

package com.gargara.flanestate;

import com.example.secretid.PlayerDataState;
import io.github.flemmli97.flan.claim.Claim;
import io.github.flemmli97.flan.claim.ClaimStorage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;

public class EstateState extends PersistentState {

    public static class RentedProperty {
        public UUID renterUuid;
        public UUID ownerUuid;
        public double price;
        public long lastPaidTime;
        public boolean lockedOut;
        public BlockPos signPos;

        public RentedProperty(UUID renterUuid, UUID ownerUuid, double price, long lastPaidTime, boolean lockedOut, BlockPos signPos) {
            this.renterUuid = renterUuid;
            this.ownerUuid = ownerUuid;
            this.price = price;
            this.lastPaidTime = lastPaidTime;
            this.lockedOut = lockedOut;
            this.signPos = signPos;
        }
    }

    private final Map<UUID, RentedProperty> rentedProperties = new HashMap<>();

    public void addRentedProperty(UUID claimUuid, UUID renterUuid, UUID ownerUuid, double price, long timeOfDay, BlockPos signPos) {
        rentedProperties.put(claimUuid, new RentedProperty(renterUuid, ownerUuid, price, timeOfDay, false, signPos));
        this.markDirty();
    }

    public void removeRentedProperty(UUID claimUuid) {
        rentedProperties.remove(claimUuid);
        this.markDirty();
    }

    public Map<UUID, RentedProperty> getRentedProperties() {
        return rentedProperties;
    }

    public static EstateState createFromNbt(NbtCompound tag) {
        EstateState state = new EstateState();
        NbtList list = tag.getList("RentedProperties", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound comp = list.getCompound(i);
            UUID claimUuid = comp.getUuid("ClaimUuid");
            UUID renterUuid = comp.getUuid("RenterUuid");
            UUID ownerUuid = comp.getUuid("OwnerUuid");
            double price = comp.getDouble("Price");
            long lastPaidTime = comp.getLong("LastPaidTime");
            boolean lockedOut = comp.getBoolean("LockedOut");
            BlockPos signPos = null;
            if (comp.contains("SignPosX")) {
                signPos = new BlockPos(comp.getInt("SignPosX"), comp.getInt("SignPosY"), comp.getInt("SignPosZ"));
            }
            state.rentedProperties.put(claimUuid, new RentedProperty(renterUuid, ownerUuid, price, lastPaidTime, lockedOut, signPos));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, RentedProperty> entry : rentedProperties.entrySet()) {
            NbtCompound comp = new NbtCompound();
            comp.putUuid("ClaimUuid", entry.getKey());
            comp.putUuid("RenterUuid", entry.getValue().renterUuid);
            comp.putUuid("OwnerUuid", entry.getValue().ownerUuid);
            comp.putDouble("Price", entry.getValue().price);
            comp.putLong("LastPaidTime", entry.getValue().lastPaidTime);
            comp.putBoolean("LockedOut", entry.getValue().lockedOut);
            if (entry.getValue().signPos != null) {
                comp.putInt("SignPosX", entry.getValue().signPos.getX());
                comp.putInt("SignPosY", entry.getValue().signPos.getY());
                comp.putInt("SignPosZ", entry.getValue().signPos.getZ());
            }
            list.add(comp);
        }
        tag.put("RentedProperties", list);
        return tag;
    }

    public static EstateState getServerState(MinecraftServer server) {
        PersistentStateManager stateManager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return stateManager.getOrCreate(EstateState::createFromNbt, EstateState::new, "flanestate_data");
    }
}

package refinedstorage.tile;

import io.netty.buffer.ByteBuf;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import refinedstorage.RefinedStorage;
import refinedstorage.inventory.InventorySimple;
import refinedstorage.network.MessagePriorityUpdate;
import refinedstorage.storage.IStorage;
import refinedstorage.storage.IStorageGui;
import refinedstorage.storage.IStorageProvider;
import refinedstorage.storage.StorageItem;
import refinedstorage.tile.settings.ICompareSetting;
import refinedstorage.tile.settings.IModeSetting;
import refinedstorage.tile.settings.IRedstoneModeSetting;
import refinedstorage.tile.settings.ModeSettingUtils;
import refinedstorage.util.InventoryUtils;

import java.util.List;

public class TileExternalStorage extends TileMachine implements IStorageProvider, IStorage, IStorageGui, ICompareSetting, IModeSetting {
    public static final String NBT_PRIORITY = "Priority";
    public static final String NBT_COMPARE = "Compare";
    public static final String NBT_MODE = "Mode";

    private InventorySimple inventory = new InventorySimple("external_storage", 9, this);

    private int priority = 0;
    private int compare = 0;
    private int mode = 0;

    @SideOnly(Side.CLIENT)
    private int stored = 0;

    @Override
    public int getEnergyUsage() {
        return 2;
    }

    @Override
    public void updateMachine() {
    }

    @Override
    public void addItems(List<StorageItem> items) {
        IInventory connectedInventory = getConnectedInventory();

        if (connectedInventory != null) {
            for (int i = 0; i < connectedInventory.getSizeInventory(); ++i) {
                if (connectedInventory.getStackInSlot(i) != null) {
                    items.add(new StorageItem(connectedInventory.getStackInSlot(i)));
                }
            }
        }
    }

    @Override
    public void push(ItemStack stack) {
        IInventory connectedInventory = getConnectedInventory();

        if (connectedInventory == null) {
            return;
        }

        InventoryUtils.pushToInventory(connectedInventory, stack);
    }

    @Override
    public ItemStack take(ItemStack stack, int flags) {
        IInventory connectedInventory = getConnectedInventory();

        if (connectedInventory == null) {
            return null;
        }

        int quantity = stack.stackSize;

        for (int i = 0; i < connectedInventory.getSizeInventory(); ++i) {
            ItemStack slot = connectedInventory.getStackInSlot(i);

            if (slot != null && InventoryUtils.compareStack(slot, stack, flags)) {
                if (quantity > slot.stackSize) {
                    quantity = slot.stackSize;
                }

                slot.stackSize -= quantity;

                if (slot.stackSize == 0) {
                    connectedInventory.setInventorySlotContents(i, null);
                }

                ItemStack newItem = slot.copy();

                newItem.stackSize = quantity;

                return newItem;
            }
        }

        return null;
    }

    @Override
    public boolean canPush(ItemStack stack) {
        IInventory connectedInventory = getConnectedInventory();

        if (connectedInventory == null) {
            return false;
        }

        return ModeSettingUtils.doesNotViolateMode(inventory, this, compare, stack) && InventoryUtils.canPushToInventory(connectedInventory, stack);
    }

    public IInventory getConnectedInventory() {
        TileEntity tile = worldObj.getTileEntity(pos.offset(getDirection()));

        if (tile instanceof IInventory) {
            return (IInventory) tile;
        }

        return null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);

        buf.writeInt(priority);
        buf.writeInt(getConnectedInventory() == null ? 0 : InventoryUtils.getInventoryItems(getConnectedInventory()));
        buf.writeInt(compare);
        buf.writeInt(mode);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        super.fromBytes(buf);

        priority = buf.readInt();
        stored = buf.readInt();
        compare = buf.readInt();
        mode = buf.readInt();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        InventoryUtils.restoreInventory(inventory, 0, nbt);

        if (nbt.hasKey(NBT_PRIORITY)) {
            priority = nbt.getInteger(NBT_PRIORITY);
        }

        if (nbt.hasKey(NBT_COMPARE)) {
            compare = nbt.getInteger(NBT_COMPARE);
        }

        if (nbt.hasKey(NBT_MODE)) {
            mode = nbt.getInteger(NBT_MODE);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);

        InventoryUtils.saveInventory(inventory, 0, nbt);

        nbt.setInteger(NBT_PRIORITY, priority);
        nbt.setInteger(NBT_COMPARE, compare);
        nbt.setInteger(NBT_MODE, mode);
    }

    @Override
    public int getCompare() {
        return compare;
    }

    @Override
    public void setCompare(int compare) {
        markDirty();

        this.compare = compare;
    }

    @Override
    public boolean isWhitelist() {
        return mode == 0;
    }

    @Override
    public boolean isBlacklist() {
        return mode == 1;
    }

    @Override
    public void setToWhitelist() {
        markDirty();

        this.mode = 0;
    }

    @Override
    public void setToBlacklist() {
        markDirty();

        this.mode = 1;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        markDirty();

        this.priority = priority;
    }

    @Override
    public void addStorages(List<IStorage> storages) {
        storages.add(this);
    }

    @Override
    public String getName() {
        return "gui.refinedstorage:external_storage";
    }

    @Override
    public IRedstoneModeSetting getRedstoneModeSetting() {
        return this;
    }

    @Override
    public ICompareSetting getCompareSetting() {
        return this;
    }

    @Override
    public IModeSetting getModeSetting() {
        return this;
    }

    @Override
    public int getStored() {
        return stored;
    }

    @Override
    public int getCapacity() {
        if (getConnectedInventory() == null) {
            return 0;
        }

        return getConnectedInventory().getSizeInventory() * 64;
    }

    @Override
    public void onPriorityChanged(int priority) {
        RefinedStorage.NETWORK.sendToServer(new MessagePriorityUpdate(pos, priority));
    }

    @Override
    public IInventory getInventory() {
        return inventory;
    }
}

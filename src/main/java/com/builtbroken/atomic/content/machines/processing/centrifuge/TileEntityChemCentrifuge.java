package com.builtbroken.atomic.content.machines.processing.centrifuge;

import com.builtbroken.atomic.AtomicScience;
import com.builtbroken.atomic.client.EffectRefs;
import com.builtbroken.atomic.config.content.ConfigContent;
import com.builtbroken.atomic.content.items.wrench.WrenchColor;
import com.builtbroken.atomic.content.items.wrench.WrenchMode;
import com.builtbroken.atomic.content.machines.processing.ProcessorRecipeHandler;
import com.builtbroken.atomic.content.machines.processing.TileEntityProcessingMachine;
import com.builtbroken.atomic.content.machines.processing.centrifuge.gui.ContainerChemCentrifuge;
import com.builtbroken.atomic.content.machines.processing.centrifuge.gui.GuiChemCentrifuge;
import com.builtbroken.atomic.content.machines.processing.centrifuge.inventory.InventoryChemCentrifuge;
import com.builtbroken.atomic.content.machines.processing.centrifuge.inventory.PipeInventoryChemCentrifuge;
import com.builtbroken.atomic.content.recipes.ProcessingRecipeList;
import com.builtbroken.atomic.content.recipes.chem.RecipeChemCentrifuge;
import com.builtbroken.atomic.lib.SideSettings;
import com.builtbroken.atomic.lib.fluid.FluidSideWrapper;
import com.builtbroken.atomic.lib.gui.IGuiTile;
import com.builtbroken.atomic.network.netty.PacketSystem;
import com.builtbroken.atomic.network.packet.client.PacketSpawnParticle;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @see <a href="https://github.com/BuiltBrokenModding/VoltzEngine/blob/development/license.md">License</a> for what you can and can't do with the code.
 * Created by Dark(DarkGuardsman, Robert) on 5/23/2018.
 */
public class TileEntityChemCentrifuge extends TileEntityProcessingMachine<IItemHandlerModifiable, TileEntityChemCentrifuge, RecipeChemCentrifuge> implements IGuiTile
{
    public static final int SLOT_FLUID_INPUT = 0;
    public static final int SLOT_ITEM_OUTPUT = 1;
    public static final int SLOT_BATTERY = 2;
    public static final int SLOT_FLUID_OUTPUT = 3;
    public static final int INVENTORY_SIZE = 4;

    public static int PROCESSING_TIME = 100;

    private final FluidTank inputTank;
    private final FluidTank outputTank;

    private final SideSettings inputTankSideSettings = new SideSettings(true);
    private final SideSettings outputTankSideSettings = new SideSettings(false);

    private final FluidSideWrapper[] fluidSideWrappers = new FluidSideWrapper[6];

    public TileEntityChemCentrifuge()
    {
        inputTank = new FluidTank(Fluid.BUCKET_VOLUME * 10); //TODO expose only one tank per side (output, input, none per side)
        outputTank = new FluidTank(Fluid.BUCKET_VOLUME * 10);

        for (EnumFacing side : EnumFacing.values())
        {
            fluidSideWrappers[side.ordinal()] = new FluidSideWrapper(side);
            fluidSideWrappers[side.ordinal()].add(inputTankSideSettings, inputTank, false);  //TODO switch to map of all tanks for easier access
            fluidSideWrappers[side.ordinal()].add(outputTankSideSettings, outputTank, true);
        }
    }

    @Override
    public void update(int ticks, boolean isClient)
    {
        super.update(ticks, isClient);
        if (!isClient)
        {
            drainBattery(SLOT_BATTERY);
        }
    }

    @Override
    protected IItemHandlerModifiable createInventory()
    {
        return new PipeInventoryChemCentrifuge(this);
    }

    @Override
    protected IItemHandlerModifiable createInternalInventory()
    {
        return new InventoryChemCentrifuge(this);
    }

    @Override
    protected int inventorySize()
    {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing)
    {
        if (facing != null && capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) //TODO if facing is null return group container
        {
            return fluidSideWrappers[facing.ordinal()].hasTank();
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing)
    {
        if (facing != null && capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
        {
            return (T) fluidSideWrappers[facing.ordinal()];
        }
        return super.getCapability(capability, facing);
    }

    @Override
    protected void onProcessed()
    {
        if (isServer())
        {
            PacketSpawnParticle packetSpawnParticle = new PacketSpawnParticle(world.provider.getDimension(),
                    xi() + 0.5, yi() + 0.5, zi() + 0.5,
                    0, 0, 0,
                    EffectRefs.CENTRIFUGE_COMPLETE);
            PacketSystem.INSTANCE.sendToAllAround(packetSpawnParticle, world, this, 30);
        }
    }

    @Override
    protected void doEffects(int ticks)
    {
        if (world.rand.nextFloat() > 0.4)
        {
            AtomicScience.sideProxy.spawnParticle(EffectRefs.CENTRIFUGE_RUNNING, xi() + 0.5, yi() + 0.5, zi() + 0.5, 0, 0, 0);
        }
    }

    @Override
    protected void preProcess(int ticks)
    {
        fillTank(SLOT_FLUID_INPUT, getInputTank());
    }

    @Override
    protected int getProcessingTime()
    {
        return PROCESSING_TIME;
    }

    @Override
    protected void postProcess(int ticks)
    {
        outputFluids(SLOT_FLUID_OUTPUT, getOutputTank());
        outputFluidToTiles(getOutputTank(), f -> outputTankSideSettings.get(f));
    }

    @Override
    protected ProcessingRecipeList getRecipeList()
    {
        return ProcessorRecipeHandler.INSTANCE.chemCentrifugeProcessingRecipe;
    }

    @Override
    public void onWrench(WrenchMode type, WrenchColor color, EnumFacing side, EntityPlayer player)
    {
        if (type == WrenchMode.FLUID && side != null)
        {
            if (color == WrenchColor.GREEN)
            {
                outputTankSideSettings.toggle(side);
                player.sendMessage(new TextComponentString(outputTankSideSettings.get(side) ? "Green tank set to output on side" : "Green tank set to ignore side")); //TODO translation
            }
            else if (color == WrenchColor.BLUE)
            {
                inputTankSideSettings.toggle(side);
                player.sendMessage(new TextComponentString(inputTankSideSettings.get(side) ? "Blue tank set to input on side" : "Blue tank set to ignore side")); //TODO translation
            }
        }
    }

    //-----------------------------------------------
    //--------Fluid Tank Handling -------------------
    //-----------------------------------------------

    public FluidTank getInputTank()
    {
        return inputTank;
    }

    public FluidTank getOutputTank()
    {
        return outputTank;
    }

    //-----------------------------------------------
    //--------Props ---------------------------------
    //-----------------------------------------------

    @Override
    public int getEnergyUsage()
    {
        return ConfigContent.POWER_USAGE.CENTRIFUGE;
    }

    //-----------------------------------------------
    //--------GUI Handler ---------------------------
    //-----------------------------------------------

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player)
    {
        return new ContainerChemCentrifuge(player, this);
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player)
    {
        return new GuiChemCentrifuge(player, this);
    }

    @Override
    protected void writeGuiPacket(List<Object> dataList, EntityPlayer player)
    {
        super.writeGuiPacket(dataList, player);
        dataList.add(getInputTank());
        dataList.add(getOutputTank());
    }

    @Override
    protected void readGuiPacket(ByteBuf buf, EntityPlayer player)
    {
        super.readGuiPacket(buf, player);
        getInputTank().readFromNBT(ByteBufUtils.readTag(buf));
        getOutputTank().readFromNBT(ByteBufUtils.readTag(buf));
    }

    //-----------------------------------------------
    //--------Save/Load -----------------------------
    //-----------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt)
    {
        nbt.setTag("outputTank", getOutputTank().writeToNBT(new NBTTagCompound()));
        nbt.setTag("inputTank", getInputTank().writeToNBT(new NBTTagCompound()));

        nbt.setTag("outputTankSides", outputTankSideSettings.save(new NBTTagCompound()));
        nbt.setTag("inputTankSides", inputTankSideSettings.save(new NBTTagCompound()));

        return super.writeToNBT(nbt);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt)
    {
        super.readFromNBT(nbt);
        getOutputTank().readFromNBT(nbt.getCompoundTag("outputTank"));
        getInputTank().readFromNBT(nbt.getCompoundTag("inputTank"));

        outputTankSideSettings.load(nbt.getCompoundTag("outputTankSides"));
        inputTankSideSettings.load(nbt.getCompoundTag("inputTankSides"));
    }
}

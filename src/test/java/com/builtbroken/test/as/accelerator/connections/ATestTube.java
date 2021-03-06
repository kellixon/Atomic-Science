package com.builtbroken.test.as.accelerator.connections;

import com.builtbroken.atomic.content.machines.accelerator.tube.TileEntityAcceleratorTube;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

/**
 * Created by Dark(DarkGuardsman, Robert) on 2019-04-17.
 */
public class ATestTube extends TileEntityAcceleratorTube
{
    public final TileEntity[] tiles = new TileEntity[6];

    @Override //Override normal access so we can force it to return what we want
    public TileEntity getTileEntity(EnumFacing side)
    {
        return tiles[side.ordinal()];
    }
}

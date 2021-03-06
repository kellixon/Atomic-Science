package com.builtbroken.atomic.content.machines.accelerator.gun;

import com.builtbroken.atomic.content.machines.accelerator.graph.AcceleratorHandler;
import com.builtbroken.atomic.content.machines.accelerator.graph.AcceleratorNetwork;
import com.builtbroken.atomic.content.machines.accelerator.graph.AcceleratorNode;
import com.builtbroken.atomic.content.machines.accelerator.data.TubeConnectionType;
import com.builtbroken.atomic.content.machines.accelerator.tube.TileEntityAcceleratorTube;
import com.builtbroken.atomic.content.machines.container.TileEntityItemContainer;
import com.builtbroken.atomic.content.machines.laser.emitter.TileEntityLaserEmitter;
import com.builtbroken.atomic.content.prefab.TileEntityMachine;
import com.builtbroken.atomic.lib.timer.TickTimerTileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

/**
 * @see <a href="https://github.com/BuiltBrokenModding/VoltzEngine/blob/development/license.md">License</a> for what you can and can't do with the code.
 * Created by Dark(DarkGuardsman, Robert) on 12/15/2018.
 */
public class TileEntityAcceleratorGun extends TileEntityMachine
{

    private AcceleratorNetwork network;

    //Fake node for particle to place inside
    private AcceleratorNode fakeNode;

    public TileEntityAcceleratorGun()
    {
        tickServer.add(TickTimerTileEntity.newConditional(20, (tick) -> validateNetwork(), () -> getNetwork() == null || getNetwork().nodes.isEmpty()));
    }

    private void validateNetwork()
    {
        //Setup a fake node for connecting to the network and providing the particle a place to spawn
        if (fakeNode == null)
        {
            fakeNode = new AcceleratorNode(getPos(), getDirection(), TubeConnectionType.NORMAL);
        }

        //If we have no network try to locate a tube with a network
        if (getNetwork() == null)
        {
            final EnumFacing facing = getDirection();

            final TileEntity tileEntity = world.getTileEntity(getPos().offset(facing));
            if (tileEntity instanceof TileEntityAcceleratorTube)
            {
                //Set network
                setNetwork(((TileEntityAcceleratorTube) tileEntity).acceleratorNode.getNetwork());

                //Connect to fake node
                ((TileEntityAcceleratorTube) tileEntity).acceleratorNode.connect(fakeNode, getDirection().getOpposite());

                //If network null create
                if (getNetwork() == null)
                {
                    setNetwork(new AcceleratorNetwork());
                    getNetwork().connect(((TileEntityAcceleratorTube) tileEntity).acceleratorNode);
                }

                //Link to network
                if (getNetwork() != null)
                {
                    getNetwork().guns.add(this);
                    getNetwork().connect(fakeNode);
                }
            }
        }

        //Find tubes
        if (getNetwork() != null)
        {
            getNetwork().path(getWorld(), getPos().offset(getDirection()));
        }
    }

    /**
     * Called when laser fires through a container into the gun
     *
     * @param container    - container holding an item
     * @param laserEmitter - laser that fired, used to get starting energy
     */
    public void onLaserFiredInto(TileEntityItemContainer container, TileEntityLaserEmitter laserEmitter)
    {
        final ItemStack heldItem = container.getHeldItem();
        if (!heldItem.isEmpty())
        {
            createParticle(heldItem, laserEmitter.boosterCount / container.consumeItems()); //TODO figure out how we are going to do energy
        }
    }

    /**
     * Creates a new particle at the gun tip fired into the system
     *
     * @param item          - item to use
     * @param energyToStart - energy to start with
     */
    public void createParticle(ItemStack item, int energyToStart)
    {
        AcceleratorHandler.newParticle(world, fakeNode, item, energyToStart);
    }

    /**
     * Called from remote system to trigger laser
     */
    public boolean fireLaser()
    {
        TileEntityLaserEmitter laser = getLaser();
        if (laser != null)
        {
            return laser.triggerFire();
        }
        return false;
    }

    /**
     * Gets the laser behind the gun tip
     *
     * @return
     */
    public TileEntityLaserEmitter getLaser()
    {
        final EnumFacing facing = getDirection().getOpposite();

        final TileEntity tileEntity = world.getTileEntity(getPos().offset(facing, 2));
        if (tileEntity instanceof TileEntityLaserEmitter)
        {
            TileEntityLaserEmitter laser = (TileEntityLaserEmitter) tileEntity;
            if (laser.getDirection() == getDirection())
            {
                return laser;
            }
        }
        return null;
    }

    public AcceleratorNetwork getNetwork()
    {
        return network;
    }

    public void setNetwork(AcceleratorNetwork network)
    {
        this.network = network;
    }
}

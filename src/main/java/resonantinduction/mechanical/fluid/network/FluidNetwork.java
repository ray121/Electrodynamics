package resonantinduction.mechanical.fluid.network;

import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import resonantinduction.api.fluid.IFluidNetwork;
import resonantinduction.api.fluid.IFluidPart;
import universalelectricity.api.net.IConnector;
import universalelectricity.core.net.ConnectionPathfinder;
import universalelectricity.core.net.Network;
import universalelectricity.core.net.NetworkTickHandler;
import calclavia.lib.utility.FluidUtility;

public class FluidNetwork extends Network<IFluidNetwork, IFluidPart, IFluidHandler> implements IFluidNetwork
{
    protected FluidTank tank = new FluidTank(0);
    protected final FluidTankInfo[] tankInfo = new FluidTankInfo[1];
    protected boolean reloadTanks = false;
    protected long ticks = 0;

    public FluidNetwork()
    {
        NetworkTickHandler.addNetwork(this);
    }

    @Override
    public void reconstruct()
    {
        if (this.reloadTanks)
        {
            this.reloadTanks();
        }
        this.tank = new FluidTank(0);
        for (IFluidPart part : this.getConnectors())
        {
            part.setNetwork(this);
            this.buildPart(part);
        }
        this.rebuildTank();
        this.reloadTanks();
    }

    public void buildPart(IFluidPart part)
    {
        FluidTank tank = part.getInternalTank();
        if (tank != null)
        {
            this.tank.setCapacity(this.tank.getCapacity() + tank.getCapacity());
            if (this.tank.getFluid() == null)
            {
                this.tank.setFluid(tank.getFluid());
            }
            else if (this.tank.getFluid().isFluidEqual(tank.getFluid()))
            {
                this.tank.getFluid().amount += tank.getFluidAmount();
            }
            else if (this.tank.getFluid() != null)
            {
                //TODO cause a mixing event
            }
        }
    }

    public void rebuildTank()
    {
        if (this.getTank() != null)
        {
            this.tankInfo[0] = this.getTank().getInfo();
        }
        else
        {
            this.tankInfo[0] = null;
        }
        this.reloadTanks = true;
        NetworkTickHandler.addNetwork(this);
    }

    @Override
    public int fill(IFluidPart source, ForgeDirection from, FluidStack resource, boolean doFill)
    {
        int prev = this.getTank().getFluidAmount();
        int fill = this.getTank().fill(resource, doFill);
        if (prev != this.getTank().getFluidAmount())
        {
            this.rebuildTank();
        }
        return fill;
    }

    @Override
    public FluidStack drain(IFluidPart source, ForgeDirection from, FluidStack resource, boolean doDrain)
    {
        if (resource != null && resource.isFluidEqual(this.getTank().getFluid()))
        {
            FluidStack before = this.getTank().getFluid();
            FluidStack drain = this.getTank().drain(resource.amount, doDrain);
            if (before != this.getTank().getFluid() || this.getTank().getFluid() == null || this.getTank().getFluid().amount != before.amount)
            {
                this.rebuildTank();
            }

            return drain;
        }
        return null;
    }

    @Override
    public FluidStack drain(IFluidPart source, ForgeDirection from, int resource, boolean doDrain)
    {
        if (this.getTank().getFluid() != null)
        {
            return this.drain(source, from, FluidUtility.getStack(this.getTank().getFluid(), resource), doDrain);
        }
        return null;
    }

    @Override
    public boolean canUpdate()
    {
        return this.reloadTanks;
    }

    @Override
    public boolean continueUpdate()
    {
        return this.reloadTanks;
    }

    @Override
    public void update()
    {
        this.ticks++;
        if (ticks >= Long.MAX_VALUE - 10)
        {
            ticks = 1;
        }
        if (this.reloadTanks && ticks % 10 == 0)
        {
            this.reloadTanks = false;
            this.reloadTanks();
        }
    }

    public void reloadTanks()
    {
        this.reloadTanks = false;
        FluidStack stack = this.getTank().getFluid();

        if (stack != null)
        {
            stack = stack.copy();
        }
        int parts = this.getConnectors().size();
        for (IFluidPart part : this.getConnectors())
        {

            part.getInternalTank().setFluid(null);
            if (stack != null)
            {
                int fillPer = stack.amount / parts;
                part.getInternalTank().fill(FluidUtility.getStack(stack, fillPer), true);
                part.onFluidChanged();
                if (parts > 1)
                    parts--;
            }
        }
    }

    @Override
    public IFluidNetwork merge(IFluidNetwork network)
    {
        FluidNetwork newNetwork = null;
        if (network != null && network.getClass().isAssignableFrom(this.getClass()) && network != this)
        {

            try
            {
                newNetwork = this.getClass().newInstance();

                newNetwork.getConnectors().addAll(this.getConnectors());
                newNetwork.getConnectors().addAll(network.getConnectors());

                network.getConnectors().clear();
                network.getNodes().clear();
                this.getConnectors().clear();
                this.getNodes().clear();

                newNetwork.reconstruct();

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

        }
        return newNetwork;
    }

    @Override
    public void split(IFluidPart splitPoint)
    {
        this.removeConnector(splitPoint);
        this.reconstruct();

        /** Loop through the connected blocks and attempt to see if there are connections between the
         * two points elsewhere. */
        Object[] connectedBlocks = splitPoint.getConnections();

        for (int i = 0; i < connectedBlocks.length; i++)
        {
            Object connectedBlockA = connectedBlocks[i];

            if (connectedBlockA instanceof IFluidPart)
            {
                for (int ii = 0; ii < connectedBlocks.length; ii++)
                {
                    final Object connectedBlockB = connectedBlocks[ii];

                    if (connectedBlockA != connectedBlockB && connectedBlockB instanceof IFluidPart)
                    {
                        ConnectionPathfinder finder = new ConnectionPathfinder((IFluidPart) connectedBlockB, splitPoint);
                        finder.findNodes((IFluidPart) connectedBlockA);

                        if (finder.results.size() <= 0)
                        {
                            try
                            {
                                /** The connections A and B are not connected anymore. Give them both
                                 * a new common network. */
                                IFluidNetwork newNetwork = this.getClass().newInstance();

                                for (IConnector node : finder.closedSet)
                                {
                                    if (node != splitPoint && node instanceof IFluidPart)
                                    {
                                        newNetwork.addConnector((IFluidPart) node);
                                    }
                                }
                                newNetwork.reconstruct();
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }

                        }
                    }
                }
            }
        }
    }

    @Override
    public void split(IFluidPart connectorA, IFluidPart connectorB)
    {
        this.reconstruct();

        /** Check if connectorA connects with connectorB. */
        ConnectionPathfinder finder = new ConnectionPathfinder(connectorB);
        finder.findNodes(connectorA);

        if (finder.results.size() <= 0)
        {
            /** The connections A and B are not connected anymore. Give them both a new common
             * network. */
            IFluidNetwork newNetwork;
            try
            {
                newNetwork = this.getClass().newInstance();

                for (IConnector node : finder.closedSet)
                {
                    if (node instanceof IFluidPart)
                    {
                        newNetwork.addConnector((IFluidPart) node);
                    }
                }

                newNetwork.reconstruct();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public FluidTank getTank()
    {
        if (this.tank == null)
        {
            this.tank = new FluidTank(0);
        }
        return this.tank;
    }

    @Override
    public FluidTankInfo[] getTankInfo()
    {
        return tankInfo;
    }

    @Override
    public String toString()
    {
        return super.toString() + "  Vol:" + this.tank.getFluidAmount();
    }

}
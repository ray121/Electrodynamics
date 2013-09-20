package dark.fluid.common;

import net.minecraft.block.material.Material;
import net.minecraftforge.common.Configuration;
import dark.core.common.DMCreativeTab;
import dark.core.prefab.IExtraObjectInfo;
import dark.core.prefab.machine.BlockMachine;
import dark.core.registration.ModObjectRegistry.BlockBuildData;

public abstract class BlockFM extends BlockMachine implements IExtraObjectInfo
{

    public BlockFM(Class<? extends BlockFM> blockClass, String name, Material material)
    {
        super(new BlockBuildData(blockClass, name, material).setConfigProvider(FluidMech.CONFIGURATION).setCreativeTab(DMCreativeTab.tabHydrualic));
    }

    @Override
    public boolean hasExtraConfigs()
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void loadExtraConfigs(Configuration config)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void loadRecipes()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void loadOreNames()
    {
        // TODO Auto-generated method stub

    }

}

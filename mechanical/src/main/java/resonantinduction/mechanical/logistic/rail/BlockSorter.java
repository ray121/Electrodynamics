package resonantinduction.mechanical.logistic.rail;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatMessageComponent;
import net.minecraft.world.World;
import resonantinduction.core.prefab.imprint.BlockImprintable;
import universalelectricity.api.UniversalElectricity;
import calclavia.lib.render.block.BlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A block that manipulates item movement between inventories.
 * 
 * @author Calclavia, DarkGuardsman
 */
public class BlockSorter extends BlockImprintable
{
	public BlockSorter(int id)
	{
		super(id, UniversalElectricity.machine);
		this.setBlockBounds(0.01f, 0.01f, 0.01f, 0.09f, 0.09f, 0.09f);
	}

	@Override
	public AxisAlignedBB getSelectedBoundingBoxFromPool(World par1World, int par2, int par3, int par4)
	{
		return AxisAlignedBB.getAABBPool().getAABB(par2, par3, par4, (double) par2 + 1, (double) par3 + 1, (double) par4 + 1);
	}

	@Override
	public boolean onSneakMachineActivated(World world, int x, int y, int z, EntityPlayer entityPlayer, int side, float hitX, float hitY, float hitZ)
	{
		TileEntity tileEntity = world.getBlockTileEntity(x, y, z);

		if (tileEntity instanceof TileSorter)
		{
			if (!world.isRemote)
			{
				((TileSorter) tileEntity).setSelfPulse(!((TileSorter) tileEntity).isSelfPulse());
				entityPlayer.sendChatToPlayer(ChatMessageComponent.createFromText("Manipulator set to " + (((TileSorter) tileEntity).isSelfPulse() ? "auto pulse" : "not pulse")));
			}
		}
		return true;
	}

	@Override
	public boolean onSneakUseWrench(World world, int x, int y, int z, EntityPlayer entityPlayer, int side, float hitX, float hitY, float hitZ)
	{
		TileEntity tileEntity = world.getBlockTileEntity(x, y, z);

		if (tileEntity instanceof TileSorter)
		{
			TileSorter manip = (TileSorter) tileEntity;
			boolean manipMode = manip.isOutput();
			boolean inverted = manip.isInverted();
			if (manipMode && !inverted)
			{
				manip.toggleInversion();
			}
			else if (manipMode && inverted)
			{
				manip.toggleOutput();
				manip.toggleInversion();
			}
			else if (!manipMode && !inverted)
			{
				manip.toggleInversion();
			}
			else
			{
				manip.toggleOutput();
				manip.toggleInversion();
			}
			if (!world.isRemote)
			{
				entityPlayer.sendChatToPlayer(ChatMessageComponent.createFromText("Manipulator outputing =  " + manip.isOutput()));
			}
		}

		return true;
	}

	@Override
	public TileEntity createNewTileEntity(World var1)
	{
		return new TileSorter();
	}

	@SideOnly(Side.CLIENT)
	@Override
	public int getRenderType()
	{
		return BlockRenderingHandler.ID;
	}

	@Override
	public boolean isOpaqueCube()
	{
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean renderAsNormalBlock()
	{
		return false;
	}

	@Override
	public int damageDropped(int par1)
	{
		return 0;
	}
}

package resonantinduction.archaic.fluid.gutter;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidHandler;
import resonantinduction.archaic.fluid.grate.TileGrate;
import resonantinduction.core.Reference;
import resonantinduction.core.ResonantInduction;
import resonantinduction.core.fluid.TilePressureNode;
import resonantinduction.core.grid.fluid.IPressureNodeProvider;
import resonantinduction.core.grid.fluid.PressureNode;
import universalelectricity.api.vector.Vector3;
import calclavia.lib.content.module.TileRender;
import calclavia.lib.prefab.vector.Cuboid;
import calclavia.lib.render.FluidRenderUtility;
import calclavia.lib.render.RenderUtility;
import calclavia.lib.utility.FluidUtility;
import calclavia.lib.utility.WorldUtility;

/**
 * The gutter, used for fluid transfer.
 * 
 * @author Calclavia
 * 
 */
public class TileGutter extends TilePressureNode
{
	public TileGutter()
	{
		super(Material.wood);
		textureName = "material_wood_surface";
		isOpaqueCube = false;
		normalRender = false;
		bounds = new Cuboid(0, 0, 0, 1, 0.99, 1);

		node = new PressureNode(this)
		{
			@Override
			public void recache()
			{
				synchronized (connections)
				{
					connections.clear();
					byte previousConnections = renderSides;
					renderSides = 0;

					for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
					{
						TileEntity tile = position().translate(dir).getTileEntity(world());

						if (tile instanceof IFluidHandler)
						{
							if (tile instanceof IPressureNodeProvider)
							{
								PressureNode check = ((IPressureNodeProvider) tile).getNode(PressureNode.class, dir.getOpposite());

								if (check != null && canConnect(dir, check) && check.canConnect(dir.getOpposite(), this))
								{
									renderSides = WorldUtility.setEnableSide(renderSides, dir, true);
									connections.put(check, dir);

								}
							}
							else
							{
								connections.put(tile, dir);

								if (tile instanceof TileGrate)
								{
									renderSides = WorldUtility.setEnableSide(renderSides, dir, true);
								}
							}
						}
					}

					/** Only send packet updates if visuallyConnected changed. */
					if (previousConnections != renderSides)
					{
						sendRenderUpdate();
					}
				}
			}

			@Override
			public int getPressure(ForgeDirection dir)
			{
				if (dir == ForgeDirection.UP)
					return -1;

				if (dir == ForgeDirection.DOWN)
					return 2;

				return 0;
			}

			@Override
			public int getMaxFlowRate()
			{
				return 20;
			}
		};
	}

	@Override
	public Iterable<Cuboid> getCollisionBoxes()
	{
		List<Cuboid> list = new ArrayList<Cuboid>();

		float thickness = 0.1F;

		if (!WorldUtility.isEnabledSide(renderSides, ForgeDirection.DOWN))
		{
			list.add(new Cuboid(0.0F, 0.0F, 0.0F, 1.0F, thickness, 1.0F));
		}

		if (!WorldUtility.isEnabledSide(renderSides, ForgeDirection.WEST))
		{
			list.add(new Cuboid(0.0F, 0.0F, 0.0F, thickness, 1.0F, 1.0F));
		}
		if (!WorldUtility.isEnabledSide(renderSides, ForgeDirection.NORTH))
		{
			list.add(new Cuboid(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, thickness));
		}

		if (!WorldUtility.isEnabledSide(renderSides, ForgeDirection.EAST))
		{
			list.add(new Cuboid(1.0F - thickness, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F));
		}

		if (!WorldUtility.isEnabledSide(renderSides, ForgeDirection.SOUTH))
		{
			list.add(new Cuboid(0.0F, 0.0F, 1.0F - thickness, 1.0F, 1.0F, 1.0F));
		}

		return list;
	}

	@Override
	public void collide(Entity entity)
	{
		if (getInternalTank().getFluidAmount() > 0)
		{
			for (int i = 2; i < 6; i++)
			{
				ForgeDirection dir = ForgeDirection.getOrientation(i);
				int pressure = node.getPressure(dir);
				Vector3 position = position().translate(dir);

				TileEntity checkTile = position.getTileEntity(world());

				if (checkTile instanceof TileGutter)
				{
					int deltaPressure = pressure - ((TileGutter) checkTile).node.getPressure(dir.getOpposite());

					entity.motionX += 0.01 * dir.offsetX * deltaPressure;
					entity.motionY += 0.01 * dir.offsetY * deltaPressure;
					entity.motionZ += 0.01 * dir.offsetZ * deltaPressure;
				}
			}

			if (getInternalTank().getFluid().getFluid().getTemperature() >= 373)
			{
				entity.setFire(5);
			}
		}

		if (entity instanceof EntityItem)
		{
			entity.noClip = true;
		}
	}

	@Override
	public boolean activate(EntityPlayer player, int side, Vector3 vector3)
	{
		if (player.getCurrentEquippedItem() != null && player.getCurrentEquippedItem().getItem() == ResonantInduction.itemDust)
		{
			return false;
		}

		if (!world().isRemote)
		{
			return FluidUtility.playerActivatedFluidItem(world(), x(), y(), z(), player, side);
		}

		return true;
	}

	@Override
	public void onFillRain()
	{
		if (!world().isRemote)
		{
			fill(ForgeDirection.UNKNOWN, new FluidStack(FluidRegistry.WATER, 10), true);
		}
	}

	@Override
	public void onNeighborChanged()
	{
		/**
		 * Drain block above if it is a fluid.
		 */
		Vector3 drainPos = new Vector3(this).translate(0, 1, 0);
		FluidStack drain = FluidUtility.drainBlock(worldObj, drainPos, false);

		if (drain != null)
		{
			if (fill(ForgeDirection.UP, drain, true) > 0)
				FluidUtility.drainBlock(worldObj, drainPos, true);
		}
	}

	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill)
	{
		if (!resource.getFluid().isGaseous())
		{
			return super.fill(from, resource, doFill);
		}

		return 0;
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid)
	{
		return from != ForgeDirection.UP && !fluid.isGaseous();
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid)
	{
		return from != ForgeDirection.UP && !fluid.isGaseous();
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected TileRender newRenderer()
	{
		return new TileRender()
		{
			public final IModelCustom MODEL = AdvancedModelLoader.loadModel(Reference.MODEL_DIRECTORY + "gutter.tcn");
			public final ResourceLocation TEXTURE = new ResourceLocation(Reference.DOMAIN, Reference.MODEL_PATH + "gutter.png");

			@Override
			public boolean renderStatic(Vector3 position)
			{
				return true;
			}

			@Override
			public boolean renderDynamic(Vector3 position, boolean isItem, float frame)
			{
				GL11.glPushMatrix();
				GL11.glTranslated(position.x + 0.5, position.y + 0.5, position.z + 0.5);

				FluidStack liquid = getInternalTank().getFluid();
				int capacity = getInternalTank().getCapacity();

				render(0, renderSides);

				if (world() != null)
				{
					FluidTank tank = getInternalTank();
					double percentageFilled = (double) tank.getFluidAmount() / (double) tank.getCapacity();

					if (percentageFilled > 0.1)
					{
						GL11.glPushMatrix();
						GL11.glScaled(0.990, 0.99, 0.990);

						double ySouthEast = FluidUtility.getAveragePercentageFilledForSides(TileGutter.class, percentageFilled, world(), position(), ForgeDirection.SOUTH, ForgeDirection.EAST);
						double yNorthEast = FluidUtility.getAveragePercentageFilledForSides(TileGutter.class, percentageFilled, world(), position(), ForgeDirection.NORTH, ForgeDirection.EAST);
						double ySouthWest = FluidUtility.getAveragePercentageFilledForSides(TileGutter.class, percentageFilled, world(), position(), ForgeDirection.SOUTH, ForgeDirection.WEST);
						double yNorthWest = FluidUtility.getAveragePercentageFilledForSides(TileGutter.class, percentageFilled, world(), position(), ForgeDirection.NORTH, ForgeDirection.WEST);

						FluidRenderUtility.renderFluidTesselation(tank, ySouthEast, yNorthEast, ySouthWest, yNorthWest);
						GL11.glPopMatrix();
					}
				}

				GL11.glPopMatrix();
				return true;
			}

			@Override
			public boolean renderItem(ItemStack itemStack)
			{
				GL11.glTranslated(0.5, 0.5, 0.5);
				render(itemStack.getItemDamage(), Byte.parseByte("001100", 2));
				return true;
			}

			public void render(int meta, byte sides)
			{
				RenderUtility.bind(TEXTURE);

				for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
				{
					if (dir != ForgeDirection.UP && dir != ForgeDirection.DOWN)
					{
						if (!WorldUtility.isEnabledSide(sides, dir))
						{
							GL11.glPushMatrix();
							RenderUtility.rotateBlockBasedOnDirection(dir);
							MODEL.renderOnly("left", "backCornerL", "frontCornerL");
							GL11.glPopMatrix();
						}
					}
				}

				if (!WorldUtility.isEnabledSide(sides, ForgeDirection.DOWN))
				{
					MODEL.renderOnly("base");
				}
				else
				{
					GL11.glPushMatrix();
					GL11.glRotatef(-90, 0, 0, 1);
					MODEL.renderOnly("backCornerL", "frontCornerL");
					GL11.glPopMatrix();
					GL11.glPushMatrix();
					GL11.glRotatef(90, 0, 1, 0);
					GL11.glRotatef(-90, 0, 0, 1);
					MODEL.renderOnly("backCornerL", "frontCornerL");
					GL11.glPopMatrix();
				}
			}
		};
	}

}
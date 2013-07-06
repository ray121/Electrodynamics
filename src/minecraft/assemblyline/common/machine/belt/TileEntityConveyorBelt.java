package assemblyline.common.machine.belt;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import universalelectricity.core.vector.Vector3;
import universalelectricity.prefab.implement.IRotatable;
import universalelectricity.prefab.network.IPacketReceiver;
import universalelectricity.prefab.network.PacketManager;
import assemblyline.api.IBelt;
import assemblyline.common.AssemblyLine;
import assemblyline.common.machine.TileEntityAssembly;

import com.google.common.io.ByteArrayDataInput;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class TileEntityConveyorBelt extends TileEntityAssembly implements IPacketReceiver, IBelt, IRotatable
{
	public enum SlantType
	{
		NONE,
		UP,
		DOWN,
		TOP
	}

	public static final int MAX_FRAME = 13;
	public static final int MAX_SLANT_FRAME = 23;

	public final float acceleration = 0.01f;
	public final float maxSpeed = 0.1f;
	/** Current rotation of the model wheels */
	public float wheelRotation = 0;
	private int animFrame = 0; // this is from 0 to 15
	private SlantType slantType = SlantType.NONE;
	/** Entities that are ignored allowing for other tiles to interact with them */
	public List<Entity> IgnoreList = new ArrayList<Entity>();

	@Override
	public void onUpdate()
	{
		/* PROCESSES IGNORE LIST AND REMOVES UNNEED ENTRIES */
		Iterator<Entity> it = this.IgnoreList.iterator();
		while (it.hasNext())
		{
			if (!this.getAffectedEntities().contains(it.next()))
			{
				it.remove();
			}
		}

		if (this.worldObj.isRemote && this.isRunning() && !this.worldObj.isBlockIndirectlyGettingPowered(this.xCoord, this.yCoord, this.zCoord))
		{
			if (this.ticks % 10 == 0 && this.worldObj.isRemote && this.worldObj.getBlockId(this.xCoord - 1, this.yCoord, this.zCoord) != AssemblyLine.blockConveyorBelt.blockID && this.worldObj.getBlockId(xCoord, yCoord, zCoord - 1) != AssemblyLine.blockConveyorBelt.blockID)
			{
				this.worldObj.playSound(this.xCoord, this.yCoord, this.zCoord, "mods.assemblyline.conveyor", 0.5f, 0.7f, true);
			}

			this.wheelRotation = (40 + this.wheelRotation) % 360;

			float wheelRotPct = wheelRotation / 360f;

			// Sync the animation. Slant belts are slower.
			if (this.getSlant() == SlantType.NONE || this.getSlant() == SlantType.TOP)
			{
				this.animFrame = (int) (wheelRotPct * MAX_FRAME);
				if (this.animFrame < 0)
					this.animFrame = 0;
				if (this.animFrame > MAX_FRAME)
					this.animFrame = MAX_FRAME;
			}
			else
			{
				this.animFrame = (int) (wheelRotPct * MAX_SLANT_FRAME);
				if (this.animFrame < 0)
					this.animFrame = 0;
				if (this.animFrame > MAX_SLANT_FRAME)
					this.animFrame = MAX_SLANT_FRAME;
			}
		}

	}

	@Override
	public Packet getDescriptionPacket()
	{
		return PacketManager.getPacket(AssemblyLine.CHANNEL, this, 3, this.slantType.ordinal());
	}

	public SlantType getSlant()
	{
		return slantType;
	}

	public void setSlant(SlantType slantType)
	{
		if (slantType == null)
		{
			slantType = SlantType.NONE;
		}
		this.slantType = slantType;
		this.worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	/** Is this belt in the front of a conveyor line? Used for rendering. */
	public boolean getIsFirstBelt()
	{
		Vector3 vec = new Vector3(this);
		TileEntity fBelt = vec.clone().modifyPositionFromSide(this.getDirection()).getTileEntity(this.worldObj);
		TileEntity bBelt = vec.clone().modifyPositionFromSide(this.getDirection().getOpposite()).getTileEntity(this.worldObj);
		if (fBelt instanceof TileEntityConveyorBelt && !(bBelt instanceof TileEntityConveyorBelt))
		{
			return ((TileEntityConveyorBelt) fBelt).getDirection() == this.getDirection();
		}
		return false;
	}

	/** Is this belt in the middile of two belts? Used for rendering. */
	public boolean getIsMiddleBelt()
	{

		Vector3 vec = new Vector3(this);
		TileEntity fBelt = vec.clone().modifyPositionFromSide(this.getDirection()).getTileEntity(this.worldObj);
		TileEntity bBelt = vec.clone().modifyPositionFromSide(this.getDirection().getOpposite()).getTileEntity(this.worldObj);
		if (fBelt instanceof TileEntityConveyorBelt && bBelt instanceof TileEntityConveyorBelt)
		{
			return ((TileEntityConveyorBelt) fBelt).getDirection() == this.getDirection() && ((TileEntityConveyorBelt) bBelt).getDirection() == this.getDirection();
		}
		return false;
	}

	/** Is this belt in the back of a conveyor line? Used for rendering. */
	public boolean getIsLastBelt()
	{
		Vector3 vec = new Vector3(this);
		TileEntity fBelt = vec.clone().modifyPositionFromSide(this.getDirection()).getTileEntity(this.worldObj);
		TileEntity bBelt = vec.clone().modifyPositionFromSide(this.getDirection().getOpposite()).getTileEntity(this.worldObj);
		if (bBelt instanceof TileEntityConveyorBelt && !(fBelt instanceof TileEntityConveyorBelt))
		{
			return ((TileEntityConveyorBelt) bBelt).getDirection() == this.getDirection().getOpposite();
		}
		return false;
	}

	@Override
	public boolean handlePacket(int id, DataInputStream dis, EntityPlayer player)
	{
		if (!super.handlePacket(id, dis, player) && this.worldObj.isRemote)
		{
			try
			{
				if (id == 3)
				{
					this.slantType = SlantType.values()[dis.readInt()];
					return true;
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return false;
	}

	@Override
	public void setDirection(World world, int x, int y, int z, ForgeDirection facingDirection)
	{
		this.worldObj.setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, facingDirection.ordinal(), 3);
	}

	public void setDirection(ForgeDirection facingDirection)
	{
		this.setDirection(worldObj, xCoord, yCoord, zCoord, facingDirection);
	}

	@Override
	public ForgeDirection getDirection(IBlockAccess world, int x, int y, int z)
	{
		return ForgeDirection.getOrientation(this.getBlockMetadata());
	}

	public ForgeDirection getDirection()
	{
		return this.getDirection(worldObj, xCoord, yCoord, zCoord);
	}

	@Override
	public List<Entity> getAffectedEntities()
	{
		return worldObj.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(this.xCoord, this.yCoord, this.zCoord, this.xCoord + 1, this.yCoord + 1, this.zCoord + 1));
	}

	public int getAnimationFrame()
	{
		return this.animFrame;
	}

	/** NBT Data */
	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		this.slantType = SlantType.values()[nbt.getByte("slant")];
	}

	/** Writes a tile entity to NBT. */
	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		nbt.setByte("slant", (byte) this.slantType.ordinal());
	}

	@Override
	public void ignoreEntity(Entity entity)
	{
		if (!this.IgnoreList.contains(entity))
		{
			this.IgnoreList.add(entity);
		}

	}

	@Override
	public boolean canConnect(ForgeDirection direction)
	{
		return direction == ForgeDirection.DOWN;
	}
}

package resonantinduction.wire.part;

import java.util.Arrays;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Icon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.common.ForgeDirection;

import org.lwjgl.opengl.GL11;

import resonantinduction.Utility;
import resonantinduction.wire.EnumWireMaterial;
import resonantinduction.wire.IAdvancedConductor;
import resonantinduction.wire.render.RenderLainWire;
import resonantinduction.wire.render.RenderPartWire;
import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.lighting.LazyLightMatrix;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.TextureUtils;
import codechicken.lib.vec.BlockCoord;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Rotation;
import codechicken.lib.vec.Vector3;
import codechicken.multipart.JNormalOcclusion;
import codechicken.multipart.NormalOcclusionTest;
import codechicken.multipart.PartMap;
import codechicken.multipart.TFacePart;
import codechicken.multipart.TMultiPart;
import codechicken.multipart.TileMultipart;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * This is the base class for all wire types. It can be used for any sub type,
 * as it contains the base calculations necessary to create a working wire. This
 * calculates all possible connections to sides, around corners, and inside
 * corners, while checking for microblock obstructions.
 * 
 * @author Modified by Calclavia, MrTJP
 * 
 */
public class PartFlatWire extends PartWireBase implements TFacePart, JNormalOcclusion
{
	public static Cuboid6[][] selectionBounds = new Cuboid6[3][6];
	public static Cuboid6[][] occlusionBounds = new Cuboid6[3][6];

	static
	{
		for (int t = 0; t < 3; t++)
		{
			// Subtract the box a little because we'd like things like posts to get first hit
			Cuboid6 selection = new Cuboid6(0, 0, 0, 1, (t + 2) / 16D, 1).expand(-0.005);
			Cuboid6 occlusion = new Cuboid6(2 / 8D, 0, 2 / 8D, 6 / 8D, (t + 2) / 16D, 6 / 8D);
			for (int s = 0; s < 6; s++)
			{
				selectionBounds[t][s] = selection.copy().apply(Rotation.sideRotations[s].at(Vector3.center));
				occlusionBounds[t][s] = occlusion.copy().apply(Rotation.sideRotations[s].at(Vector3.center));
			}
		}
	}

	public byte side;

	/**
	 * A map of the corners.
	 * 
	 * 
	 * Currently split into 4 nybbles (from lowest)
	 * 0 = Corner connections (this wire should connect around a corner to something external)
	 * 1 = Straight connections (this wire should connect to something external)
	 * 2 = Internal connections (this wire should connect to something internal)
	 * 3 = Internal open connections (this wire is not blocked by a cover/edge part and *could*
	 * connect through side)
	 * bit 16 = connection to the centerpart
	 * 5 = Render corner connections. Like corner connections but set to low if the other wire part
	 * is smaller than this (they render to us not us to them)
	 */
	public int connMap;

	public PartFlatWire()
	{
		super();
	}

	public PartFlatWire(int typeID)
	{
		this(EnumWireMaterial.values()[typeID]);
	}

	public PartFlatWire(EnumWireMaterial type)
	{
		super();
		material = type;
	}

	public void preparePlacement(int side, int meta)
	{
		this.side = (byte) (side ^ 1);
		this.material = EnumWireMaterial.values()[meta];
	}

	/**
	 * PACKET and NBT Methods
	 */
	@Override
	public void load(NBTTagCompound tag)
	{
		super.load(tag);
		this.side = tag.getByte("side");
		this.connMap = tag.getInteger("connMap");
	}

	@Override
	public void save(NBTTagCompound tag)
	{
		super.save(tag);
		tag.setByte("side", this.side);
		tag.setInteger("connMap", this.connMap);
	}

	@Override
	public void readDesc(MCDataInput packet)
	{
		super.readDesc(packet);
		this.side = packet.readByte();
		this.connMap = packet.readInt();
	}

	@Override
	public void writeDesc(MCDataOutput packet)
	{
		super.writeDesc(packet);
		packet.writeByte(this.side);
		packet.writeInt(this.connMap);
	}

	@Override
	public void read(MCDataInput packet)
	{
		super.read(packet);
		read(packet, packet.readUByte());
	}

	public void read(MCDataInput packet, int packetID)
	{
		if (packetID == 0)
		{
			this.connMap = packet.readInt();
			tile().markRender();
		}
	}

	public void sendConnUpdate()
	{
		tile().getWriteStream(this).writeByte(0).writeInt(connMap);
	}

	@Override
	public void onRemoved()
	{
		super.onRemoved();

		if (!world().isRemote)
		{
			for (int r = 0; r < 4; r++)
			{
				if (maskConnects(r))
				{
					if ((connMap & 1 << r) != 0)
					{
						notifyCornerChange(r);
					}
					else if ((connMap & 0x10 << r) != 0)
					{
						notifyStraightChange(r);
					}
				}
			}
		}
	}

	@Override
	public void onChunkLoad()
	{
		if ((connMap & 0x80000000) != 0) // compat with converters, recalc connections
		{
			if (dropIfCantStay())
				return;

			connMap = 0;

			updateInternalConnections();
			if (updateOpenConnections())
				updateExternalConnections();

			tile().markDirty();
		}
	}

	public boolean canStay()
	{
		BlockCoord pos = new BlockCoord(tile()).offset(side);
		return Utility.canPlaceWireOnSide(world(), pos.x, pos.y, pos.z, ForgeDirection.getOrientation(side ^ 1), false);
	}

	public boolean dropIfCantStay()
	{
		if (!canStay())
		{
			drop();
			return true;
		}
		return false;
	}

	public void drop()
	{
		TileMultipart.dropItem(getItem(), world(), Vector3.fromTileEntityCenter(tile()));
		tile().remPart(this);
	}

	/**
	 * Recalculates connections to blocks outside this space
	 * 
	 * @return true if a new connection was added or one was removed
	 */
	protected boolean updateExternalConnections()
	{
		int newConn = 0;
		for (int r = 0; r < 4; r++)
		{
			if (!maskOpen(r))
				continue;

			if (connectStraight(r))
				newConn |= 0x10 << r;
			else
			{
				int cnrMode = connectCorner(r);
				if (cnrMode != 0)
				{
					newConn |= 1 << r;
					if (cnrMode == 2)
						newConn |= 0x100000 << r;// render flag
				}
			}
		}

		if (newConn != (connMap & 0xF000FF))
		{
			int diff = connMap ^ newConn;
			connMap = connMap & ~0xF000FF | newConn;

			// notify corner disconnections
			for (int r = 0; r < 4; r++)
				if ((diff & 1 << r) != 0)
					notifyCornerChange(r);

			return true;
		}
		return false;
	}

	/**
	 * Recalculates connections to other parts within this space
	 * 
	 * @return true if a new connection was added or one was removed
	 */
	protected boolean updateInternalConnections()
	{
		int newConn = 0;
		for (int r = 0; r < 4; r++)
			if (connectInternal(r))
				newConn |= 0x100 << r;

		if (connectCenter())
			newConn |= 0x10000;

		if (newConn != (connMap & 0x10F00))
		{
			connMap = connMap & ~0x10F00 | newConn;
			return true;
		}
		return false;
	}

	/**
	 * Recalculates connections that can be made to other parts outside of this
	 * space
	 * 
	 * @return true if external connections should be recalculated
	 */
	protected boolean updateOpenConnections()
	{
		int newConn = 0;
		for (int r = 0; r < 4; r++)
			if (connectionOpen(r))
				newConn |= 0x1000 << r;

		if (newConn != (connMap & 0xF000))
		{
			connMap = connMap & ~0xF000 | newConn;
			return true;
		}
		return false;
	}

	public boolean connectionOpen(int r)
	{
		int absDir = Rotation.rotateSide(side, r);
		TMultiPart facePart = tile().partMap(absDir);
		if (facePart != null && (!(facePart instanceof PartFlatWire) || !canConnectToType((PartFlatWire) facePart)))
			return false;

		if (tile().partMap(PartMap.edgeBetween(side, absDir)) != null)
			return false;

		return true;
	}

	/**
	 * Return a corner connection state.
	 * 0 = No connection
	 * 1 = Physical connection
	 * 2 = Render connection
	 */
	public int connectCorner(int r)
	{
		int absDir = Rotation.rotateSide(side, r);

		BlockCoord pos = new BlockCoord(tile());
		pos.offset(absDir);

		if (!canConnectThroughCorner(pos, absDir ^ 1, side))
			return 0;

		pos.offset(side);
		TileMultipart t = Utility.getMultipartTile(world(), pos);
		if (t != null)
		{
			TMultiPart tp = t.partMap(absDir ^ 1);
			if (tp instanceof IAdvancedConductor)
			{
				boolean b = ((PartFlatWire) tp).connectCorner(this, Rotation.rotationTo(absDir ^ 1, side ^ 1));
				if (b)
				{
					// let them connect to us
					if (tp instanceof PartFlatWire && !renderThisCorner((PartFlatWire) tp))
						return 1;

					return 2;
				}
			}
		}
		return 0;
	}

	public boolean canConnectThroughCorner(BlockCoord pos, int side1, int side2)
	{
		if (world().isAirBlock(pos.x, pos.y, pos.z))
			return true;

		TileMultipart t = Utility.getMultipartTile(world(), pos);
		if (t != null)
			return t.partMap(side1) == null && t.partMap(side2) == null && t.partMap(PartMap.edgeBetween(side1, side2)) == null;

		return false;
	}

	public boolean connectStraight(int r)
	{
		int absDir = Rotation.rotateSide(side, r);

		BlockCoord pos = new BlockCoord(tile()).offset(absDir);
		TileMultipart t = Utility.getMultipartTile(world(), pos);
		if (t != null)
		{
			TMultiPart tp = t.partMap(side);
			if (tp instanceof PartFlatWire)
				return ((PartFlatWire) tp).connectStraight(this, (r + 2) % 4);
		}

		return connectStraightOverride(absDir);
	}

	public boolean connectStraightOverride(int absDir)
	{
		return false;
	}

	public boolean connectInternal(int r)
	{
		int absDir = Rotation.rotateSide(side, r);

		if (tile().partMap(PartMap.edgeBetween(absDir, side)) != null)
			return false;

		TMultiPart tp = tile().partMap(absDir);
		if (tp instanceof PartFlatWire)
			return ((PartFlatWire) tp).connectInternal(this, Rotation.rotationTo(absDir, side));

		return connectInternalOverride(tp, r);
	}

	public boolean connectInternalOverride(TMultiPart p, int r)
	{
		return false;
	}

	public boolean connectCenter()
	{
		TMultiPart t = tile().partMap(6);
		if (t instanceof PartFlatWire)
			return ((PartFlatWire) t).connectInternal(this, side);

		return false;
	}

	public boolean renderThisCorner(IAdvancedConductor part)
	{
		if (!(part instanceof PartFlatWire))
			return false;

		PartFlatWire wire = (PartFlatWire) part;
		if (wire.getThickness() == getThickness())
			return side < wire.side;

		return wire.getThickness() > getThickness();
	}

	public boolean connectCorner(IAdvancedConductor wire, int r)
	{
		if (canConnectToType(wire) && maskOpen(r))
		{
			int oldConn = connMap;
			connMap |= 0x1 << r;
			if (renderThisCorner(wire))// render connection
				connMap |= 0x100000 << r;

			if (oldConn != connMap)
				sendConnUpdate();
			return true;
		}
		return false;
	}

	public boolean connectStraight(IAdvancedConductor wire, int r)
	{
		if (canConnectToType(wire) && maskOpen(r))
		{
			int oldConn = connMap;
			connMap |= 0x10 << r;
			if (oldConn != connMap)
				sendConnUpdate();
			return true;
		}
		return false;
	}

	public boolean connectInternal(IAdvancedConductor wire, int r)
	{
		if (canConnectToType(wire))
		{
			int oldConn = connMap;
			connMap |= 0x100 << r;
			if (oldConn != connMap)
				sendConnUpdate();
			return true;
		}
		return false;
	}

	public boolean canConnectCorner(int r)
	{
		return true;
	}

	public void notifyCornerChange(int r)
	{
		int absDir = Rotation.rotateSide(side, r);

		BlockCoord pos = new BlockCoord(tile()).offset(absDir).offset(side);
		world().notifyBlockOfNeighborChange(pos.x, pos.y, pos.z, tile().getBlockType().blockID);
	}

	public void notifyStraightChange(int r)
	{
		int absDir = Rotation.rotateSide(side, r);

		BlockCoord pos = new BlockCoord(tile()).offset(absDir);
		world().notifyBlockOfNeighborChange(pos.x, pos.y, pos.z, tile().getBlockType().blockID);
	}

	public boolean maskConnects(int r)
	{
		return (connMap & 0x111 << r) != 0;
	}

	public boolean maskOpen(int r)
	{
		return (connMap & 0x1000 << r) != 0;
	}

	/** START TILEMULTIPART INTERACTIONS **/
	@Override
	public float getStrength(MovingObjectPosition hit, EntityPlayer player)
	{
		return 4;
	}

	@Override
	public int getSlotMask()
	{
		return 1 << side;
	}

	@Override
	public Iterable<IndexedCuboid6> getSubParts()
	{
		return Arrays.asList(new IndexedCuboid6(0, selectionBounds[getThickness()][side]));
	}

	@Override
	public boolean occlusionTest(TMultiPart npart)
	{
		return NormalOcclusionTest.apply(this, npart);
	}

	@Override
	public Iterable<Cuboid6> getOcclusionBoxes()
	{
		return Arrays.asList(occlusionBounds[getThickness()][side]);
	}

	public int getThickness()
	{
		return 1;
	}

	@Override
	public int redstoneConductionMap()
	{
		return 0xF;
	}

	@Override
	public boolean solid(int arg0)
	{
		return false;
	}

	@Override
	public boolean isBlockedOnSide(ForgeDirection side)
	{
		return false;
	}

	@Override
	public String getType()
	{
		return "resonant_induction_flat_wire";
	}

	/**
	 * RENDERING
	 */
	@SideOnly(Side.CLIENT)
	public Icon getIcon()
	{
		return RenderPartWire.lainWireIcon;
	}

	public int getColour()
	{
		return this.getMaterial().color.pack();
	}

	public boolean useStaticRenderer()
	{
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void renderStatic(Vector3 pos, LazyLightMatrix olm, int pass)
	{
		if (pass == 0 && useStaticRenderer())
		{
			CCRenderState.setBrightness(world(), x(), y(), z());
			RenderLainWire.render(this, pos);
			CCRenderState.setColour(-1);
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void renderDynamic(Vector3 pos, float frame, int pass)
	{
		if (pass == 0 && !useStaticRenderer())
		{
			GL11.glDisable(GL11.GL_LIGHTING);
			TextureUtils.bindAtlas(0);
			CCRenderState.useModelColours(true);
			CCRenderState.startDrawing(7);
			RenderLainWire.render(this, pos);
			CCRenderState.draw();
			CCRenderState.setColour(-1);
			GL11.glEnable(GL11.GL_LIGHTING);
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void drawBreaking(RenderBlocks renderBlocks)
	{
		CCRenderState.reset();
		RenderLainWire.renderBreakingOverlay(renderBlocks.overrideBlockTexture, this);
	}

}
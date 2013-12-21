package com.builtbroken.assemblyline.client.render;

import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyTile;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeDirection;

import org.lwjgl.opengl.GL11;

import universalelectricity.api.Compatibility;
import universalelectricity.api.net.IConnector;
import universalelectricity.api.vector.Vector3;
import universalelectricity.api.vector.VectorHelper;
import buildcraft.api.power.IPowerReceptor;

import com.builtbroken.assemblyline.AssemblyLine;
import com.builtbroken.assemblyline.client.model.ModelCopperWire;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderBlockWire extends TileEntitySpecialRenderer
{
    public static final ResourceLocation TEXTURE = new ResourceLocation(AssemblyLine.DOMAIN, "textures/models/copperWire.png");

    public static final ModelCopperWire model = new ModelCopperWire();

    @Override
    public void renderTileEntityAt(TileEntity tileEntity, double d, double d1, double d2, float f)
    {
        // Texture file
        this.bindTexture(TEXTURE);
        GL11.glPushMatrix();
        GL11.glTranslatef((float) d + 0.5F, (float) d1 + 1.5F, (float) d2 + 0.5F);
        GL11.glScalef(1.0F, -1F, -1F);

        boolean[] renderSide = new boolean[6];

        for (byte i = 0; i < 6; i++)
        {
            ForgeDirection dir = ForgeDirection.getOrientation(i);
            TileEntity ent = VectorHelper.getTileEntityFromSide(tileEntity.worldObj, new Vector3(tileEntity), dir);

            if (ent instanceof IConnector)
            {
                if (((IConnector) ent).canConnect(dir.getOpposite()))
                {
                    renderSide[i] = true;
                }
            }
            else if (ent instanceof IEnergyTile)
            {
                if (ent instanceof IEnergyAcceptor)
                {
                    if (((IEnergyAcceptor) ent).acceptsEnergyFrom(tileEntity, dir.getOpposite()))
                    {
                        renderSide[i] = true;
                    }
                }
                else
                {
                    renderSide[i] = true;
                }
            }
            else if (ent instanceof IPowerReceptor)
            {
                renderSide[i] = true;
            }
        }

        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS)
        {
            if (renderSide[side.ordinal()])
            {
                model.renderSide(side);
            }
        }
        model.renderSide(ForgeDirection.UNKNOWN);
        GL11.glPopMatrix();
    }
}
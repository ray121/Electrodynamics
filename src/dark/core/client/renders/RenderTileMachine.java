package dark.core.client.renders;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class RenderTileMachine extends TileEntitySpecialRenderer
{

    public RenderTileMachine()
    {

    }

    /** Sudo method for setting the texture for current render
     *
     * @param name */
    public void bindTextureByName(String domain, String name)
    {
        this.bindTexture(new ResourceLocation(domain, name));
    }

    public void bindTextureByName(ResourceLocation name)
    {
        this.bindTexture(name);
    }

    /** Gets the texture based on block and metadata mainly used by item/block inv render */
    public abstract ResourceLocation getTexture(int block, int meta);

}

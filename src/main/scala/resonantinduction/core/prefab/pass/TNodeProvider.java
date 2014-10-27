package resonantinduction.core.prefab.pass;

import codechicken.multipart.PartMap;
import codechicken.multipart.TMultiPart;
import codechicken.multipart.TileMultipart;
import net.minecraftforge.common.util.ForgeDirection;
import resonant.api.grid.INode;
import resonant.api.grid.INodeProvider;

/**
 * TNodeProvider Trait.
 * Keep this in Java for smoother ASM.
 * @author Calclavia
 */
public class TNodeProvider extends TileMultipart implements INodeProvider
{
	public INode getNode(Class<? extends INode> nodeType, ForgeDirection from)
	{
		TMultiPart nodePart = partMap(from.ordinal());

		if (nodePart == null)
		{
			nodePart = partMap(PartMap.CENTER.ordinal());
		}

		for (int i = 0; i < 6; i++)
		{
			if (from.ordinal() != i && (from.ordinal() ^ 1) != i)
			{
				if (nodePart == null)
				{
					nodePart = partMap(i);
				}
			}
		}

		if (nodePart instanceof INodeProvider)
		{
			return ((INodeProvider) nodePart).getNode(nodeType, from);
		}

		return null;
	}
}

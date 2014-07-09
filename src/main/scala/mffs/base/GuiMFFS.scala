package mffs.base

import mffs.ModularForceFieldSystem
import mffs.render.button.GuiIcon
import net.minecraft.client.gui.{GuiButton, GuiTextField}
import net.minecraft.init.Blocks
import net.minecraft.inventory.Container
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import resonant.api.blocks.IBlockFrequency
import resonant.api.mffs.IBiometricIdentifierLink
import resonant.lib.gui.GuiContainerBase
import resonant.lib.network.PacketTile
import resonant.lib.utility.LanguageUtility
import resonant.lib.wrapper.WrapList._
import universalelectricity.core.transform.region.Rectangle
import universalelectricity.core.transform.vector.Vector2

class GuiMFFS(container: Container, frequencyTile: IBlockFrequency) extends GuiContainerBase(container)
{
  ySize = 217

  def this(container: Container) = this(container, null)

  override def initGui()
  {
    super.initGui
    buttonList.clear()
    //Activation button
    buttonList.add(new GuiIcon(0, this.width / 2 - 110, this.height / 2 - 104, new ItemStack(Blocks.unlit_redstone_torch), new ItemStack(Blocks.redstone_torch)))

    /*Keyboard.enableRepeatEvents(true)
     if (this.frequencyTile != null)
     {
       this.textFieldFrequency = new GuiTextField(this.fontRendererObj, this.textFieldPos.xi, this.textFieldPos.yi, 50, 12)
       this.textFieldFrequency.setMaxStringLength(Settings.maxFrequencyDigits)
       this.textFieldFrequency.setText(frequencyTile.getFrequency + "")
     }*/
  }

  protected override def actionPerformed(guiButton: GuiButton)
  {
    super.actionPerformed(guiButton)
    if (this.frequencyTile != null && guiButton.id == 0)
    {
      ModularForceFieldSystem.packetHandler.sendToServer(new PacketTile(frequencyTile.asInstanceOf[TileEntity], TilePacketType.TOGGLE_ACTIVATION.id: Integer))
    }
  }

  override def updateScreen()
  {
    super.updateScreen()

    if (textFieldFrequency != null)
    {
      if (!textFieldFrequency.isFocused)
      {
        textFieldFrequency.setText(this.frequencyTile.getFrequency + "")
      }
    }
    if (frequencyTile.isInstanceOf[TileMFFS])
    {
      if (buttonList.size > 0 && this.buttonList.get(0) != null)
      {
        buttonList.get(0).asInstanceOf[GuiIcon].setIndex(if ((this.frequencyTile.asInstanceOf[TileMFFS]).isRedstoneActive) 1 else 0)
      }
    }
  }

  override def mouseClicked(x: Int, y: Int, par3: Int)
  {
    super.mouseClicked(x, y, par3)

    if (textFieldFrequency != null)
    {
      textFieldFrequency.mouseClicked(x - this.containerWidth, y - this.containerHeight, par3)
    }
  }

  protected override def drawGuiContainerForegroundLayer(mouseX: Int, mouseY: Int)
  {
    super.drawGuiContainerForegroundLayer(mouseX, mouseY)

    if (textFieldFrequency != null)
    {
      if (new Rectangle(textFieldPos.xi, textFieldPos.yi, textFieldPos.xi + textFieldFrequency.getWidth, textFieldPos.yi + 12).intersects(new Vector2(mouseX, mouseY)))
      {
        this.tooltip = LanguageUtility.getLocal("gui.frequency.tooltip")
      }
    }
  }

  protected override def drawGuiContainerBackgroundLayer(var1: Float, x: Int, y: Int)
  {
    super.drawGuiContainerBackgroundLayer(var1, x, y)

    if (frequencyTile.isInstanceOf[IBiometricIdentifierLink])
    {
      drawBulb(167, 4, (frequencyTile.asInstanceOf[IBiometricIdentifierLink]).getBiometricIdentifier != null)
    }
  }

}
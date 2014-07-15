package resonantinduction.core.resource

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.InputStream
import java.util.{ArrayList, HashMap, Iterator, List}
import javax.imageio.ImageIO

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.registry.{GameRegistry, LanguageRegistry}
import cpw.mods.fml.relauncher.{Side, SideOnly}
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.item.crafting.FurnaceRecipes
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.util.{IIcon, ResourceLocation}
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.fluids.{BlockFluidFinite, FluidContainerRegistry, FluidRegistry, FluidStack}
import net.minecraftforge.oredict.OreDictionary
import resonant.api.recipe.MachineRecipes
import resonant.lib.config.Config
import resonant.lib.utility.LanguageUtility
import resonantinduction.core.ResonantInduction.RecipeType
import resonantinduction.core.prefab.FluidColored
import resonantinduction.core.resource.fluid.{BlockFluidMaterial, BlockFluidMixture}
import resonantinduction.core.{CoreContent, Reference, ResonantInduction, Settings}
import scala.collection.JavaConversions._

import scala.collection.mutable

/**
 * Generates the resources based on available ores in Resonant Induction
 * @author Calclavia
 */
object ResourceGenerator
{
  private final val materials = mutable.Set.empty[String]
  private final val materialColorCache: HashMap[String, Integer] = new HashMap[String, Integer]
  private final val iconColorCache: HashMap[IIcon, Integer] = new HashMap[IIcon, Integer]

  @Config(category = "resource-generator")
  var enableAll: Boolean = true
  @Config(category = "resource-generator")
  var enableAllFluids: Boolean = true

  /**
   * A list of material names. They are all camelCase reference of ore dictionary names without
   * the "ore" or "ingot" prefix.
   * <p/>
   * Name, ID
   */
  private[resource] var maxID: Int = 0
  @Config(category = "resource-generator", comment = "Allow the Resource Generator to make ore dictionary compatible recipes?")
  private val allowOreDictCompatibility: Boolean = true

  def generate(materialName: String)
  {
    val nameCaps = LanguageUtility.capitalizeFirst(materialName)
    var localizedName = materialName

    val list = OreDictionary.getOres("ingot" + nameCaps)

    if (list.size > 0)
    {
      localizedName = list.get(0).getDisplayName.trim
      if (LanguageUtility.getLocal(localizedName) != null && LanguageUtility.getLocal(localizedName) != "")
      {
        localizedName = LanguageUtility.getLocal(localizedName)
      }

      localizedName.replace(LanguageUtility.getLocal("misc.resonantinduction.ingot"), "").replaceAll("^ ", "").replaceAll(" #", "")
    }

    if (enableAllFluids)
    {
      val fluidMolten: FluidColored = new FluidColored(materialNameToMolten(materialName))
      fluidMolten.setDensity(7)
      fluidMolten.setViscosity(5000)
      fluidMolten.setTemperature(273 + 1538)
      FluidRegistry.registerFluid(fluidMolten)
      LanguageRegistry.instance.addStringLocalization(fluidMolten.getUnlocalizedName, LanguageUtility.getLocal("tooltip.molten") + " " + localizedName)
      val blockFluidMaterial: BlockFluidMaterial = new BlockFluidMaterial(fluidMolten)
      GameRegistry.registerBlock(blockFluidMaterial, "molten" + nameCaps)
      ResonantInduction.blockMoltenFluid.put(getID(materialName), blockFluidMaterial)
      FluidContainerRegistry.registerFluidContainer(fluidMolten, CoreContent.BucketMolten.getStackFromMaterial(materialName))
      val fluidMixture: FluidColored = new FluidColored(materialNameToMixture(materialName))
      FluidRegistry.registerFluid(fluidMixture)
      val blockFluidMixture: BlockFluidMixture = new BlockFluidMixture(fluidMixture)
      LanguageRegistry.instance.addStringLocalization(fluidMixture.getUnlocalizedName, localizedName + " " + LanguageUtility.getLocal("tooltip.mixture"))
      GameRegistry.registerBlock(blockFluidMixture, "mixture" + nameCaps)
      ResonantInduction.blockMixtureFluids.put(getID(materialName), blockFluidMixture)
      FluidContainerRegistry.registerFluidContainer(fluidMixture, CoreContent.BucketMixture.getStackFromMaterial(materialName))
      
      if (allowOreDictCompatibility)
      {
        MachineRecipes.INSTANCE.addRecipe(RecipeType.SMELTER.name, new FluidStack(fluidMolten, FluidContainerRegistry.BUCKET_VOLUME), "ingot" + nameCaps)
      }
      else
      {
        MachineRecipes.INSTANCE.addRecipe(RecipeType.SMELTER.name, new FluidStack(fluidMolten, FluidContainerRegistry.BUCKET_VOLUME), "ingot" + nameCaps)
      }
    }

    val dust: ItemStack = CoreContent.dust.getStackFromMaterial(materialName)
    val rubble: ItemStack = CoreContent.rubble.getStackFromMaterial(materialName)
    val refinedDust: ItemStack = CoreContent.refinedDust.getStackFromMaterial(materialName)
    if (allowOreDictCompatibility)
    {
      OreDictionary.registerOre("rubble" + nameCaps, CoreContent.rubble.getStackFromMaterial(materialName))
      OreDictionary.registerOre("dirtyDust" + nameCaps, CoreContent.dust.getStackFromMaterial(materialName))
      OreDictionary.registerOre("dust" + nameCaps, CoreContent.refinedDust.getStackFromMaterial(materialName))
      MachineRecipes.INSTANCE.addRecipe(RecipeType.GRINDER.name, "rubble" + nameCaps, dust, dust)
      MachineRecipes.INSTANCE.addRecipe(RecipeType.MIXER.name, "dirtyDust" + nameCaps, refinedDust)
    }
    else
    {
      MachineRecipes.INSTANCE.addRecipe(RecipeType.GRINDER.name, rubble, dust, dust)
      MachineRecipes.INSTANCE.addRecipe(RecipeType.MIXER.name, dust, refinedDust)
    }
    FurnaceRecipes.smelting.addSmelting(dust.itemID, dust.getItemDamage, OreDictionary.getOres("ingot" + nameCaps).get(0).copy, 0.7f)
    val smeltResult: ItemStack = OreDictionary.getOres("ingot" + nameCaps).get(0).copy
    FurnaceRecipes.smelting.addSmelting(refinedDust.itemID, refinedDust.getItemDamage, smeltResult, 0.7f)
    if (OreDictionary.getOres("ore" + nameCaps).size > 0)
    {
      MachineRecipes.INSTANCE.addRecipe(RecipeType.CRUSHER.name, "ore" + nameCaps, "rubble" + nameCaps)
    }
  }

  def generateOreResources
  {
    OreDictionary.registerOre("ingotGold", Item.ingotGold)
    OreDictionary.registerOre("ingotIron", Item.ingotIron)
    OreDictionary.registerOre("oreGold", Block.oreGold)
    OreDictionary.registerOre("oreIron", Block.oreIron)
    OreDictionary.registerOre("oreLapis", Block.oreLapis)
    MachineRecipes.INSTANCE.addRecipe(RecipeType.SMELTER.name, new FluidStack(FluidRegistry.LAVA, FluidContainerRegistry.BUCKET_VOLUME), new ItemStack(Block.stone))
    MachineRecipes.INSTANCE.addRecipe(RecipeType.CRUSHER.name, Block.cobblestone, Block.gravel)
    MachineRecipes.INSTANCE.addRecipe(RecipeType.CRUSHER.name, Block.stone, Block.cobblestone)
    MachineRecipes.INSTANCE.addRecipe(RecipeType.CRUSHER.name, Block.chest, new ItemStack(Block.planks, 7, 0))
    MachineRecipes.INSTANCE.addRecipe(RecipeType.GRINDER.name, Block.cobblestone, Block.sand)
    MachineRecipes.INSTANCE.addRecipe(RecipeType.GRINDER.name, Block.gravel, Block.sand)
    MachineRecipes.INSTANCE.addRecipe(RecipeType.GRINDER.name, Block.glass, Block.sand)
    val it: Iterator[String] = materials.keySet.iterator
    while (it.hasNext)
    {
      val materialName: String = it.next
      val nameCaps: String = LanguageUtility.capitalizeFirst(materialName)
      if (OreDictionary.getOres("ore" + nameCaps).size > 0)
      {
        generate(materialName)
      }
      else
      {
        it.remove
      }
    }
  }

  @SideOnly(Side.CLIENT)
  def computeColors
  {
    for (material <- materials)
    {
      val totalR: Int = 0
      val totalG: Int = 0
      val totalB: Int = 0
      val colorCount: Int = 0
      for (ingotStack <- OreDictionary.getOres("ingot" + LanguageUtility.capitalizeFirst(material)))
      {
        val theIngot: Item = ingotStack.getItem
        val color: Int = getAverageColor(ingotStack)
        materialColorCache.put(material, color)
      }
      if (!materialColorCache.containsKey(material))
      {
        materialColorCache.put(material, 0xFFFFFF)
      }
    }
  }

  /**
   * Gets the average color of this item.
   *
   * @param itemStack
   * @return The RGB hexadecimal color code.
   */
  @SideOnly(Side.CLIENT) def getAverageColor(itemStack: ItemStack): Int =
  {
    var totalR: Int = 0
    var totalG: Int = 0
    var totalB: Int = 0
    var colorCount: Int = 0
    val item: Item = itemStack.getItem
    try
    {
      val icon: IIcon = item.getIconIndex(itemStack)
      if (iconColorCache.containsKey(icon))
      {
        return iconColorCache.get(icon)
      }
      var iconString: String = icon.getIconName
      if (iconString != null && !iconString.contains("MISSING_ICON_ITEM"))
      {
        iconString = (if (iconString.contains(":")) iconString.replace(":", ":" + Reference.ITEM_TEXTURE_DIRECTORY) else Reference.ITEM_TEXTURE_DIRECTORY + iconString) + ".png"
        val textureLocation: ResourceLocation = new ResourceLocation(iconString)
        val inputstream: InputStream = Minecraft.getMinecraft.getResourceManager.getResource(textureLocation).getInputStream
        val bufferedimage: BufferedImage = ImageIO.read(inputstream)
        val width: Int = bufferedimage.getWidth
        val height: Int = bufferedimage.getWidth
        {
          var x: Int = 0
          while (x < width)
          {
            {
              {
                var y: Int = 0
                while (y < height)
                {
                  {
                    val rgb: Color = new Color(bufferedimage.getRGB(x, y))
                    val luma: Double = 0.2126 * rgb.getRed + 0.7152 * rgb.getGreen + 0.0722 * rgb.getBlue
                    if (luma > 40)
                    {
                      totalR += rgb.getRed
                      totalG += rgb.getGreen
                      totalB += rgb.getBlue
                      colorCount += 1
                    }
                  }
                  ({y += 1; y - 1 })
                }
              }
            }
            ({x += 1; x - 1 })
          }
        }
      }
      if (colorCount > 0)
      {
        totalR /= colorCount
        totalG /= colorCount
        totalB /= colorCount
        val averageColor: Int = new Color(totalR, totalG, totalB).brighter.getRGB
        iconColorCache.put(icon, averageColor)
        return averageColor
      }
    }
    catch
      {
        case e: Exception =>
        {
          ResonantInduction.LOGGER.fine("Failed to compute colors for: " + item)
        }
      }
    return 0xFFFFFF
  }

  def moltenToMaterial(fluidName: String): String =
  {
    return fluidNameToMaterial(fluidName, "molten")
  }

  def materialNameToMolten(fluidName: String): String =
  {
    return materialNameToFluid(fluidName, "molten")
  }

  def mixtureToMaterial(fluidName: String): String =
  {
    return fluidNameToMaterial(fluidName, "mixture")
  }

  def materialNameToMixture(fluidName: String): String =
  {
    return materialNameToFluid(fluidName, "mixture")
  }

  def fluidNameToMaterial(fluidName: String, `type`: String): String =
  {
    return LanguageUtility.decapitalizeFirst(LanguageUtility.underscoreToCamel(fluidName).replace(`type`, ""))
  }

  def materialNameToFluid(materialName: String, `type`: String): String =
  {
    return `type` + "_" + LanguageUtility.camelToLowerUnderscore(materialName)
  }

  def getMixture(name: String): BlockFluidFinite =
  {
    return ResonantInduction.blockMixtureFluids.get(getID(name))
  }

  def getMolten(name: String): BlockFluidFinite =
  {
    return ResonantInduction.blockMoltenFluid.get(getID(name))
  }

  def getName(id: Int): String =
  {
    return materials.inverse.get(id)
  }

  def getName(itemStack: ItemStack): String =
  {
    return LanguageUtility.decapitalizeFirst(OreDictionary.getOreName(OreDictionary.getOreID(itemStack)).replace("dirtyDust", "").replace("dust", "").replace("ore", "").replace("ingot", ""))
  }

  def getColor(name: String): Int =
  {
    if (name != null && materialColorCache.containsKey(name))
    {
      return materialColorCache.get(name)
    }
    return 0xFFFFFF
  }

  @Deprecated def getMaterials: List[String] =
  {
    val returnMaterials: List[String] = new ArrayList[String]
    {
      var i: Int = 0
      while (i < materials.size)
      {
        {
          returnMaterials.add(getName(i))
        }
        ({i += 1; i - 1 })
      }
    }
    return returnMaterials
  }

  @SubscribeEvent
  def oreRegisterEvent(evt: OreDictionary.OreRegisterEvent)
  {
    if (evt.Name.startsWith("ingot"))
    {
      val oreDictName: String = evt.Name.replace("ingot", "")
      val materialName: String = LanguageUtility.decapitalizeFirst(oreDictName)
      if (!materials.containsKey(materialName))
      {
        Settings.config.load
        val allowMaterial: Boolean = Settings.config.get("Resource_Generator", "Enable " + oreDictName, true).getBoolean(true)
        Settings.config.save
        if (!allowMaterial || OreDetectionBlackList.isIngotBlackListed("ingot" + oreDictName) || OreDetectionBlackList.isOreBlackListed("ore" + oreDictName))
        {
          return
        }
        materials.put(materialName, ({maxID += 1; maxID - 1 }))
      }
    }
  }

  @SubscribeEvent
  @SideOnly(Side.CLIENT)
  def reloadTextures(e: TextureStitchEvent.Post)
  {
    computeColors
  }

}
package com.calclavia.edx.optics

import com.calclavia.edx.core.{EDX, Reference}
import com.calclavia.edx.optics.api.fortron.Fortron
import com.calclavia.edx.optics.content.{OpticsContent, OpticsModels, OpticsTextures}
import com.calclavia.edx.optics.grid.{OpticGrid, OpticGridPacket}
import com.calclavia.edx.optics.security.MFFSPermissions
import nova.core.component.fluid.Fluid
import nova.core.event.{BlockEvent, ServerEvent}
import nova.core.loader.{Loadable, Mod}
import nova.scala.wrapper.FunctionalWrapper._

@Mod(
	id = Reference.opticsID,
	name = Reference.name + ": Optics",
	version = Reference.version,
	novaVersion = Reference.novaVersion,
	dependencies = Array(Reference.electricID),
	domains = Array("edx")
)
object Optics extends Loadable {
	//TODO: Remove tempID
	val tempID = ""
	//var movementManager: MovementManager = null

	override def preInit() {
		//Register OpticGrid packets
		EDX.network.register(new OpticGridPacket)

		//Hook block change event
		EDX.events.on(classOf[BlockEvent.Change]).bind((evt: BlockEvent.Change) => EventHandler.onBlockChange(evt))

		//Init frequency grid1
		EDX.events
			.on(classOf[ServerEvent.Start])
			.bind((evt: ServerEvent.Start) => {
			GraphFrequency.client = new GraphFrequency
			GraphFrequency.server = new GraphFrequency
		})

		EDX.events
			.on(classOf[ServerEvent.Stop])
			.bind((evt: ServerEvent.Stop) => OpticGrid.clear())

		EDX.fluids.register(Fortron.fortronID, () => new Fluid())

		OpticsContent.preInit()
		OpticsModels.preInit()
		OpticsTextures.preInit()
	}

	override def init() {
		OpticsContent.init()
		OpticsModels.init()
		OpticsTextures.init()
	}

	override def postInit() {
		/**
		 * Add to black lists

		Blacklist.stabilizationBlacklist.add(Blocks.water)
		Blacklist.stabilizationBlacklist.add(Blocks.flowing_water)
		Blacklist.stabilizationBlacklist.add(Blocks.lava)
		Blacklist.stabilizationBlacklist.add(Blocks.flowing_lava)

		Blacklist.disintegrationBlacklist.add(Blocks.water)
		Blacklist.disintegrationBlacklist.add(Blocks.flowing_water)
		Blacklist.disintegrationBlacklist.add(Blocks.lava)
		Blacklist.disintegrationBlacklist.add(Blocks.flowing_lava)

		Blacklist.mobilizerBlacklist.add(Blocks.bedrock)
		Blacklist.mobilizerBlacklist.add(Content.forceField)

		try {
			val clazz = Class.forName("ic2.api.block.ExplosionWhitelist")
			clazz.getMethod("addWhitelistedBlock", classOf[Block]).invoke(null, Content.forceField)
		}
		catch {
			case _: Throwable => Reference.logger.info("IC2 Explosion white list API not found. Ignoring...")
		}
		 */

		//Initiate MFFS Permissions
		MFFSPermissions

		OpticsContent.postInit()
		OpticsModels.postInit()
		OpticsTextures.postInit()
	}

}
package com.calclavia.edx.electric.circuit.component.laser

import java.util.function.Supplier
import java.util.{Set => JSet}

import com.calclavia.edx.core.prefab.BlockEDX
import com.calclavia.edx.electric.ElectricContent
import com.calclavia.edx.electric.api.{ConnectionBuilder, Electric}
import com.calclavia.edx.electric.circuit.component.laser.WaveGrid.Electromagnetic
import com.calclavia.edx.electric.grid.NodeElectricComponent
import com.resonant.lib.WrapFunctions._
import nova.core.block.Block.{BlockPlaceEvent, RightClickEvent}
import nova.core.block.Stateful
import nova.core.block.component.{LightEmitter, StaticBlockRenderer}
import nova.core.component.renderer.ItemRenderer
import nova.core.component.transform.Orientation
import nova.core.game.Game
import nova.core.render.model.Model
import nova.core.retention.Storable
import nova.core.util.transform.matrix.Quaternion
import nova.core.util.transform.vector.Vector3d
import nova.core.util.{Direction, Ray}
import nova.scala.{ExtendedUpdater, IO}

/**
 * An emitter that shoots out lasers.
 *
 * Consider: E=hf. Higher frequency light has more energy.
 *
 * @author Calclavia
 */
class BlockLaserEmitter extends BlockEDX with Stateful with ExtendedUpdater with Storable {
	private val electricNode = add(new NodeElectricComponent(this))
	private val orientation = add(new Orientation(this)).hookBlockEvents()
	private val laserHandler = add(new WaveHandler(this))
	private val io = add(new IO(this))
	private val renderer = add(new StaticBlockRenderer(this))
	private val itemRenderer = add(new ItemRenderer(this))
	private val lightEmitter = add(new LightEmitter())

	orientation.setMask(63)

	electricNode.setPositiveConnections(new ConnectionBuilder(classOf[Electric]).setBlock(this).setConnectMask(io.inputMask).adjacentWireSupplier().asInstanceOf[Supplier[JSet[Electric]]])
	electricNode.setNegativeConnections(new ConnectionBuilder(classOf[Electric]).setBlock(this).setConnectMask(io.outputMask).adjacentWireSupplier().asInstanceOf[Supplier[JSet[Electric]]])
	electricNode.setResistance(100)

	collider.isCube(false)
	collider.isOpaqueCube(false)

	lightEmitter.setEmittedLevel(supplier(() => (electricNode.power / WaveGrid.maxPower).toFloat))

	placeEvent.add((evt: BlockPlaceEvent) => {
		io.setIOAlternatingOrientation()
		world.markStaticRender(position)
	})

	rightClickEvent.add((evt: RightClickEvent) => {
		io.setIOAlternatingOrientation()
		world.markStaticRender(position)
	})

	rightClickEvent.add((evt: RightClickEvent) => println(electricNode.positives()))

	renderer.setOnRender(
		(model: Model) => {
			val rot = orientation.orientation match {
				case Direction.UP => Quaternion.fromAxis(Vector3d.xAxis, -Math.PI / 2)
				case Direction.DOWN => Quaternion.fromAxis(Vector3d.xAxis, Math.PI / 2)
				case Direction.NORTH => Quaternion.fromAxis(Vector3d.yAxis, Math.PI / 2)
				case Direction.SOUTH => Quaternion.fromAxis(Vector3d.yAxis, -Math.PI / 2)
				case Direction.WEST => Quaternion.fromAxis(Vector3d.yAxis, Math.PI)
				case Direction.EAST => Quaternion.fromAxis(Vector3d.yAxis, 0)
				case _ => Quaternion.identity
			}

			model.rotate(rot)

			if (orientation.orientation.y == 0) {
				model.rotate(Vector3d.yAxis, -Math.PI / 2)
			}
			else {
				model.rotate(Vector3d.xAxis, Math.PI)
			}

			model.children.add(ElectricContent.laserEmitterModel.getModel)
			model.bindAll(ElectricContent.laserEmitterTexture)
		}
	)

	override def update(deltaTime: Double) {
		super.update(deltaTime)

		if (Game.network.isServer) {
			if (electricNode.power > 0) {
				val dir = orientation.orientation.toVector.toDouble
				laserHandler.create(new Electromagnetic(new Ray(position.toDouble + 0.5 + dir * 0.51, dir), position.toDouble + dir * 0.2 + 0.5, electricNode.power / 20))
			}
			else {
				laserHandler.remove()
			}
		}
	}

	override def getID: String = "laserEmitter"
}

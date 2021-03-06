package com.soywiz.korge.scene

import com.soywiz.klock.*
import com.soywiz.korag.*
import com.soywiz.korge.resources.*
import com.soywiz.korge.time.*
import com.soywiz.korge.util.*
import com.soywiz.korge.view.*
import com.soywiz.korinject.*
import com.soywiz.korio.async.*
import com.soywiz.korma.geom.*
import kotlin.coroutines.experimental.*

abstract class Scene : InjectorAsyncDependency, ViewsContainer, CoroutineContextHolder {
	lateinit var injector: AsyncInjector
	override lateinit var views: Views
	val ag: AG get() = views.ag
	lateinit var sceneContainer: SceneContainer
	lateinit var resourcesRoot: ResourcesRoot
	//protected lateinit var bus: Bus
	internal lateinit var _sceneViewContainer: Container; private set
	lateinit var sceneView: Container; private set
	val root get() = _sceneViewContainer
	protected val cancellables = CancellableGroup()
	override val coroutineContext: CoroutineContext get() = views.coroutineContext

	protected open fun createSceneView(): Container = Container()

	override suspend fun init(injector: AsyncInjector): Unit {
		//this.injector = injector
		//this.views = injector.get() // @TODO: Bug in Kotlin.JS (no suspension point!)
		//this.sceneContainer = injector.get() // @TODO: Bug in Kotlin.JS (no suspension point!)
		//this.resourcesRoot = injector.get() // @TODO: Bug in Kotlin.JS (no suspension point!)

		this.injector = injector
		this.views = injector.get(Views::class)
		this.sceneContainer = injector.get(SceneContainer::class)
		this.resourcesRoot = injector.get(ResourcesRoot::class)

		//Console.log(injector)
		//println("Scene.init:ResourcesRoot[1]:" + injector.get<ResourcesRoot>())
		//println("Scene.init:ResourcesRoot[2]:" + injector.get(ResourcesRoot::class))
		//this.bus = injector.get()
		_sceneViewContainer = Container()
		sceneView = createSceneView()
		_sceneViewContainer += sceneView
	}

	abstract suspend fun Container.sceneInit(): Unit

	open suspend fun sceneAfterInit() {
	}

	open suspend fun sceneBeforeLeaving() {
	}

	open suspend fun sceneDestroy() {
		cancellables.cancel()
	}

	open suspend fun sceneAfterDestroy() {
	}
}

abstract class ScaledScene() : Scene() {
	open val sceneSize: ISize = ISize(320, 240)
	open val sceneScale: Double = 2.0
	open val sceneFiltering: Boolean = false

	override fun createSceneView(): Container = ScaleView(
		sceneSize.width.toInt(),
		sceneSize.height.toInt(),
		scale = sceneScale,
		filtering = sceneFiltering
	)
}

class EmptyScene : Scene() {
	override suspend fun Container.sceneInit() {
	}
}

abstract class LogScene : Scene() {
	open val sceneName: String get() = "LogScene"
	open val log = arrayListOf<String>()

	open fun log(msg: String) {
		this.log += msg
	}

	override suspend fun init(injector: AsyncInjector) {
		super.init(injector)
	}

	override suspend fun Container.sceneInit() {
		log("$sceneName.sceneInit")
	}

	override suspend fun sceneAfterInit() {
		log("$sceneName.sceneAfterInit")
		super.sceneAfterInit()
	}

	override suspend fun sceneDestroy() {
		log("$sceneName.sceneDestroy")
		super.sceneDestroy()
	}

	override suspend fun sceneAfterDestroy() {
		log("$sceneName.sceneAfterDestroy")
		super.sceneAfterDestroy()
	}
}

suspend fun Scene.sleep(time: TimeSpan) = sceneView.sleep(time)
suspend fun Scene.sleepMs(time: Int) = sceneView.sleepMs(time)

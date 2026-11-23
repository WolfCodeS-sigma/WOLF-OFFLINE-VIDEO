package com.wolfcodes.enginepoc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilamentCompatibilityTest {
    @Test
    fun nativeEngineAndSceneResourcesCanBeCreatedAndDestroyed() {
        Filament.init()
        val engine = Engine.create(Engine.Backend.OPENGL)
        val renderer = engine.createRenderer()
        val scene = engine.createScene()
        val view = engine.createView()
        val cameraEntity = EntityManager.get().create()
        try {
            val camera = engine.createCamera(cameraEntity)
            view.camera = camera
            view.scene = scene
            assertNotNull(renderer)
            assertNotNull(scene)
            assertNotNull(view)
            assertNotNull(camera)
        } finally {
            engine.destroyRenderer(renderer)
            engine.destroyView(view)
            engine.destroyScene(scene)
            engine.destroyCameraComponent(cameraEntity)
            EntityManager.get().destroy(cameraEntity)
            engine.destroy()
        }
    }
}

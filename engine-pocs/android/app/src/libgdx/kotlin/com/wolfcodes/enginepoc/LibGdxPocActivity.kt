package com.wolfcodes.enginepoc

import android.os.Bundle
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.graphics.GL20
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object LibGdxPocSignal {
    @Volatile
    private var firstFrame = CountDownLatch(1)

    fun reset() {
        firstFrame = CountDownLatch(1)
    }

    fun reportFrame() {
        firstFrame.countDown()
    }

    fun awaitFirstFrame(timeout: Long, unit: TimeUnit): Boolean =
        firstFrame.await(timeout, unit)
}

class LibGdxPocActivity : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize(object : ApplicationAdapter() {
            override fun render() {
                Gdx.gl.glClearColor(0.12f, 0.0f, 0.0f, 1.0f)
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
                LibGdxPocSignal.reportFrame()
            }
        })
    }
}

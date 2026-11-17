package com.clem.ioslock

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Un service n'a pas de cycle de vie utilisable par la caméra (CameraX).
 * On en fabrique un : démarré tant que l'écran de verrouillage est actif.
 */
class OverlayLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    init {
        registry.currentState = Lifecycle.State.CREATED
    }

    fun start() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.STARTED
        }
    }

    fun stop() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.CREATED
        }
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

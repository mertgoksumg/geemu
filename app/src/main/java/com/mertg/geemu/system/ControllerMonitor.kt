package com.mertg.geemu.system

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.mertg.geemu.model.ControllerDevice

class ControllerMonitor(
    context: Context,
    private val onChanged: (List<ControllerDevice>, ControllerDevice?, Boolean) -> Unit
) : InputManager.InputDeviceListener {
    private val inputManager = context.getSystemService(InputManager::class.java)
    private val knownDevices = mutableMapOf<Int, ControllerDevice>()

    fun start() {
        refresh()
        inputManager.registerInputDeviceListener(this, null)
    }

    fun stop() {
        inputManager.unregisterInputDeviceListener(this)
    }

    fun connectedControllers(): List<ControllerDevice> = knownDevices.values.sortedBy { it.name }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = inputManager.getInputDevice(deviceId)?.toControllerDevice() ?: return
        knownDevices[deviceId] = device
        onChanged(connectedControllers(), device, true)
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val removed = knownDevices.remove(deviceId) ?: return
        onChanged(connectedControllers(), removed, false)
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        inputManager.getInputDevice(deviceId)?.toControllerDevice()?.let { knownDevices[deviceId] = it }
        onChanged(connectedControllers(), knownDevices[deviceId], true)
    }

    private fun refresh() {
        knownDevices.clear()
        inputManager.inputDeviceIds.forEach { id ->
            inputManager.getInputDevice(id)?.toControllerDevice()?.let { knownDevices[id] = it }
        }
        onChanged(connectedControllers(), null, true)
    }
}

private fun InputDevice.toControllerDevice(): ControllerDevice? {
    val controllerSources = InputDevice.SOURCE_GAMEPAD or
        InputDevice.SOURCE_JOYSTICK or
        InputDevice.SOURCE_DPAD
    if (isVirtual || sources and controllerSources == 0) return null
    return ControllerDevice(
        deviceId = id,
        descriptor = descriptor.orEmpty(),
        name = name.ifBlank { "Bilinmeyen controller" },
        vendorId = vendorId,
        productId = productId
    )
}

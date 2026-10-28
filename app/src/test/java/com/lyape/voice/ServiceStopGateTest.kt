package com.lyape.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceStopGateTest {

    @Test
    fun `un cierre viejo no detiene la sesion nueva`() {
        val gate = ServiceStopGate()
        var stopped = 0
        gate.start(10L) {}
        gate.start(20L) {}
        gate.stop(10L) { stopped++ }
        assertEquals(0, stopped)
        gate.stop(20L) { stopped++ }
        gate.stop(20L) { stopped++ }
        assertEquals(1, stopped)
    }

    @Test fun `inicio concurrente durante parada se ejecuta despues del efecto completo`() {
        val tasks = ArrayDeque<() -> Unit>()
        val effects = mutableListOf<String>()
        lateinit var commands: SerialServiceCommands
        commands = SerialServiceCommands(
            post = { tasks.addLast(it); true },
            startEffect = { effects += "start $it" },
            stopEffect = {
                commands.start(20L) // Solicitud concurrente en la antigua ventana de carrera.
                effects += "stop"
            },
            onFailure = { error("Unexpected failure") },
        )
        commands.start(10L)
        commands.stop(10L)
        while (tasks.isNotEmpty()) tasks.removeFirst()()
        commands.stop(10L)
        while (tasks.isNotEmpty()) tasks.removeFirst()()
        assertEquals(listOf("start 10", "stop", "start 20"), effects)
    }

    @Test fun `fallo de inicio se informa sin aceptar el token nuevo`() {
        val tasks = ArrayDeque<() -> Unit>()
        val failures = mutableListOf<Long>()
        var stops = 0
        val commands = SerialServiceCommands(
            { tasks.addLast(it); true }, { throw IllegalStateException() },
            { stops++ }, { failures += it },
        )
        commands.start(10L)
        commands.stop(10L)
        while (tasks.isNotEmpty()) tasks.removeFirst()()
        assertEquals(listOf(10L), failures)
        assertEquals(0, stops)
    }
}

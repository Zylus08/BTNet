package net.meshnet.simulator

import kotlin.random.Random

interface FailureInjector {
    fun shouldCrash(random: Random): Boolean
    fun drainBattery(currentLevel: Float, deltaTimeSeconds: Float): Float
}

class StandardFailureInjector(
    private val crashProbabilityPerTick: Double = 0.0001,
    private val batteryDrainPerSecond: Float = 0.0005f
) : FailureInjector {
    
    override fun shouldCrash(random: Random): Boolean {
        return random.nextDouble() < crashProbabilityPerTick
    }

    override fun drainBattery(currentLevel: Float, deltaTimeSeconds: Float): Float {
        return (currentLevel - (batteryDrainPerSecond * deltaTimeSeconds)).coerceAtLeast(0f)
    }
}

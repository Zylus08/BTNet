package net.meshnet.simulator

import kotlin.random.Random

interface MobilityModel {
    fun calculateNextPosition(x: Double, y: Double, deltaTimeSeconds: Float, random: Random): Pair<Double, Double>
}

class RandomWaypointMobility(
    private val speed: Double = 1.5, // m/s (walking speed)
    private val areaWidth: Double,
    private val areaHeight: Double
) : MobilityModel {
    
    override fun calculateNextPosition(x: Double, y: Double, deltaTimeSeconds: Float, random: Random): Pair<Double, Double> {
        // Simplified random walk for stub
        val dx = (random.nextDouble() * 2 - 1) * speed * deltaTimeSeconds
        val dy = (random.nextDouble() * 2 - 1) * speed * deltaTimeSeconds
        
        val newX = (x + dx).coerceIn(0.0, areaWidth)
        val newY = (y + dy).coerceIn(0.0, areaHeight)
        
        return Pair(newX, newY)
    }
}

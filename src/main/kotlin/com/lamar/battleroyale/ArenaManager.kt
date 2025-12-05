package com.lamar.battleroyale

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import kotlin.random.Random
import kotlin.math.abs

class ArenaManager(private val plugin: BattleRoyalePlugin) {

    private val file = File(plugin.dataFolder, "arenas.yml")
    private val config = YamlConfiguration.loadConfiguration(file)
    
    private var center: Location? = null
    private var size: Double = 0.0
    private var lobby: Location? = null
    
    // Temp storage for pos setup
    private var pos1: Location? = null
    private var pos2: Location? = null

    init {
        // Load lobby if exists
        if (config.contains("lobby")) {
            lobby = config.getLocation("lobby")
        }
        // Load arena if exists
        if (config.contains("center") && config.contains("size")) {
            center = config.getLocation("center")
            size = config.getDouble("size")
            startGlobalBorderTask()
        }
    }

    fun setArenaCenter(center: Location, size: Double) {
        this.center = center
        this.size = size
        config.set("center", center)
        config.set("size", size)
        saveConfig()
        startGlobalBorderTask()
    }
    
    private var globalBorderTask: org.bukkit.scheduler.BukkitRunnable? = null
    
    private fun startGlobalBorderTask() {
        globalBorderTask?.cancel()
        
        if (center == null || size <= 0.0) return
        
        globalBorderTask = object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                val currentCenter = center ?: return
                val radius = size / 2
                val world = currentCenter.world ?: return
                
                val minX = currentCenter.x - radius
                val maxX = currentCenter.x + radius
                val minZ = currentCenter.z - radius
                val maxZ = currentCenter.z + radius
                
                val minY = plugin.config.getInt("border.min-y", -64)
                val maxY = plugin.config.getInt("border.max-y", 320)
                val vDensity = plugin.config.getDouble("border.vertical-particle-density", 2.0)
                val hDensity = plugin.config.getDouble("border.horizontal-particle-density", 0.5)
                val viewDist = plugin.config.getInt("border.view-distance", 32).toDouble()
                
                val particleName = plugin.config.getString("border.particle", "FLAME")!!
                val particleCount = plugin.config.getInt("border.particle-count", 1)
                val particle = try {
                    org.bukkit.Particle.valueOf(particleName)
                } catch (e: IllegalArgumentException) {
                    org.bukkit.Particle.FLAME
                }
                
                val dustOptions = if (particle == org.bukkit.Particle.DUST) {
                    org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f)
                } else {
                    null
                }
                
                // Loop through ALL players in the world (since this is global border)
                world.players.forEach { player ->
                    val loc = player.location
                    
                    // MinX Side
                    if (abs(loc.x - minX) <= viewDist) {
                        renderWallSegment(player, minX, minX, maxOf(minZ, loc.z - viewDist), minOf(maxZ, loc.z + viewDist), minY, maxY, hDensity, vDensity, particle, particleCount, dustOptions)
                    }
                    
                    // MaxX Side
                    if (abs(loc.x - maxX) <= viewDist) {
                        renderWallSegment(player, maxX, maxX, maxOf(minZ, loc.z - viewDist), minOf(maxZ, loc.z + viewDist), minY, maxY, hDensity, vDensity, particle, particleCount, dustOptions)
                    }
                    
                    // MinZ Side
                    if (abs(loc.z - minZ) <= viewDist) {
                        renderWallSegment(player, maxOf(minX, loc.x - viewDist), minOf(maxX, loc.x + viewDist), minZ, minZ, minY, maxY, hDensity, vDensity, particle, particleCount, dustOptions)
                    }
                    
                    // MaxZ Side
                    if (abs(loc.z - maxZ) <= viewDist) {
                        renderWallSegment(player, maxOf(minX, loc.x - viewDist), minOf(maxX, loc.x + viewDist), maxZ, maxZ, minY, maxY, hDensity, vDensity, particle, particleCount, dustOptions)
                    }
                }
            }
        }
        globalBorderTask?.runTaskTimer(plugin, 0L, 10L) // Slower update for global border? Or same? Let's do 10L (0.5s) to save perf.
    }

    private fun renderWallSegment(player: org.bukkit.entity.Player, x1: Double, x2: Double, z1: Double, z2: Double, minY: Int, maxY: Int, hDensity: Double, vDensity: Double, particle: org.bukkit.Particle, count: Int, dustOptions: org.bukkit.Particle.DustOptions?) {
        var y = minY.toDouble()
        while (y < maxY) {
            if (x1 == x2) { // Vertical wall along Z
                var z = z1
                while (z <= z2) {
                    spawnParticleForPlayer(player, particle, x1, y, z, count, dustOptions)
                    z += hDensity
                }
            } else { // Horizontal wall along X
                var x = x1
                while (x <= x2) {
                    spawnParticleForPlayer(player, particle, x, y, z1, count, dustOptions)
                    x += hDensity
                }
            }
            y += vDensity
        }
    }
    
    private fun spawnParticleForPlayer(player: org.bukkit.entity.Player, particle: org.bukkit.Particle, x: Double, y: Double, z: Double, count: Int, dustOptions: org.bukkit.Particle.DustOptions?) {
        if (dustOptions != null) {
            player.spawnParticle(particle, x, y, z, count, 0.0, 0.0, 0.0, 0.0, dustOptions)
        } else {
            player.spawnParticle(particle, x, y, z, count, 0.0, 0.0, 0.0, 0.0)
        }
    }
    
    fun setPos1(loc: Location) {
        pos1 = loc
        checkAndSetFromPos()
    }
    
    fun setPos2(loc: Location) {
        pos2 = loc
        checkAndSetFromPos()
    }
    
    private fun checkAndSetFromPos() {
        if (pos1 != null && pos2 != null) {
            if (pos1!!.world != pos2!!.world) return
            
            val world = pos1!!.world
            val minX = minOf(pos1!!.x, pos2!!.x)
            val maxX = maxOf(pos1!!.x, pos2!!.x)
            val minZ = minOf(pos1!!.z, pos2!!.z)
            val maxZ = maxOf(pos1!!.z, pos2!!.z)
            
            val centerX = minX + (maxX - minX) / 2
            val centerZ = minZ + (maxZ - minZ) / 2
            // Use highest Y or average? Let's use pos1 Y for now or highest block
            val centerY = pos1!!.y 
            
            val newCenter = Location(world, centerX, centerY, centerZ)
            val newSize = maxOf(maxX - minX, maxZ - minZ) // Use the larger dimension for square size
            
            setArenaCenter(newCenter, newSize)
        }
    }
    
    fun setLobby(loc: Location) {
        this.lobby = loc
        config.set("lobby", loc)
        saveConfig()
    }
    
    fun getLobby(): Location? {
        return lobby
    }

    private fun saveConfig() {
        try {
            config.save(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun teleportRandomly(player: Player) {
        if (center == null || size <= 0.0) {
            player.sendMessage(plugin.getMessage("arena-not-set"))
            return
        }
        
        val world = center!!.world
        val half = size / 2
        val minX = center!!.x - half
        val maxX = center!!.x + half
        val minZ = center!!.z - half
        val maxZ = center!!.z + half
        
        var loc: Location
        var attempts = 0
        val maxAttempts = plugin.config.getInt("safe-teleport.max-attempts", 20)
        
        do {
            val x = Random.nextDouble(minX, maxX)
            val z = Random.nextDouble(minZ, maxZ)
            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1.0
            loc = Location(world, x, y, z)
            attempts++
        } while (!isSafe(loc) && attempts < maxAttempts)
        
        if (attempts >= maxAttempts) {
            // Fallback to center + high Y
            val fallbackY = world.getHighestBlockYAt(center!!.x.toInt(), center!!.z.toInt()) + 1.0
            loc = Location(world, center!!.x, fallbackY, center!!.z)
            player.sendMessage(plugin.getMessage("safe-spot-not-found-fallback"))
        }
        
        player.teleport(loc)
    }
    
    private fun isSafe(loc: Location): Boolean {
        val block = loc.block
        val below = block.getRelative(0, -1, 0)
        
        if (plugin.config.getBoolean("safe-teleport.check-water", true)) {
            if (block.type == org.bukkit.Material.WATER || below.type == org.bukkit.Material.WATER) return false
        }
        
        if (plugin.config.getBoolean("safe-teleport.check-lava", true)) {
            if (block.type == org.bukkit.Material.LAVA || below.type == org.bukkit.Material.LAVA) return false
        }
        
        if (plugin.config.getBoolean("safe-teleport.check-air-above", true)) {
            if (!block.type.isAir || !block.getRelative(0, 1, 0).type.isAir) return false
        }
        
        return true
    }

    fun isInArena(loc: Location): Boolean {
        if (center == null || size <= 0.0) return false
        if (loc.world != center!!.world) return false
        
        val half = size / 2
        val minX = center!!.x - half
        val maxX = center!!.x + half
        val minZ = center!!.z - half
        val maxZ = center!!.z + half
        
        return loc.x >= minX && loc.x <= maxX && loc.z >= minZ && loc.z <= maxZ
    }

    fun getCenter(): Location? {
        return center
    }
    
    fun getSize(): Double {
        return size
    }
}

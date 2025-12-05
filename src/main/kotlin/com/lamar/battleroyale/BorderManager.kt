package com.lamar.battleroyale

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.abs

class BorderManager(private val plugin: BattleRoyalePlugin, private val game: Game) {

    data class BorderPhase(
        val time: Int, // Seconds to wait
        val shrinkTime: Int, // Ticks between shrink steps
        val radius: Double, // Amount to shrink per step (per side)
        val targetSize: Double,
        val damage: Double
    )

    private val phases = mutableListOf<BorderPhase>()
    private var currentPhaseIndex = 0
    private var currentSize = 0.0
    private var center: Location? = null
    
    var state = "WAITING" // WAITING, SHRINKING, HOLDING
    var timeRemaining = 0
    
    private var task: BukkitRunnable? = null
    private var particleTask: BukkitRunnable? = null
    
    fun loadConfig() {
        phases.clear()
        val configPhases = plugin.config.getMapList("border.phases")
        for (map in configPhases) {
            phases.add(BorderPhase(
                time = (map["time"] as Number).toInt(),
                shrinkTime = (map["shrink-time"] as Number).toInt(),
                radius = (map["radius"] as Number).toDouble(),
                targetSize = (map["size"] as Number).toDouble(),
                damage = (map["damage"] as Number).toDouble()
            ))
        }
    }
    
    fun start(center: Location, initialSize: Double) {
        this.center = center
        this.currentSize = initialSize
        this.currentPhaseIndex = 0
        loadConfig()
        
        if (this.currentSize <= 0.0) {
             this.currentSize = plugin.config.getDouble("border.arena-size", 500.0)
        }
        
        startPhase()
        startParticleTask()
    }
    
    fun stop() {
        task?.cancel()
        task = null
        particleTask?.cancel()
        particleTask = null
        state = "WAITING"
    }
    
    private fun startPhase() {
        if (currentPhaseIndex >= phases.size) {
            state = "HOLDING"
            timeRemaining = 0
            return
        }
        
        val phase = phases[currentPhaseIndex]
        state = "WAITING"
        timeRemaining = phase.time
        
        task = object : BukkitRunnable() {
            override fun run() {
                if (timeRemaining > 0) {
                    timeRemaining--
                } else {
                    // Start shrinking
                    cancel()
                    startShrinking(phase)
                }
            }
        }
        task?.runTaskTimer(plugin, 0L, 20L)
    }
    
    private fun startShrinking(phase: BorderPhase) {
        state = "SHRINKING"
        
        task = object : BukkitRunnable() {
            override fun run() {
                if (currentSize > phase.targetSize) {
                    currentSize -= (phase.radius * 2) // Shrink from both sides
                    if (currentSize < phase.targetSize) currentSize = phase.targetSize
                } else {
                    cancel()
                    currentPhaseIndex++
                    startPhase()
                }
            }
        }
        task?.runTaskTimer(plugin, 0L, phase.shrinkTime.toLong())
    }
    
    fun isInsideBorder(loc: Location): Boolean {
        if (center == null) return false
        
        val half = currentSize / 2
        val minX = center!!.x - half
        val maxX = center!!.x + half
        val minZ = center!!.z - half
        val maxZ = center!!.z + half
        
        return loc.x >= minX && loc.x <= maxX && loc.z >= minZ && loc.z <= maxZ
    }
    
    private val messageCooldowns = mutableMapOf<java.util.UUID, Long>()

    private fun startParticleTask() {
        particleTask = object : BukkitRunnable() {
            override fun run() {
                if (center == null || game.state != GameState.PLAYING) {
                    cancel()
                    return
                }
                
                val world = center!!.world
                val half = currentSize / 2
                val minX = center!!.x - half
                val maxX = center!!.x + half
                val minZ = center!!.z - half
                val maxZ = center!!.z + half
                
                val minY = plugin.config.getInt("border.min-y", -64)
                val maxY = plugin.config.getInt("border.max-y", 320)
                val density = plugin.config.getDouble("border.horizontal-particle-density", 0.5)
                val vDensity = plugin.config.getDouble("border.vertical-particle-density", 2.0)
                val viewDist = plugin.config.getInt("border.view-distance", 32).toDouble()
                
                val particleName = plugin.config.getString("border.particle", "FLAME")!!
                val particleCount = plugin.config.getInt("border.particle-count", 1)
                val particle = try {
                    Particle.valueOf(particleName)
                } catch (e: IllegalArgumentException) {
                    Particle.FLAME
                }
                
                val dustOptions = if (particle == Particle.DUST) {
                    Particle.DustOptions(org.bukkit.Color.RED, 1.0f)
                } else {
                    null
                }
                
                // Player-Centric Rendering
                game.players.forEach { uuid ->
                    val player = Bukkit.getPlayer(uuid)
                    if (player != null && player.world == world) {
                        val loc = player.location
                        
                        // Check distance to each side
                        // If close enough, render that segment
                        
                        // MinX Side
                        if (abs(loc.x - minX) <= viewDist) {
                            renderWallSegment(player, minX, minX, maxOf(minZ, loc.z - viewDist), minOf(maxZ, loc.z + viewDist), minY, maxY, density, vDensity, particle, particleCount, dustOptions)
                        }
                        
                        // MaxX Side
                        if (abs(loc.x - maxX) <= viewDist) {
                            renderWallSegment(player, maxX, maxX, maxOf(minZ, loc.z - viewDist), minOf(maxZ, loc.z + viewDist), minY, maxY, density, vDensity, particle, particleCount, dustOptions)
                        }
                        
                        // MinZ Side
                        if (abs(loc.z - minZ) <= viewDist) {
                            renderWallSegment(player, maxOf(minX, loc.x - viewDist), minOf(maxX, loc.x + viewDist), minZ, minZ, minY, maxY, density, vDensity, particle, particleCount, dustOptions)
                        }
                        
                        // MaxZ Side
                        if (abs(loc.z - maxZ) <= viewDist) {
                            renderWallSegment(player, maxOf(minX, loc.x - viewDist), minOf(maxX, loc.x + viewDist), maxZ, maxZ, minY, maxY, density, vDensity, particle, particleCount, dustOptions)
                        }
                    }
                }
                
                checkPlayers()
            }
        }
        particleTask?.runTaskTimer(plugin, 0L, 5L) 
    }
    
    private fun renderWallSegment(player: Player, x1: Double, x2: Double, z1: Double, z2: Double, minY: Int, maxY: Int, hDensity: Double, vDensity: Double, particle: Particle, count: Int, dustOptions: Particle.DustOptions?) {
        var y = minY.toDouble()
        while (y < maxY) {
            // Optimization: Only render Y levels close to player?
            // User requested full Y range, but we can clamp to player Y +/- viewDist for even better performance if needed.
            // But let's stick to full Y for now as requested, but maybe clamp if it's too laggy.
            // Actually, let's clamp Y to player view as well, otherwise looking up/down might miss particles?
            // No, user said -64 to 320. Let's respect that for now.
            
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
    
    private fun spawnParticleForPlayer(player: Player, particle: Particle, x: Double, y: Double, z: Double, count: Int, dustOptions: Particle.DustOptions?) {
        if (dustOptions != null) {
            player.spawnParticle(particle, x, y, z, count, 0.0, 0.0, 0.0, 0.0, dustOptions)
        } else {
            player.spawnParticle(particle, x, y, z, count, 0.0, 0.0, 0.0, 0.0)
        }
    }
    
    private fun checkPlayers() {
        if (center == null) return
        
        val half = currentSize / 2
        val minX = center!!.x - half
        val maxX = center!!.x + half
        val minZ = center!!.z - half
        val maxZ = center!!.z + half
        
        var damage = 1.0
        if (currentPhaseIndex < phases.size) {
            damage = phases[currentPhaseIndex].damage
        } else if (phases.isNotEmpty()) {
            damage = phases.last().damage
        }
        
        val cooldownTime = plugin.config.getInt("border.message-cooldown", 5) * 1000L
        val now = System.currentTimeMillis()
        
        game.players.forEach { uuid ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null && player.gameMode != org.bukkit.GameMode.SPECTATOR) {
                val loc = player.location
                if (loc.world == center!!.world) {
                    if (loc.x < minX || loc.x > maxX || loc.z < minZ || loc.z > maxZ) {
                        player.damage(damage)
                        
                        val lastMessage = messageCooldowns[uuid] ?: 0L
                        if (now - lastMessage >= cooldownTime) {
                            player.sendMessage(plugin.getMessage("outside-border"))
                            messageCooldowns[uuid] = now
                        }
                    }
                }
            }
        }
    }
}

package com.example.model

import androidx.compose.ui.graphics.Color

data class GameRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun intersects(other: GameRect): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }
    
    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }
}

enum class WeaponType(
    val displayName: String,
    val fireRateMs: Long,
    val damage: Int,
    val maxAmmo: Int,
    val cost: Int,
    val emoji: String
) {
    FIST("Fist", 350L, 12, 0, 0, "👊"),
    PISTOL("Pistol", 450L, 25, 120, 150, "🔫"),
    UZI("Uzi", 120L, 16, 400, 450, "🎚️"),
    SHOTGUN("Shotgun", 800L, 50, 48, 700, "🕶️")
}

enum class VehicleType(
    val displayName: String,
    val maxSpeed: Float,
    val accel: Float,
    val deaccel: Float,
    val turnSpeed: Float,
    val sizeWidth: Float,
    val sizeHeight: Float
) {
    SEDAN("Sedan", 6.5f, 0.12f, 0.05f, 3.2f, 50f, 26f),
    SPORTS("Sports Car", 10.0f, 0.24f, 0.08f, 4.2f, 48f, 25f),
    TRUCK("Truck", 4.5f, 0.07f, 0.03f, 2.2f, 60f, 32f),
    POLICE("Police Cruiser", 8.2f, 0.18f, 0.06f, 3.8f, 50f, 26f)
}

data class PlayerState(
    val x: Float = 200f,
    val y: Float = 200f,
    val angle: Float = 0f,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val armor: Int = 50,
    val maxArmor: Int = 100,
    val cash: Int = 300,
    val wantedLevel: Int = 0, // 0 to 5 stars
    val currentWeapon: WeaponType = WeaponType.FIST,
    val ammo: Map<WeaponType, Int> = mapOf(
        WeaponType.FIST to 0,
        WeaponType.PISTOL to 30,
        WeaponType.UZI to 0,
        WeaponType.SHOTGUN to 0
    ),
    val inVehicleId: String? = null,
    val isDead: Boolean = false
)

data class Vehicle(
    val id: String,
    val type: VehicleType,
    val x: Float,
    val y: Float,
    val angle: Float,
    val speed: Float = 0f,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val isDestroyed: Boolean = false,
    val isPolice: Boolean = false,
    val color: Color
) {
    fun getBoundingBox(): GameRect {
        // Simple bounding box based on dimensions centered on (x, y)
        val halfW = type.sizeWidth / 2f
        val halfH = type.sizeHeight / 2f
        return GameRect(x - halfW, y - halfH, x + halfW, y + halfH)
    }
}

enum class NPCType {
    CIVILIAN, GANGSTER, POLICE
}

data class NPC(
    val id: String,
    val type: NPCType,
    val x: Float,
    val y: Float,
    val angle: Float,
    val health: Int = 50,
    val maxHealth: Int = 50,
    val isDead: Boolean = false,
    val targetX: Float? = null,
    val targetY: Float? = null,
    val speed: Float = 1.2f,
    val lastShotTime: Long = 0L,
    val isAlerted: Boolean = false
) {
    fun getBoundingBox(): GameRect {
        return GameRect(x - 14f, y - 14f, x + 14f, y + 14f)
    }
}

data class Bullet(
    val id: String,
    val x: Float,
    val y: Float,
    val dx: Float, // horizontal step
    val dy: Float, // vertical step
    val speed: Float,
    val damage: Int,
    val fromPlayer: Boolean,
    val ownerId: String? = null
)

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val alpha: Float,
    val life: Int,
    val maxLife: Int
)

enum class MissionObjectiveType {
    STEAL_CAR, ELIMINATE_TARGET, ESCAPE_POLICE, DELIVER_PACKAGE
}

data class Mission(
    val id: String,
    val title: String,
    val description: String,
    val reward: Int,
    val objectiveType: MissionObjectiveType,
    val objectiveTargetId: String? = null, // specific gangster or car type
    val objectiveX: Float = 0f,
    val objectiveY: Float = 0f,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val timeLimitSec: Int? = null,
    val timeRemainingSec: Int? = null
)

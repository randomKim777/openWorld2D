package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.*

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("retro_auto_theft_prefs", Context.MODE_PRIVATE)

    // Map size (3000 x 3000)
    val mapWidth = 3000f
    val mapHeight = 3000f
    val tileSize = 250f

    // Buildings and special zones
    val buildings = mutableListOf<GameRect>()
    val safehouseZone = GameRect(250f, 250f, 500f, 500f) // (1, 1) cell - Tony's Safehouse Garage
    val ammunationZone = GameRect(2500f, 2500f, 2750f, 2750f) // (10, 10) cell - Weapon Shop
    val tonyBossZone = GameRect(1250f, 1250f, 1400f, 1400f) // (5, 5) cell - Mission giver

    // State flows
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _npcs = MutableStateFlow<List<NPC>>(emptyList())
    val npcs: StateFlow<List<NPC>> = _npcs.asStateFlow()

    private val _bullets = MutableStateFlow<List<Bullet>>(emptyList())
    val bullets: StateFlow<List<Bullet>> = _bullets.asStateFlow()

    private val _particles = MutableStateFlow<List<Particle>>(emptyList())
    val particles: StateFlow<List<Particle>> = _particles.asStateFlow()

    // Missions list
    private val _missions = MutableStateFlow<List<Mission>>(emptyList())
    val missions: StateFlow<List<Mission>> = _missions.asStateFlow()

    private val _activeMission = MutableStateFlow<Mission?>(null)
    val activeMission: StateFlow<Mission?> = _activeMission.asStateFlow()

    // HUD / Menu states
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isShopOpen = MutableStateFlow(false)
    val isShopOpen: StateFlow<Boolean> = _isShopOpen.asStateFlow()

    private val _isGarageOpen = MutableStateFlow(false)
    val isGarageOpen: StateFlow<Boolean> = _isGarageOpen.asStateFlow()

    private val _isBossMenuOpen = MutableStateFlow(false)
    val isBossMenuOpen: StateFlow<Boolean> = _isBossMenuOpen.asStateFlow()

    private val _screenMessage = MutableStateFlow<String?>(null)
    val screenMessage: StateFlow<String?> = _screenMessage.asStateFlow()

    private val _screenShake = MutableStateFlow(0f)
    val screenShake: StateFlow<Float> = _screenShake.asStateFlow()

    // Controller input states
    private var moveDx = 0f
    private var moveDy = 0f

    private var lastShootTime = 0L

    init {
        generateCityMap()
        loadProgress()
        resetGameWorld()
        startGameLoop()
    }

    private fun generateCityMap() {
        buildings.clear()
        // Generate buildings on non-road tiles. Roads are rows/cols: 1, 5, 9
        for (row in 0 until 12) {
            for (col in 0 until 12) {
                val isRoadRow = row == 1 || row == 5 || row == 9
                val isRoadCol = col == 1 || col == 5 || col == 9
                
                if (!isRoadRow && !isRoadCol) {
                    // Skip spawn safety zones and special zones
                    if (row == 0 && col == 0) continue // Spawning zone safety
                    if (row == 1 && col == 1) continue // Safehouse area
                    if (row == 10 && col == 10) continue // Ammunation area
                    if (row == 5 && col == 5) continue // Central Tony Boss area
                    
                    // Create building blocks with nice padding
                    val left = col * tileSize + 25f
                    val top = row * tileSize + 25f
                    val right = (col + 1) * tileSize - 25f
                    val bottom = (row + 1) * tileSize - 25f
                    buildings.add(GameRect(left, top, right, bottom))
                }
            }
        }
    }

    private fun loadProgress() {
        val savedCash = sharedPrefs.getInt("player_cash", 300)
        _playerState.value = _playerState.value.copy(cash = savedCash)
    }

    fun saveProgress() {
        sharedPrefs.edit().putInt("player_cash", _playerState.value.cash).apply()
    }

    fun resetGameWorld() {
        // Safe spawn coordinates
        _playerState.value = PlayerState(
            x = 150f,
            y = 150f,
            cash = _playerState.value.cash,
            health = 100,
            armor = 50,
            currentWeapon = WeaponType.FIST,
            ammo = mapOf(
                WeaponType.FIST to 0,
                WeaponType.PISTOL to 40,
                WeaponType.UZI to 0,
                WeaponType.SHOTGUN to 0
            ),
            inVehicleId = null,
            wantedLevel = 0,
            isDead = false
        )

        _bullets.value = emptyList()
        _particles.value = emptyList()
        _activeMission.value = null
        _isShopOpen.value = false
        _isGarageOpen.value = false
        _isBossMenuOpen.value = false
        _screenMessage.value = "레트로 오토 테프트 시티에 오신 것을 환영합니다!\n조작패드로 운전하고 총을 쏘며 미션을 완수하세요!"

        // Spawn some vehicles
        val vehicleList = mutableListOf<Vehicle>()
        val carColors = listOf(Color.Red, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta, Color.LightGray)
        
        // Sedan spawns on roads
        vehicleList.add(Vehicle(UUID.randomUUID().toString(), VehicleType.SEDAN, 350f, 700f, 0f, color = carColors[0]))
        vehicleList.add(Vehicle(UUID.randomUUID().toString(), VehicleType.SPORTS, 350f, 1500f, 90f, color = carColors[1]))
        vehicleList.add(Vehicle(UUID.randomUUID().toString(), VehicleType.TRUCK, 1350f, 500f, 180f, color = carColors[2]))
        vehicleList.add(Vehicle(UUID.randomUUID().toString(), VehicleType.SEDAN, 1350f, 2000f, 270f, color = carColors[3]))
        vehicleList.add(Vehicle(UUID.randomUUID().toString(), VehicleType.SPORTS, 2350f, 800f, 90f, color = carColors[4]))
        vehicleList.add(Vehicle(UUID.randomUUID().toString(), VehicleType.TRUCK, 2350f, 1800f, 0f, color = carColors[5]))
        
        // Add a police car parked somewhere
        vehicleList.add(Vehicle(UUID.randomUUID().toString(), VehicleType.POLICE, 1350f, 1350f, 0f, isPolice = true, color = Color.White))
        vehicleList.add(Vehicle(UUID.randomUUID().toString(), VehicleType.POLICE, 2350f, 2350f, 90f, isPolice = true, color = Color.White))

        _vehicles.value = vehicleList

        // Spawn some civilian NPCs
        val npcList = mutableListOf<NPC>()
        val roadCoords = listOf(350f, 1350f, 2350f)
        for (i in 0 until 18) {
            val isGangster = i % 5 == 0
            val type = if (isGangster) NPCType.GANGSTER else NPCType.CIVILIAN
            // Place on sidewalks (slightly offset from road center)
            val roadX = roadCoords.random()
            val roadY = (100..2900).random().toFloat()
            npcList.add(
                NPC(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    x = roadX + if (i % 2 == 0) -60f else 60f,
                    y = roadY,
                    angle = (0..359).random().toFloat(),
                    health = if (isGangster) 75 else 40,
                    maxHealth = if (isGangster) 75 else 40,
                    speed = if (isGangster) 1.5f else 1.0f
                )
            )
        }
        _npcs.value = npcList

        // Set up the mission tree
        setupMissions()
    }

    private fun setupMissions() {
        _missions.value = listOf(
            Mission(
                id = "m1",
                title = "1단계: 스포츠카 탈취 작전",
                description = "토니 보스: '이봐, 내 차고에 빠른 스포츠카 한 대가 필요해. 서부 도로에 멋진 스포츠카가 스폰되어 있을 거야. 그걸 훔쳐서 내 차고(Safehouse)에 가져와!'",
                reward = 500,
                objectiveType = MissionObjectiveType.STEAL_CAR,
                objectiveTargetId = "SPORTS",
                objectiveX = 350f,
                objectiveY = 350f // safehouse center is 375, 375
            ),
            Mission(
                id = "m2",
                title = "2단계: 라이벌 조직 소탕",
                description = "토니 보스: '동부 구역에 라이벌 폭력배 녀석들이 영역을 침범하고 있어. 가서 녀석들 3명을 권총이나 펀치로 처리해 버려!'",
                reward = 1000,
                objectiveType = MissionObjectiveType.ELIMINATE_TARGET,
                objectiveTargetId = "GANGSTER"
            ),
            Mission(
                id = "m3",
                title = "3단계: 긴급 서류 배달",
                description = "토니 보스: '조직 비밀 금고의 비밀 장부를 급히 차고(Safehouse)로 배달해야 해. 경찰이 쫓아올 테니 45초 안에 가야 해!'",
                reward = 800,
                objectiveType = MissionObjectiveType.DELIVER_PACKAGE,
                objectiveX = 375f,
                objectiveY = 375f,
                timeLimitSec = 45,
                timeRemainingSec = 45
            ),
            Mission(
                id = "m4",
                title = "최종 단계: 경찰의 총격 탈출",
                description = "토니 보스: '빌어먹을! 헤이스트 계획이 탄로났다! 경찰들이 대거 출동했어. 45초 동안 살아남거나, 차고에 가서 현금 $150를 주고 도색 수리를 해서 경찰을 따돌려!'",
                reward = 1500,
                objectiveType = MissionObjectiveType.ESCAPE_POLICE,
                timeLimitSec = 45,
                timeRemainingSec = 45
            )
        )
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            var tickCounter = 0
            while (true) {
                delay(16) // ~60 FPS
                if (!_isPaused.value && !_playerState.value.isDead) {
                    updatePhysics()
                    tickCounter++
                    
                    // Natural healing/wanted decay/siren flashes every second
                    if (tickCounter % 60 == 0) {
                        handlePeriodicUpdates()
                    }
                }
            }
        }
    }

    // Handles movement inputs from virtual pads
    fun updateMoveInput(dx: Float, dy: Float) {
        moveDx = dx
        moveDy = dy
    }

    // Bullet trigger
    fun pressShoot() {
        val player = _playerState.value
        if (player.isDead) return

        val now = System.currentTimeMillis()
        val weapon = player.currentWeapon
        if (now - lastShootTime < weapon.fireRateMs) return

        if (weapon != WeaponType.FIST) {
            val currentAmmo = player.ammo[weapon] ?: 0
            if (currentAmmo <= 0) {
                _screenMessage.value = "탄약이 부족합니다!"
                return
            }
            // Deduct ammo
            val updatedAmmo = player.ammo.toMutableMap()
            updatedAmmo[weapon] = currentAmmo - 1
            _playerState.value = player.copy(ammo = updatedAmmo)
        }

        lastShootTime = now
        triggerWeaponFire(player, weapon)
    }

    private fun triggerWeaponFire(player: PlayerState, weapon: WeaponType) {
        val playerAngleRad = Math.toRadians(player.angle.toDouble())
        val barrelOffsetX = (cos(playerAngleRad) * 20f).toFloat()
        val barrelOffsetY = (sin(playerAngleRad) * 20f).toFloat()

        val startX = player.x + barrelOffsetX
        val startY = player.y + barrelOffsetY

        _screenShake.value = when (weapon) {
            WeaponType.FIST -> 2f
            WeaponType.PISTOL -> 5f
            WeaponType.UZI -> 8f
            WeaponType.SHOTGUN -> 15f
        }

        if (weapon == WeaponType.FIST) {
            // Punch range melee attack
            spawnParticles(startX, startY, 3, Color.Yellow, speedScale = 1.5f, maxLife = 10)
            dealMeleeDamage(startX, startY, weapon.damage)
        } else if (weapon == WeaponType.SHOTGUN) {
            // Shotgun fires 3 spreading pellets
            val angles = listOf(-10f, 0f, 10f)
            val newBullets = mutableListOf<Bullet>()
            for (offsetAngle in angles) {
                val bulletAngleRad = Math.toRadians((player.angle + offsetAngle).toDouble())
                val dx = cos(bulletAngleRad).toFloat()
                val dy = sin(bulletAngleRad).toFloat()
                newBullets.add(
                    Bullet(
                        id = UUID.randomUUID().toString(),
                        x = startX,
                        y = startY,
                        dx = dx,
                        dy = dy,
                        speed = 18f,
                        damage = weapon.damage,
                        fromPlayer = true
                    )
                )
            }
            _bullets.value = _bullets.value + newBullets
            // Muzzle flash
            spawnParticles(startX, startY, 6, Color(0xFFFFB300), speedScale = 4f, maxLife = 12)
        } else {
            // Pistol/Uzi single shots
            val dx = cos(playerAngleRad).toFloat()
            val dy = sin(playerAngleRad).toFloat()
            val bullet = Bullet(
                id = UUID.randomUUID().toString(),
                x = startX,
                y = startY,
                dx = dx,
                dy = dy,
                speed = 22f,
                damage = weapon.damage,
                fromPlayer = true
            )
            _bullets.value = _bullets.value + listOf(bullet)
            // Muzzle flash
            spawnParticles(startX, startY, 4, Color(0xFFFFCC00), speedScale = 3f, maxLife = 10)
        }

        // Commit crime check
        if (weapon != WeaponType.FIST) {
            checkAndAlertPolice()
        }
    }

    private fun checkAndAlertPolice() {
        val p = _playerState.value
        // Alert gangsters or police nearby
        val npcsTemp = _npcs.value
        var alertedAnyone = false
        val updatedNPCs = npcsTemp.map { npc ->
            val dist = dist(p.x, p.y, npc.x, npc.y)
            if (dist < 450f && !npc.isDead) {
                alertedAnyone = true
                npc.copy(isAlerted = true)
            } else {
                npc
            }
        }
        _npcs.value = updatedNPCs

        // If police alert is close, raise wanted level to at least 1 star
        if (alertedAnyone && p.wantedLevel == 0) {
            val policeClose = npcsTemp.any { it.type == NPCType.POLICE && dist(p.x, p.y, it.x, it.y) < 500f && !it.isDead }
            if (policeClose) {
                _playerState.value = p.copy(wantedLevel = 1)
                _screenMessage.value = "수배 레벨 발동! 경찰들이 쫓아옵니다!"
            }
        }
    }

    private fun dealMeleeDamage(startX: Float, startY: Float, damage: Int) {
        val npcsTemp = _npcs.value
        val updatedNPCs = npcsTemp.map { npc ->
            val d = dist(startX, startY, npc.x, npc.y)
            if (d < 50f && !npc.isDead) {
                spawnParticles(npc.x, npc.y, 4, Color.Red, speedScale = 2f)
                val newHealth = npc.health - damage
                if (newHealth <= 0) {
                    addCash(25 + (0..30).random())
                    triggerCrimeIncrement(npc)
                    npc.copy(health = 0, isDead = true, isAlerted = false)
                } else {
                    npc.copy(health = newHealth, isAlerted = true)
                }
            } else {
                npc
            }
        }
        _npcs.value = updatedNPCs
    }

    private fun triggerCrimeIncrement(killedNpc: NPC) {
        val player = _playerState.value
        val starIncrease = when (killedNpc.type) {
            NPCType.CIVILIAN -> 1
            NPCType.GANGSTER -> 0 // minor crime to kill rival gangster
            NPCType.POLICE -> 2 // major crime!
        }
        val newStars = min(5, player.wantedLevel + starIncrease)
        if (newStars != player.wantedLevel) {
            _playerState.value = player.copy(wantedLevel = newStars)
            _screenMessage.value = "수배 레벨 상승: ${newStars}성 수배!"
        }
    }

    // Hijack car logic
    fun pressHijack() {
        val player = _playerState.value
        if (player.isDead) return

        if (player.inVehicleId != null) {
            // EXIT CAR
            val vehicle = _vehicles.value.find { it.id == player.inVehicleId }
            if (vehicle != null) {
                // Spawn player to the left of car
                val angleRad = Math.toRadians((vehicle.angle - 90).toDouble())
                val exitX = vehicle.x + (cos(angleRad) * 40f).toFloat()
                val exitY = vehicle.y + (sin(angleRad) * 40f).toFloat()
                
                // Keep inside bounds
                val finalX = exitX.coerceIn(50f, mapWidth - 50f)
                val finalY = exitY.coerceIn(50f, mapHeight - 50f)

                _playerState.value = player.copy(
                    inVehicleId = null,
                    x = finalX,
                    y = finalY,
                    angle = vehicle.angle
                )
                // Slow the car to 0
                _vehicles.value = _vehicles.value.map {
                    if (it.id == vehicle.id) it.copy(speed = 0f) else it
                }
                _screenMessage.value = "차량에서 하차했습니다."
            }
        } else {
            // ENTER / STEAL NEAREST CAR
            val nearest = _vehicles.value
                .filter { !it.isDestroyed }
                .map { it to dist(player.x, player.y, it.x, it.y) }
                .filter { it.second < 85f }
                .minByOrNull { it.second }

            if (nearest != null) {
                val car = nearest.first
                _playerState.value = player.copy(
                    inVehicleId = car.id,
                    x = car.x,
                    y = car.y
                )
                _screenMessage.value = "${car.type.displayName} 차량을 탈취했습니다!"

                // Crime trigger: Stealing police car gets instantly 2 stars, civilian car gets 1 star if police nearby
                if (car.isPolice) {
                    val stars = max(2, player.wantedLevel)
                    _playerState.value = _playerState.value.copy(wantedLevel = stars)
                    _screenMessage.value = "경찰차 탈취! 수배 레벨 발동!"
                } else if (player.wantedLevel == 0) {
                    // Check if police can see
                    val policeAround = _npcs.value.any { it.type == NPCType.POLICE && dist(player.x, player.y, it.x, it.y) < 400f }
                    if (policeAround) {
                        _playerState.value = _playerState.value.copy(wantedLevel = 1)
                        _screenMessage.value = "절도 목격됨! 경찰 출동!"
                    }
                }

                // Verify mission objective (STEAL_CAR)
                val active = _activeMission.value
                if (active != null && active.objectiveType == MissionObjectiveType.STEAL_CAR) {
                    if (car.type.name == active.objectiveTargetId) {
                        _screenMessage.value = "작전 차량 확보 완료! 은신처(Safehouse) 차고로 이 차를 배달하세요!"
                    }
                }
            } else {
                _screenMessage.value = "가까운 곳에 탈취할 수 있는 차량이 없습니다."
            }
        }
    }

    fun cycleWeapon() {
        val player = _playerState.value
        val weapons = WeaponType.values()
        val currentIndex = weapons.indexOf(player.currentWeapon)
        var nextIndex = (currentIndex + 1) % weapons.size
        
        // Find next unlocked weapon (weapon that has ammo, or fist)
        var found = false
        for (i in 0 until weapons.size) {
            val wp = weapons[nextIndex]
            if (wp == WeaponType.FIST || (player.ammo[wp] ?: 0) > 0) {
                _playerState.value = player.copy(currentWeapon = wp)
                found = true
                break
            }
            nextIndex = (nextIndex + 1) % weapons.size
        }
        if (!found) {
            _playerState.value = player.copy(currentWeapon = WeaponType.FIST)
        }
    }

    private fun updatePhysics() {
        // Decrease screen shake
        if (_screenShake.value > 0f) {
            _screenShake.value = max(0f, _screenShake.value - 0.5f)
        }

        updatePlayerAndVehicleMovement()
        updateNpcAI()
        updateProjectiles()
        updateParticles()
        checkZoneInteractions()
        checkMissionProgress()
    }

    private fun updatePlayerAndVehicleMovement() {
        val player = _playerState.value
        val inCarId = player.inVehicleId

        if (inCarId != null) {
            // Driving physics
            val updatedCars = _vehicles.value.map { car ->
                if (car.id == inCarId) {
                    // Steer vehicle
                    val speed = car.speed
                    val turnFactor = if (speed >= 0) 1f else -1f
                    // Only turn when moving
                    val speedRatio = (speed / car.type.maxSpeed).coerceIn(-1f, 1f)
                    val rotDelta = moveDx * car.type.turnSpeed * (if (abs(speedRatio) > 0.1f) speedRatio else speedRatio * 2f) * turnFactor
                    val newAngle = (car.angle + rotDelta + 360f) % 360f

                    // Acceleration
                    var newSpeed = speed
                    if (moveDy > 0f) {
                        newSpeed += car.type.accel
                        if (newSpeed > car.type.maxSpeed) newSpeed = car.type.maxSpeed
                        // Spawn tire smoke occasionally
                        if (abs(moveDx) > 0.6f && (0..3).random() == 0) {
                            spawnParticles(car.x, car.y, 1, Color.DarkGray, speedScale = 0.5f, maxLife = 15)
                        }
                    } else if (moveDy < 0f) {
                        newSpeed -= car.type.deaccel * 2.5f
                        if (newSpeed < -car.type.maxSpeed * 0.4f) newSpeed = -car.type.maxSpeed * 0.4f
                    } else {
                        // Friction
                        if (newSpeed > 0f) {
                            newSpeed -= car.type.deaccel
                            if (newSpeed < 0f) newSpeed = 0f
                        } else if (newSpeed < 0f) {
                            newSpeed += car.type.deaccel
                            if (newSpeed > 0f) newSpeed = 0f
                        }
                    }

                    // Compute proposed positions
                    val rad = Math.toRadians(newAngle.toDouble())
                    val nextX = car.x + (cos(rad) * newSpeed).toFloat()
                    val nextY = car.y + (sin(rad) * newSpeed).toFloat()

                    // Slide along building collision walls
                    var finalX = car.x
                    var finalY = car.y

                    val boundingBoxX = GameRect(nextX - car.type.sizeWidth/2f, car.y - car.type.sizeHeight/2f, nextX + car.type.sizeWidth/2f, car.y + car.type.sizeHeight/2f)
                    val boundingBoxY = GameRect(car.x - car.type.sizeWidth/2f, nextY - car.type.sizeHeight/2f, car.x + car.type.sizeWidth/2f, nextY + car.type.sizeHeight/2f)

                    var hitX = false
                    var hitY = false

                    // Map edge constraints
                    if (nextX < 40f || nextX > mapWidth - 40f) {
                        hitX = true
                    } else {
                        if (checkBuildingCollision(boundingBoxX)) {
                            hitX = true
                        } else {
                            finalX = nextX
                        }
                    }

                    if (nextY < 40f || nextY > mapHeight - 40f) {
                        hitY = true
                    } else {
                        if (checkBuildingCollision(boundingBoxY)) {
                            hitY = true
                        } else {
                            finalY = nextY
                        }
                    }

                    // Collide damage
                    var finalHealth = car.health
                    var finalSpeed = newSpeed
                    if ((hitX || hitY) && abs(newSpeed) > 1.5f) {
                        finalSpeed = -newSpeed * 0.3f // Bounce slightly
                        val damage = (abs(newSpeed) * 4f).toInt()
                        finalHealth = max(0, car.health - damage)
                        _screenShake.value = 8f
                        // Spawn impact sparks
                        spawnParticles(car.x, car.y, 6, Color.Yellow, speedScale = 2.5f)
                    }

                    // Destroyed check
                    var isDestroyed = car.isDestroyed
                    if (finalHealth <= 0 && !isDestroyed) {
                        isDestroyed = true
                        triggerCarExplosion(car)
                    }

                    car.copy(
                        x = finalX,
                        y = finalY,
                        angle = newAngle,
                        speed = finalSpeed,
                        health = finalHealth,
                        isDestroyed = isDestroyed
                    )
                } else {
                    car
                }
            }

            _vehicles.value = updatedCars

            // Bind player coordinates to active car coordinates
            val playerCar = updatedCars.find { it.id == inCarId }
            if (playerCar != null) {
                if (playerCar.isDestroyed) {
                    // Kick player out of destroyed car with huge damage
                    _playerState.value = player.copy(
                        inVehicleId = null,
                        health = max(0, player.health - 60),
                        wantedLevel = max(1, player.wantedLevel)
                    )
                    _screenMessage.value = "차량이 폭발했습니다! 큰 충격을 받았습니다!"
                    checkPlayerDeath()
                } else {
                    _playerState.value = player.copy(
                        x = playerCar.x,
                        y = playerCar.y,
                        angle = playerCar.angle
                    )
                }
            }
        } else {
            // Walking physics
            val walkSpeed = 3.5f
            val rad = Math.toRadians(player.angle.toDouble())

            // Update angle based on walking joystick
            var targetAngle = player.angle
            val length = sqrt(moveDx * moveDx + moveDy * moveDy)
            if (length > 0.1f) {
                targetAngle = (Math.toDegrees(atan2(moveDy.toDouble(), moveDx.toDouble())).toFloat() + 360f) % 360f
                
                // Move player
                val nextX = player.x + moveDx * walkSpeed
                val nextY = player.y + moveDy * walkSpeed

                var finalX = player.x
                var finalY = player.y

                val size = 15f
                val boxX = GameRect(nextX - size, player.y - size, nextX + size, player.y + size)
                val boxY = GameRect(player.x - size, nextY - size, player.x + size, nextY + size)

                if (nextX >= size && nextX <= mapWidth - size && !checkBuildingCollision(boxX)) {
                    finalX = nextX
                }
                if (nextY >= size && nextY <= mapHeight - size && !checkBuildingCollision(boxY)) {
                    finalY = nextY
                }

                _playerState.value = player.copy(
                    x = finalX,
                    y = finalY,
                    angle = targetAngle
                )
            }
        }
    }

    private fun triggerCarExplosion(car: Vehicle) {
        spawnParticles(car.x, car.y, 25, Color(0xFFFF4500), speedScale = 5f, maxLife = 40) // Fire burst
        spawnParticles(car.x, car.y, 20, Color.DarkGray, speedScale = 3f, maxLife = 35) // Smoke
        _screenShake.value = 25f

        // Damage everything nearby in radius 150px
        val p = _playerState.value
        val dPlayer = dist(car.x, car.y, p.x, p.y)
        if (dPlayer < 150f) {
            val updatedHealth = max(0, p.health - 65)
            _playerState.value = p.copy(health = updatedHealth)
            checkPlayerDeath()
        }

        // Damage other npcs
        _npcs.value = _npcs.value.map { npc ->
            val dNpc = dist(car.x, car.y, npc.x, npc.y)
            if (dNpc < 150f && !npc.isDead) {
                spawnParticles(npc.x, npc.y, 5, Color.Red)
                npc.copy(health = 0, isDead = true)
            } else {
                npc
            }
        }
    }

    private fun checkPlayerDeath() {
        if (_playerState.value.health <= 0) {
            _playerState.value = _playerState.value.copy(isDead = true, inVehicleId = null)
            _screenMessage.value = "사망하셨습니다!\n화면 하단의 [재시작] 버튼을 눌러 다시 도전하세요."
            // Deduct money penalty
            val penalty = (_playerState.value.cash * 0.2f).toInt()
            _playerState.value = _playerState.value.copy(cash = max(0, _playerState.value.cash - penalty))
            saveProgress()
        }
    }

    private fun checkBuildingCollision(rect: GameRect): Boolean {
        for (b in buildings) {
            if (rect.intersects(b)) return true
        }
        return false
    }

    private fun updateNpcAI() {
        val player = _playerState.value
        val updatedNPCs = _npcs.value.map { npc ->
            if (npc.isDead) return@map npc

            var nx = npc.x
            var ny = npc.y
            var nAngle = npc.angle
            var nAlerted = npc.isAlerted
            var lastShot = npc.lastShotTime

            val distToPlayer = dist(npc.x, npc.y, player.x, player.y)

            // Alerted if player has wanted level and npc is police, or gangster within sight
            if (player.wantedLevel > 0 && npc.type == NPCType.POLICE) {
                nAlerted = true
            }

            if (nAlerted && (npc.type == NPCType.POLICE || npc.type == NPCType.GANGSTER)) {
                // Chase / Shoot player
                val dx = player.x - npc.x
                val dy = player.y - npc.y
                val angleToPlayer = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                nAngle = angleToPlayer

                if (distToPlayer > 180f) {
                    // Move closer
                    val speed = npc.speed * 1.5f
                    val rad = Math.toRadians(angleToPlayer.toDouble())
                    val nextX = npc.x + (cos(rad) * speed).toFloat()
                    val nextY = npc.y + (sin(rad) * speed).toFloat()

                    val size = 12f
                    val boxX = GameRect(nextX - size, npc.y - size, nextX + size, npc.y + size)
                    val boxY = GameRect(npc.x - size, nextY - size, npc.x + size, nextY + size)

                    if (!checkBuildingCollision(boxX)) nx = nextX
                    if (!checkBuildingCollision(boxY)) ny = nextY
                } else if (distToPlayer < 75f) {
                    // Backpedal slightly
                    val speed = npc.speed * 0.8f
                    val rad = Math.toRadians((angleToPlayer + 180f).toDouble())
                    nx = (npc.x + (cos(rad) * speed).toFloat()).coerceIn(50f, mapWidth - 50f)
                    ny = (npc.y + (sin(rad) * speed).toFloat()).coerceIn(50f, mapHeight - 50f)
                }

                // Shoot at player
                val now = System.currentTimeMillis()
                if (distToPlayer < 350f && now - lastShot > 1200L) {
                    lastShot = now
                    triggerNpcShoot(npc)
                }
            } else {
                // Wander around sidewalks
                var tx = npc.targetX
                var ty = npc.targetY

                if (tx == null || ty == null || dist(npc.x, npc.y, tx, ty) < 20f) {
                    // Choose a new point nearby
                    val isRoadY = (0..1).random() == 0
                    val offset = (-200..200).random().toFloat()
                    tx = (npc.x + offset).coerceIn(50f, mapWidth - 50f)
                    ty = (npc.y + offset).coerceIn(50f, mapHeight - 50f)
                }

                val dx = tx - npc.x
                val dy = ty - npc.y
                val angleToTarget = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                nAngle = angleToTarget

                val rad = Math.toRadians(angleToTarget.toDouble())
                val nextX = npc.x + (cos(rad) * npc.speed).toFloat()
                val nextY = npc.y + (sin(rad) * npc.speed).toFloat()

                val size = 10f
                val boxX = GameRect(nextX - size, npc.y - size, nextX + size, npc.y + size)
                val boxY = GameRect(npc.x - size, nextY - size, npc.x + size, nextY + size)

                if (!checkBuildingCollision(boxX)) nx = nextX else tx = null
                if (!checkBuildingCollision(boxY)) ny = nextY else ty = null

                npc.copy(x = nx, y = ny, angle = nAngle, targetX = tx, targetY = ty, isAlerted = nAlerted, lastShotTime = lastShot)
            }

            npc.copy(x = nx, y = ny, angle = nAngle, isAlerted = nAlerted, lastShotTime = lastShot)
        }
        _npcs.value = updatedNPCs
    }

    private fun triggerNpcShoot(npc: NPC) {
        val angleRad = Math.toRadians(npc.angle.toDouble())
        val startX = npc.x + (cos(angleRad) * 15f).toFloat()
        val startY = npc.y + (sin(angleRad) * 15f).toFloat()

        val bullet = Bullet(
            id = UUID.randomUUID().toString(),
            x = startX,
            y = startY,
            dx = cos(angleRad).toFloat(),
            dy = sin(angleRad).toFloat(),
            speed = 12f,
            damage = if (npc.type == NPCType.POLICE) 10 else 6,
            fromPlayer = false,
            ownerId = npc.id
        )

        _bullets.value = _bullets.value + listOf(bullet)
        spawnParticles(startX, startY, 2, Color.White, speedScale = 2f, maxLife = 8)
    }

    private fun updateProjectiles() {
        val bulletsTemp = _bullets.value
        val npcsTemp = _npcs.value
        val player = _playerState.value

        val activeBullets = mutableListOf<Bullet>()

        for (bullet in bulletsTemp) {
            val nextX = bullet.x + bullet.dx * bullet.speed
            val nextY = bullet.y + bullet.dy * bullet.speed

            // Out of bounds check
            if (nextX < 0f || nextX > mapWidth || nextY < 0f || nextY > mapHeight) {
                continue
            }

            // Building collision check
            val bulletRect = GameRect(nextX - 2f, nextY - 2f, nextX + 2f, nextY + 2f)
            if (checkBuildingCollision(bulletRect)) {
                // Spawn impact spark particles
                spawnParticles(nextX, nextY, 3, Color.Yellow, speedScale = 1.5f, maxLife = 10)
                continue
            }

            var hit = false

            if (bullet.fromPlayer) {
                // Bullet from player -> check NPC collision
                val hitNpc = npcsTemp.find { !it.isDead && it.getBoundingBox().contains(nextX, nextY) }
                if (hitNpc != null) {
                    hit = true
                    spawnParticles(nextX, nextY, 5, Color.Red, speedScale = 2f)
                    
                    // Damage NPC
                    val updatedNPCs = _npcs.value.map { n ->
                        if (n.id == hitNpc.id) {
                            val newHealth = n.health - bullet.damage
                            if (newHealth <= 0) {
                                addCash(50 + (0..50).random())
                                triggerCrimeIncrement(n)
                                n.copy(health = 0, isDead = true, isAlerted = false)
                            } else {
                                n.copy(health = newHealth, isAlerted = true)
                            }
                        } else {
                            n
                        }
                    }
                    _npcs.value = updatedNPCs
                }
            } else {
                // Bullet from NPC -> check Player collision
                val inVehicle = player.inVehicleId != null
                val targetRect = if (inVehicle) {
                    _vehicles.value.find { it.id == player.inVehicleId }?.getBoundingBox()
                } else {
                    GameRect(player.x - 14f, player.y - 14f, player.x + 14f, player.y + 14f)
                }

                if (targetRect != null && targetRect.contains(nextX, nextY)) {
                    hit = true
                    if (inVehicle) {
                        // Damage car
                        val updatedCars = _vehicles.value.map { car ->
                            if (car.id == player.inVehicleId) {
                                val newHealth = max(0, car.health - bullet.damage / 2)
                                spawnParticles(nextX, nextY, 4, Color.LightGray, speedScale = 1.5f)
                                var isDestroyed = car.isDestroyed
                                if (newHealth <= 0 && !isDestroyed) {
                                    isDestroyed = true
                                    triggerCarExplosion(car)
                                }
                                car.copy(health = newHealth, isDestroyed = isDestroyed)
                            } else {
                                car
                            }
                        }
                        _vehicles.value = updatedCars
                    } else {
                        // Damage Player
                        spawnParticles(nextX, nextY, 5, Color.Red, speedScale = 2f)
                        _screenShake.value = 10f
                        var armorDeduct = bullet.damage
                        var finalArmor = player.armor
                        var finalHealth = player.health

                        if (player.armor > 0) {
                            finalArmor = max(0, player.armor - armorDeduct)
                            val excess = armorDeduct - player.armor
                            if (excess > 0) {
                                finalHealth = max(0, player.health - excess)
                            }
                        } else {
                            finalHealth = max(0, player.health - armorDeduct)
                        }

                        _playerState.value = player.copy(health = finalHealth, armor = finalArmor)
                        checkPlayerDeath()
                    }
                }
            }

            if (!hit) {
                activeBullets.add(bullet.copy(x = nextX, y = nextY))
            }
        }

        _bullets.value = activeBullets
    }

    private fun updateParticles() {
        val currentParticles = _particles.value
        val updated = mutableListOf<Particle>()
        for (p in currentParticles) {
            val newLife = p.life - 1
            if (newLife > 0) {
                val alpha = newLife.toFloat() / p.maxLife.toFloat()
                updated.add(
                    p.copy(
                        x = p.x + p.vx,
                        y = p.y + p.vy,
                        life = newLife,
                        alpha = alpha
                    )
                )
            }
        }
        _particles.value = updated
    }

    private fun checkZoneInteractions() {
        val player = _playerState.value
        
        // Safehouse Check (Pay 'N' Spray and Repair)
        val inSafehouse = safehouseZone.contains(player.x, player.y)
        _isGarageOpen.value = inSafehouse && player.inVehicleId != null

        // Ammu-Nation Shop Check
        val inAmmunation = ammunationZone.contains(player.x, player.y)
        _isShopOpen.value = inAmmunation && player.inVehicleId == null

        // Tony Boss Check
        val inTonyBoss = tonyBossZone.contains(player.x, player.y)
        _isBossMenuOpen.value = inTonyBoss && player.inVehicleId == null
    }

    private fun checkMissionProgress() {
        val active = _activeMission.value ?: return
        val player = _playerState.value

        // Decrease timer if exists
        var isFailed = active.isFailed
        var timeRemaining = active.timeRemainingSec

        if (active.timeLimitSec != null && timeRemaining != null) {
            // Note: timeRemaining is updated in handlePeriodicUpdates (once per sec)
            if (timeRemaining <= 0) {
                isFailed = true
                _activeMission.value = active.copy(isFailed = true)
                _screenMessage.value = "미션 실패! 시간이 초과되었습니다."
            }
        }

        if (isFailed) return

        when (active.objectiveType) {
            MissionObjectiveType.STEAL_CAR -> {
                // Deliver sports car to Safehouse
                if (player.inVehicleId != null) {
                    val car = _vehicles.value.find { it.id == player.inVehicleId }
                    if (car != null && car.type.name == active.objectiveTargetId) {
                        // Check if player parked in Safehouse Garage zone
                        if (safehouseZone.contains(car.x, car.y)) {
                            // Mission complete!
                            completeActiveMission()
                        }
                    }
                }
            }
            MissionObjectiveType.ELIMINATE_TARGET -> {
                // Eliminate gangsters (count of active gangsters in alleyways)
                val gangstersLeft = _npcs.value.count { it.type == NPCType.GANGSTER && !it.isDead }
                if (gangstersLeft <= 1) { // eliminated rival target density
                    completeActiveMission()
                }
            }
            MissionObjectiveType.DELIVER_PACKAGE -> {
                // Deliver coordinates to Safehouse
                if (safehouseZone.contains(player.x, player.y)) {
                    completeActiveMission()
                }
            }
            MissionObjectiveType.ESCAPE_POLICE -> {
                // Survive and clear wanted level
                if (player.wantedLevel == 0 && (active.timeRemainingSec ?: 0) <= 1) {
                    completeActiveMission()
                }
            }
        }
    }

    private fun completeActiveMission() {
        val active = _activeMission.value ?: return
        addCash(active.reward)
        _screenMessage.value = "축하합니다! 미션 완료!\n보상: $${active.reward}을 획득했습니다!"
        
        // Mark as completed in missions tree
        _missions.value = _missions.value.map {
            if (it.id == active.id) it.copy(isCompleted = true) else it
        }
        
        _activeMission.value = null
        saveProgress()
    }

    private fun handlePeriodicUpdates() {
        val player = _playerState.value

        // Timer decrease for active mission
        val active = _activeMission.value
        if (active != null && active.timeLimitSec != null) {
            val rem = active.timeRemainingSec ?: active.timeLimitSec
            val newRem = max(0, rem - 1)
            _activeMission.value = active.copy(timeRemainingSec = newRem)
            
            // final escape police trigger check
            if (active.objectiveType == MissionObjectiveType.ESCAPE_POLICE && newRem == 0) {
                if (player.wantedLevel == 0) {
                    completeActiveMission()
                } else {
                    _activeMission.value = active.copy(isFailed = true, timeRemainingSec = 0)
                    _screenMessage.value = "미션 실패! 경찰 추격을 완전히 따돌리지 못했습니다."
                }
            }
        }

        // Wanted level natural decay if out of sight
        if (player.wantedLevel > 0 && active?.id != "m4") {
            val policeNearby = _npcs.value.any { it.type == NPCType.POLICE && dist(player.x, player.y, it.x, it.y) < 550f && !it.isDead }
            if (!policeNearby) {
                // Decrease wanted level slowly if out of sight
                val nextWanted = max(0, player.wantedLevel - 1)
                _playerState.value = player.copy(wantedLevel = nextWanted)
                if (nextWanted == 0) {
                    _screenMessage.value = "경찰 상황 해제. 안전합니다."
                } else {
                    _screenMessage.value = "수배 레벨이 ${nextWanted}성으로 하락 중입니다..."
                }
            }
        }

        // Spawn/maintain police vehicles if wanted level is active
        if (player.wantedLevel > 0) {
            val currentPoliceVehicles = _vehicles.value.count { it.isPolice && !it.isDestroyed }
            val targetPoliceCars = when (player.wantedLevel) {
                1 -> 1
                2 -> 2
                3 -> 3
                else -> 4
            }

            if (currentPoliceVehicles < targetPoliceCars) {
                // Spawn a new police vehicle nearby
                val offsetAngle = (0..359).random().toDouble()
                val dist = 550f
                val spawnX = (player.x + cos(Math.toRadians(offsetAngle)) * dist).toFloat().coerceIn(100f, mapWidth - 100f)
                val spawnY = (player.y + sin(Math.toRadians(offsetAngle)) * dist).toFloat().coerceIn(100f, mapHeight - 100f)
                
                // Add
                val pCar = Vehicle(
                    id = UUID.randomUUID().toString(),
                    type = VehicleType.POLICE,
                    x = spawnX,
                    y = spawnY,
                    angle = (0..359).random().toFloat(),
                    isPolice = true,
                    color = Color.White
                )
                _vehicles.value = _vehicles.value + pCar
            }

            // Spawn police officers on foot
            val currentPoliceOfficers = _npcs.value.count { it.type == NPCType.POLICE && !it.isDead }
            val targetOfficers = player.wantedLevel * 2
            if (currentPoliceOfficers < targetOfficers) {
                val offsetAngle = (0..359).random().toDouble()
                val dist = 500f
                val spawnX = (player.x + cos(Math.toRadians(offsetAngle)) * dist).toFloat().coerceIn(100f, mapWidth - 100f)
                val spawnY = (player.y + sin(Math.toRadians(offsetAngle)) * dist).toFloat().coerceIn(100f, mapHeight - 100f)
                
                val pOfficer = NPC(
                    id = UUID.randomUUID().toString(),
                    type = NPCType.POLICE,
                    x = spawnX,
                    y = spawnY,
                    angle = 0f,
                    health = 60,
                    maxHealth = 60,
                    speed = 1.4f,
                    isAlerted = true
                )
                _npcs.value = _npcs.value + pOfficer
            }
        }

        // Control police vehicle AI - chase the player!
        if (player.wantedLevel > 0) {
            _vehicles.value = _vehicles.value.map { car ->
                if (car.isPolice && !car.isDestroyed && car.id != player.inVehicleId) {
                    val dPlayer = dist(car.x, car.y, player.x, player.y)
                    if (dPlayer < 750f) {
                        // Drive towards player
                        val dx = player.x - car.x
                        val dy = player.y - car.y
                        val targetAngle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                        
                        // Slowly steer towards player
                        var newAngle = car.angle
                        val angleDiff = (targetAngle - car.angle + 540f) % 360f - 180f
                        newAngle += if (angleDiff > 0) min(car.type.turnSpeed, angleDiff) else max(-car.type.turnSpeed, angleDiff)
                        
                        // Accelerate towards player
                        var spd = car.speed + car.type.accel
                        if (spd > car.type.maxSpeed * 0.9f) spd = car.type.maxSpeed * 0.9f

                        // Update coordinates with collision check
                        val rad = Math.toRadians(newAngle.toDouble())
                        val nextX = car.x + (cos(rad) * spd).toFloat()
                        val nextY = car.y + (sin(rad) * spd).toFloat()

                        var fx = car.x
                        var fy = car.y
                        val boxX = GameRect(nextX - car.type.sizeWidth/2f, car.y - car.type.sizeHeight/2f, nextX + car.type.sizeWidth/2f, car.y + car.type.sizeHeight/2f)
                        val boxY = GameRect(car.x - car.type.sizeWidth/2f, nextY - car.type.sizeHeight/2f, car.x + car.type.sizeWidth/2f, nextY + car.type.sizeHeight/2f)

                        if (!checkBuildingCollision(boxX)) fx = nextX else spd = -spd * 0.3f
                        if (!checkBuildingCollision(boxY)) fy = nextY else spd = -spd * 0.3f

                        car.copy(x = fx, y = fy, angle = newAngle, speed = spd)
                    } else {
                        car
                    }
                } else {
                    car
                }
            }
        }
    }

    // Helper math utilities
    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    fun spawnParticles(x: Float, y: Float, count: Int, color: Color, speedScale: Float = 3f, maxLife: Int = 20) {
        val list = mutableListOf<Particle>()
        for (i in 0 until count) {
            val angle = (Math.random() * 360).toFloat()
            val rad = Math.toRadians(angle.toDouble())
            val spd = (Math.random() * speedScale).toFloat() + 0.5f
            list.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (cos(rad) * spd).toFloat(),
                    vy = (sin(rad) * spd).toFloat(),
                    color = color,
                    size = (Math.random() * 8f).toFloat() + 4f,
                    alpha = 1f,
                    life = maxLife,
                    maxLife = maxLife
                )
            )
        }
        _particles.value = _particles.value + list
    }

    // Helper adding cash safely
    fun addCash(amount: Int) {
        _playerState.value = _playerState.value.copy(cash = _playerState.value.cash + amount)
        saveProgress()
    }

    // Spend cash safely
    fun trySpendCash(amount: Int): Boolean {
        val player = _playerState.value
        if (player.cash >= amount) {
            _playerState.value = player.copy(cash = player.cash - amount)
            saveProgress()
            return true
        }
        return false
    }

    // Shop actions
    fun buyAmmunationItem(weapon: WeaponType) {
        if (weapon == WeaponType.FIST) return

        val cost = weapon.cost
        if (trySpendCash(cost)) {
            val player = _playerState.value
            val currentAmmo = player.ammo[weapon] ?: 0
            val addedAmmo = when (weapon) {
                WeaponType.PISTOL -> 30
                WeaponType.UZI -> 100
                WeaponType.SHOTGUN -> 12
                else -> 0
            }
            val updatedAmmo = player.ammo.toMutableMap()
            updatedAmmo[weapon] = min(weapon.maxAmmo, currentAmmo + addedAmmo)
            _playerState.value = player.copy(
                ammo = updatedAmmo,
                currentWeapon = weapon // auto-equip
            )
            _screenMessage.value = "${weapon.displayName} 무기와 탄약을 구매했습니다!"
        } else {
            _screenMessage.value = "현금이 부족합니다! 미션이나 시민 사냥으로 돈을 모으세요!"
        }
    }

    fun buyHealthRefill() {
        val cost = 80
        if (trySpendCash(cost)) {
            _playerState.value = _playerState.value.copy(health = 100, armor = 100)
            _screenMessage.value = "체력과 아머를 풀 리필했습니다!"
        } else {
            _screenMessage.value = "현금이 부족합니다!"
        }
    }

    // Safehouse actions
    fun repairVehicle() {
        val player = _playerState.value
        val carId = player.inVehicleId ?: return
        val cost = 100
        if (trySpendCash(cost)) {
            _vehicles.value = _vehicles.value.map { car ->
                if (car.id == carId) car.copy(health = car.maxHealth, isDestroyed = false) else car
            }
            _screenMessage.value = "차량을 완벽히 수리했습니다!"
        } else {
            _screenMessage.value = "수리비 $100가 부족합니다!"
        }
    }

    fun resprayVehicle() {
        val player = _playerState.value
        val carId = player.inVehicleId ?: return
        val cost = 150
        if (trySpendCash(cost)) {
            val randomColors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.Cyan, Color.White)
            _vehicles.value = _vehicles.value.map { car ->
                if (car.id == carId) car.copy(color = randomColors.random()) else car
            }
            _playerState.value = player.copy(wantedLevel = 0)
            _screenMessage.value = "차량이 완벽하게 도색되어 수배 레벨이 클리어되었습니다!"
        } else {
            _screenMessage.value = "도색비 $150가 부족합니다!"
        }
    }

    // Mission controls
    fun triggerMission(missionId: String) {
        val selected = _missions.value.find { it.id == missionId } ?: return
        
        // Spawn/reset mission elements
        if (missionId == "m3") {
            // instant package position setup
            _playerState.value = _playerState.value.copy(wantedLevel = 2) // police alert
        } else if (missionId == "m4") {
            // instant level 3 chase
            _playerState.value = _playerState.value.copy(wantedLevel = 3)
        }

        _activeMission.value = selected.copy(
            timeRemainingSec = selected.timeLimitSec
        )
        _isBossMenuOpen.value = false
        _screenMessage.value = "미션 시작!\n'${selected.title}'"
    }

    fun cancelActiveMission() {
        _activeMission.value = null
        _screenMessage.value = "진행 중이던 미션을 취소했습니다."
    }

    fun closeShop() {
        _isShopOpen.value = false
    }

    fun closeGarage() {
        _isGarageOpen.value = false
    }

    fun closeBossMenu() {
        _isBossMenuOpen.value = false
    }

    fun clearScreenMessage() {
        _screenMessage.value = null
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }
}

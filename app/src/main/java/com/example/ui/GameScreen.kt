package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.viewmodel.GameViewModel
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val playerState by viewModel.playerState.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val npcs by viewModel.npcs.collectAsState()
    val bullets by viewModel.bullets.collectAsState()
    val particles by viewModel.particles.collectAsState()
    
    val missions by viewModel.missions.collectAsState()
    val activeMission by viewModel.activeMission.collectAsState()
    
    val isPaused by viewModel.isPaused.collectAsState()
    val isShopOpen by viewModel.isShopOpen.collectAsState()
    val isGarageOpen by viewModel.isGarageOpen.collectAsState()
    val isBossMenuOpen by viewModel.isBossMenuOpen.collectAsState()
    val screenMessage by viewModel.screenMessage.collectAsState()
    val screenShake by viewModel.screenShake.collectAsState()

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Pulse effects for police/wanted levels
    var pulseSirenState by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(180)
            pulseSirenState = !pulseSirenState
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .drawBehind {
                // Draw a subtle radial gradient
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1A1A1A), Color(0xFF000000)),
                        center = center,
                        radius = max(size.width, size.height) * 0.7f
                    )
                )
                
                // Draw a subtle dot grid pattern
                val dotSpacing = 20.dp.toPx()
                val dotRadius = 1.dp.toPx()
                val cols = (size.width / dotSpacing).toInt() + 1
                val rows = (size.height / dotSpacing).toInt() + 1
                for (col in 0..cols) {
                    for (row in 0..rows) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = dotRadius,
                            center = Offset(col * dotSpacing, row * dotSpacing)
                        )
                    }
                }
            }
    ) {
        // --- GAME CANVAS (OPEN WORLD RENDERER) ---
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val viewWidth = constraints.maxWidth.toFloat()
            val viewHeight = constraints.maxHeight.toFloat()
            val halfW = viewWidth / 2f
            val halfH = viewHeight / 2f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("game_canvas")
            ) {
                // Apply screen shake offset
                val shakeX = if (screenShake > 0) (Math.random() * screenShake - screenShake / 2).toFloat() else 0f
                val shakeY = if (screenShake > 0) (Math.random() * screenShake - screenShake / 2).toFloat() else 0f

                // Camera translation centered on the Player
                val camX = halfW - playerState.x + shakeX
                val camY = halfH - playerState.y + shakeY

                // Helper to check if a world coordinate is off-screen (for culling to improve performance)
                fun isOffscreen(x: Float, y: Float, radius: Float): Boolean {
                    val screenX = x + camX
                    val screenY = y + camY
                    return screenX < -radius || screenX > viewWidth + radius || screenY < -radius || screenY > viewHeight + radius
                }

                drawContext.canvas.save()
                drawContext.canvas.translate(camX, camY)

                // 1. Draw Asphalt Ground
                drawRect(
                    color = Color(0xFF141A22),
                    topLeft = Offset(0f, 0f),
                    size = Size(viewModel.mapWidth, viewModel.mapHeight)
                )

                // 2. Draw Roads (horizontal and vertical lanes)
                val roadColor = Color(0xFF1E2633)
                val sidewalkColor = Color(0xFF333E52)
                val roadSize = viewModel.tileSize

                for (i in listOf(1, 5, 9)) {
                    val roadOffset = i * roadSize
                    // Horizontal roads
                    drawRect(
                        color = roadColor,
                        topLeft = Offset(0f, roadOffset),
                        size = Size(viewModel.mapWidth, roadSize)
                    )
                    // Sidewalks for horizontal roads
                    drawRect(color = sidewalkColor, topLeft = Offset(0f, roadOffset), size = Size(viewModel.mapWidth, 12f))
                    drawRect(color = sidewalkColor, topLeft = Offset(0f, roadOffset + roadSize - 12f), size = Size(viewModel.mapWidth, 12f))

                    // Vertical roads
                    drawRect(
                        color = roadColor,
                        topLeft = Offset(roadOffset, 0f),
                        size = Size(roadSize, viewModel.mapHeight)
                    )
                    // Sidewalks for vertical roads
                    drawRect(color = sidewalkColor, topLeft = Offset(roadOffset, 0f), size = Size(12f, viewModel.mapHeight))
                    drawRect(color = sidewalkColor, topLeft = Offset(roadOffset + roadSize - 12f, 0f), size = Size(12f, viewModel.mapHeight))
                }

                // Draw Dashed lane dividers down center of roads
                val roadIndices = listOf(1, 5, 9)
                for (idx in roadIndices) {
                    val centerOffset = idx * roadSize + roadSize / 2f
                    // Horizontal dividers
                    var cx = 0f
                    while (cx < viewModel.mapWidth) {
                        drawLine(
                            color = Color(0xFFFFCC00),
                            start = Offset(cx, centerOffset),
                            end = Offset(cx + 40f, centerOffset),
                            strokeWidth = 3f
                        )
                        cx += 80f
                    }
                    // Vertical dividers
                    var cy = 0f
                    while (cy < viewModel.mapHeight) {
                        drawLine(
                            color = Color(0xFFFFCC00),
                            start = Offset(centerOffset, cy),
                            end = Offset(centerOffset, cy + 40f),
                            strokeWidth = 3f
                        )
                        cy += 80f
                    }
                }

                // 3. Draw Special Site Markings
                // Safehouse (Green zone)
                drawRect(
                    color = Color(0x3300FF00),
                    topLeft = Offset(viewModel.safehouseZone.left, viewModel.safehouseZone.top),
                    size = Size(viewModel.safehouseZone.right - viewModel.safehouseZone.left, viewModel.safehouseZone.bottom - viewModel.safehouseZone.top)
                )
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(viewModel.safehouseZone.left, viewModel.safehouseZone.top),
                    size = Size(viewModel.safehouseZone.right - viewModel.safehouseZone.left, viewModel.safehouseZone.bottom - viewModel.safehouseZone.top),
                    style = Stroke(width = 6f)
                )

                // Ammu-Nation (Blue gun store zone)
                drawRect(
                    color = Color(0x330099FF),
                    topLeft = Offset(viewModel.ammunationZone.left, viewModel.ammunationZone.top),
                    size = Size(viewModel.ammunationZone.right - viewModel.ammunationZone.left, viewModel.ammunationZone.bottom - viewModel.ammunationZone.top)
                )
                drawRect(
                    color = Color(0xFF0099FF),
                    topLeft = Offset(viewModel.ammunationZone.left, viewModel.ammunationZone.top),
                    size = Size(viewModel.ammunationZone.right - viewModel.ammunationZone.left, viewModel.ammunationZone.bottom - viewModel.ammunationZone.top),
                    style = Stroke(width = 6f)
                )

                // Tony Boss Area (Red glowing meeting circle)
                drawCircle(
                    color = Color(0x44FF0033),
                    radius = 45f,
                    center = Offset(viewModel.tonyBossZone.left + 75f, viewModel.tonyBossZone.top + 75f)
                )
                drawCircle(
                    color = Color(0xFFFF0033),
                    radius = 45f,
                    center = Offset(viewModel.tonyBossZone.left + 75f, viewModel.tonyBossZone.top + 75f),
                    style = Stroke(width = 5f)
                )

                // 4. Draw 3D-extruded Buildings with window lights
                for (b in viewModel.buildings) {
                    if (isOffscreen(b.left, b.top, 250f)) continue

                    val extrudeX = 16f
                    val extrudeY = 16f

                    // Draw 3D wall shadow
                    drawRect(
                        color = Color(0xFF090D14),
                        topLeft = Offset(b.left + extrudeX, b.top + extrudeY),
                        size = Size(b.right - b.left, b.bottom - b.top)
                    )

                    // Draw Roof Top
                    drawRect(
                        color = Color(0xFF222B3B),
                        topLeft = Offset(b.left, b.top),
                        size = Size(b.right - b.left, b.bottom - b.top)
                    )
                    // Draw neon top borders
                    drawRect(
                        color = Color(0xFF4A5F80),
                        topLeft = Offset(b.left, b.top),
                        size = Size(b.right - b.left, b.bottom - b.top),
                        style = Stroke(width = 3f)
                    )

                    // Draw windows inside building
                    val winW = 8f
                    val winH = 12f
                    val stepX = 28f
                    val stepY = 32f
                    var currX = b.left + 18f
                    while (currX < b.right - 18f) {
                        var currY = b.top + 18f
                        while (currY < b.bottom - 18f) {
                            // Only light up some windows for realism
                            val lightsOn = (currX.toInt() * currY.toInt() % 7) > 2
                            drawRect(
                                color = if (lightsOn) Color(0xCCFFDD44) else Color(0x33101520),
                                topLeft = Offset(currX, currY),
                                size = Size(winW, winH)
                            )
                            currY += stepY
                        }
                        currX += stepX
                    }
                }

                // 5. Draw Active Vehicles (including player vehicle)
                for (car in vehicles) {
                    if (isOffscreen(car.x, car.y, 80f)) continue

                    rotate(degrees = car.angle, pivot = Offset(car.x, car.y)) {
                        val halfW = car.type.sizeWidth / 2f
                        val halfH = car.type.sizeHeight / 2f

                        // Draw Shadow
                        drawRoundRect(
                            color = Color(0x55000000),
                            topLeft = Offset(car.x - halfW + 4f, car.y - halfH + 4f),
                            size = Size(car.type.sizeWidth, car.type.sizeHeight),
                            cornerRadius = CornerRadius(6f, 6f)
                        )

                        // Draw Car Base Body
                        drawRoundRect(
                            color = if (car.isDestroyed) Color(0xFF333333) else car.color,
                            topLeft = Offset(car.x - halfW, car.y - halfH),
                            size = Size(car.type.sizeWidth, car.type.sizeHeight),
                            cornerRadius = CornerRadius(6f, 6f)
                        )

                        // Special details for police car
                        if (car.isPolice) {
                            // Police logo stripes
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(car.x - 10f, car.y - halfH),
                                size = Size(20f, car.type.sizeHeight)
                            )
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(car.x - 4f, car.y - halfH),
                                size = Size(8f, car.type.sizeHeight)
                            )
                        }

                        // Windshield / Glass Cabin
                        drawRoundRect(
                            color = Color(0xCC112233),
                            topLeft = Offset(car.x - 10f, car.y - halfH + 4f),
                            size = Size(18f, car.type.sizeHeight - 8f),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                        // Windshield highlights
                        drawRect(
                            color = Color(0x55FFFFFF),
                            topLeft = Offset(car.x - 8f, car.y - halfH + 5f),
                            size = Size(5f, car.type.sizeHeight - 10f)
                        )

                        // Engine Hood Lines
                        drawLine(
                            color = Color(0x22FFFFFF),
                            start = Offset(car.x + 8f, car.y - halfH + 4f),
                            end = Offset(car.x + 18f, car.y - halfH + 4f),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color(0x22FFFFFF),
                            start = Offset(car.x + 8f, car.y + halfH - 4f),
                            end = Offset(car.x + 18f, car.y + halfH - 4f),
                            strokeWidth = 2f
                        )

                        // Headlights / Tail lights
                        if (!car.isDestroyed) {
                            // Headlights
                            drawCircle(Color(0xFFFFFF99), 3f, Offset(car.x + halfW - 2f, car.y - halfH + 5f))
                            drawCircle(Color(0xFFFFFF99), 3f, Offset(car.x + halfW - 2f, car.y + halfH - 5f))
                            // Tail lights
                            drawRect(Color.Red, topLeft = Offset(car.x - halfW, car.y - halfH + 4f), size = Size(2f, 4f))
                            drawRect(Color.Red, topLeft = Offset(car.x - halfW, car.y + halfH - 8f), size = Size(2f, 4f))

                            // Siren flashes on Police Cruisers
                            if (car.isPolice) {
                                val sirenColor = if (pulseSirenState) Color.Red else Color.Blue
                                drawCircle(sirenColor, 6f, Offset(car.x, car.y))
                                drawCircle(Color.White, 3f, Offset(car.x, car.y))
                            }
                        }
                    }

                    // Headlight cone beams for the active driving car
                    if (playerState.inVehicleId == car.id && !car.isDestroyed) {
                        rotate(degrees = car.angle, pivot = Offset(car.x, car.y)) {
                            // Draw yellow gradient-like headlight cone
                            val path = Path().apply {
                                moveTo(car.x + car.type.sizeWidth / 2f, car.y)
                                lineTo(car.x + car.type.sizeWidth / 2f + 160f, car.y - 70f)
                                lineTo(car.x + car.type.sizeWidth / 2f + 160f, car.y + 70f)
                                close()
                            }
                            drawPath(
                                path = path,
                                color = Color(0x25FFFF55)
                            )
                        }
                    }
                }

                // 6. Draw NPCs (Pedestrians, Gangsters, Police Officers on foot)
                for (npc in npcs) {
                    if (isOffscreen(npc.x, npc.y, 40f)) continue

                    if (npc.isDead) {
                        // Blood puddle under corpse
                        drawCircle(Color(0x99AA0000), 16f, center = Offset(npc.x, npc.y))
                        drawCircle(Color(0x99AA0000), 8f, center = Offset(npc.x + 10f, npc.y + 4f))

                        rotate(degrees = npc.angle, pivot = Offset(npc.x, npc.y)) {
                            // Dead character drawn flat
                            drawCircle(Color.Gray, 11f, center = Offset(npc.x, npc.y))
                            // draw weapon fell off
                            drawLine(Color.DarkGray, start = Offset(npc.x + 8f, npc.y + 2f), end = Offset(npc.x + 20f, npc.y + 6f), strokeWidth = 3f)
                        }
                    } else {
                        // Alive NPC
                        rotate(degrees = npc.angle, pivot = Offset(npc.x, npc.y)) {
                            // Shadow
                            drawCircle(Color(0x44000000), 13f, center = Offset(npc.x + 2f, npc.y + 2f))

                            // Clothes Color based on NPCType
                            val shirtColor = when (npc.type) {
                                NPCType.CIVILIAN -> Color(0xFFE57373)
                                NPCType.GANGSTER -> Color(0xFFBA68C8)
                                NPCType.POLICE -> Color(0xFF1E88E5)
                            }
                            // Pants/Legs
                            drawCircle(Color(0xFF263238), 11f, center = Offset(npc.x, npc.y))
                            // Torso shirt
                            drawCircle(shirtColor, 10f, center = Offset(npc.x, npc.y))
                            // Head
                            drawCircle(Color(0xFFFFCC80), 6f, center = Offset(npc.x, npc.y))

                            // Alert indicator
                            if (npc.isAlerted) {
                                drawCircle(Color.Red, 2f, center = Offset(npc.x, npc.y))
                            }

                            // Hands / Weapon barrel
                            if (npc.type == NPCType.POLICE || npc.type == NPCType.GANGSTER) {
                                // Draw Pistol barrel pointing ahead
                                drawLine(
                                    color = Color.DarkGray,
                                    start = Offset(npc.x + 8f, npc.y + 4f),
                                    end = Offset(npc.x + 18f, npc.y + 4f),
                                    strokeWidth = 3f
                                )
                            }
                        }
                    }
                }

                // 7. Draw Player (on foot)
                if (playerState.inVehicleId == null) {
                    val p = playerState
                    if (p.isDead) {
                        drawCircle(Color(0x99AA0000), 18f, center = Offset(p.x, p.y))
                        drawCircle(Color.LightGray, 11f, center = Offset(p.x, p.y))
                    } else {
                        rotate(degrees = p.angle, pivot = Offset(p.x, p.y)) {
                            // Shadow
                            drawCircle(Color(0x55000000), 14f, center = Offset(p.x + 2f, p.y + 2f))
                            // Pants
                            drawCircle(Color(0xFF37474F), 12f, center = Offset(p.x, p.y))
                            // Hero leather jacket (Cyber punk yellow!)
                            drawCircle(Color(0xFFFFD600), 11f, center = Offset(p.x, p.y))
                            // Head
                            drawCircle(Color(0xFFFFCC80), 7f, center = Offset(p.x, p.y))

                            // Hair / sunglasses
                            drawRect(Color.Black, topLeft = Offset(p.x - 3f, p.y - 4f), size = Size(5f, 8f))

                            // Weapon barrel
                            if (p.currentWeapon != WeaponType.FIST) {
                                val gunLength = when (p.currentWeapon) {
                                    WeaponType.PISTOL -> 18f
                                    WeaponType.UZI -> 22f
                                    WeaponType.SHOTGUN -> 26f
                                    else -> 0f
                                }
                                drawLine(
                                    color = Color(0xFF1E1E1E),
                                    start = Offset(p.x + 8f, p.y + 5f),
                                    end = Offset(p.x + gunLength, p.y + 5f),
                                    strokeWidth = 3.5f
                                )
                            }
                        }
                    }
                }

                // 8. Draw Projectile Bullets
                for (bullet in bullets) {
                    if (isOffscreen(bullet.x, bullet.y, 10f)) continue
                    drawLine(
                        color = if (bullet.fromPlayer) Color(0xFFFFEE55) else Color(0xFFFF5555),
                        start = Offset(bullet.x - bullet.dx * 12f, bullet.y - bullet.dy * 12f),
                        end = Offset(bullet.x, bullet.y),
                        strokeWidth = 3f
                    )
                }

                // 9. Draw Particle FX
                for (part in particles) {
                    if (isOffscreen(part.x, part.y, 10f)) continue
                    drawCircle(
                        color = part.color.copy(alpha = part.alpha),
                        radius = part.size * part.alpha,
                        center = Offset(part.x, part.y)
                    )
                }

                drawContext.canvas.restore()
            }
        }

        // --- HUD OVERLAYS (TOP ACTION LABELS & STATS) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // MINIMAP & STATUS CARDS (Top Left Column as in HTML Design)
                Column(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // MINIMAP Canvas
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2D2D2D))
                            .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val mSize = size.width
                            val scale = mSize / 3000f

                            // Draw miniature map roads (simplified)
                            val roadIndices = listOf(1, 5, 9)
                            for (idx in roadIndices) {
                                val offset = idx * 250f * scale
                                val rW = 250f * scale
                                // Horizontal roads
                                drawRect(Color(0xFF2B3A52), Offset(0f, offset), Size(mSize, rW))
                                // Vertical roads
                                drawRect(Color(0xFF2B3A52), Offset(offset, 0f), Size(rW, mSize))
                            }

                            // Draw Special points
                            // Safehouse: Green Dot
                            drawCircle(Color.Green, 5f, Offset(375f * scale, 375f * scale))
                            // Weapon Shop: Blue Dot
                            drawCircle(Color(0xFF0099FF), 5f, Offset(2625f * scale, 2625f * scale))
                            // Tony Boss: Red Dot
                            drawCircle(Color.Red, 5f, Offset(1325f * scale, 1325f * scale))

                            // Draw NPC/Police positions on Minimap
                            for (npc in npcs) {
                                if (!npc.isDead) {
                                    val color = if (npc.type == NPCType.POLICE) Color.Blue else Color.White
                                    drawCircle(color, 2f, Offset(npc.x * scale, npc.y * scale))
                                }
                            }

                            // Draw Player Position as Pulsing Triangle/Circle
                            drawCircle(Color.Yellow, 4f, Offset(playerState.x * scale, playerState.y * scale))
                            drawCircle(Color.Yellow, 8f, Offset(playerState.x * scale, playerState.y * scale), style = Stroke(width = 1.5f))
                        }
                    }

                    // Mini Health & Armor progress bars directly underneath
                    Row(
                        modifier = Modifier.width(96.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Health bar (Green in design HTML)
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .weight(1f)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(playerState.health / 100f)
                                    .background(Color(0xFF4CAF50), RoundedCornerShape(50.dp))
                            )
                        }

                        // Armor bar (Blue in design HTML)
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .weight(1f)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(playerState.armor / 100f)
                                    .background(Color(0xFF0099FF), RoundedCornerShape(50.dp))
                            )
                        }
                    }
                }

                // HEALTH, ARMOR, CASH, WEAPON (Top Right Panel)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Massive extra bold Cash green text with drop shadow (from HTML theme)
                    Text(
                        text = "$${playerState.cash}",
                        color = Color(0xFF4CAF50),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(0f, 4f),
                                blurRadius = 4f
                            )
                        ),
                        modifier = Modifier
                            .testTag("cash_display")
                    )

                    // Subtitle tag: Clock + WANTED level with outline borders
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(50.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "21:45",
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(Color.White.copy(alpha = 0.4f), CircleShape)
                        )
                        Text(
                            text = "WANTED " + "★".repeat(playerState.wantedLevel.coerceIn(0, 5)) + "☆".repeat((5 - playerState.wantedLevel).coerceIn(0, 5)),
                            color = if (playerState.wantedLevel > 0) Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Active Weapon Card (matching HTML's weapon display box)
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1C1B1F), RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = playerState.currentWeapon.displayName.uppercase(),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                                val ammoText = if (playerState.currentWeapon == WeaponType.FIST) "👊" else {
                                    val currentAmmo = playerState.ammo[playerState.currentWeapon] ?: 0
                                    "$currentAmmo / ${playerState.currentWeapon.maxAmmo}"
                                }
                                Text(
                                    text = ammoText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = playerState.currentWeapon.emoji,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            // ACTIVE MISSION DISPLAY BOX
            if (activeMission != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE0A0A0A)),
                    border = BorderStroke(2.dp, Color(0xFFFFD700)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = activeMission!!.title.uppercase(),
                                    color = Color(0xFFFFD700),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black,
                                            offset = Offset(0f, 2f),
                                            blurRadius = 2f
                                        )
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(1.dp)
                                            .width(16.dp)
                                            .background(Color.White.copy(alpha = 0.3f))
                                    )
                                    Text(
                                        text = "MISSION ACTIVE",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                            }

                            if (activeMission!!.timeRemainingSec != null) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Red, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${activeMission!!.timeRemainingSec}s",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        Text(
                            text = when (activeMission!!.objectiveType) {
                                MissionObjectiveType.STEAL_CAR -> "작전 지시: 은신처 차고로 스포츠카 배달하기"
                                MissionObjectiveType.ELIMINATE_TARGET -> "작전 지시: 동부 골목의 라이벌 조직 소탕하기"
                                MissionObjectiveType.DELIVER_PACKAGE -> "작전 지시: 비밀 장부를 은신처로 긴급 운송하기"
                                MissionObjectiveType.ESCAPE_POLICE -> "작전 지시: 경찰 수배를 도색 수리로 따돌리고 생존하기"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // --- RETRO TYPEWRITER / NOTIFICATION MESSAGE ---
        AnimatedVisibility(
            visible = screenMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEE080C14)),
                border = BorderStroke(2.dp, Color.Cyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Alert",
                            tint = Color.Cyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "메시지 디스패치",
                            color = Color.Cyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = screenMessage ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Button(
                        onClick = { viewModel.clearScreenMessage() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("확인", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- AMMU-NATION SHOP DIALOG (MODAL OVERLAY) ---
        if (isShopOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { /* Block clicks background */ },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                    border = BorderStroke(2.dp, Color(0xFF0099FF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "AMMU-NATION",
                                    color = Color(0xFF0099FF),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "무기 및 장비 보급소",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { viewModel.closeShop() },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        Text(
                            text = "보유 캐시: $${playerState.cash}",
                            color = Color(0xFF4CAF50),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )

                        // Pistol Item
                        ShopItemRow(
                            title = "Pistol 권총 (30발 탄약)",
                            price = 150,
                            icon = "🔫",
                            enabled = playerState.cash >= 150,
                            onBuy = { viewModel.buyAmmunationItem(WeaponType.PISTOL) }
                        )

                        // Uzi Item
                        ShopItemRow(
                            title = "Uzi 기관총 (100발 탄약)",
                            price = 450,
                            icon = "🎚️",
                            enabled = playerState.cash >= 450,
                            onBuy = { viewModel.buyAmmunationItem(WeaponType.UZI) }
                        )

                        // Shotgun Item
                        ShopItemRow(
                            title = "Shotgun 산탄총 (12발 탄약)",
                            price = 700,
                            icon = "🕶️",
                            enabled = playerState.cash >= 700,
                            onBuy = { viewModel.buyAmmunationItem(WeaponType.SHOTGUN) }
                        )

                        // Health Refill
                        ShopItemRow(
                            title = "체력 및 방탄복 완벽 보충",
                            price = 80,
                            icon = "❤️",
                            enabled = playerState.cash >= 80,
                            onBuy = { viewModel.buyHealthRefill() }
                        )
                    }
                }
            }
        }

        // --- SAFE_HOUSE REPAIR/SPRAY GARAGE MENU ---
        if (isGarageOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                    border = BorderStroke(2.dp, Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PAY 'N' SPRAY",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "불법 도색 및 차량 수리점",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { viewModel.closeGarage() },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        Text(
                            text = "보유 캐시: $${playerState.cash}",
                            color = Color(0xFF4CAF50),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )

                        Button(
                            onClick = { viewModel.repairVehicle() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Text("차량 완벽 수리 ($100)", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }

                        Button(
                            onClick = { viewModel.resprayVehicle() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Text("도색 스프레이 및 수배 해제 ($150)", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // --- TONY BOSS MISSION GIVER SELECTION MENU ---
        if (isBossMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                    border = BorderStroke(2.dp, Color(0xFFFF3333)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TONY'S MISSIONS",
                                    color = Color(0xFFFF3333),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "조직 보스 '토니'의 비밀 지령",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { viewModel.closeBossMenu() },
                                modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        Text(
                            text = "수락할 위험 지령을 선택하십시오.",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 250.dp)
                        ) {
                            items(missions) { mission ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.triggerMission(mission.id) },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                                    border = BorderStroke(
                                        1.dp,
                                        if (mission.isCompleted) Color(0xFF4CAF50) else Color(0xFFFF3333).copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = mission.title,
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (mission.isCompleted) "완료" else "보상 $${mission.reward}",
                                                color = if (mission.isCompleted) Color(0xFF4CAF50) else Color(0xFFFFD700),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = mission.description,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- BACKPACK RESET & ACTION NOTIFIER (Death / Paused Overlay) ---
        if (playerState.isDead) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD990000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "WASTED\n(사망하셨습니다)",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 40.sp,
                        fontFamily = FontFamily.Serif
                    )
                    Text(
                        text = "현금 20%의 병원 수수료를 차감하고 차고 안전구역에서 다시 시작합니다.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Button(
                        onClick = { viewModel.resetGameWorld() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("재시작 하기", color = Color.Red, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // --- FLOATING LOCATION ZONE DISPLAY (Styled after HTML bottom location badge) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(50.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(50.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulsing dot
                val locationDotAlpha by rememberInfiniteTransition(label = "").animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = ""
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer(alpha = locationDotAlpha)
                        .background(Color(0xFF42A5F5), CircleShape)
                )

                val zoneName = when {
                    playerState.x < 1000f && playerState.y < 1000f -> "SAFEHOUSE SAFE ZONE"
                    playerState.x > 2000f && playerState.y > 2000f -> "WEAPONS DISTRICT"
                    playerState.x in 1000f..2000f && playerState.y in 1000f..2000f -> "TONY'S DOWNTOWN"
                    else -> "VESPUCCI BEACH"
                }

                Text(
                    text = zoneName,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }

        // --- VIRTUAL CONTROLLER HUD BOTTOM LAYER ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // LEFT SIDE: VIRTUAL ANALOG PAD (Movement / Steering)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "MOVEMENT",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    viewModel.updateMoveInput(0f, 0f)
                                },
                                onDragCancel = {
                                    viewModel.updateMoveInput(0f, 0f)
                                },
                                onDrag = { change, dragAmount ->
                                    val bounds = 56f
                                    val inputX = (change.position.x - bounds) / bounds
                                    val inputY = (change.position.y - bounds) / bounds
                                    
                                    val length = sqrt(inputX * inputX + inputY * inputY)
                                    val finalX = if (length > 1f) inputX / length else inputX
                                    val finalY = if (length > 1f) inputY / length else inputY

                                    viewModel.updateMoveInput(finalX, finalY)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Inner controller joystick handle (glassmorphism/radial glow)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
                    )
                }
            }

            // RIGHT SIDE: ACTION NEON ROUND BUTTONS (Shoot, Hijack, Weapon)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // SWITCH WEAPON BUTTON (Purple/lavender "JUMP" styled)
                    Button(
                        onClick = { viewModel.cycleWeapon() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD0BCFF).copy(alpha = 0.15f),
                            contentColor = Color(0xFFD0BCFF)
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .border(BorderStroke(1.5.dp, Color(0xFFD0BCFF).copy(alpha = 0.3f)), CircleShape)
                            .testTag("weapon_button"),
                        contentPadding = PaddingValues(0.dp),
                        shape = CircleShape
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "WEAP",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFD0BCFF)
                            )
                            Text(
                                text = playerState.currentWeapon.emoji,
                                fontSize = 16.sp
                            )
                        }
                    }

                    // STEAL / HIJACK ENTER/EXIT VEHICLE BUTTON (High contrast white action button styled)
                    Button(
                        onClick = { viewModel.pressHijack() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(58.dp)
                            .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
                            .testTag("hijack_button"),
                        contentPadding = PaddingValues(0.dp),
                        shape = CircleShape
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (playerState.inVehicleId != null) "EXIT" else "HIJACK",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Icon(
                                imageVector = if (playerState.inVehicleId != null) Icons.Default.DirectionsRun else Icons.Default.DirectionsCar,
                                contentDescription = "Hijack",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ACTION/BUY AMMO BUTTON (Visible if shop or safehouse can trigger)
                    val showAction = isShopOpen || isGarageOpen || isBossMenuOpen
                    if (showAction) {
                        Button(
                            onClick = {
                                // No action directly needed, shop triggers on screen click,
                                // but we show a helper reminder to access shop menus
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9900).copy(alpha = 0.15f),
                                contentColor = Color(0xFFFF9900)
                            ),
                            modifier = Modifier
                                .size(54.dp)
                                .border(BorderStroke(1.5.dp, Color(0xFFFF9900).copy(alpha = 0.3f)), CircleShape),
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "SHOP",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFF9900)
                                )
                                Icon(
                                    imageVector = Icons.Default.Store,
                                    contentDescription = "Store Action",
                                    tint = Color(0xFFFF9900),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // SHOOT BUTTON (Red/orange glowing FIRE button styled)
                    Button(
                        onClick = { viewModel.pressShoot() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF3333).copy(alpha = 0.2f),
                            contentColor = Color(0xFFFF3333)
                        ),
                        modifier = Modifier
                            .size(72.dp)
                            .border(BorderStroke(2.dp, Color(0xFFFF3333).copy(alpha = 0.5f)), CircleShape)
                            .testTag("shoot_button"),
                        contentPadding = PaddingValues(0.dp),
                        shape = CircleShape
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "FIRE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF3333),
                                letterSpacing = 1.sp
                            )
                            Icon(
                                imageVector = Icons.Default.TrackChanges,
                                contentDescription = "Shoot",
                                tint = Color(0xFFFF3333),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShopItemRow(
    title: String,
    price: Int,
    icon: String,
    enabled: Boolean,
    onBuy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
        border = BorderStroke(1.dp, if (enabled) Color(0xFF0099FF) else Color.White.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = icon, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "PRICE: $${price}",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Button(
                onClick = onBuy,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0099FF),
                    disabledContainerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "BUY",
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        }
    }
}

package com.example.coc_automator

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Port of execute_attack_sequence / placement helpers from automated_attack.py.
 * Coordinates are in Python "base" space and scaled via [ScreenGeometry].
 */
class CoCAttackAutomation(
    private val geometry: ScreenGeometry,
    private val tap: (Int, Int) -> Unit,
    private val controller: AutomationController,
    private val captureFullScreen: suspend () -> Bitmap?,
    private val coords: AttackCoordinateConfig = AttackCoordinateConfig.default(),
    /** Wait after troop deployment before End battle (chunked sleeps). */
    private val postDeployWaitMs: Long = 30_000L,
    private val resourceThreshold: Long = 500_000L,
    private val maxSearchAttempts: Int = 10,
    private val onStatus: (String) -> Unit = {},
    private val ocrCaptureSaver: OcrCaptureSaver? = null,
) {

    private fun status(msg: String) = onStatus(msg)

    private fun bx(x: Int) = geometry.x(x)
    private fun by(y: Int) = geometry.y(y)

    private suspend fun tapAt(baseX: Int, baseY: Int) {
        controller.waitUnpaused()
        val sx = bx(baseX)
        val sy = by(baseY)
        DebugLog.d("tapAt base=($baseX,$baseY) -> screen=($sx,$sy)")
        tap(sx, sy)
    }

    private suspend fun tapWithDeviation(
        baseX: Int,
        baseY: Int,
        deviation: Int = 2,
        maxRetries: Int = 1,
    ) {
        controller.attackTick()
        val x = baseX + Random.nextInt(-deviation, deviation + 1)
        val y = baseY + Random.nextInt(-deviation, deviation + 1)
        val sx = bx(x)
        val sy = by(y)
        DebugLog.d("tapDev base=($baseX,$baseY)->($x,$y) screen=($sx,$sy) dev=$deviation")
        tap(sx, sy)
    }

    private data class CardPositions(
        val goblin: Int,
        val siege: Int,
        val king: Int,
        val queen: Int,
        val warden: Int,
        val champion: Int,
        val jumpSpell: Int,
        val quakeSpell: Int,
    )

    private fun calculateCardPositions(heroCount: Int): CardPositions {
        val c = coords.cardBar
        val cardWidth = c.cardWidth
        val cardGap = c.cardGap
        val missingHeroes = 4 - heroCount.coerceIn(1, 4)
        val shift = if (missingHeroes > 0) missingHeroes * (cardWidth + cardGap) else 0
        return CardPositions(
            goblin = c.goblin,
            siege = c.siege,
            king = c.king,
            queen = c.queen,
            warden = c.warden,
            champion = c.champion,
            jumpSpell = c.jumpSpellBase - shift,
            quakeSpell = c.quakeSpellBase - shift,
        )
    }

    private suspend fun placeJumpSpell(heroCount: Int) {
        status("Jump spell…")
        DebugLog.d("phase: placeJumpSpell (heroes=$heroCount)")
        val pos = calculateCardPositions(heroCount)
        val deviation = Random.nextInt(5, 7)
        val selectX = pos.jumpSpell + Random.nextInt(-deviation, deviation + 1)
        tapAt(selectX, coords.jumpSpellSelectY)
        controller.interruptibleSleep(100)
        val locs = coords.jumpPlacements
        for ((lx, ly) in locs) {
            tapWithDeviation(lx, ly, deviation = Random.nextInt(2, 4))
            controller.interruptibleSleep(50)
        }
    }

    private suspend fun placeQuakeSpells(heroCount: Int) {
        status("Quake spells…")
        DebugLog.d("phase: placeQuakeSpells (heroes=$heroCount)")
        val pos = calculateCardPositions(heroCount)
        val deviation = Random.nextInt(5, 7)
        val selectX = pos.quakeSpell + Random.nextInt(-deviation, deviation + 1)
        tapAt(selectX, coords.quakeSpellSelectY)
        controller.interruptibleSleep(100)
        val (qx, qy) = coords.quakeSpamPoint
        repeat(5) {
            tapWithDeviation(qx, qy, deviation = Random.nextInt(2, 4))
            controller.interruptibleSleep(50)
        }
    }

    private suspend fun placeHeroesInSequence(heroCount: Int) {
        status("Deploying heroes…")
        DebugLog.d("phase: placeHeroesInSequence count=$heroCount")
        val pos = calculateCardPositions(heroCount)
        val positionsByKey = mapOf(
            "king" to pos.king,
            "queen" to pos.queen,
            "warden" to pos.warden,
            "champion" to pos.champion,
        )
        val heroKeys = listOf("king", "queen", "warden", "champion")
        for (i in 0 until heroCount.coerceIn(1, 4)) {
            val key = heroKeys[i]
            DebugLog.d("hero: $key")
            val centerX = positionsByKey.getValue(key)
            val deviation = Random.nextInt(5, 7)
            val selectX = centerX + Random.nextInt(-deviation, deviation + 1)
            tapAt(selectX, coords.heroSelectY)
            controller.interruptibleSleep(100)
            val (px, py) = coords.heroPlacements[i]
            val numPlacements = Random.nextInt(2, 4)
            repeat(numPlacements) { j ->
                val offsetX = Random.nextInt(-3, 4) * (j + 1)
                val offsetY = Random.nextInt(-3, 4) * (j + 1)
                tapWithDeviation(px + offsetX, py + offsetY, deviation = Random.nextInt(2, 4))
                controller.interruptibleSleep(30)
            }
            controller.interruptibleSleep(50)
            val (dx, dy) = coords.dismissTap
            tapAt(dx, dy)
            controller.interruptibleSleep(50)
        }
    }

    private suspend fun placeSiegeMachine() {
        status("Siege machine…")
        DebugLog.d("phase: placeSiegeMachine")
        val pos = calculateCardPositions(4)
        val deviation = Random.nextInt(5, 7)
        val selectX = pos.siege + Random.nextInt(-deviation, deviation + 1)
        tapAt(selectX, coords.siegeSelectY)
        controller.interruptibleSleep(100)
        val (baseX, baseY) = coords.siegePlacement
        val numPlacements = Random.nextInt(2, 4)
        repeat(numPlacements) { i ->
            val offsetX = Random.nextInt(-3, 4) * (i + 1)
            val offsetY = Random.nextInt(-3, 4) * (i + 1)
            tapWithDeviation(baseX + offsetX, baseY + offsetY, deviation = Random.nextInt(2, 4))
            controller.interruptibleSleep(30)
        }
        controller.interruptibleSleep(50)
        val (dx, dy) = coords.dismissTap
        tapAt(dx, dy)
        controller.interruptibleSleep(50)
    }

    private suspend fun placeGoblins(count: Int = 106) {
        status("Deploying goblins…")
        DebugLog.d("phase: placeGoblins count=$count")
        val pos = calculateCardPositions(4)
        val deviation = Random.nextInt(5, 7)
        val selectX = pos.goblin + Random.nextInt(-deviation, deviation + 1)
        tapAt(selectX, coords.goblinSelectY)
        controller.interruptibleSleep(50)
        val goblinLocations = coords.goblinPlacements
        val n = goblinLocations.size
        val perLoc = count / n
        val remainder = count % n
        var index = 0
        for (i in goblinLocations.indices) {
            val numHere = perLoc + if (i < remainder) 1 else 0
            repeat(numHere) {
                if (index % 12 == 0) controller.attackTick()
                index++
                val (lx, ly) = goblinLocations[i]
                DebugLog.d("goblin tap #$index loc=($lx,$ly) bucket=$i")
                tapWithDeviation(lx, ly, deviation = 2)
            }
        }
    }

    private suspend fun clickAttack() {
        status("Tap: Attack")
        DebugLog.d("UI: Attack button")
        val (x, y) = coords.uiAttack
        tapAt(x, y)
        controller.interruptibleSleep(500)
    }

    private suspend fun clickFindMatch() {
        status("Tap: Find match")
        DebugLog.d("UI: Find Match")
        val (x, y) = coords.uiFindMatch
        tapAt(x, y)
        controller.interruptibleSleep(500)
    }

    private suspend fun clickAddReinforcements() {
        status("Tap: Add reinforcements")
        DebugLog.d("UI: Add Reinforcements")
        val (x, y) = coords.uiAddReinforcements
        tapAt(x, y)
        controller.interruptibleSleep(500)
    }

    private suspend fun clickConfirmReinforcements() {
        status("Tap: Confirm reinforcements")
        DebugLog.d("UI: Confirm Reinforcements")
        val (x, y) = coords.uiConfirmReinforcements
        tapAt(x, y)
        controller.interruptibleSleep(500)
    }

    /** "Attack!" on plan screen — from adb_record_taps.py: screen (103,1986) → base (223,894). */
    private suspend fun clickStartAttack() {
        status("Tap: Start attack")
        DebugLog.d("UI: Start Attack")
        val (x, y) = coords.uiStartAttack
        tapAt(x, y)
        controller.interruptibleSleep(500)
    }

    private suspend fun clickNextButton() {
        status("Tap: Next base (skip)")
        DebugLog.d("UI: Next (skip base)")
        val (x, y) = coords.uiNext
        tapWithDeviation(x, y, deviation = 50)
        controller.interruptibleSleep(2000)
    }

    private suspend fun clickEndBattle() {
        status("Tap: End battle")
        DebugLog.d("UI: End Battle")
        val (x, y) = coords.uiEndBattle
        tapWithDeviation(x, y, deviation = 50)
        controller.interruptibleSleep(1000)
    }

    private suspend fun clickConfirm() {
        status("Tap: Confirm")
        DebugLog.d("UI: Confirm")
        val (x, y) = coords.uiConfirm
        tapWithDeviation(x, y, deviation = 50)
        controller.interruptibleSleep(2000)
    }

    private suspend fun clickReturnHome() {
        status("Tap: Return home")
        DebugLog.d("UI: Return Home")
        val (x, y) = coords.uiReturnHome
        tapAt(x, y)
        controller.interruptibleSleep(1000)
    }

    private suspend fun extractResourcesFromScreenshot(full: Bitmap): Pair<String?, String?> {
        status("OCR: reading gold & elixir…")
        val goldBmp = cropForOcr(full, coords.goldOcrRect, "gold")
        val elixirBmp = cropForOcr(full, coords.elixirOcrRect, "elixir")
        return try {
            val (g, e) = bitmapToGoldElixirStrings(goldBmp, elixirBmp)
            ocrCaptureSaver?.saveGoldElixirCrops(goldBmp, elixirBmp, g, e)
            status("OCR · Gold: ${g ?: "—"} · Elixir: ${e ?: "—"}")
            g to e
        } finally {
            if (!goldBmp.isRecycled) goldBmp.recycle()
            if (!elixirBmp.isRecycled) elixirBmp.recycle()
        }
    }

    private fun cropForOcr(full: Bitmap, rect: OcrCropRect, label: String): Bitmap {
        val gl = geometry.cropLeft(rect.left).coerceIn(0, full.width - 1)
        val gt = geometry.cropTop(rect.top).coerceIn(0, full.height - 1)
        val gw = geometry.cropWidth(rect.left, rect.right).coerceAtLeast(1).coerceAtMost(full.width - gl)
        val gh = geometry.cropHeight(rect.top, rect.bottom).coerceAtLeast(1).coerceAtMost(full.height - gt)
        DebugLog.d("OCR crop $label: ($gl,$gt)+${gw}x${gh} base=${rect.toLogString()} frame=${full.width}x${full.height}")
        return Bitmap.createBitmap(full, gl, gt, gw, gh)
    }

    /**
     * @return true if a full attack finished; false if skipped (max searches)
     */
    suspend fun executeAttackSequence(heroCount: Int, addReinforcements: Boolean): Boolean {
        status("Starting attack sequence…")
        DebugLog.d("======== Attack sequence START heroes=$heroCount reinforce=$addReinforcements threshold=$resourceThreshold ========")
        var searchAttempts = 0
        while (searchAttempts < maxSearchAttempts) {
            searchAttempts++
            controller.attackTick()
            status("Search attempt $searchAttempts / $maxSearchAttempts")
            DebugLog.d("--- Search attempt $searchAttempts / $maxSearchAttempts ---")

            if (searchAttempts == 1) {
                clickAttack()
                clickFindMatch()
                if (addReinforcements) {
                    clickAddReinforcements()
                    clickConfirmReinforcements()
                }
                clickStartAttack()
            }

            if (searchAttempts == 1) {
                status("Waiting 10s before OCR…")
                DebugLog.d("wait 10s before first OCR")
                controller.interruptibleSleep(10_000)
            } else {
                status("Waiting 5s before OCR…")
                DebugLog.d("wait 5s before OCR")
                controller.interruptibleSleep(5_000)
            }

            val full = captureFullScreen() ?: run {
                status("Screenshot failed — aborting")
                DebugLog.w("captureFullScreen returned null — abort sequence")
                return false
            }
            DebugLog.d("screenshot ${full.width}x${full.height}")
            val (goldStr, elixirStr) = extractResourcesFromScreenshot(full)
            val shouldAttack = when {
                goldStr != null && elixirStr != null -> {
                    val goldVal = goldStr.toLongOrNull() ?: 0L
                    val elixirVal = elixirStr.toLongOrNull() ?: 0L
                    goldVal >= resourceThreshold && elixirVal >= resourceThreshold
                }
                else -> true
            }
            DebugLog.d("OCR summary gold=$goldStr elixir=$elixirStr -> shouldAttack=$shouldAttack")

            if (!shouldAttack) {
                if (searchAttempts < maxSearchAttempts) {
                    status("Below threshold · skipping base")
                    DebugLog.d("below threshold -> Next")
                    clickNextButton()
                    controller.interruptibleSleep(3000)
                    continue
                }
                DebugLog.d("max searches reached, skip attack")
                return false
            }

            status("Threshold OK · deploying troops")
            DebugLog.d("threshold OK -> troop placement")
            placeJumpSpell(heroCount)
            controller.interruptibleSleep(100)
            placeQuakeSpells(heroCount)
            controller.interruptibleSleep(100)
            placeHeroesInSequence(heroCount)
            controller.interruptibleSleep(100)
            val (dx, dy) = coords.dismissTap
            tapAt(dx, dy)
            controller.interruptibleSleep(50)
            placeSiegeMachine()
            controller.interruptibleSleep(100)
            placeGoblins(106)
            controller.interruptibleSleep(100)

            status("Waiting after deployment (${postDeployWaitMs / 1000}s)…")
            DebugLog.d("wait after deployments (chunked) total=${postDeployWaitMs}ms")
            var elapsed = 0L
            while (elapsed < postDeployWaitMs) {
                val step = minOf(10_000L, postDeployWaitMs - elapsed)
                controller.interruptibleSleep(step)
                elapsed += step
                status("Waiting after deployment… ${elapsed / 1000}s / ${postDeployWaitMs / 1000}s")
                DebugLog.d("post-deploy wait elapsed=${elapsed}ms / ${postDeployWaitMs}ms")
            }

            clickEndBattle()
            clickConfirm()
            controller.interruptibleSleep(2000)
            clickReturnHome()
            val postHomeMs = Random.nextLong(10_000L, 15_001L)
            status("Waiting ${postHomeMs / 1000}s after Return home…")
            DebugLog.d("post Return Home wait ${postHomeMs}ms (random 10–15s)")
            controller.interruptibleSleep(postHomeMs)
            status("Attack finished · idle until next loop")
            DebugLog.d("======== Attack sequence END success ========")
            return true
        }
        DebugLog.d("======== Attack sequence END (no iteration matched) ========")
        return false
    }
}

suspend fun bitmapToGoldElixirStrings(goldCrop: Bitmap, elixirCrop: Bitmap): Pair<String?, String?> =
    withContext(Dispatchers.IO) {
        val g = runCatching { ResourceOcr.extractNumberFromBitmapSync(goldCrop) }.getOrNull()
        val e = runCatching { ResourceOcr.extractNumberFromBitmapSync(elixirCrop) }.getOrNull()
        DebugLog.d("combined OCR numbers gold=$g elixir=$e")
        g to e
    }

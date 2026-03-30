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
    private val resourceThreshold: Long = 500_000L,
    private val maxSearchAttempts: Int = 10,
) {

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
        val cardWidth = 134
        val cardGap = 20
        val missingHeroes = 4 - heroCount.coerceIn(1, 4)
        val shift = if (missingHeroes > 0) missingHeroes * (cardWidth + cardGap) else 0
        return CardPositions(
            goblin = 447,
            siege = 734,
            king = 900,
            queen = 1053,
            warden = 1219,
            champion = 1363,
            jumpSpell = 1500 - shift,
            quakeSpell = 1644 - shift,
        )
    }

    private suspend fun placeJumpSpell(heroCount: Int) {
        DebugLog.d("phase: placeJumpSpell (heroes=$heroCount)")
        val pos = calculateCardPositions(heroCount)
        val deviation = Random.nextInt(5, 7)
        val selectX = pos.jumpSpell + Random.nextInt(-deviation, deviation + 1)
        tapAt(selectX, 978)
        controller.interruptibleSleep(100)
        val locs = listOf(800 to 250, 1565 to 250, 1572 to 706)
        for ((lx, ly) in locs) {
            tapWithDeviation(lx, ly, deviation = Random.nextInt(2, 4))
            controller.interruptibleSleep(50)
        }
    }

    private suspend fun placeQuakeSpells(heroCount: Int) {
        DebugLog.d("phase: placeQuakeSpells (heroes=$heroCount)")
        val pos = calculateCardPositions(heroCount)
        val deviation = Random.nextInt(5, 7)
        val selectX = pos.quakeSpell + Random.nextInt(-deviation, deviation + 1)
        tapAt(selectX, 997)
        controller.interruptibleSleep(100)
        repeat(5) {
            tapWithDeviation(1353, 444, deviation = Random.nextInt(2, 4))
            controller.interruptibleSleep(50)
        }
    }

    private val heroConfigs = listOf(
        "king" to (2263 to 469),
        "queen" to (2281 to 466),
        "warden" to (2256 to 456),
        "champion" to (2284 to 459),
    )

    private suspend fun placeHeroesInSequence(heroCount: Int) {
        DebugLog.d("phase: placeHeroesInSequence count=$heroCount")
        val pos = calculateCardPositions(heroCount)
        val positionsByKey = mapOf(
            "king" to pos.king,
            "queen" to pos.queen,
            "warden" to pos.warden,
            "champion" to pos.champion,
        )
        for (i in 0 until heroCount.coerceIn(1, 4)) {
            val (key, place) = heroConfigs[i]
            DebugLog.d("hero: $key")
            val centerX = positionsByKey.getValue(key)
            val deviation = Random.nextInt(5, 7)
            val selectX = centerX + Random.nextInt(-deviation, deviation + 1)
            tapAt(selectX, 969)
            controller.interruptibleSleep(100)
            val (px, py) = place
            val numPlacements = Random.nextInt(2, 4)
            repeat(numPlacements) { j ->
                val offsetX = Random.nextInt(-3, 4) * (j + 1)
                val offsetY = Random.nextInt(-3, 4) * (j + 1)
                tapWithDeviation(px + offsetX, py + offsetY, deviation = Random.nextInt(2, 4))
                controller.interruptibleSleep(30)
            }
            controller.interruptibleSleep(50)
            tapAt(100, 100)
            controller.interruptibleSleep(50)
        }
    }

    private suspend fun placeSiegeMachine() {
        DebugLog.d("phase: placeSiegeMachine")
        val pos = calculateCardPositions(4)
        val deviation = Random.nextInt(5, 7)
        val selectX = pos.siege + Random.nextInt(-deviation, deviation + 1)
        tapAt(selectX, 978)
        controller.interruptibleSleep(100)
        val baseX = 2272
        val baseY = 491
        val numPlacements = Random.nextInt(2, 4)
        repeat(numPlacements) { i ->
            val offsetX = Random.nextInt(-3, 4) * (i + 1)
            val offsetY = Random.nextInt(-3, 4) * (i + 1)
            tapWithDeviation(baseX + offsetX, baseY + offsetY, deviation = Random.nextInt(2, 4))
            controller.interruptibleSleep(30)
        }
        controller.interruptibleSleep(50)
        tapAt(100, 100)
        controller.interruptibleSleep(50)
    }

    private val goblinLocations = listOf(
        509 to 738, 438 to 684, 372 to 619, 313 to 578, 216 to 469,
        375 to 347, 481 to 256, 538 to 216, 619 to 166, 691 to 125,
        803 to 63, 1706 to 844, 1766 to 816, 1847 to 794, 1906 to 753,
        1963 to 713, 2022 to 669, 2075 to 638, 2141 to 575, 2197 to 531,
        2259 to 466, 2200 to 413, 2169 to 384, 2100 to 334, 2003 to 275,
        1866 to 194, 1806 to 153, 1681 to 75, 1641 to 50,
    )

    private suspend fun placeGoblins(count: Int = 106) {
        DebugLog.d("phase: placeGoblins count=$count")
        val pos = calculateCardPositions(4)
        val deviation = Random.nextInt(5, 7)
        val selectX = pos.goblin + Random.nextInt(-deviation, deviation + 1)
        tapAt(selectX, 966)
        controller.interruptibleSleep(50)
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
        DebugLog.d("UI: Attack button")
        tapAt(228, 944)
        controller.interruptibleSleep(500)
    }

    private suspend fun clickFindMatch() {
        DebugLog.d("UI: Find Match")
        tapAt(431, 809)
        controller.interruptibleSleep(500)
    }

    private suspend fun clickAddReinforcements() {
        DebugLog.d("UI: Add Reinforcements")
        tapAt(1870, 875)
        controller.interruptibleSleep(500)
    }

    private suspend fun clickConfirmReinforcements() {
        DebugLog.d("UI: Confirm Reinforcements")
        tapAt(1440, 800)
        controller.interruptibleSleep(500)
    }

    /** "Attack!" on plan screen — from adb_record_taps.py: screen (103,1986) → base (223,894). */
    private suspend fun clickStartAttack() {
        DebugLog.d("UI: Start Attack")
        tapAt(223, 894)
        controller.interruptibleSleep(500)
    }

    private suspend fun clickNextButton() {
        DebugLog.d("UI: Next (skip base)")
        tapWithDeviation(2150, 750, deviation = 50)
        controller.interruptibleSleep(2000)
    }

    private suspend fun clickEndBattle() {
        DebugLog.d("UI: End Battle")
        tapWithDeviation(187, 800, deviation = 50)
        controller.interruptibleSleep(1000)
    }

    private suspend fun clickConfirm() {
        DebugLog.d("UI: Confirm")
        tapWithDeviation(1350, 700, deviation = 50)
        controller.interruptibleSleep(2000)
    }

    private suspend fun clickReturnHome() {
        DebugLog.d("UI: Return Home")
        tapAt(1253, 916)
        controller.interruptibleSleep(1000)
    }

    private suspend fun extractResourcesFromScreenshot(full: Bitmap): Pair<String?, String?> {
        val gl = geometry.cropLeft(165)
        val gt = geometry.cropTop(145)
        val gw = geometry.cropWidth(165, 380)
        val gh = geometry.cropHeight(145, 200)
        DebugLog.d("OCR crop gold: ($gl,$gt)+${gw}x${gh} on frame ${full.width}x${full.height}")
        val goldBmp = Bitmap.createBitmap(full, gl, gt, gw, gh)

        val el = geometry.cropLeft(165)
        val et = geometry.cropTop(200)
        val ew = geometry.cropWidth(165, 380)
        val eh = geometry.cropHeight(200, 250)
        DebugLog.d("OCR crop elixir: ($el,$et)+${ew}x${eh}")
        val elixirBmp = Bitmap.createBitmap(full, el, et, ew, eh)

        return try {
            bitmapToGoldElixirStrings(goldBmp, elixirBmp)
        } finally {
            if (!goldBmp.isRecycled) goldBmp.recycle()
            if (!elixirBmp.isRecycled) elixirBmp.recycle()
        }
    }

    /**
     * @return true if a full attack finished; false if skipped (max searches)
     */
    suspend fun executeAttackSequence(heroCount: Int, addReinforcements: Boolean): Boolean {
        DebugLog.d("======== Attack sequence START heroes=$heroCount reinforce=$addReinforcements threshold=$resourceThreshold ========")
        var searchAttempts = 0
        while (searchAttempts < maxSearchAttempts) {
            searchAttempts++
            controller.attackTick()
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
                DebugLog.d("wait 10s before first OCR")
                controller.interruptibleSleep(10_000)
            } else {
                DebugLog.d("wait 5s before OCR")
                controller.interruptibleSleep(5_000)
            }

            val full = captureFullScreen() ?: run {
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
                    DebugLog.d("below threshold -> Next")
                    clickNextButton()
                    controller.interruptibleSleep(3000)
                    continue
                }
                DebugLog.d("max searches reached, skip attack")
                return false
            }

            DebugLog.d("threshold OK -> troop placement")
            placeJumpSpell(heroCount)
            controller.interruptibleSleep(100)
            placeQuakeSpells(heroCount)
            controller.interruptibleSleep(100)
            placeHeroesInSequence(heroCount)
            controller.interruptibleSleep(100)
            tapAt(100, 100)
            controller.interruptibleSleep(50)
            placeSiegeMachine()
            controller.interruptibleSleep(100)
            placeGoblins(106)
            controller.interruptibleSleep(100)

            DebugLog.d("wait 30s after deployments (chunked)")
            var elapsed = 0
            while (elapsed < 30_000) {
                val step = minOf(10_000, 30_000 - elapsed)
                controller.interruptibleSleep(step.toLong())
                elapsed += step
                DebugLog.d("post-deploy wait elapsed=${elapsed}ms / 30000ms")
            }

            clickEndBattle()
            clickConfirm()
            controller.interruptibleSleep(2000)
            clickReturnHome()
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

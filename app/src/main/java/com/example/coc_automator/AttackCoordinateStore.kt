package com.example.coc_automator

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Gold / elixir OCR region in base space (two opposite corners define the crop). */
data class OcrCropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(left < right && top < bottom) { "OCR rect needs left<right and top<bottom" }
    }

    /** Picker order: corner 1 then corner 2 (any diagonal); stored as normalized rect. */
    fun cornerPoints(): List<Pair<Int, Int>> = listOf(left to top, right to bottom)

    fun toLogString(): String = "[$left,$top]-[$right,$bottom]"

    companion object {
        fun fromDiagonalCorners(a: Pair<Int, Int>, b: Pair<Int, Int>): OcrCropRect {
            val l = min(a.first, b.first)
            val t = min(a.second, b.second)
            var r = max(a.first, b.first)
            var bot = max(a.second, b.second)
            if (r <= l) r = l + 1
            if (bot <= t) bot = t + 1
            return OcrCropRect(l, t, r, bot)
        }

        fun goldDefault() = OcrCropRect(165, 145, 380, 200)
        fun elixirDefault() = OcrCropRect(165, 200, 380, 250)
    }
}

/**
 * All attack / UI tap positions in **base** space ([ScreenGeometry.BASE_WIDTH] x [ScreenGeometry.BASE_HEIGHT]),
 * matching the Python automation reference resolution.
 */
data class AttackCoordinateConfig(
    val uiAttack: Pair<Int, Int>,
    val uiFindMatch: Pair<Int, Int>,
    val uiAddReinforcements: Pair<Int, Int>,
    val uiConfirmReinforcements: Pair<Int, Int>,
    val uiStartAttack: Pair<Int, Int>,
    val uiNext: Pair<Int, Int>,
    val uiEndBattle: Pair<Int, Int>,
    val uiConfirm: Pair<Int, Int>,
    val uiReturnHome: Pair<Int, Int>,
    val dismissTap: Pair<Int, Int>,
    val jumpSpellSelectY: Int,
    val quakeSpellSelectY: Int,
    val heroSelectY: Int,
    val siegeSelectY: Int,
    val goblinSelectY: Int,
    val cardBar: CardBarConfig,
    val jumpPlacements: List<Pair<Int, Int>>,
    val quakeSpamPoint: Pair<Int, Int>,
    val heroPlacements: List<Pair<Int, Int>>,
    val siegePlacement: Pair<Int, Int>,
    val goblinPlacements: List<Pair<Int, Int>>,
    val goldOcrRect: OcrCropRect,
    val elixirOcrRect: OcrCropRect,
) {
    fun slotPoints(slot: CoordinateSlot): List<Pair<Int, Int>> = when (slot) {
        CoordinateSlot.UiAttack -> listOf(uiAttack)
        CoordinateSlot.UiFindMatch -> listOf(uiFindMatch)
        CoordinateSlot.UiAddReinforcements -> listOf(uiAddReinforcements)
        CoordinateSlot.UiConfirmReinforcements -> listOf(uiConfirmReinforcements)
        CoordinateSlot.UiStartAttack -> listOf(uiStartAttack)
        CoordinateSlot.UiNext -> listOf(uiNext)
        CoordinateSlot.UiEndBattle -> listOf(uiEndBattle)
        CoordinateSlot.UiConfirm -> listOf(uiConfirm)
        CoordinateSlot.UiReturnHome -> listOf(uiReturnHome)
        CoordinateSlot.DismissTap -> listOf(dismissTap)
        CoordinateSlot.JumpSpellSelectY -> listOf(0 to jumpSpellSelectY)
        CoordinateSlot.QuakeSpellSelectY -> listOf(0 to quakeSpellSelectY)
        CoordinateSlot.HeroSelectY -> listOf(0 to heroSelectY)
        CoordinateSlot.SiegeSelectY -> listOf(0 to siegeSelectY)
        CoordinateSlot.GoblinSelectY -> listOf(0 to goblinSelectY)
        CoordinateSlot.CardBar -> cardBar.asPoints()
        CoordinateSlot.JumpPlacements -> jumpPlacements
        CoordinateSlot.QuakeSpamPoint -> listOf(quakeSpamPoint)
        CoordinateSlot.HeroPlacements -> heroPlacements
        CoordinateSlot.SiegePlacement -> listOf(siegePlacement)
        CoordinateSlot.GoblinPlacements -> goblinPlacements
        CoordinateSlot.OcrGoldRect -> goldOcrRect.cornerPoints()
        CoordinateSlot.OcrElixirRect -> elixirOcrRect.cornerPoints()
    }

    fun withSlot(slot: CoordinateSlot, points: List<Pair<Int, Int>>): AttackCoordinateConfig {
        require(points.size == slot.count) { "expected ${slot.count} points for $slot, got ${points.size}" }
        return when (slot) {
            CoordinateSlot.UiAttack -> copy(uiAttack = points[0])
            CoordinateSlot.UiFindMatch -> copy(uiFindMatch = points[0])
            CoordinateSlot.UiAddReinforcements -> copy(uiAddReinforcements = points[0])
            CoordinateSlot.UiConfirmReinforcements -> copy(uiConfirmReinforcements = points[0])
            CoordinateSlot.UiStartAttack -> copy(uiStartAttack = points[0])
            CoordinateSlot.UiNext -> copy(uiNext = points[0])
            CoordinateSlot.UiEndBattle -> copy(uiEndBattle = points[0])
            CoordinateSlot.UiConfirm -> copy(uiConfirm = points[0])
            CoordinateSlot.UiReturnHome -> copy(uiReturnHome = points[0])
            CoordinateSlot.DismissTap -> copy(dismissTap = points[0])
            CoordinateSlot.JumpSpellSelectY -> copy(jumpSpellSelectY = points[0].second)
            CoordinateSlot.QuakeSpellSelectY -> copy(quakeSpellSelectY = points[0].second)
            CoordinateSlot.HeroSelectY -> copy(heroSelectY = points[0].second)
            CoordinateSlot.SiegeSelectY -> copy(siegeSelectY = points[0].second)
            CoordinateSlot.GoblinSelectY -> copy(goblinSelectY = points[0].second)
            CoordinateSlot.CardBar -> copy(cardBar = cardBar.withPickedXPoints(points))
            CoordinateSlot.JumpPlacements -> copy(jumpPlacements = points)
            CoordinateSlot.QuakeSpamPoint -> copy(quakeSpamPoint = points[0])
            CoordinateSlot.HeroPlacements -> copy(heroPlacements = points)
            CoordinateSlot.SiegePlacement -> copy(siegePlacement = points[0])
            CoordinateSlot.GoblinPlacements -> copy(goblinPlacements = points)
            CoordinateSlot.OcrGoldRect -> copy(goldOcrRect = OcrCropRect.fromDiagonalCorners(points[0], points[1]))
            CoordinateSlot.OcrElixirRect -> copy(elixirOcrRect = OcrCropRect.fromDiagonalCorners(points[0], points[1]))
        }
    }

    companion object {
        fun default(): AttackCoordinateConfig = AttackCoordinateConfig(
            uiAttack = 228 to 944,
            uiFindMatch = 431 to 809,
            uiAddReinforcements = 1870 to 875,
            uiConfirmReinforcements = 1440 to 800,
            uiStartAttack = 223 to 894,
            uiNext = 2150 to 750,
            uiEndBattle = 187 to 800,
            uiConfirm = 1350 to 700,
            uiReturnHome = 1253 to 916,
            dismissTap = 100 to 100,
            jumpSpellSelectY = 978,
            quakeSpellSelectY = 997,
            heroSelectY = 969,
            siegeSelectY = 978,
            goblinSelectY = 966,
            cardBar = CardBarConfig.default(),
            jumpPlacements = listOf(800 to 250, 1565 to 250, 1572 to 706),
            quakeSpamPoint = 1353 to 444,
            heroPlacements = listOf(2263 to 469, 2281 to 466, 2256 to 456, 2284 to 459),
            siegePlacement = 2272 to 491,
            goblinPlacements = defaultGoblinPlacements(),
            goldOcrRect = OcrCropRect.goldDefault(),
            elixirOcrRect = OcrCropRect.elixirDefault(),
        )

        private fun defaultGoblinPlacements(): List<Pair<Int, Int>> = listOf(
            509 to 738, 438 to 684, 372 to 619, 313 to 578, 216 to 469,
            375 to 347, 481 to 256, 538 to 216, 619 to 166, 691 to 125,
            803 to 63, 1706 to 844, 1766 to 816, 1847 to 794, 1906 to 753,
            1963 to 713, 2022 to 669, 2075 to 638, 2141 to 575, 2197 to 531,
            2259 to 466, 2200 to 413, 2169 to 384, 2100 to 334, 2003 to 275,
            1866 to 194, 1806 to 153, 1681 to 75, 1641 to 50,
        )
    }
}

data class CardBarConfig(
    val cardWidth: Int,
    val cardGap: Int,
    val goblin: Int,
    val siege: Int,
    val king: Int,
    val queen: Int,
    val warden: Int,
    val champion: Int,
    val jumpSpellBase: Int,
    val quakeSpellBase: Int,
) {
        fun asPoints(): List<Pair<Int, Int>> = listOf(
        goblin to 0,
        siege to 0,
        king to 0,
        queen to 0,
        warden to 0,
        champion to 0,
        jumpSpellBase to 0,
        quakeSpellBase to 0,
    )

    /** Updates horizontal card / spell column centers; preserves [cardWidth] and [cardGap]. */
    fun withPickedXPoints(points: List<Pair<Int, Int>>): CardBarConfig {
        require(points.size == 8) { "card bar needs 8 points" }
        return copy(
            goblin = points[0].first,
            siege = points[1].first,
            king = points[2].first,
            queen = points[3].first,
            warden = points[4].first,
            champion = points[5].first,
            jumpSpellBase = points[6].first,
            quakeSpellBase = points[7].first,
        )
    }

    companion object {
        fun default() = CardBarConfig(
            cardWidth = 134,
            cardGap = 20,
            goblin = 447,
            siege = 734,
            king = 900,
            queen = 1053,
            warden = 1219,
            champion = 1363,
            jumpSpellBase = 1500,
            quakeSpellBase = 1644,
        )
    }
}

enum class CoordinateSlot(
    val count: Int,
    val isYOnly: Boolean = false,
    val isCardXOnly: Boolean = false,
    /** Draw a rectangle between the first two taps on the reference image. */
    val showOcrRectangle: Boolean = false,
) {
    UiAttack(1),
    UiFindMatch(1),
    UiAddReinforcements(1),
    UiConfirmReinforcements(1),
    UiStartAttack(1),
    UiNext(1),
    UiEndBattle(1),
    UiConfirm(1),
    UiReturnHome(1),
    DismissTap(1),
    OcrGoldRect(2, showOcrRectangle = true),
    OcrElixirRect(2, showOcrRectangle = true),
    JumpSpellSelectY(1, isYOnly = true),
    QuakeSpellSelectY(1, isYOnly = true),
    HeroSelectY(1, isYOnly = true),
    SiegeSelectY(1, isYOnly = true),
    GoblinSelectY(1, isYOnly = true),
    CardBar(8, isCardXOnly = true),
    JumpPlacements(3),
    QuakeSpamPoint(1),
    HeroPlacements(4),
    SiegePlacement(1),
    GoblinPlacements(28),
}

object AttackCoordinateStore {

    private const val PREFS = "attack_coordinates"
    private const val KEY_JSON = "config_json"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): AttackCoordinateConfig {
        val raw = prefs(context).getString(KEY_JSON, null) ?: return AttackCoordinateConfig.default()
        return runCatching { fromJson(JSONObject(raw)) }.getOrElse {
            AttackCoordinateConfig.default()
        }
    }

    fun save(context: Context, config: AttackCoordinateConfig) {
        prefs(context).edit().putString(KEY_JSON, toJson(config).toString()).apply()
    }

    fun resetToDefaults(context: Context) {
        save(context, AttackCoordinateConfig.default())
    }

    private fun pairsToJson(arr: List<Pair<Int, Int>>): JSONArray {
        val a = JSONArray()
        for ((x, y) in arr) {
            a.put(JSONArray().put(x).put(y))
        }
        return a
    }

    private fun pairsFromJson(a: JSONArray?): List<Pair<Int, Int>> {
        if (a == null) return emptyList()
        val out = ArrayList<Pair<Int, Int>>(a.length())
        for (i in 0 until a.length()) {
            val inner = a.getJSONArray(i)
            out.add(inner.getInt(0) to inner.getInt(1))
        }
        return out
    }

    private fun putPair(o: JSONObject, key: String, p: Pair<Int, Int>) {
        o.put(key, JSONArray().put(p.first).put(p.second))
    }

    private fun getPair(o: JSONObject, key: String, default: Pair<Int, Int>): Pair<Int, Int> {
        if (!o.has(key)) return default
        val a = o.getJSONArray(key)
        return a.getInt(0) to a.getInt(1)
    }

    private fun toJson(c: AttackCoordinateConfig): JSONObject = JSONObject().apply {
        putPair(this, "uiAttack", c.uiAttack)
        putPair(this, "uiFindMatch", c.uiFindMatch)
        putPair(this, "uiAddReinforcements", c.uiAddReinforcements)
        putPair(this, "uiConfirmReinforcements", c.uiConfirmReinforcements)
        putPair(this, "uiStartAttack", c.uiStartAttack)
        putPair(this, "uiNext", c.uiNext)
        putPair(this, "uiEndBattle", c.uiEndBattle)
        putPair(this, "uiConfirm", c.uiConfirm)
        putPair(this, "uiReturnHome", c.uiReturnHome)
        putPair(this, "dismissTap", c.dismissTap)
        put("jumpSpellSelectY", c.jumpSpellSelectY)
        put("quakeSpellSelectY", c.quakeSpellSelectY)
        put("heroSelectY", c.heroSelectY)
        put("siegeSelectY", c.siegeSelectY)
        put("goblinSelectY", c.goblinSelectY)
        put("cardBar", cardBarToJson(c.cardBar))
        put("jumpPlacements", pairsToJson(c.jumpPlacements))
        putPair(this, "quakeSpamPoint", c.quakeSpamPoint)
        put("heroPlacements", pairsToJson(c.heroPlacements))
        putPair(this, "siegePlacement", c.siegePlacement)
        put("goblinPlacements", pairsToJson(c.goblinPlacements))
        put("goldOcrRect", ocrRectToJson(c.goldOcrRect))
        put("elixirOcrRect", ocrRectToJson(c.elixirOcrRect))
    }

    private fun ocrRectToJson(r: OcrCropRect): JSONObject = JSONObject().apply {
        put("left", r.left)
        put("top", r.top)
        put("right", r.right)
        put("bottom", r.bottom)
    }

    private fun ocrRectFromJson(o: JSONObject?, default: OcrCropRect): OcrCropRect {
        if (o == null) return default
        val maxX = ScreenGeometry.BASE_WIDTH.toInt()
        val maxY = ScreenGeometry.BASE_HEIGHT.toInt()
        var left = o.optInt("left", default.left).coerceIn(0, maxX - 2)
        var top = o.optInt("top", default.top).coerceIn(0, maxY - 2)
        var right = o.optInt("right", default.right).coerceIn(1, maxX - 1)
        var bottom = o.optInt("bottom", default.bottom).coerceIn(1, maxY - 1)
        if (right <= left) right = (left + 1).coerceAtMost(maxX - 1)
        if (bottom <= top) bottom = (top + 1).coerceAtMost(maxY - 1)
        return runCatching { OcrCropRect(left, top, right, bottom) }.getOrElse { default }
    }

    private fun cardBarToJson(cb: CardBarConfig): JSONObject = JSONObject().apply {
        put("cardWidth", cb.cardWidth)
        put("cardGap", cb.cardGap)
        put("goblin", cb.goblin)
        put("siege", cb.siege)
        put("king", cb.king)
        put("queen", cb.queen)
        put("warden", cb.warden)
        put("champion", cb.champion)
        put("jumpSpellBase", cb.jumpSpellBase)
        put("quakeSpellBase", cb.quakeSpellBase)
    }

    private fun cardBarFromJson(o: JSONObject?, default: CardBarConfig): CardBarConfig {
        if (o == null) return default
        return CardBarConfig(
            cardWidth = o.optInt("cardWidth", default.cardWidth),
            cardGap = o.optInt("cardGap", default.cardGap),
            goblin = o.optInt("goblin", default.goblin),
            siege = o.optInt("siege", default.siege),
            king = o.optInt("king", default.king),
            queen = o.optInt("queen", default.queen),
            warden = o.optInt("warden", default.warden),
            champion = o.optInt("champion", default.champion),
            jumpSpellBase = o.optInt("jumpSpellBase", default.jumpSpellBase),
            quakeSpellBase = o.optInt("quakeSpellBase", default.quakeSpellBase),
        )
    }

    private fun fromJson(o: JSONObject): AttackCoordinateConfig {
        val d = AttackCoordinateConfig.default()
        val card = cardBarFromJson(o.optJSONObject("cardBar"), d.cardBar)
        return AttackCoordinateConfig(
            uiAttack = getPair(o, "uiAttack", d.uiAttack),
            uiFindMatch = getPair(o, "uiFindMatch", d.uiFindMatch),
            uiAddReinforcements = getPair(o, "uiAddReinforcements", d.uiAddReinforcements),
            uiConfirmReinforcements = getPair(o, "uiConfirmReinforcements", d.uiConfirmReinforcements),
            uiStartAttack = getPair(o, "uiStartAttack", d.uiStartAttack),
            uiNext = getPair(o, "uiNext", d.uiNext),
            uiEndBattle = getPair(o, "uiEndBattle", d.uiEndBattle),
            uiConfirm = getPair(o, "uiConfirm", d.uiConfirm),
            uiReturnHome = getPair(o, "uiReturnHome", d.uiReturnHome),
            dismissTap = getPair(o, "dismissTap", d.dismissTap),
            jumpSpellSelectY = o.optInt("jumpSpellSelectY", d.jumpSpellSelectY),
            quakeSpellSelectY = o.optInt("quakeSpellSelectY", d.quakeSpellSelectY),
            heroSelectY = o.optInt("heroSelectY", d.heroSelectY),
            siegeSelectY = o.optInt("siegeSelectY", d.siegeSelectY),
            goblinSelectY = o.optInt("goblinSelectY", d.goblinSelectY),
            cardBar = card,
            jumpPlacements = pairsFromJson(o.optJSONArray("jumpPlacements")).takeIf { it.size == 3 }
                ?: d.jumpPlacements,
            quakeSpamPoint = getPair(o, "quakeSpamPoint", d.quakeSpamPoint),
            heroPlacements = pairsFromJson(o.optJSONArray("heroPlacements")).takeIf { it.size == 4 }
                ?: d.heroPlacements,
            siegePlacement = getPair(o, "siegePlacement", d.siegePlacement),
            goblinPlacements = pairsFromJson(o.optJSONArray("goblinPlacements")).takeIf { it.size == 28 }
                ?: d.goblinPlacements,
            goldOcrRect = ocrRectFromJson(o.optJSONObject("goldOcrRect"), d.goldOcrRect),
            elixirOcrRect = ocrRectFromJson(o.optJSONObject("elixirOcrRect"), d.elixirOcrRect),
        )
    }
}

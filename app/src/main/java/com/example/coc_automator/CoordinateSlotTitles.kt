package com.example.coc_automator

internal fun CoordinateSlot.titleRes(): Int = when (this) {
    CoordinateSlot.UiAttack -> R.string.slot_ui_attack
    CoordinateSlot.UiFindMatch -> R.string.slot_ui_find_match
    CoordinateSlot.UiAddReinforcements -> R.string.slot_ui_add_reinforcements
    CoordinateSlot.UiConfirmReinforcements -> R.string.slot_ui_confirm_reinforcements
    CoordinateSlot.UiStartAttack -> R.string.slot_ui_start_attack
    CoordinateSlot.UiNext -> R.string.slot_ui_next
    CoordinateSlot.UiEndBattle -> R.string.slot_ui_end_battle
    CoordinateSlot.UiConfirm -> R.string.slot_ui_confirm
    CoordinateSlot.UiReturnHome -> R.string.slot_ui_return_home
    CoordinateSlot.DismissTap -> R.string.slot_dismiss_tap
    CoordinateSlot.OcrGoldRect -> R.string.slot_ocr_gold_rect
    CoordinateSlot.OcrElixirRect -> R.string.slot_ocr_elixir_rect
    CoordinateSlot.JumpSpellSelectY -> R.string.slot_jump_spell_row_y
    CoordinateSlot.QuakeSpellSelectY -> R.string.slot_quake_spell_row_y
    CoordinateSlot.HeroSelectY -> R.string.slot_hero_row_y
    CoordinateSlot.SiegeSelectY -> R.string.slot_siege_row_y
    CoordinateSlot.GoblinSelectY -> R.string.slot_goblin_row_y
    CoordinateSlot.CardBar -> R.string.slot_card_bar
    CoordinateSlot.JumpPlacements -> R.string.slot_jump_placements
    CoordinateSlot.QuakeSpamPoint -> R.string.slot_quake_spam
    CoordinateSlot.HeroPlacements -> R.string.slot_hero_placements
    CoordinateSlot.SiegePlacement -> R.string.slot_siege_placement
    CoordinateSlot.GoblinPlacements -> R.string.slot_goblin_placements
}

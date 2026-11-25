package com.pokedaisy.app.companion.data

/** German Gold / Silver's (Goldene / Silberne Edition's) names over English Gold / Silver's tables (scripts/gen_gen2_tables.py gold_silver_de). */
internal val GOLD_SILVER_DE_TEXT by lazy {
    GameText(
        species = { speciesNamesGoldSilverDe }, moves = { moveNamesGoldSilverDe }, items = { itemNamesGoldSilverDe }, natures = emptyList(),
        mapSecs = { landmarkNamesGoldSilverDe }, smallFontWidths = { IntArray(0) }, englishMapSecs = { mapSecDataGoldSilver },
        englishMoves = { moveDataCrystal },
    )
}

/** French Gold / Silver's names over English Gold / Silver's tables (scripts/gen_gen2_tables.py gold_silver_fr). */
internal val GOLD_SILVER_FR_TEXT by lazy {
    GameText(
        species = { speciesNamesGoldSilverFr }, moves = { moveNamesGoldSilverFr }, items = { itemNamesGoldSilverFr }, natures = emptyList(),
        mapSecs = { landmarkNamesGoldSilverFr }, smallFontWidths = { IntArray(0) }, englishMapSecs = { mapSecDataGoldSilver },
        englishMoves = { moveDataCrystal },
    )
}

/** Italian Gold / Silver's names over English Gold / Silver's tables (scripts/gen_gen2_tables.py gold_silver_it). */
internal val GOLD_SILVER_IT_TEXT by lazy {
    GameText(
        species = { speciesNamesGoldSilverIt }, moves = { moveNamesGoldSilverIt }, items = { itemNamesGoldSilverIt }, natures = emptyList(),
        mapSecs = { landmarkNamesGoldSilverIt }, smallFontWidths = { IntArray(0) }, englishMapSecs = { mapSecDataGoldSilver },
        englishMoves = { moveDataCrystal },
    )
}

/** Spanish Gold / Silver's names over English Gold / Silver's tables (scripts/gen_gen2_tables.py gold_silver_es). */
internal val GOLD_SILVER_ES_TEXT by lazy {
    GameText(
        species = { speciesNamesGoldSilverEs }, moves = { moveNamesGoldSilverEs }, items = { itemNamesGoldSilverEs }, natures = emptyList(),
        mapSecs = { landmarkNamesGoldSilverEs }, smallFontWidths = { IntArray(0) }, englishMapSecs = { mapSecDataGoldSilver },
        englishMoves = { moveDataCrystal },
    )
}

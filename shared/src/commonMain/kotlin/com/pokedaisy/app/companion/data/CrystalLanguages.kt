package com.pokedaisy.app.companion.data

/** German Crystal's (Kristall's) names over English Crystal's tables (scripts/gen_gen2_tables.py crystal_de). */
internal val CRYSTAL_DE_TEXT by lazy {
    GameText(
        species = { speciesNamesCrystalDe }, moves = { moveNamesCrystalDe }, items = { itemNamesCrystalDe }, natures = emptyList(),
        mapSecs = { landmarkNamesCrystalDe }, smallFontWidths = { IntArray(0) }, englishMapSecs = { mapSecDataCrystal },
        englishMoves = { moveDataCrystal },
    )
}

/** French Crystal's names over English Crystal's tables (scripts/gen_gen2_tables.py crystal_fr). */
internal val CRYSTAL_FR_TEXT by lazy {
    GameText(
        species = { speciesNamesCrystalFr }, moves = { moveNamesCrystalFr }, items = { itemNamesCrystalFr }, natures = emptyList(),
        mapSecs = { landmarkNamesCrystalFr }, smallFontWidths = { IntArray(0) }, englishMapSecs = { mapSecDataCrystal },
        englishMoves = { moveDataCrystal },
    )
}

/** Italian Crystal's names over English Crystal's tables (scripts/gen_gen2_tables.py crystal_it). */
internal val CRYSTAL_IT_TEXT by lazy {
    GameText(
        species = { speciesNamesCrystalIt }, moves = { moveNamesCrystalIt }, items = { itemNamesCrystalIt }, natures = emptyList(),
        mapSecs = { landmarkNamesCrystalIt }, smallFontWidths = { IntArray(0) }, englishMapSecs = { mapSecDataCrystal },
        englishMoves = { moveDataCrystal },
    )
}

/** Spanish Crystal's names over English Crystal's tables (scripts/gen_gen2_tables.py crystal_es). */
internal val CRYSTAL_ES_TEXT by lazy {
    GameText(
        species = { speciesNamesCrystalEs }, moves = { moveNamesCrystalEs }, items = { itemNamesCrystalEs }, natures = emptyList(),
        mapSecs = { landmarkNamesCrystalEs }, smallFontWidths = { IntArray(0) }, englishMapSecs = { mapSecDataCrystal },
        englishMoves = { moveDataCrystal },
    )
}

package com.pokedaisy.app.companion.i18n

/**
 * PC fan games (PcGameActivity, the library's TOOLS cards; behind FeatureFlags.pcGames): what the game screen says
 * while it loads or when it can't start. LOADING..., EGG and Lv{0} are other tables'.
 */
internal val trPcGames: Map<String, Tr> = mapOf(
    "A PC fan game" to Tr(ja = "パソコンの ファンゲーム", fr = "Un jeu de fan sur PC", de = "Ein Fan-Spiel für PC", it = "Un gioco amatoriale per PC", es = "Un juego de fans de PC", br = "Um jogo de fã de PC", pt = "Um jogo de fã de PC", ko = "PC 팬 게임"),
    "RESUMING..." to Tr(ja = "つづきから...", fr = "REPRISE...", de = "FORTSETZEN...", it = "RIPRESA...", es = "REANUDANDO...", br = "RETOMANDO...", pt = "A RETOMAR...", ko = "이어서 하는 중..."),
    "NO GAME FOUND" to Tr(ja = "ゲームが みつかりません", fr = "AUCUN JEU TROUVÉ", de = "KEIN SPIEL GEFUNDEN", it = "NESSUN GIOCO TROVATO", es = "NO SE ENCONTRÓ EL JUEGO", br = "NENHUM JOGO ENCONTRADO", pt = "NENHUM JOGO ENCONTRADO", ko = "게임을 찾을 수 없어요"),
    "NO CORE: put {0} in {1}" to Tr(ja = "コアが ありません: {0}を {1}に いれて ください", fr = "PAS DE CŒUR : mets {0} dans {1}", de = "KEIN CORE: lege {0} in {1}", it = "NESSUN CORE: metti {0} in {1}", es = "NO HAY NÚCLEO: pon {0} en {1}", br = "SEM NÚCLEO: coloque {0} em {1}", pt = "SEM NÚCLEO: põe {0} em {1}", ko = "코어 없음: {0} 파일을 {1} 폴더에 넣어 주세요"),
    "COULDN'T SET UP THE GAME'S FILES" to Tr(ja = "ゲームの ファイルを よういできません", fr = "IMPOSSIBLE DE PRÉPARER LES FICHIERS", de = "SPIELDATEIEN NICHT BEREIT", it = "IMPOSSIBILE PREPARARE I FILE", es = "NO SE PUDIERON PREPARAR LOS ARCHIVOS", br = "NÃO DEU PARA PREPARAR OS ARQUIVOS", pt = "NÃO FOI POSSÍVEL PREPARAR OS FICHEIROS", ko = "게임 파일을 준비할 수 없어요"),
    "COULDN'T BACK UP THE SAVES: the game stays closed" to Tr(ja = "セーブを バックアップ できません: ゲームは ひらきません", fr = "SAUVEGARDES NON COPIÉES : le jeu reste fermé", de = "SPEICHERSTÄNDE NICHT GESICHERT: das Spiel bleibt zu", it = "SALVATAGGI NON COPIATI: il gioco resta chiuso", es = "NO SE COPIARON LAS PARTIDAS: el juego no se abre", br = "NÃO DEU PARA COPIAR OS SAVES: o jogo fica fechado", pt = "NÃO FOI POSSÍVEL COPIAR OS SAVES: o jogo fica fechado", ko = "세이브를 백업할 수 없어요: 게임을 열지 않아요"),
    "THE GAME DIDN'T START" to Tr(ja = "ゲームが はじまりませんでした", fr = "LE JEU N'A PAS DÉMARRÉ", de = "DAS SPIEL STARTETE NICHT", it = "IL GIOCO NON È PARTITO", es = "EL JUEGO NO SE INICIÓ", br = "O JOGO NÃO COMEÇOU", pt = "O JOGO NÃO ARRANCOU", ko = "게임이 시작되지 않았어요"),
    "Press BACK again to close the game" to Tr(ja = "もういちど もどるで ゲームを とじます", fr = "Appuie encore sur RETOUR pour fermer le jeu", de = "Drück noch mal ZURÜCK, um das Spiel zu schließen", it = "Premi di nuovo INDIETRO per chiudere il gioco", es = "Pulsa ATRÁS otra vez para cerrar el juego", br = "Aperte VOLTAR de novo para fechar o jogo", pt = "Carrega outra vez em VOLTAR para fechar o jogo", ko = "뒤로를 한 번 더 누르면 게임을 닫아요"),
)

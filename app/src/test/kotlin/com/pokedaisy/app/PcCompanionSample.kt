package com.pokedaisy.app

/**
 * A PC fan game's companion snapshot as our preload sends it (linux/pc_games/pokedaisy_companion.rb, VERSION 3), with
 * the invented creatures of the manual and the site (website/src/lib/creatures.js) and a short type table in Essentials'
 * numbering (Fire 10, Water 11, Grass 12, Electric 13, Fairy 18, a fusion game's own 20). No icons: no game folder.
 */
object PcCompanionSample {
    val JSON = """
    {"v":3,"frame":4200,
     "typeTable":[
       {"id":0,"name":"Normal","weak":[1],"resist":[],"immune":[7]},
       {"id":1,"name":"Fighting","weak":[2,14,18],"resist":[5,6,17],"immune":[]},
       {"id":2,"name":"Flying","weak":[5,13,15],"resist":[1,6,12],"immune":[4]},
       {"id":5,"name":"Rock","weak":[1,4,8,11,12],"resist":[0,2,3,10],"immune":[]},
       {"id":7,"name":"Ghost","weak":[7,17],"resist":[3,6],"immune":[0,1]},
       {"id":10,"name":"Fire","weak":[4,5,11],"resist":[6,8,10,12,15,18],"immune":[]},
       {"id":11,"name":"Water","weak":[12,13],"resist":[8,10,11,15],"immune":[]},
       {"id":12,"name":"Grass","weak":[2,3,6,10,15],"resist":[4,11,12,13],"immune":[]},
       {"id":13,"name":"Electric","weak":[4],"resist":[2,8,13],"immune":[]},
       {"id":18,"name":"Fairy","weak":[3,8],"resist":[1,6,17],"immune":[16]},
       {"id":20,"name":"Ice/Fire/Electric","weak":[1,4,11],"resist":[15,2,8,13,6,12,18,10],"immune":[]}
     ],
     "trainer":{"name":"DAISY","id":12345,"money":3820,"badges":2,"seconds":5400},
     "party":[
       {"name":"Pip","species":"EMBERPUP","dex":4,"level":18,"hp":51,"totalhp":51,"status":"NONE","egg":false,"shiny":false,
        "gender":0,"fusion":false,"types":[10],"item":"Ember Charm","nature":"Adamant","natureId":3,"ability":"Blaze",
        "stats":[51,32,25,22,24,30],"ivs":[31,20,18,10,25,30],"evs":[12,40,0,0,4,30],
        "moves":[{"name":"Ember","pp":21,"total":25,"type":10,"power":40},{"name":"Tackle","pp":35,"total":35,"type":0,"power":40},
                 {"name":"Growl","pp":40,"total":40,"type":0,"power":0}]},
       {"name":"BUBBLEFIN","species":"BUBBLEFIN","dex":7,"level":16,"hp":12,"totalhp":44,"status":"POISON","egg":false,"shiny":true,
        "gender":1,"fusion":false,"types":[11],"item":null,"nature":"Calm","natureId":20,"ability":"Torrent",
        "stats":[44,22,28,26,30,21],"ivs":[10,10,10,10,10,10],"evs":[0,0,0,0,0,0],
        "moves":[{"name":"Bubble","pp":30,"total":30,"type":11,"power":40}]},
       {"name":"VOLTMOUSE","species":"VOLTMOUSE/GALEFINCH","dex":505,"level":20,"hp":0,"totalhp":48,"status":"NONE","egg":false,"shiny":false,
        "gender":2,"fusion":true,"types":[13,2],"item":null,"nature":"Timid","natureId":10,"ability":"Static",
        "stats":[48,24,20,30,22,40],"ivs":[5,5,5,5,5,5],"evs":[0,0,0,0,0,0],
        "moves":[{"name":"Spark","pp":20,"total":20,"type":13,"power":65},{"name":"Gust","pp":35,"total":35,"type":2,"power":40}]},
       {"name":"Egg","species":"SPROUTLING","dex":1,"level":1,"hp":1,"totalhp":1,"status":"NONE","egg":true,"shiny":false,
        "gender":0,"fusion":false,"types":[12],"moves":[]}
     ],
     "bag":[
       {"name":"Items","items":[{"name":"Ember Charm","qty":1,"desc":"A warm charm."},{"name":"Escape Rope","qty":2}]},
       {"name":"Medicine","items":[{"name":"Potion","qty":5,"desc":"Restores 20 HP."}]},
       {"name":"Poké Balls","items":[{"name":"Poké Ball","qty":10}]},
       {"name":"TMs & HMs","items":[]},
       {"name":"Berries","items":[{"name":"Oran Berry","qty":3}]},
       {"name":"Mail","items":[]},
       {"name":"Battle Items","items":[]},
       {"name":"Key Items","items":[{"name":"Bicycle","qty":1}]}
     ],
     "map":{"id":42,"name":"Route 2","x":12,"y":30},
     "inBattle":false,"scene":"Scene_Map"}
    """.trimIndent()

    /** [JSON] mid-battle (snapshot VERSION 4): Pip out against a trainer's BUBBLEFIN, a second foe in reserve. */
    val BATTLE_JSON: String = org.json.JSONObject(JSON).put("battle", org.json.JSONObject("""
    {"trainer":true,"double":false,"menu":"command","menuBattler":0,
     "player":[{"name":"Pip","species":"EMBERPUP","level":18,"hp":40,"totalhp":51,"status":"BURN","egg":false,"shiny":false,
       "gender":0,"types":[10],"item":"Ember Charm","ability":"Blaze","natureId":3,
       "stats":[51,32,25,22,24,30],"ivs":[31,20,18,10,25,30],"evs":[12,40,0,0,4,30],
       "moves":[{"name":"Ember","pp":20,"total":25,"type":10,"power":40},{"name":"Tackle","pp":34,"total":35,"type":0,"power":40},
                {"name":"Growl","pp":40,"total":40,"type":0,"power":0}],
       "index":0,"partyIndex":0,"battleStats":[32,25,30,22,24],"stages":[1,0,-1,0,0,0,0],"fainted":false}],
     "foes":[{"name":"BUBBLEFIN","species":"BUBBLEFIN","level":17,"hp":20,"totalhp":46,"status":"NONE","egg":false,"shiny":false,
       "gender":1,"types":[11],"item":null,"ability":"Torrent",
       "moves":[{"name":"Bubble","pp":28,"total":30,"type":11,"power":40},{"name":"Tackle","pp":35,"total":35,"type":0,"power":40}],
       "index":1,"partyIndex":0,"battleStats":[22,28,21,26,30],"stages":[0,-1,0,0,0,0,0],"fainted":false}],
     "foeParty":[
       {"name":"BUBBLEFIN","species":"BUBBLEFIN","level":17,"hp":20,"totalhp":46,"status":"NONE","egg":false,"types":[11]},
       {"name":"THORNLET","species":"THORNLET","level":18,"hp":50,"totalhp":50,"status":"NONE","egg":false,"types":[12]}
     ]}
    """.trimIndent())).toString()
}

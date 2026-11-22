# pokedaisy_companion.rb - PokeDaisy's companion hook for Pokemon Essentials games.
# Loaded by the mkxp-z core as a preload script, before the game's own scripts.
#
# The game's state goes into one fixed buffer (a "mailbox") that the host finds once by its magic
# bytes in the core's memory (RETRO_MEMORY_SYSTEM_RAM = the Ruby heap) and then reads every frame:
#   0  16  magic "PKDAISY-MBX-0001" (written byte by byte, so the whole string never exists elsewhere)
#   16  4  sequence number (u32 LE), odd while a write is in progress
#   20  4  payload length (u32 LE)
#   24  .  payload: JSON (hand-written encoder: the sandbox may have no json library)
# Every name in it is the game's own (its PBS data, in the game's language), so the host draws them as they are.
# Nothing here writes game state: it only reads.

module PokeDaisyCompanion
  CAPACITY = 256 * 1024
  HEADER = 24
  EVERY = 15 # frames between snapshots (4 a second at 60 fps)
  VERSION = 7
  # Essentials' stat keys, in the order "stats" sends them.
  STAT_KEYS = [:HP, :ATTACK, :DEFENSE, :SPECIAL_ATTACK, :SPECIAL_DEFENSE, :SPEED]
  # A battler's stat stages, in the order the companion keeps them (its battle stats, then accuracy and evasion).
  STAGE_KEYS = [:ATTACK, :DEFENSE, :SPEED, :SPECIAL_ATTACK, :SPECIAL_DEFENSE, :ACCURACY, :EVASION]

  # The battle running (pbStartBattle's own object, set by [hook_battle] for its length); nil = none.
  class << self
    attr_accessor :battle
  end

  # The game's battle class gets a wrapper round pbStartBattle (the whole battle, start to end) once the game's scripts
  # have defined it: our preload runs before them. Essentials v19 calls it PokeBattle_Battle, v20+ Battle.
  def self.hook_battle
    return if @battle_hooked
    cls = (defined?(::PokeBattle_Battle) && ::PokeBattle_Battle) ||
          (defined?(::Battle) && ::Battle.is_a?(Class) && ::Battle.method_defined?(:pbStartBattle) && ::Battle)
    return unless cls && cls.method_defined?(:pbStartBattle)
    @battle_hooked = true
    cls.prepend(Module.new do
      def pbStartBattle(*args, &block)
        PokeDaisyCompanion.battle = self
        super
      ensure
        PokeDaisyCompanion.battle = nil
      end
    end)
  end

  # One battler as the companion's BATTLE tab takes it: the party entry of who it shows as (an Illusion's), then what the
  # battle has changed - HP, status, types, moves with their PP, stages, stats.
  def self.battler(b)
    return PokeDaisyReborn.battler(b) if PokeDaisyReborn.on?
    shown = try { b.displayPokemon } || try { b.pokemon }
    out = (shown && try { mon(shown) }) || {}
    out.merge(
      "index" => b.index,
      "partyIndex" => try { b.pokemonIndex },
      "level" => try { b.level },
      "hp" => try { b.hp },
      "totalhp" => try { b.totalhp },
      "status" => try { b.status },
      "types" => try { b.pbTypes(true).map { |t| type_id(t) } },
      "item" => try { b.item ? GameData::Item.get(b.item).name : nil },
      "ability" => try { b.ability ? GameData::Ability.get(b.ability).name : nil },
      "moves" => try {
        b.moves.map { |m|
          { "name" => try { m.name }, "pp" => try { m.pp }, "total" => try { m.total_pp },
            "type" => type_id(try { m.type }), "power" => try { m.baseDamage } || 0 }
        }
      },
      "battleStats" => try { [b.attack, b.defense, b.speed, b.spatk, b.spdef] },
      "stages" => try { STAGE_KEYS.map { |k| b.stages[k] || 0 } },
      "fainted" => try { b.fainted? } || false,
    )
  end

  # ---- picks from the companion ---------------------------------------------------------------------------------------
  # The host asks with one line of Ruby (the core's cheat call runs it once): PokeDaisyCompanion.request(:fight, 2). The
  # game's own menus then take it on their next update - the command or fight window's cursor set, and the game's next
  # USE check answered once - so the pick goes through the game's own code, sounds and checks. A switch skips the party
  # screen and asks the battle's own switch checks (a refusal opens the screen as usual). Unused picks go stale.
  PICK_FRAMES = 180

  class << self
    attr_accessor :menu, :menu_battler
  end

  def self.request(kind, arg = nil)
    @pick = { kind: kind, arg: arg, frame: @frame }
  end

  def self.pick
    return nil unless @pick
    if @frame - @pick[:frame] > PICK_FRAMES
      @pick = nil
      return nil
    end
    @pick
  end

  def self.drop_pick
    @pick = nil
  end

  # Input.trigger?'s answer for [button] once ([press]): what a pick presses in the game's menu loop.
  def self.press(button)
    @press = button
    @press_frame = @frame
  end

  # Only within a few frames of [press]: a menu that closed first must not leave a press for the next text box.
  def self.take_press(button)
    return false unless @press && @press == button
    fresh = @frame - (@press_frame || 0) <= 10
    @press = nil
    fresh
  end

  def self.use_button
    Input.const_defined?(:USE) ? Input::USE : Input::C
  end

  def self.back_button
    Input.const_defined?(:BACK) ? Input::BACK : Input::B
  end

  # From the battle scene's update (pbUpdate(cw)): the command or fight window waiting, and a pick for it.
  def self.on_menu_update(scene, cw)
    p = pick
    return unless p && cw
    sprites = scene.instance_variable_get(:@sprites) || {}
    # Essentials 19 names them commandWindow / fightWindow; the Reborn engine commandwindow / fightwindow.
    if cw.equal?(sprites["commandWindow"]) || cw.equal?(sprites["commandwindow"])
      cmd = { fight: 0, action: p[:arg], switch: 2 }[p[:kind]]
      return drop_pick unless cmd.is_a?(Integer) && cmd.between?(0, 3)
      cw.index = cmd
      press(use_button)
      drop_pick if p[:kind] == :action  # a fight / switch carries on to the next window
    elsif cw.equal?(sprites["fightWindow"]) || cw.equal?(sprites["fightwindow"])
      case p[:kind]
      when :back
        press(back_button)
        drop_pick
      when :fight
        moves = try { cw.battler.moves } || []
        m = p[:arg].is_a?(Integer) ? moves[p[:arg]] : nil
        if m && (try { m.id } || try { m.move })
          cw.respond_to?(:setIndex) ? cw.setIndex(p[:arg]) : cw.index = p[:arg]
          press(use_button)
        end
        drop_pick
      else
        drop_pick
      end
    end
  end

  # The battle scene gets its hooks once the game's scripts have defined it: which menu waits ([menu]) and the picks.
  def self.hook_scene
    return if @scene_hooked
    # The Reborn engine's scene has its own loops (PokeDaisyReborn.hook_scene); asked until its scripts are in.
    return (@scene_hooked = true) if PokeDaisyReborn.on? && PokeDaisyReborn.hook_scene
    return if PokeDaisyReborn.on?
    cls = (defined?(::PokeBattle_Scene) && ::PokeBattle_Scene) || (defined?(::Battle::Scene) && ::Battle::Scene)
    return unless cls && cls.method_defined?(:pbCommandMenuEx) && cls.method_defined?(:pbUpdate)
    @scene_hooked = true
    cls.prepend(Module.new do
      def pbUpdate(cw = nil)
        r = super
        PokeDaisyCompanion.on_menu_update(self, cw)
        r
      end

      def pbCommandMenuEx(idxBattler, *args, &block)
        PokeDaisyCompanion.menu = "command"
        PokeDaisyCompanion.menu_battler = idxBattler
        super
      ensure
        PokeDaisyCompanion.menu = nil
      end

      def pbFightMenu(idxBattler, *args, &block)
        PokeDaisyCompanion.menu = "fight"
        PokeDaisyCompanion.menu_battler = idxBattler
        super
      ensure
        PokeDaisyCompanion.menu = nil
      end

      def pbItemMenu(idxBattler, *args, &block)
        PokeDaisyCompanion.menu = "bag"
        super
      ensure
        PokeDaisyCompanion.menu = nil
      end

      # A switch picked on the companion: the battle's own checks with the party index, no party screen. Refused (or
      # none picked), the game's own screen as usual.
      def pbPartyScreen(idxBattler, *args, &block)
        p = PokeDaisyCompanion.pick
        if p && p[:kind] == :switch && p[:arg].is_a?(Integer) && block
          PokeDaisyCompanion.drop_pick
          return if block.call(p[:arg], nil)
        end
        PokeDaisyCompanion.menu = "party"
        super
      ensure
        PokeDaisyCompanion.menu = nil
      end
    end)
  end

  def self.battle_snapshot(bt)
    return PokeDaisyReborn.battle(bt) if PokeDaisyReborn.on?
    list = try { bt.battlers } || []
    out = {
      "trainer" => try { bt.trainerBattle? } || false,
      "double" => (try { bt.pbSideSize(0) } || 1) > 1 || (try { bt.pbSideSize(1) } || 1) > 1,
      "player" => list.each_with_index.select { |b, i| b && i.even? }.map { |b, _| battler(b) },
      "foes" => list.each_with_index.select { |b, i| b && i.odd? }.map { |b, _| battler(b) },
      "menu" => @menu,
      "menuBattler" => @menu_battler,
    }
    if out["trainer"]
      out["foeParty"] = try { bt.pbParty(1).map { |p| p ? mon(p) : nil } }
    end
    out
  end

  @buf = nil
  @seq = 0
  @frame = 0
  @last = nil
  @icons = {} # icon file per key: each lookup is a file check through the sandbox

  def self.buffer
    return @buf if @buf
    b = "\0".b * (HEADER + CAPACITY)
    m = [80, 75, 68, 65, 73, 83, 89, 45, 77, 66, 88, 45, 48, 48, 48, 49] # the magic, as numbers
    m.each_with_index { |c, i| b.setbyte(i, c) }
    @buf = b
  end

  def self.put_u32(off, v)
    b = buffer
    4.times { |i| b.setbyte(off + i, (v >> (8 * i)) & 0xFF) }
  end

  def self.publish(data)
    return if data == @last # nothing new: the host's read finds the same sequence number
    @last = data
    b = buffer
    s = data.b
    s = s[0, CAPACITY] if s.bytesize > CAPACITY
    @seq = (@seq + 1) | 1
    put_u32(16, @seq)                    # odd: writing
    b[HEADER, s.bytesize] = s            # same length in place: the buffer is never reallocated
    put_u32(20, s.bytesize)
    @seq += 1
    put_u32(16, @seq)                    # even: done
  end

  # JSON already written (a Town Map's places: built once, sent as they are each time).
  Raw = Struct.new(:json)

  # --- a tiny JSON encoder ---
  def self.j(v)
    case v
    when Raw then v.json
    when nil then "null"
    when true then "true"
    when false then "false"
    when Integer then v.to_s
    when Float then v.finite? ? v.to_s : "null"
    when Symbol then j(v.to_s)
    when String
      s = v.dup.force_encoding("UTF-8")
      s = s.scrub("?") unless s.valid_encoding?
      '"' + s.gsub(/[\\"\u0000-\u001f]/) { |c| c == "\\" ? "\\\\" : c == '"' ? "\\\"" : format("\\u%04x", c.ord) } + '"'
    when Array then "[" + v.map { |e| j(e) }.join(",") + "]"
    when Hash then "{" + v.map { |k, e| j(k.to_s) + ":" + j(e) }.join(",") + "}"
    else j(v.to_s)
    end
  end

  # A value, or nil if the game hasn't got it (another Essentials version, a game's own class).
  def self.try
    yield
  rescue Exception
    nil
  end

  def self.cached(key)
    return @icons[key] if @icons.key?(key)
    @icons[key] = try { yield }
  end

  # A type as the game numbers it (GameData::Type#id_number): the host names it and finds its matchups through the
  # snapshot's "typeTable", so a game's own types (Fairy, a hack's) read right.
  def self.type_id(t)
    return PokeDaisyReborn.type_id(t) if PokeDaisyReborn.on?
    try { GameData::Type.get(t).id_number }
  end

  # Every type: its name and what it's weak to, resists and is immune to, by number. The same each time: built once.
  def self.type_table
    return PokeDaisyReborn.type_table if PokeDaisyReborn.on?
    @types ||= try {
      list = []
      GameData::Type.each do |t|
        next if try { t.pseudo_type }
        ids = ->(a) { (a || []).map { |x| type_id(x) }.compact }
        list << { "id" => t.id_number, "name" => t.name, "weak" => ids.(t.weaknesses),
                  "resist" => ids.(t.resistances), "immune" => ids.(t.immunities) }
      end
      list
    } || []
  end

  # The party icon: one file, or a fusion's head and body (Infinite Fusion draws the head's icon over the body's from
  # [offset] rows below the middle, as its createFusionIcon does).
  def self.icon(p)
    return { "file" => cached([:egg]) { GameData::Species.icon_filename_from_pokemon(p) } } if try { p.egg? }
    if try { p.respond_to?(:isFusion?) && p.isFusion? }
      body = try { GameData::Species.get(getBodyID(p.species)).species }
      head = try { GameData::Species.get(getHeadID(p.species, getBodyID(p.species))).species }
      if body && head
        return {
          "head" => cached([:icon, head, p.spriteform_head]) { GameData::Species.icon_filename(head, p.spriteform_head) },
          "body" => cached([:icon, body, p.spriteform_body]) { GameData::Species.icon_filename(body, p.spriteform_body) },
          "offset" => try { Settings::FUSION_ICON_SPRITE_OFFSET } || 10,
        }
      end
    end
    key = [:mon, p.species, try { p.form }, try { p.gender }, try { p.shiny? }]
    { "file" => cached(key) { GameData::Species.icon_filename_from_pokemon(p) } }
  end

  def self.mon(p)
    return PokeDaisyReborn.mon(p) if PokeDaisyReborn.on?
    {
      "name" => try { p.name },
      "species" => try { p.speciesName },
      "dex" => try { p.species_data.id_number },
      "level" => try { p.level },
      "hp" => try { p.hp },
      "totalhp" => try { p.totalhp },
      "status" => try { p.status },
      "egg" => try { p.egg? } || false,
      "shiny" => try { p.shiny? } || false,
      "gender" => try { p.gender },
      "fusion" => try { p.respond_to?(:isFusion?) && p.isFusion? } || false,
      "types" => try { [p.type1, p.type2].uniq.map { |t| type_id(t) } },
      "item" => try { p.item ? p.item.name : nil },
      "nature" => try { p.nature.name },
      "natureId" => try { p.nature.id_number }, # Essentials numbers natures as Gen 3 does (HARDY 0 .. QUIRKY 24)
      "ability" => try { p.ability.name },
      "stats" => try { [p.totalhp, p.attack, p.defense, p.spatk, p.spdef, p.speed] },
      # In the stats' order; Essentials keeps them as hashes by stat (v19+).
      "ivs" => try { STAT_KEYS.map { |k| p.iv[k] } },
      "evs" => try { STAT_KEYS.map { |k| p.ev[k] } },
      "moves" => try {
        p.moves.map { |m|
          data = try { GameData::Move.get(m.id) }
          { "name" => try { m.name } || m.id.to_s, "pp" => m.pp, "total" => m.total_pp,
            "type" => type_id(try { m.type } || try { data.type }), "power" => try { data.base_damage } || 0 }
        }
      },
      "icon" => try { icon(p) },
    }
  end

  def self.bag(b)
    return PokeDaisyReborn.bag(b) if PokeDaisyReborn.on?
    names = try { PokemonBag.pocketNames } || []
    b.pockets.each_with_index.map { |pk, i|
      next nil if i == 0
      {
        "name" => names[i] || "POCKET #{i}",
        "items" => pk.map { |it|
          id = it[0]
          { "name" => try { GameData::Item.get(id).name } || id.to_s, "qty" => it[1],
            "desc" => cached([:desc, id]) { GameData::Item.get(id).description },
            "icon" => cached([:item, id]) { GameData::Item.icon_filename(id) } }
        },
      }
    }.compact
  end

  # --- the Town Map ---
  # Where the player is on the game's Town Map (the map's metadata: [region, x, y]) and that region's map: its picture,
  # the size of a tile and where a tile's middle is, its places' tiles, the ones whose switch is still off (the game
  # leaves them unnamed) and the player's mark. nil off the Town Map (a map without a position).
  def self.town_map
    return PokeDaisyReborn.town_map if PokeDaisyReborn.on?
    md = try { GameData::MapMetadata.try_get($game_map.map_id) }
    pos = md ? try { md.town_map_position } : nil
    return nil unless pos.is_a?(Array) && pos.size >= 3
    data = town_map_region(pos[0])
    return nil unless data
    picture = data[:picture]
    # Infinite Fusion's Town Map (Marin's Better Region Map) draws map/map_postgame once the story is over.
    if defined?(BetterRegionMap) && picture.to_s =~ /\Amap\/map(\.png)?\z/i && try { isPostgame? }
      picture = "map/map_postgame"
    end
    {
      "region" => pos[0], "x" => pos[1], "y" => pos[2], "picture" => picture,
      "tile" => data[:tile], "centre" => data[:centre], "head" => town_map_head,
      "hidden" => data[:switched].reject { |_, sw| try { $game_switches[sw] } }.map(&:first),
      "points" => Raw.new(data[:json]),
    }
  end

  # A region's Town Map, built once: v20+ keep it as GameData::TownMap, v19 in town_map.dat ([name, picture, points]).
  # A point is [x, y, name, point of interest, fly destination..., switch].
  def self.town_map_region(region)
    @town_maps ||= {}
    return @town_maps[region] if @town_maps.key?(region)
    @town_maps[region] = try {
      if defined?(GameData::TownMap) && GameData::TownMap.respond_to?(:get)
        tm = GameData::TownMap.get(region)
        file = tm.filename
        points = tm.point
      else
        d = pbLoadTownMapData[region]
        file = d[1]
        points = d[2]
      end
      points ||= []
      if defined?(BetterRegionMap)
        tile = (try { BetterRegionMap::TileWidth } || 16).to_i
        centre = tile / 2 # its sprites sit on tile * size + size / 2
      else
        tile = (try { PokemonRegionMap_Scene::SQUAREWIDTH } || try { PokemonRegionMap_Scene::SQUARE_WIDTH } || 16).to_i
        centre = 0 # Essentials' own: -size / 2 + tile * size, a sprite one tile wide
      end
      {
        picture: file, tile: tile, centre: centre,
        switched: points.each_with_index.select { |pt, _| pt[7].is_a?(Integer) && pt[7] > 0 }.map { |pt, i| [i, pt[7]] },
        json: j(points.map { |pt| [pt[0], pt[1], place_name(pt[2]), place_name(pt[3])] }),
      }
    }
  end

  # A place's name in the player's language (the game's PlaceNames messages), else as the data has it.
  def self.place_name(n)
    return nil if n.nil? || n == ""
    try { pbGetMessageFromHash(MessageTypes::PlaceNames, n) } || n
  end

  # The player's mark on the Town Map: Better Region Map's own, else the trainer type's map icon.
  def self.town_map_head
    return "Graphics/Pictures/map/location_icon" if defined?(BetterRegionMap)
    t = (defined?($Trainer) && $Trainer) || (defined?($player) && $player)
    t ? try { GameData::TrainerType.player_map_icon_filename(t.trainer_type) } : nil
  end

  # --- the wild Pokémon here (the GUIDE's HERE) ---
  # The encounter kinds a map can have, as Essentials names them; the game picks the time of day's own of each
  # (find_valid_encounter_type_for_time: LandDay, WaterNight...). The Bug Contest's isn't the player's to meet.
  WILD_BASES = [:Land, :Land1, :Land2, :Land3, :Cave, :Water, :OldRod, :GoodRod, :SuperRod, :RockSmash, :HeadbuttLow,
                :HeadbuttHigh]

  # The current map's wild Pokémon as the game set them up for it ($PokemonEncounters' tables, after any mode or
  # randomizer of the game's own), the kinds that hold now, each Pokémon once a kind: its levels, its share of the odds,
  # its icon, and whether the Pokédex has seen and caught it. Built once per map and time of day; the Pokédex each time.
  def self.wild
    return PokeDaisyReborn.wild if PokeDaisyReborn.on?
    enc = defined?($PokemonEncounters) && $PokemonEncounters
    return nil unless enc
    tables = try { enc.instance_variable_get(:@encounter_tables) }
    return nil unless tables.is_a?(Hash)
    time = try { pbGetTimeNow }
    types = WILD_BASES.map { |b| try { enc.find_valid_encounter_type_for_time(b, time) } || (tables.key?(b) ? b : nil) }
    types = types.compact.uniq
    key = [try { $game_map.map_id }, types, tables.object_id]
    if key != @wild_key
      @wild_key = key
      @wild = types.map { |t| wild_kind(t, WILD_BASES.select { |b| t.to_s.start_with?(b.to_s) }.max_by { |b| b.to_s.size } || t, tables[t]) }.compact
    end
    dex = try { ((defined?($Trainer) && $Trainer) || (defined?($player) && $player)).pokedex }
    @wild.map do |k|
      { "kind" => k[:kind], "slots" => k[:slots].map { |sl|
        sl.merge("seen" => dex ? !!try { dex.seen?(sl[:id]) } : nil, "owned" => dex ? !!try { dex.owned?(sl[:id]) } : nil)
          .reject { |key, _| key == :id }
      } }
    end
  end

  # One kind's list: [chance, species, min level, max level] entries, a species' chances added up.
  def self.wild_kind(type, base, list)
    return nil if !list || list.empty?
    total = list.sum { |e| e[0].to_i }
    return nil if total <= 0
    by = {}
    list.each do |e|
      s = (by[e[1]] ||= { id: e[1], chance: 0, min: e[2], max: e[3] || e[2] })
      s[:chance] += e[0].to_i
      s[:min] = [s[:min], e[2]].min
      s[:max] = [s[:max], e[3] || e[2]].max
    end
    slots = by.values.sort_by { |s| -s[:chance] }.map do |s|
      {
        id: s[:id], "name" => try { GameData::Species.get(s[:id]).name } || s[:id].to_s,
        "min" => s[:min], "max" => s[:max], "percent" => (s[:chance] * 100.0 / total).round,
        "icon" => @wild_icons[s[:id]] ||= wild_icon(s[:id], s[:min]),
      }
    end
    { kind: base.to_s, slots: slots }
  end
  @wild_icons = {}

  # A wild species' party icon, from a Pokémon made for it (never shiny, never added anywhere): the party's own rules,
  # a fusion's two halves included.
  def self.wild_icon(species, level)
    pk = try { Pokemon.new(species, [level.to_i, 1].max) }
    return nil unless pk
    try { pk.shiny = false }
    try { icon(pk) }
  end

  def self.snapshot
    out = { "v" => VERSION, "frame" => (Graphics.frame_count rescue 0), "typeTable" => type_table }
    t = (defined?($Trainer) && $Trainer) || (defined?($player) && $player)
    if t
      out["trainer"] = {
        "name" => try { t.name }, "id" => try { t.public_ID } || try { t.publicID } || try { t.id }, "money" => try { t.money },
        "badges" => try { t.badge_count } || try { t.numbadges },
        "seconds" => try { Graphics.frame_count / Graphics.frame_rate },
      }
      out["party"] = try { t.party.map { |p| mon(p) } } || []
    end
    bag = (defined?($PokemonBag) && $PokemonBag) || (defined?($bag) && $bag)
    out["bag"] = try { bag(bag) } if bag
    if defined?($game_map) && $game_map
      out["map"] = {
        "id" => try { $game_map.map_id },
        "name" => try { pbGetMapNameFromId($game_map.map_id) },
        "x" => try { $game_player.x }, "y" => try { $game_player.y },
      }
      tm = try { town_map }
      out["townMap"] = tm if tm
      w = try { wild }
      out["wild"] = w if w
    end
    out["inBattle"] = try { $game_temp ? !!$game_temp.in_battle : false } || false
    # Its own guard: whatever a battle does, the party and bag above still go out.
    if @battle
      out["battle"] = begin
        battle_snapshot(@battle)
      rescue Exception
        nil
      end
    end
    out["scene"] = try { $scene ? $scene.class.name : nil }
    out
  end

  def self.tick
    @frame += 1
    hook_battle
    hook_scene
    return if @frame % EVERY != 0
    publish(j(snapshot))
  rescue Exception => e
    publish(j({ "v" => VERSION, "error" => e.class.name }))
  end
end

# ---- the Reborn engine ------------------------------------------------------------------------------------------------
# Pokémon Reborn and Rejuvenation run an older Essentials rewritten by their team, not v19+: no GameData. Their data is
# one cache ($cache: pkmn, moves, items, types, abil, natures, mapdata, town_map) keyed by symbols, Pokémon are
# PokeBattle_Pokemon, the bag keeps item symbols per pocket with the counts apart, a map's encounters are hashes of
# species to [chance, min level, max level] lists. This module reads the same snapshot from there; PokeDaisyCompanion's
# readers hand over to it while [on?].
module PokeDaisyReborn
  module_function

  def try
    yield
  rescue Exception
    nil
  end

  # The game runs on it: $cache with its Pokémon data and no GameData (asked again until $cache is up).
  def on?
    return @on unless @on.nil?
    return false unless defined?($cache) && $cache
    @on = !defined?(::GameData::Species) && $cache.respond_to?(:pkmn) && $cache.respond_to?(:types)
  end

  # Types numbered by their place in $cache.types (the snapshot's ids), weak / resist / immune as the game lists them.
  def type_ids
    @type_ids ||= ($cache.types.keys.each_with_index.to_h rescue {})
  end

  def type_id(t) = t.nil? ? nil : type_ids[t]

  def type_table
    @type_table ||= try {
      $cache.types.map do |sym, td|
        ids = ->(a) { (a || []).map { |x| type_id(x) }.compact }
        { "id" => type_id(sym), "name" => td.name, "weak" => ids.(td.weak), "resist" => ids.(td.resist),
          "immune" => ids.(td.immune) }
      end
    } || []
  end

  def species_name(sp, form = 0) = try { getMonName(sp, form) } || try { $cache.pkmn[sp, form].name } || sp.to_s
  def item_name(i) = i.nil? ? nil : (try { $cache.items[i].name } || i.to_s)
  def move_data(m) = try { $cache.moves[m] }
  # Seen / caught: Rejuvenation's Pokédex answers seen? / owned?, Reborn's keeps a hash per species. nil = no Pokédex.
  def dex_marks(sp)
    dex = try { $Trainer.pokedex }
    return [nil, nil] unless dex
    seen = try { dex.seen?(sp) }
    owned = try { dex.owned?(sp) }
    if seen.nil? || owned.nil?
      d = try { dex.dexList[sp] }
      return [nil, nil] unless d
      seen = try { d[:seen?] } if seen.nil?
      owned = try { d[:owned?] } if owned.nil?
    end
    [!!seen, !!owned]
  end

  # Gender as Essentials numbers it: Rejuvenation's are letters (:M, :F, :N).
  def gender(g)
    case g.to_s
    when "0", "M" then 0
    when "1", "F" then 1
    when "" then nil
    else 2
    end
  end

  # Their natures keyed in the Gen 3 order (a Pokémon's is personalID % 25 of the keys): the place is Essentials' number.
  def nature_id(n) = try { $cache.natures.keys.index(n) }

  # The party icon as their pbPokemonIconBitmap picks it: the first file of icon<dex><f><egg>, icon<dex><egg>,
  # <species><f><egg>, <species><egg> (in lower case), iconEgg, icon000 - in Graphics/Icons (Reborn: by name) or
  # Graphics/Icons/Pokemon (Rejuvenation: by number) - a sheet with a 128 x 64 row (two frames) per form, the shiny ones
  # to the right. [x] / [y] / [w] / [h] are the row (the host starts over at the top past the end).
  def icon(p)
    form = try { p.form } || 0
    shiny = try { p.isShiny? } || false
    file = icon_file(p.species, form, try { p.isFemale? } || false, try { p.isEgg? } || false)
    file && { "file" => file, "x" => shiny ? 128 : 0, "y" => form * 64, "w" => 128, "h" => 64 }
  end

  def icon_file(sp, form = 0, female = false, egg = false)
    PokeDaisyCompanion.cached([:rbicon, sp, form, female, egg]) do
      dex = try { $cache.pkmn[sp, form].dexnum } || 0
      f = female ? "f" : ""
      e = egg ? "egg" : ""
      name = sp.to_s.downcase
      names = [format("icon%03d%s%s", dex, f, e), format("icon%03d%s", dex, e), "#{name}#{f}#{e}", "#{name}#{e}"]
      %w[Graphics/Icons/ Graphics/Icons/Pokemon/].product(names + %w[iconEgg icon000]).map(&:join)
        .find { |n| try { pbResolveBitmap(n) } }
    end
  end

  # A species' own icon, for a list of wild ones (no Pokémon made for it): its first form's, not shiny.
  def species_icon(sp)
    file = icon_file(sp)
    file && { "file" => file, "x" => 0, "y" => 0, "w" => 128, "h" => 64 }
  end

  def mon(p)
    form = try { p.form } || 0
    egg = try { p.isEgg? } || false
    {
      "name" => try { p.name },
      "species" => species_name(p.species, form),
      "dex" => try { $cache.pkmn[p.species].dexnum },
      "level" => try { p.level },
      "hp" => try { p.hp },
      "totalhp" => try { p.totalhp },
      "status" => try { p.status ? p.status.to_s : nil },
      "egg" => egg,
      "shiny" => try { p.isShiny? } || false,
      "gender" => gender(try { p.gender }),
      "fusion" => false,
      "types" => try { [p.type1, p.type2].compact.uniq.map { |t| type_id(t) } },
      "item" => item_name(try { p.item }),
      "nature" => try { $cache.natures[p.nature].name },
      "natureId" => nature_id(try { p.nature }),
      "ability" => try { $cache.abil[p.ability].name },
      "stats" => try { [p.totalhp, p.attack, p.defense, p.spatk, p.spdef, p.speed] },
      # Arrays in the stats' order already (HP, Attack, Defense, Sp. Atk, Sp. Def, Speed: their calcStats').
      "ivs" => try { p.iv.first(6) },
      "evs" => try { p.ev.first(6) },
      "moves" => try {
        p.moves.compact.map { |m|
          d = move_data(m.move)
          { "name" => try { d.name } || m.move.to_s, "pp" => m.pp, "total" => try { m.totalpp },
            "type" => type_id(try { d.type }), "power" => try { d.basedamage } || 0 }
        }
      },
      "icon" => try { icon(p) },
    }
  end

  # Their pockets by name (Settings' pbPocketNames: Items, Medicine, Poké Balls, TMs & HMs, Berries, Crystals or
  # Enhancements, Favorite Items, Key Items), each with the companion's kind: positions differ from Essentials'.
  POCKET_KINDS = [nil, "items", "medicine", "balls", "tms", "berries", "items", "items", "key"].freeze

  def bag(b)
    names = try { pbPocketNames } || []
    counts = try { b.contents } || {}
    try { b.pockets }.to_a.each_with_index.map { |pk, i|
      next nil if i == 0 || pk.nil?
      {
        "name" => names[i] || "POCKET #{i}",
        "kind" => POCKET_KINDS[i] || "items",
        "items" => pk.map { |id|
          { "name" => item_name(id), "qty" => counts[id] || 1,
            "desc" => PokeDaisyCompanion.cached([:rbdesc, id]) { $cache.items[id].desc },
            "icon" => PokeDaisyCompanion.cached([:rbitem, id]) { pbItemIconFile(id) } }
        },
      }
    }.compact
  end

  # Their Town Map: $cache.town_map holds each region's {name, filename} by number and a TownMapData per tile by [x, y].
  # The player's spot is the map's MapPosition, moved through a big map by MapSize as their RegionMap does; tiles are
  # drawn by Essentials' own rule (centred on tile * 16).
  def town_map
    md = try { $cache.mapdata[$game_map.map_id] }
    pos = md && try { md.MapPosition }
    return nil unless pos.is_a?(Array) && pos.size >= 3
    region, x, y = pos[0], pos[1], pos[2]
    size = try { md.MapSize }
    if size && size[0].to_i > 0
      w = size[0]
      h = (size[1].length * 1.0 / w).ceil
      x += ($game_player.x * w / $game_map.width).floor if w > 1
      y += ($game_player.y * h / $game_map.height).floor if h > 1
    end
    data = town_map_region(region)
    return nil unless data
    {
      "region" => region, "x" => x, "y" => y, "picture" => data[:picture], "tile" => 16, "centre" => 0,
      "head" => try { pbPlayerHeadFile($Trainer.trainertype) }, "hidden" => [],
      "points" => PokeDaisyCompanion::Raw.new(data[:json]),
    }
  end

  def town_map_region(region)
    @town_maps ||= {}
    return @town_maps[region] if @town_maps.key?(region)
    @town_maps[region] = try {
      tm = $cache.town_map
      info = tm[region]
      file = info.is_a?(Hash) ? (info[:filename] || info[1]) : (info.is_a?(Array) ? info[1] : nil)
      points = tm.select { |k, v| k.is_a?(Array) && (try { v.region }.nil? || v.region == region) }.map do |k, v|
        [k[0], k[1], place_name(try { v.name }), place_name(try { v.poi })]
      end
      { picture: file, json: PokeDaisyCompanion.j(points) }
    }
  end

  def place_name(n)
    return nil if n.nil? || n == ""
    try { pbGetMessageFromHash(MessageTypes::PlaceNames, n) } || try { pbGetMessageFromHash(:PlaceNames, n) } || n.to_s
  end

  # The map's wild Pokémon ($PokemonEncounters' @enctypes, set up for it): the grass list the clock picks (night, day or
  # morning where the map has one, as pbEncounterType does), then the other ways, each Pokémon once with its chances
  # added up. Built once per map and time of day; the Pokédex each time.
  WAYS = [[:Cave, "Cave"], [:Water, "Water"], [:RockSmash, "RockSmash"], [:OldRod, "OldRod"], [:GoodRod, "GoodRod"],
          [:SuperRod, "SuperRod"], [:Headbutt, "HeadbuttLow"]].freeze

  def wild
    enc = defined?($PokemonEncounters) && $PokemonEncounters
    return nil unless enc && defined?(::EncounterTypes)
    tables = try { enc.instance_variable_get(:@enctypes) }
    return nil unless tables.is_a?(Array)
    et = ::EncounterTypes
    time = try { pbGetTimeNow }
    land = et::Land
    land = et::LandNight if tables[et::LandNight] && try { PBDayNight.isNight?(time) }
    land = et::LandDay if tables[et::LandDay] && try { PBDayNight.isDay?(time) }
    land = et::LandMorning if tables[et::LandMorning] && try { PBDayNight.isMorning?(time) }
    key = [try { $game_map.map_id }, land, tables.object_id]
    if key != @wild_key
      @wild_key = key
      ways = [[land, "Land"]] + WAYS.map { |c, k| [try { et.const_get(c) }, k] }
      @wild = ways.map { |t, kind| t && kind_of(kind, tables[t]) }.compact
    end
    @wild.map do |k|
      { "kind" => k[:kind], "slots" => k[:slots].map { |sl|
        seen, owned = dex_marks(sl[:id])
        sl.merge("seen" => seen, "owned" => owned).reject { |key, _| key == :id }
      } }
    end
  end

  def kind_of(kind, list)
    return nil unless list.is_a?(Hash) && !list.empty?
    by = list.map do |sp, rows|
      # Rejuvenation keys some by [species, what picks the form] ([:DEERLING, :Season]).
      sp = sp[0] if sp.is_a?(Array)
      rows = Array(rows)
      { id: sp, chance: rows.sum { |r| r[0].to_i }, min: rows.map { |r| r[1] }.min, max: rows.map { |r| r[2] || r[1] }.max }
    end
    total = by.sum { |s| s[:chance] }
    return nil if total <= 0
    slots = by.sort_by { |s| -s[:chance] }.map do |s|
      { id: s[:id], "name" => species_name(s[:id]), "min" => s[:min], "max" => s[:max],
        "percent" => (s[:chance] * 100.0 / total).round, "icon" => try { species_icon(s[:id]) } }
    end
    { kind: kind, slots: slots }
  end

  # A battler: its Pokémon as above, then what the battle changed (HP, status, types, moves and PP, stats, stages).
  STAGES = %i[ATTACK DEFENSE SPEED SPATK SPDEF ACCURACY EVASION].freeze

  def battler(b)
    out = (try { mon(b.pokemon) }) || {}
    out.merge(
      "name" => try { b.pokemon.name } || try { b.name },
      "index" => b.index,
      "partyIndex" => try { b.pokemonIndex },
      "level" => try { b.level },
      "hp" => try { b.hp },
      "totalhp" => try { b.totalhp },
      "status" => try { b.status ? b.status.to_s : nil },
      "types" => try { [b.type1, b.type2].compact.uniq.map { |t| type_id(t) } },
      "item" => item_name(try { b.item }),
      "ability" => try { $cache.abil[b.ability].name },
      "moves" => try {
        b.moves.compact.select { |m| m.move }.map { |m|
          { "name" => try { m.name } || m.move.to_s, "pp" => try { m.pp }, "total" => try { m.totalpp },
            "type" => type_id(try { m.type }), "power" => try { m.basedamage } || 0 }
        }
      },
      "battleStats" => try { [b.attack, b.defense, b.speed, b.spatk, b.spdef] },
      "stages" => try { STAGES.map { |k| b.stages[::PBStats.const_get(k)] || 0 } },
      "fainted" => try { b.isFainted? } || false,
    )
  end

  def battle(bt)
    list = try { bt.battlers } || []
    trainer = try { !bt.pbIsWild? }
    trainer = !try { bt.opponent }.nil? if trainer.nil?
    out = {
      "trainer" => trainer,
      "double" => try { bt.doublebattle } || false,
      # A single battle keeps its empty second places (no Pokémon in them).
      "player" => list.each_with_index.select { |b, i| b && i.even? && try { b.pokemon } }.map { |b, _| battler(b) },
      "foes" => list.each_with_index.select { |b, i| b && i.odd? && try { b.pokemon } }.map { |b, _| battler(b) },
      "menu" => PokeDaisyCompanion.menu,
      "menuBattler" => PokeDaisyCompanion.menu_battler,
    }
    out["foeParty"] = try { bt.pbParty(1).map { |p| p ? mon(p) : nil } } if out["trainer"]
    out
  end

  # Their battle scene: the command and fight loops call pbFrameUpdate(cw) (Essentials 19's pbUpdate), the windows are
  # "commandwindow" / "fightwindow", a fight window's cursor is setIndex, and a switch is the scene's pbSwitch (Reborn:
  # the party index) or pbSwitchPlayer (Rejuvenation: the index per battler), each after the battle's own pbCanSwitch? -
  # so a pick answers it with that same check, no party screen; refused, the game's screen opens as usual. The bag is
  # the battle's pbItemMenu (Reborn) or the scene's (Rejuvenation); Rejuvenation has a second fight menu, pbFightMenuDX.
  def can_switch?(battle, index, slot, lax)
    m = lax ? :pbCanSwitchLax? : :pbCanSwitch?
    battle.send(m, index, slot, showMessage: true)
  rescue ArgumentError
    battle.send(m, index, slot, true)
  end

  # The switch picked on the companion, if it's allowed now (a refusal is the game's own message), else nil.
  def switch_pick(battle, index, lax)
    p = PokeDaisyCompanion.pick
    return nil unless p && p[:kind] == :switch && p[:arg].is_a?(Integer)
    PokeDaisyCompanion.drop_pick
    can_switch?(battle, index, p[:arg], lax) ? p[:arg] : nil
  end
  def hook_scene
    return true if @scene_hooked
    return false unless defined?(::PokeBattle_Scene) && ::PokeBattle_Scene.method_defined?(:pbFrameUpdate) &&
                        ::PokeBattle_Scene.method_defined?(:pbCommandMenuEx)
    @scene_hooked = true
    ::PokeBattle_Scene.prepend(Module.new do
      def pbFrameUpdate(cw = nil, *args)
        r = super
        PokeDaisyCompanion.on_menu_update(self, cw)
        r
      end

      def pbCommandMenuEx(index, *args, &block)
        PokeDaisyCompanion.menu = "command"
        PokeDaisyCompanion.menu_battler = index
        super
      ensure
        PokeDaisyCompanion.menu = nil
      end

      def pbFightMenu(index, *args, &block)
        PokeDaisyCompanion.menu = "fight"
        PokeDaisyCompanion.menu_battler = index
        super
      ensure
        PokeDaisyCompanion.menu = nil
      end
    end)
    scene_extra = Module.new
    if ::PokeBattle_Scene.method_defined?(:pbFightMenuDX)
      scene_extra.define_method(:pbFightMenuDX) do |index, *args, &block|
        PokeDaisyCompanion.menu = "fight"
        PokeDaisyCompanion.menu_battler = index
        super(index, *args, &block)
      ensure
        PokeDaisyCompanion.menu = nil
      end
    end
    if ::PokeBattle_Scene.method_defined?(:pbItemMenu)
      scene_extra.define_method(:pbItemMenu) do |*args, &block|
        PokeDaisyCompanion.menu = "bag"
        super(*args, &block)
      ensure
        PokeDaisyCompanion.menu = nil
      end
    end
    if ::PokeBattle_Scene.method_defined?(:pbSwitch) && ::PokeBattle_Scene.instance_method(:pbSwitch).arity.abs >= 3
      scene_extra.define_method(:pbSwitch) do |index, lax, cancancel, *args, &block|
        slot = PokeDaisyReborn.switch_pick(@battle, index, lax)
        if slot
          @battle.logSwitch(index, slot) rescue nil
          return slot
        end
        PokeDaisyCompanion.menu = "party"
        super(index, lax, cancancel, *args, &block)
      ensure
        PokeDaisyCompanion.menu = nil
      end
    end
    if ::PokeBattle_Scene.method_defined?(:pbSwitchPlayer)
      scene_extra.define_method(:pbSwitchPlayer) do |indices, lax, cancancel, *args, &block|
        if indices.is_a?(Array) && indices.length == 1
          slot = PokeDaisyReborn.switch_pick(@battle, indices.first, lax)
          return { indices.first => slot } if slot
        end
        PokeDaisyCompanion.menu = "party"
        super(indices, lax, cancancel, *args, &block)
      ensure
        PokeDaisyCompanion.menu = nil
      end
    end
    ::PokeBattle_Scene.prepend(scene_extra)
    if defined?(::PokeBattle_Battle) && ::PokeBattle_Battle.method_defined?(:pbItemMenu) && !::PokeBattle_Scene.method_defined?(:pbItemMenu)
      ::PokeBattle_Battle.prepend(Module.new do
        def pbItemMenu(*args, &block)
          PokeDaisyCompanion.menu = "bag"
          super
        ensure
          PokeDaisyCompanion.menu = nil
        end
      end)
    end
    true
  end
end

module Graphics
  class << self
    unless method_defined?(:__pokedaisy_update)
      alias_method :__pokedaisy_update, :update
      def update(*args)
        r = __pokedaisy_update(*args)
        PokeDaisyCompanion.tick
        r
      end
    end
  end
end

# A pick's press (PokeDaisyCompanion.press): the game's next check of that button reads it as pressed, once.
module Input
  class << self
    unless method_defined?(:__pokedaisy_trigger)
      alias_method :__pokedaisy_trigger, :trigger?
      def trigger?(button)
        return true if PokeDaisyCompanion.take_press(button)
        __pokedaisy_trigger(button)
      end
    end
  end
end

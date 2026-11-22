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
    if cw.equal?(sprites["commandWindow"])
      cmd = { fight: 0, action: p[:arg], switch: 2 }[p[:kind]]
      return drop_pick unless cmd.is_a?(Integer) && cmd.between?(0, 3)
      cw.index = cmd
      press(use_button)
      drop_pick if p[:kind] == :action  # a fight / switch carries on to the next window
    elsif cw.equal?(sprites["fightWindow"])
      case p[:kind]
      when :back
        press(back_button)
        drop_pick
      when :fight
        moves = try { cw.battler.moves } || []
        if p[:arg].is_a?(Integer) && moves[p[:arg]] && moves[p[:arg]].id
          cw.index = p[:arg]
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
    try { GameData::Type.get(t).id_number }
  end

  # Every type: its name and what it's weak to, resists and is immune to, by number. The same each time: built once.
  def self.type_table
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
        "name" => try { t.name }, "id" => try { t.public_ID } || try { t.id }, "money" => try { t.money },
        "badges" => try { t.badge_count },
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

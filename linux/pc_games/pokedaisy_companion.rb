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
  VERSION = 4
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

  def self.battle_snapshot(bt)
    list = try { bt.battlers } || []
    out = {
      "trainer" => try { bt.trainerBattle? } || false,
      "double" => (try { bt.pbSideSize(0) } || 1) > 1 || (try { bt.pbSideSize(1) } || 1) > 1,
      "player" => list.each_with_index.select { |b, i| b && i.even? }.map { |b, _| battler(b) },
      "foes" => list.each_with_index.select { |b, i| b && i.odd? }.map { |b, _| battler(b) },
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

  # --- a tiny JSON encoder ---
  def self.j(v)
    case v
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

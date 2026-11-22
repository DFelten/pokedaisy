# pokedaisy_companion.rb - PokeDaisy's companion hook for Pokemon Essentials games (spike).
# Loaded by the mkxp-z core as a preload script, before the game's own scripts.
#
# The game's state goes into one fixed buffer (a "mailbox") that the host finds once by its magic
# bytes in the core's memory (RETRO_MEMORY_SYSTEM_RAM = the Ruby heap) and then reads every frame:
#   0  16  magic "PKDAISY-MBX-0001" (written byte by byte, so the whole string never exists elsewhere)
#   16  4  sequence number (u32 LE), odd while a write is in progress
#   20  4  payload length (u32 LE)
#   24  .  payload: JSON (hand-written encoder: the sandbox may have no json library)
# Nothing here writes game state: it only reads.

module PokeDaisyCompanion
  CAPACITY = 256 * 1024
  HEADER = 24
  EVERY = 15 # frames between snapshots (4 a second at 60 fps)

  @buf = nil
  @seq = 0
  @frame = 0

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

  def self.try
    yield
  rescue Exception => e
    "error: #{e.class}"
  end

  def self.mon(p)
    {
      "species" => try { p.species },
      "name" => try { p.name },
      "speciesName" => try { p.speciesName },
      "level" => try { p.level },
      "hp" => try { p.hp },
      "totalhp" => try { p.totalhp },
      "status" => try { p.status },
      "egg" => try { p.egg? },
      "shiny" => try { p.shiny? },
      "fusion" => try { p.respond_to?(:isFusion?) ? p.isFusion? : false },
      "dex" => try { p.species_data.id_number },
      "item" => try { p.item_id },
      "nature" => try { p.nature_id rescue p.nature.id },
      "ability" => try { p.ability_id rescue p.ability.id },
      "stats" => try { [p.totalhp, p.attack, p.defense, p.spatk, p.spdef, p.speed] },
      "moves" => try { p.moves.map { |m| { "id" => m.id, "pp" => m.pp, "total" => m.total_pp } } },
    }
  end

  def self.snapshot
    out = { "v" => 1, "frame" => (Graphics.frame_count rescue 0) }
    t = (defined?($Trainer) && $Trainer) || (defined?($player) && $player)
    if t
      out["trainer"] = {
        "name" => try { t.name }, "id" => try { t.public_ID rescue t.id }, "money" => try { t.money },
        "badges" => try { t.badge_count },
      }
      out["party"] = try { t.party.map { |p| mon(p) } }
    end
    bag = (defined?($PokemonBag) && $PokemonBag) || (defined?($bag) && $bag)
    if bag
      out["bag"] = try {
        bag.pockets.each_with_index.map { |pk, i| i == 0 ? nil : pk.map { |it| [it[0], it[1]] } }.compact
      }
    end
    if defined?($game_map) && $game_map
      out["map"] = {
        "id" => try { $game_map.map_id },
        "name" => try { pbGetMapNameFromId($game_map.map_id) },
        "x" => try { $game_player.x }, "y" => try { $game_player.y },
      }
    end
    out["inBattle"] = try { $game_temp ? !!$game_temp.in_battle : false }
    out["scene"] = try { $scene ? $scene.class.name : nil }
    out
  end

  def self.tick
    @frame += 1
    return if @frame % EVERY != 0
    publish(j(snapshot))
  rescue Exception => e
    publish(j({ "v" => 1, "error" => e.class.name }))
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

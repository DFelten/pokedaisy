# pokedaisy_ruby32_compat.rb - methods Ruby 3.2 removed that Essentials games written for Ruby 3.0 / 3.1 still call.
# The mkxp-z libretro core runs Ruby 3.3; the games' own Windows builds run 3.0 / 3.1.
class << File
  alias_method :exists?, :exist? unless method_defined?(:exists?)
end
class << Dir
  alias_method :exists?, :exist? unless method_defined?(:exists?)
end
class Object
  def taint; self; end unless method_defined?(:taint)
  def untaint; self; end unless method_defined?(:untaint)
  def tainted?; false; end unless method_defined?(:tainted?)
  def trust; self; end unless method_defined?(:trust)
  def untrust; self; end unless method_defined?(:untrust)
  def untrusted?; false; end unless method_defined?(:untrusted?)
  def =~(_other); nil; end unless method_defined?(:=~)
end

# The libretro core's Kernel#caller (binding-sandbox.cpp's kernel_caller) takes no arguments; native mkxp-z's takes any.
# Essentials' echoln calls caller(1..1). Pass the arguments on to Ruby's own caller, one frame further out.
if Kernel.respond_to?(:_mkxp_kernel_caller_alias, true)
  module Kernel
    def caller(start = 1, length = nil)
      start = start.is_a?(Range) ? ((start.begin || 0) + 1)..(start.end && start.end + 1) : start + 1
      length ? Kernel.send(:_mkxp_kernel_caller_alias, start, length) : Kernel.send(:_mkxp_kernel_caller_alias, start)
    end
    module_function :caller
    public :caller
  end
end

# mkxp-z's HTTPLite module isn't in the libretro core (it has no network). Games that use it (Infinite Fusion: settings,
# sprite and dex downloads, Mystery Gift, Wonder Trade) check the status, so answer "offline": they take their
# no-internet path, as on a PC without a connection. HTTPLite::JSON is Ruby's json where the sandbox has it.
unless defined?(HTTPLite)
  module HTTPLite
    OFFLINE = { status: 0, body: "" }.freeze
    def self.get(*_args) = OFFLINE.dup
    def self.post(*_args) = OFFLINE.dup
    def self.post_body(*_args) = OFFLINE.dup
    module JSON
      begin
        require "json"
        def self.parse(s) = ::JSON.parse(s)
        def self.stringify(o) = ::JSON.generate(o)
      rescue LoadError
        def self.parse(_s) = raise(ArgumentError, "no JSON in this build")
        def self.stringify(o) = o.to_s
      end
    end
  end
end

# Typed text never reaches the game in the libretro core (its Input.gets / text_input are stubs), so an Essentials name
# entry in keyboard mode ($PokemonSystem.textinput 1, Infinite Fusion's default) can't take a letter and the intro stops
# at "Enter your name". The option reads as 0 instead: the game's own letter grid (D-pad, A, B to delete, OK). The game's
# classes are defined after the preloads, so this goes on at the first frame, when they all are.
module PokeDaisyCursorTextEntry
  def textinput = 0
end
# Reborn's and Rejuvenation's options ask useKeyboard? (their KEYBOARD option, on by default): no, so their name entry
# is their own letter grid as well.
module PokeDaisyNoKeyboard
  def useKeyboard? = false
end
module Graphics
  class << self
    unless method_defined?(:__pokedaisy_text_entry_update)
      alias_method :__pokedaisy_text_entry_update, :update
      def update(*args)
        unless @__pokedaisy_text_entry
          @__pokedaisy_text_entry = true
          PokemonSystem.prepend(PokeDaisyCursorTextEntry) if defined?(PokemonSystem)
          PokemonOptions.prepend(PokeDaisyNoKeyboard) if defined?(PokemonOptions) && PokemonOptions.method_defined?(:useKeyboard?)
        end
        __pokedaisy_text_entry_update(*args)
      end
    end
  end
end

# Libraries the libretro core's Ruby hasn't got (its image carries no rubygems, no sockets, no native gems) that games
# require at boot: Reborn and Rejuvenation ask for rubygems only for Gem::Version (a save's version against a patch's),
# and load their online / Discord scripts unasked. A require that fails for one of these takes a stand-in instead: a
# small Gem::Version, and nothing at all for the network ones, so the game acts as a PC that's offline. Any other
# missing library still fails as before.
module PokeDaisyStandIns
  class Version
    include Comparable
    attr_reader :segments

    def initialize(v)
      @string = v.to_s.strip
      @segments = @string.scan(/\d+|[a-z]+/i).map { |s| s =~ /\A\d+\z/ ? s.to_i : s }
    end

    def to_s = @string
    def version = @string

    def <=>(other)
      other = Version.new(other) unless other.is_a?(Version)
      a = segments
      b = other.segments
      [a.size, b.size].max.times do |i|
        x = a[i] || 0
        y = b[i] || 0
        next if x == y
        # A word (a pre-release: "1.0.a") sorts before any number.
        return x.is_a?(String) ? (y.is_a?(String) ? x <=> y : -1) : (y.is_a?(String) ? 1 : x <=> y)
      end
      0
    end

    def self.correct?(v) = v.to_s =~ /\A\s*\d+(\.\d+)*([.-]?[a-z0-9]+)*\s*\z/i ? true : false
    def self.create(v) = v.is_a?(Version) ? v : new(v)
  end

  STANDINS = {
    "rubygems" => -> {
      Object.const_set(:Gem, Module.new) unless defined?(::Gem)
      ::Gem.const_set(:Version, Version) unless ::Gem.const_defined?(:Version, false)
    },
    # No network in the core: a connection then fails with the NameError an offline game's own rescue takes (Reborn's
    # and Rejuvenation's update check at boot, their online play), not a LoadError past it.
    "socket" => -> {},
    "openssl" => -> {},
    # Discord's native gem: its module as on a PC where Discord isn't running (never connected; every call does nothing).
    "discord" => -> {
      unless defined?(::Discord)
        d = Module.new
        d.define_singleton_method(:connected?) { false }
        d.define_singleton_method(:method_missing) { |*_a, **_k, &_b| nil }
        d.define_singleton_method(:respond_to_missing?) { |*_a| true }
        Object.const_set(:Discord, d)
      end
    },
  }.freeze
  @done = {}

  def self.stand_in(name)
    s = STANDINS[name.to_s]
    return nil unless s
    return false if @done[name.to_s]
    @done[name.to_s] = true
    s.call
    true
  end
end

module Kernel
  unless private_method_defined?(:__pokedaisy_require) || method_defined?(:__pokedaisy_require)
    alias_method :__pokedaisy_require, :require
    private :__pokedaisy_require
    def require(name)
      __pokedaisy_require(name)
    rescue LoadError
      r = PokeDaisyStandIns.stand_in(name)
      raise if r.nil?
      r
    end
    private :require
  end
end

# Reborn's and Rejuvenation's own mkxp-z builds add buttons D, E and F (hold to run, turbo, mute: their mkxp.json's
# bindingNames), which the libretro core's Input hasn't got. E, their turbo (Graphics.frame_rate up from 40, which the
# core paces by itself), is START here: the pad's START reaches the core as Alt, which they only read to stop running
# while it's held. D and F are the "no button" code: never pressed (A runs as well, and the app has its own volume).
module Input
  const_set(:E, const_defined?(:ALT) ? ALT : 0) unless const_defined?(:E, false)
  %i[D F].each { |c| const_set(c, 0) unless const_defined?(c, false) }
end

# Their builds' System.file_exist?: File.exist? that also looks in the folders mkxp.json's "patches" names, where the
# game's updater puts its fixes (Reborn's and Rejuvenation's "patch"). Read from mkxp.json; "patch" if it can't be read.
module System
  unless respond_to?(:file_exist?)
    def self.pokedaisy_patch_dirs
      @pokedaisy_patch_dirs ||= begin
        File.read("mkxp.json")[/"patches"\s*:\s*\[([^\]]*)\]/, 1].to_s.scan(/"([^"]+)"/).flatten
      rescue StandardError
        []
      end.then { |d| d.empty? ? ["patch"] : d }
    end

    def self.file_exist?(path)
      File.exist?(path) || pokedaisy_patch_dirs.any? { |d| File.exist?(File.join(d, path)) }
    end
  end
end

# Rejuvenation's build takes a sixth Audio.bgm_play argument (a fade-in) and tells its own build from an older one by
# calling it so, rescuing the TypeError an older one raises; the libretro core's raises ArgumentError for the count
# instead, which it doesn't rescue. Past five, the extra arguments are left off: the older build's answer, and the game
# plays its music without the fade.
module Audio
  class << self
    if method_defined?(:bgm_play) && !method_defined?(:__pokedaisy_bgm_play)
      alias_method :__pokedaisy_bgm_play, :bgm_play
      def bgm_play(*args) = __pokedaisy_bgm_play(*args.first(5))
    end
  end
end

# Their builds' TilemapUtils.updateAutoTiles steps every map's animated tiles at once; the libretro core's Tilemap#update
# animates its own, so here it has nothing to do (Rejuvenation already checks for it; Reborn calls it unless on JoiPlay).
unless defined?(TilemapUtils)
  module TilemapUtils
    def self.updateAutoTiles; end
  end
end

# More of their builds' calls, found by asking the running games for every Audio / Graphics / System / Input method their
# scripts use (only the ones the libretro core hasn't got, each only if missing):
module Audio
  class << self
    # A sound placed on the map: played plainly, as their scripts do on JoiPlay.
    unless method_defined?(:se_play_position)
      def se_play_position(name, volume = 100, pitch = 100, *_where) = se_play(name, volume, pitch)
    end
  end
end

module Graphics
  class << self
    # Their build's battle start / end hooks (Reborn's field effects call them): nothing to do here.
    def onStartBattle(*_args); end unless method_defined?(:onStartBattle)
    def onEndBattle(*_args); end unless method_defined?(:onEndBattle)
  end
end

module Input
  class << self
    # Shift is the core's SHIFT button; key combos and other modifiers are never held; a pad's trigger is the button's.
    def shiftKeyPressed? = press?(Input::SHIFT) unless method_defined?(:shiftKeyPressed?)
    def shiftKeyTriggered? = trigger?(Input::SHIFT) unless method_defined?(:shiftKeyTriggered?)
    def isComboKeyPressed? = false unless method_defined?(:isComboKeyPressed?)
    def modifierKeyPressed? = false unless method_defined?(:modifierKeyPressed?)
    def triggerGamepad?(button) = trigger?(button) unless method_defined?(:triggerGamepad?)
    def repeatexAny?(*_keys) = false unless method_defined?(:repeatexAny?)
  end
end

# The sandbox's root can't be listed; a game that looks beside its own save folder (Rejuvenation's New Game looks for
# your Reborn and Where Love Lies saves in the folder above, "/" here) finds nothing there, as on a PC with no such saves.
class << Dir
  %i[each_child children entries].each do |m|
    next if method_defined?(:"__pokedaisy_#{m}")
    alias_method :"__pokedaisy_#{m}", m
    define_method(m) do |path, *args, **kw, &block|
      send(:"__pokedaisy_#{m}", path, *args, **kw, &block)
    rescue Errno::ENOENT
      raise unless path.to_s.squeeze("/") == "/"
      m == :each_child ? nil : []
    end
  end
end

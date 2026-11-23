# pokedaisy_ruby18_compat.rb - Essentials games written for Ruby 1.8 (Essentials v16 / v17 and the forks on them, e.g.
# the Spanish BES: GS Vitro) on the mkxp-z libretro core's Ruby 3.3. Their own Windows builds embed mkxp with 1.8.7.
#
# A 1.8 game is told by its scripts ($RGSS_SCRIPTS, which the core sets before the preloads run): a `when x:` line, which
# no later Ruby parses. Everything here is for such a game only; any other game runs exactly as before.
#
# The core evaluates each of the game's scripts with `eval` on the top-level object (binding-sandbox.cpp's
# eval_script_func), so this file puts its own `eval` there: each script gets 1.8's byte reads ([adapt]), and one that
# still doesn't parse has the lines Ruby names rewritten in the few ways 1.8 allowed and later Rubies don't ([rewrite]).
module PokeDaisyRuby18
  ROUNDS = 40 # rewrite rounds per script: each fixes the lines one parse named
  OLD_WHEN = /^[ \t]*when[ \t]+[^\n#]*[^:\s]:(?:[ \t]|$)/n

  def self.old_game?
    return @old if defined?(@old)
    list = defined?($RGSS_SCRIPTS) && $RGSS_SCRIPTS.is_a?(Array) ? $RGSS_SCRIPTS : []
    @old = list.any? { |e| e.is_a?(Array) && e[3].is_a?(String) && e[3].b.match?(OLD_WHEN) }
  rescue StandardError
    @old = false
  end

  # 1.8's `x[k]` with a String: the byte's number. Anything else (an Array, nil) is indexed as it is.
  def self.byte(x, k) = x.is_a?(String) ? x.getbyte(k) : x[k]

  # A receiver as 1.8 code writes one before `.read(n)` or `[k]`: a name, an instance / global variable, a chain of calls
  # without arguments.
  RECEIVER = /((?:[@$]{0,2}[A-Za-z_][A-Za-z0-9_]*)(?:\.[a-z_][A-Za-z0-9_]*[?!]?)*)/n

  # A 1.8 game's script as 1.8 read it:
  # - its byte reads through [byte]: `file.read(n)[k]`, and `name[k]` compared with a hex number, shifted or masked
  #   (`rstr[0]==0xFB`, `rstr[1]>>4`, `line[0]==0xEF`). An Array there is still indexed as before.
  # - `proc { }` / `proc do` as `lambda`: 1.8's Kernel#proc made a lambda, so a `return` in it leaves the proc (Essentials'
  #   event handlers do: `Events.onAction += proc { ... return if ... }`). A later Ruby's proc returns from the script that
  #   made it, long finished - a LocalJumpError, and in the core's sandbox a trap that closes the game. With 1.8's
  #   arguments too: one with no parameters took any (`|*|`), one with a single `|a|` given several got them as an array.
  PROC = /(?<![\w.:@$])proc([ \t]*)(\{|do\b)([ \t\r\n]*)(?:\|([^|\n]*)\|)?/n

  def self.lambda_for(space, open, gap, params)
    return "lambda#{space}#{open} |*|#{gap}" if params.nil?
    v = params.strip
    return "lambda#{space}#{open}#{gap}|#{params}|" unless v.match?(/\A[a-z_][A-Za-z0-9_]*\z/n)
    "lambda#{space}#{open}#{gap}|*__pd_#{v}| #{v} = __pd_#{v}.size == 1 ? __pd_#{v}[0] : __pd_#{v};"
  end

  def self.adapt(src)
    return src unless src.is_a?(String)
    enc = src.encoding
    out = src.b
    out = out.gsub(PROC) { lambda_for($1, $2, $3, $4) }
    out = out.gsub(/#{RECEIVER}(\.read\(\d+\))\[(\d+)\]/n) { "PokeDaisyRuby18.byte(#{$1}#{$2}, #{$3})" }
    out = out.gsub(/#{RECEIVER}\[(\d+)\](?=[ \t]*(?:==[ \t]*0x|>>|&[ \t]*0x))/n) { "PokeDaisyRuby18.byte(#{$1}, #{$2})" }
    out.force_encoding(enc)
  end

  # The line numbers a SyntaxError names ("053:AudioUtilities:919: syntax error..."), first error first.
  def self.error_lines(err, file)
    msg = err.message.to_s
    lines = msg.scan(/#{Regexp.escape(file.to_s)}:(\d+):/).flatten.map(&:to_i)
    lines = msg.scan(/:(\d+): /).flatten.map(&:to_i) if lines.empty?
    lines.uniq
  end

  # One line in Ruby 3's words, or nil when none of the rewrites apply. [escapes]: the parse said "invalid multibyte
  # escape" (1.8's regexes were bytes; a UTF-8 one can't hold \x80-\xFF, so they become the characters U+0080-U+00FF).
  def self.fix_line(line, escapes = false)
    out = line.dup
    out = out.gsub(/\\x([89a-fA-F][0-9a-fA-F])/n) { "\\u00#{$1}" } if escapes
    # `when 1: foo` (1.8's colon after a when) -> `when 1 then foo`. The colon is the first one with a space or the line's
    # end after it that isn't half of `::` (a symbol's colon has its name right after it).
    out = out.sub(/\A(\s*when\s+.*?[^:\s]):(?=\s|$)/n) { "#{$1} then" }
    # `name (a, b)` (a space before an argument list of more than one) -> `name(a, b)`.
    out = out.gsub(/\b([a-z_][A-Za-z0-9_]*[!?]?) +\((?=[^()]*,)/n) do
      %w[if unless while until and or not return when in case elsif then do yield puts p].include?($1) ? $& : "#{$1}("
    end
    out == line ? nil : out
  end

  # The script rewritten until it parses (or no rewrite helps): the source to run, or nil to leave the error as it is.
  def self.rewrite(src, file)
    code = src.dup
    ROUNDS.times do
      begin
        RubyVM::InstructionSequence.compile(code, file.to_s)
        return code
      rescue SyntaxError => e
        rows = code.b.split("\n", -1) # bytes: a script's text may not be valid UTF-8, and every rewrite is ASCII
        changed = false
        error_lines(e, file).each do |n|
          # A parse error is named on its line or the one after (a `when x:` breaks the next `when`).
          [n - 1, n - 2].each do |i|
            next if i < 0 || i >= rows.size
            fixed = fix_line(rows[i], e.message.include?("multibyte escape"))
            next unless fixed
            rows[i] = fixed
            changed = true
          end
        end
        return nil unless changed
        code = rows.join("\n").force_encoding(src.encoding)
      end
    end
    nil
  end
end

if PokeDaisyRuby18.old_game?
  module PokeDaisyRuby18
    # The game's screen: Essentials v16 / v17 name it in DEFAULTSCREENWIDTH / HEIGHT (512 x 384) and their mkxp builds
    # open at it; the core opens an RPG Maker XP game at 640 x 480. Set once its Settings script has named the size.
    def self.size_screen
      return if @sized || !defined?(::DEFAULTSCREENWIDTH) || !defined?(::DEFAULTSCREENHEIGHT)
      @sized = true
      w = ::DEFAULTSCREENWIDTH.to_i
      h = ::DEFAULTSCREENHEIGHT.to_i
      Graphics.resize_screen(w, h) if w > 0 && h > 0 && (Graphics.width != w || Graphics.height != h)
    rescue StandardError
      nil
    end

    # Saves: Essentials v16 / v17's RTP.getSaveFolder tries Windows' Saved Games, then the game's own folder (read-only
    # in the core), then %LOCALAPPDATA%. On mkxp the later Essentials (and the Reborn engine) use System.data_directory,
    # the core's /Save/ (the app backs it up before each start), so the save goes there. Set once the game defines RTP.
    module SaveFolder
      def getSaveFolder
        dir = System.data_directory.to_s
        dir.empty? ? super : dir
      rescue StandardError
        super
      end
    end

    def self.save_folder
      return if @saves || !defined?(::RTP) || !::RTP.respond_to?(:getSaveFolder) || !defined?(::System)
      return unless ::System.respond_to?(:data_directory)
      @saves = true
      ::RTP.singleton_class.prepend(SaveFolder)
    end

    # Essentials v16 / v17's StringInput (what reads Data/dexdata.dat and the like from memory) returns `@string[@pos]`
    # from getc: a byte's number in 1.8, a one-letter string later, and every fgetb / fgetw on it adds those up.
    module ByteGetc
      def getc
        c = super
        c.is_a?(String) ? c.getbyte(0) : c
      end
    end

    def self.byte_getc
      return if @getc || !defined?(::StringInput) || !::StringInput.method_defined?(:getc)
      @getc = true
      ::StringInput.prepend(ByteGetc)
    end

    # After each of the game's scripts: what waits on something the game defines.
    def self.after_script
      size_screen
      save_folder
      byte_getc
    end
  end

  # Opening a folder as a file: Ruby on Windows refuses it (Errno::EACCES), and the core opens it and then fails an
  # assertion of its own on the first read (the game is closed). Essentials v16 / v17 do it for any language without a
  # messages file (GS Vitro's Spanish: pbLoadMessages("Data/")) and an autotile with no name, and rescue that refusal.
  class << File
    alias_method :__pokedaisy_open, :open
    def open(name, *args, **opts, &block)
      raise Errno::EACCES, name.to_s if (name.is_a?(String) || name.respond_to?(:to_path)) && File.directory?(name)
      __pokedaisy_open(name, *args, **opts, &block)
    end
  end

  class << self
    # The core's call for each script: top_self.eval(source, nil, file), run at the top level as the core's own is. Under
    # RPG Maker XP's 1.8 a script's top-level `def`s are public (Essentials v16 / v17 test them with
    # `Kernel.respond_to?("pbSetResizeFactor")`, and GS Vitro's screen capture turns into a nil stand-in when that's
    # false); at the top-level binding they'd be private, so the script starts with `public` (on a line 0 of its own).
    def eval(src, bind = nil, file = "(eval)", line = 1)
      run = lambda do |code|
        code = "public\n" + code if bind.nil? && code.is_a?(String)
        Kernel.instance_method(:eval).bind(self).call(code, bind || TOPLEVEL_BINDING, file, bind.nil? ? line - 1 : line)
      end
      code = PokeDaisyRuby18.adapt(src)
      begin
        run.call(code)
      rescue SyntaxError => e
        fixed = code.is_a?(String) ? PokeDaisyRuby18.rewrite(code, file) : nil
        raise e unless fixed
        run.call(fixed)
      end
    ensure
      PokeDaisyRuby18.after_script
    end
  end

  # The core's Win32API stand-ins (mkxp-z's win32_wrap.rb, which looks a call up in Win32API_Impl) find the game's
  # window by FindWindowA alone, as handle 42; Essentials v16 / v17's pbFindRgssWindow walks FindWindowEx and checks the
  # window's thread against GetCurrentThreadId (both 0 here), and raises when none matches. Its mouse code asks at once.
  module Win32API_Impl
    module User32
      class FindWindowEx
        def call(args) = args[2] == "RGSS Player" && args[1].to_i == 0 ? 42 : 0
      end

      class GetWindowThreadProcessId
        def call(_args) = 0
      end
    end

    module Kernel32
      class GetCurrentThreadId
        def call(_args) = 0
      end
    end
  end

  # Windows' TEMP isn't set in the core. Essentials v16 / v17's animated-picture loader steps into it
  # (Dir.chdir(ENV["TEMP"])) to unpack a GIF with a Windows DLL the core hasn't got, so it writes nothing there.
  ENV["TEMP"] ||= ENV["TMP"] || Dir.pwd
  ENV["TMP"] ||= ENV["TEMP"]

  # Ruby 1.8 calls later Rubies took out, as 1.8 did them.
  class Thread
    # 1.8's green threads could be held still around a change; the game's scripts run on one thread here.
    def self.critical = false unless respond_to?(:critical)

    def self.critical=(v)
      v
    end unless respond_to?(:critical=)
  end

  class Array
    def nitems = count { |e| !e.nil? } unless method_defined?(:nitems)
  end

  class Hash
    def index(value) = key(value) unless method_defined?(:index)
  end

  Fixnum = Integer unless defined?(Fixnum)
  Bignum = Integer unless defined?(Bignum)
end

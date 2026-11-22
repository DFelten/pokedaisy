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

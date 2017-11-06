# frozen_string_literal: true

require 'forwardable'
require 'tempfile'

module Embulk
  module Output
    class GoogleSpreadsheets < OutputPlugin

      class WriteBuffer
        extend Forwardable
        include Enumerable

        attr_reader :file

        def initialize
          @file = Tempfile.new(safe_class_name)
        end

        def close
          return if file.nil?

          file.close
          file.unlink
          file = nil
        end

        def write_record(r)
          write(Marshal.dump(r) << "\n")
        end

        def each
          file.rewind
          file.each do |serialized_record|
            yield Marshal.restore(serialized_record)
          end
        end

        def_delegators :@file, :write

      private
        # retrun a class name safe for filesystem
        def safe_class_name()
          self.class.name.downcase.gsub(/[^\d\w]/, '_')
        end

      end
    end
  end
end

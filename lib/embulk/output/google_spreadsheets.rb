# frozen_string_literal: true

require 'googleauth'
require 'google/apis/sheets_v4'

require_relative 'google_spreadsheets/write_buffer'
require_relative 'google_spreadsheets/spreadsheets_client'

module Embulk
  module Output

    class GoogleSpreadsheets < OutputPlugin
      Plugin.register_output("google_spreadsheets", self)

      # support config by file path or content which supported by org.embulk.spi.unit.LocalFile
      # json_keyfile:
      #   content: |
      class LocalFile
        # return JSON string
        def self.load(v)
          if v.is_a?(String)
            File.read(v)
          elsif v.is_a?(Hash)
            v['content']
          end
        end
      end

      def self.transaction(config, schema, count, &control)
        # configuration code:
        task = {
          # required
          "json_keyfile"     => config.param("json_keyfile",      LocalFile, nil),
          "spreadsheets_url" => config.param("spreadsheets_url",   :string),
          "worksheet_title"  => config.param("worksheet_title",   :string),

          # optional
          "auth_method"      => config.param("auth_method",       :string,  default: "authorized_user"), # 'auth_method' or 'service_account'
          "mode"             => config.param("mode",              :string,  default: "append"), # `replace` or `append`
          "header_line"      => config.param("header_line",       :bool,    default: false),
          "start_column"     => config.param("start_column",      :integer, default: 1),
          "start_row"        => config.param("start_row",         :integer, default: 1),
          "null_string"      => config.param("null_string",       :string,  default: ""),
          "default_timezone" => config.param("default_timezone",  :string,  default: "+00:00"),
          "default_timestamp_format" => config.param("default_timestamp_format", :string,  default: "%Y-%m-%d %H:%M:%S.%6N %z"),
        }

        #
        # prepare to write records
        #

        # support 'UTC' specially
        task["default_timezone"] = "+00:00" if task["default_timezone"] == 'UTC'

        client = SpreadsheetsClient.new(task, schema)

        mode = task["mode"].to_sym
        raise "unsupported mode: #{mode.inspect}" unless [:append, :replace].include? mode

        if mode == :replace
          client.clear_records
        end

        if task['header_line']
          client.write_header_line
        end

        Embulk.logger.info("set task: #{task.inspect}")

        task_reports = yield(task)
        next_config_diff = {}
        return next_config_diff
      end

      def init
        @mode        = task["mode"].to_sym
        @null_string = task["null_string"]

        @client = SpreadsheetsClient.new(task, schema)
        init_cursor @client
        @buffer = WriteBuffer.new
      end

      def close
      end

      def add(page)
        num_records = 0

        page.each do |values|
          record = schema.names.zip(schema.types, values)

          f = record.map do |(name, type, value)|
            format(type, value)
          end

          @buffer.write_record(f)
          num_records += 1
        end

        Embulk.logger.info("buffering #{num_records} records")
      end

      def finish
        total = 0

        @buffer.each_slice(1000) do |chunked_records|
          Embulk.logger.debug { "flush buffer: write %d records to spreadsheet" % chunked_records.length }
          total += chunked_records.length

          @client.write_records(chunked_records)
        end

        Embulk.logger.info("finish to write total %d records" % total)

        @buffer.close
      end

      def abort
        @buffer.close
      end

      def commit
        task_report = {}
        return task_report
      end

      def init_cursor(client)
        client.set_cursor_to_last_row
      end

      def format(type, v)
        return @null_string if v.nil?

        case type
        when :timestamp
          zone_offset = task['default_timezone']
          format      = task['default_timestamp_format']

          v.dup.localtime(zone_offset).strftime(format)
        when :json
          v.to_json
        else
          v
        end
      end
    end
  end
end

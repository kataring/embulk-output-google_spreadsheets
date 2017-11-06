# frozen_string_literal: true

module Embulk
  module Output
    class GoogleSpreadsheets < OutputPlugin

      class SpreadsheetsClient

        DEFAULT_SCOPE = [
          Google::Apis::SheetsV4::AUTH_SPREADSHEETS
        ]

        attr_reader :service, :spreadsheets_url, :spreadsheets_id, :worksheet_title, :schema, :task, :cursor

        def initialize(task, schema)
          @task = task
          @schema = schema

          @spreadsheets_url = task['spreadsheets_url']
          @spreadsheets_id  = spreadsheets_id_by_url(@spreadsheets_url)
          if @spreadsheets_id.nil?
            raise ArgumentError, "failed to extract spreadsheet's id from url, maybe given spreadsheets_url is invalid."
          end

          @worksheet_title = task['worksheet_title']

          @service = Google::Apis::SheetsV4::SheetsService.new
          @service.client_options.application_name = 'embulk-output-google_spreadsheets'
          @service.authorization = make_credentials(task)

          @cursor = 0
        end

        def replace_mode?
          task['mode'] == 'replace'
        end

        def append_mode?
          task['mode'] == 'append'
        end

        def write_header_line
          vr = ensure_value_range(headers)
          service.update_spreadsheet_value spreadsheets_id, range, vr, value_input_option: 'RAW'
        end

        def write_records(records)
          vr = ensure_value_range(records)
          service.append_spreadsheet_value spreadsheets_id, next_range(records), vr, value_input_option: 'RAW'
        end

        def append(records)
          vr = ensure_value_range(records)
          service.append_spreadsheet_value spreadsheets_id, all_records_range, vr, value_input_option: 'RAW'
        end

        def set_cursor_to_last_row
          res = service.get_spreadsheet_values spreadsheets_id, all_records_range
          @cursor = res.values&.size || 0

          Embulk.logger.info { "fetched existing #{@cursor} records (including header line), set next records position at #{@cursor + task['start_row']}" }
        end

        def clear_records
          service.clear_values spreadsheets_id, all_records_range
        end

        def headers
          schema.names
        end

      private
        def make_credentials(task)
          key = StringIO.new(JSON.parse(task['json_keyfile']).to_json)

          case task['auth_method']
          when 'authorized_user'
            Google::Auth::UserRefreshCredentials.make_creds(json_key_io: key, scope: DEFAULT_SCOPE)
          when 'service_account'
            Google::Auth::ServiceAccountCredentials.make_creds(json_key_io: key, scope: DEFAULT_SCOPE)
          else
            raise ConfigError.new("Unknown auth method: #{task['auth_method']}")
          end
        end

        def spreadsheets_id_by_url(url)
          base_url = 'https://docs.google.com/spreadsheets/d/'
          url.scan(%r{#{base_url}([^/]+)}).dig(0, 0) # return spreadsheets id if matche succeed, nil otherwise
        end

        def range
          c = col_num_to_a1 task["start_column"]
          r = task["start_row"]

          "#{safe_worksheet_title}!#{c}#{r}"
        end

        def next_range(records)
          c = col_num_to_a1 task["start_column"]
          r = task["start_row"] + cursor
          @cursor += records.length

          "#{safe_worksheet_title}!#{c}#{r}"
        end

        def all_records_range
          end_col = col_num_to_a1(task["start_column"] + headers.length - 1)
          "#{range}:#{end_col}"
        end

        # retrun a worksheet title safe for spreadsheet api
        def safe_worksheet_title
          @safe_worksheet_title ||= begin
            title_safe = worksheet_title.gsub("'", "''") # escape signle quote
            "'#{title_safe}'" # surround title with single quotes
          end
        end

        # ensure given data type as value range
        # see: https://developers.google.com/sheets/api/reference/rest/v4/spreadsheets.values#ValueRange
        #
        # {
        #   major_dimension: String // DIMENSION_UNSPECIFIED, ROWS or COLUMNS
        #   values: Array[Array[Object]]
        # }
        #
        # ValueRange.values is Array<Array<Object>>,
        # outer array representing list of rows and
        # iner array representing list of columns in row.
        def ensure_value_range(arr_or_obj)
          {
            major_dimension: "ROWS",
            values: ensure_2dimension_array(arr_or_obj)
          }
        end

        def ensure_2dimension_array(arr_or_obj)
          arr = ensure_array(arr_or_obj)
          return [arr] unless arr[0].is_a? Array

          arr
        end

        def ensure_array(arr_or_obj)
          return [arr_or_obj] unless arr_or_obj.is_a? Array

          arr_or_obj
        end

        # convert col number to a1 annotation
        #
        def col_num_to_a1(col_num)
          offset   = 'A'.ord
          radix    = 26 # number of letters in alphabet

          a1 = []

          while 1 <= col_num do
            # adjust col number.
            # A1 annotation is base-26 (A-Z) but 0 does not exists.
            #
            # for example,
            #         (NA) ==  0 base-10
            #    A base-26 ==  1 base-10
            #    Z base-26 == 26 base-10
            #   AA base-26 == 27 base-10 (if A is 0, AA also become 0)
            #
            col_num -= 1
            a1 << (col_num % radix + offset).chr
            col_num /= radix
          end

          return 'A' if a1.empty?

          a1.reverse.join
        end
      end
    end
  end
end

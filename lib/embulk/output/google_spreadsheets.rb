Embulk::JavaPlugin.register_output(
  "google_spreadsheets", "org.embulk.output.google_spreadsheets.GoogleSpreadsheetsOutputPlugin",
  File.expand_path('../../../../classpath', __FILE__))

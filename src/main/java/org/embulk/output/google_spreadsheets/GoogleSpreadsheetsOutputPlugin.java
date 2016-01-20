package org.embulk.output.google_spreadsheets;

import org.embulk.config.TaskReport;
import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.Task;
import org.embulk.config.TaskSource;
import org.embulk.spi.Exec;
import org.embulk.spi.OutputPlugin;
import org.embulk.spi.Schema;
import org.embulk.spi.TransactionalPageOutput;
import org.embulk.spi.Page;
import org.embulk.spi.Column;
import org.embulk.spi.ColumnVisitor;
import org.embulk.spi.PageReader;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.util.ServiceException;
import com.google.api.services.drive.DriveScopes;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.http.HttpTransport;

import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Arrays;
import org.embulk.spi.time.Timestamp;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import java.security.GeneralSecurityException;

public class GoogleSpreadsheetsOutputPlugin
        implements OutputPlugin
{
    public interface PluginTask
            extends Task
    {
        @Config("service_account_email")
        public String getServiceAccountEmail();

        @Config("spreadsheet_id")
        public String getSpreadsheetId();

        @Config("p12_keyfile")
        public String getP12Keyfile();

        @Config("sheet_index")
        @ConfigDefault("0")
        public int getSheetIndex();

        @Config("application_name")
        @ConfigDefault("\"Embulk-GoogleSpreadsheets-OutputPlugin\"")
        public String getApplicationName();

        @Config("default_timezone")
        @ConfigDefault("\"UTC\"")
        public String getDefaultTimezone();
    }

    private final Logger log;

    public GoogleSpreadsheetsOutputPlugin()
    {
        log = Exec.getLogger(getClass());
    }

    @Override
    public ConfigDiff transaction(ConfigSource config,
            Schema schema, int taskCount,
            OutputPlugin.Control control)
    {
        PluginTask task = config.loadConfig(PluginTask.class);

        // retryable (idempotent) output:
        // return resume(task.dump(), schema, taskCount, control);

        // non-retryable (non-idempotent) output:
        control.run(task.dump());
        return Exec.newConfigDiff();
    }

    @Override
    public ConfigDiff resume(TaskSource taskSource,
            Schema schema, int taskCount,
            OutputPlugin.Control control)
    {
        throw new UnsupportedOperationException("google_spreadsheets output plugin does not support resuming");
    }

    @Override
    public void cleanup(TaskSource taskSource,
            Schema schema, int taskCount,
            List<TaskReport> successTaskReports)
    {
    }

    private GoogleCredential getServiceAccountCredential(PluginTask task)
            throws IOException, GeneralSecurityException
    {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        List<String> scopes = Arrays.asList(DriveScopes.DRIVE, "https://spreadsheets.google.com/feeds");

        return new GoogleCredential.Builder().setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setServiceAccountId(task.getServiceAccountEmail())
                .setServiceAccountPrivateKeyFromP12File(new File(task.getP12Keyfile()))
                .setServiceAccountScopes(scopes)
                .build();
    }

    @Override
    public TransactionalPageOutput open(TaskSource taskSource, Schema schema, int taskIndex)
    {
        PluginTask task = taskSource.loadTask(PluginTask.class);
        GoogleSpreadsheetsPageOutput pageOutput = new GoogleSpreadsheetsPageOutput(task);
        pageOutput.open(schema);
        return pageOutput;
    }

    public class GoogleSpreadsheetsPageOutput implements TransactionalPageOutput
    {
        private PageReader pageReader;
        private SpreadsheetService service;
        private WorksheetEntry worksheet;
        private ListEntry row;
        private DateTimeFormatter formatter;

        public GoogleSpreadsheetsPageOutput(PluginTask task) {
            try {
                GoogleCredential credentials = getServiceAccountCredential(task);
                service = new SpreadsheetService(task.getApplicationName());
                service.setProtocolVersion(SpreadsheetService.Versions.V3);
                service.setOAuth2Credentials(credentials);

                URL entryUrl = new URL("https://spreadsheets.google.com/feeds/spreadsheets/" + task.getSpreadsheetId());
                SpreadsheetEntry spreadsheet = service.getEntry(entryUrl, SpreadsheetEntry.class);

                WorksheetFeed worksheetFeed = service.getFeed(spreadsheet.getWorksheetFeedUrl(), WorksheetFeed.class);
                List<WorksheetEntry> worksheets = worksheetFeed.getEntries();
                worksheet = worksheets.get(task.getSheetIndex());

                DateTimeZone zone = DateTimeZone.forID(task.getDefaultTimezone());
                formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);
            }
            catch (ServiceException e) {
                throw new RuntimeException(e);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }

        void open(final Schema schema)
        {
            pageReader = new PageReader(schema);
        }

        @Override
        public void add(Page page)
        {
            log.debug("add: start");
            pageReader.setPage(page);
            URL listFeedUrl = worksheet.getListFeedUrl();

            while (pageReader.nextRecord()) {
                row = new ListEntry();
                pageReader.getSchema().visitColumns(new ColumnVisitor() {
                    @Override
                    public void booleanColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            log.debug("booleanColumn: null");
                            row.getCustomElements().setValueLocal(column.getName(), "");
                        } else {
                            log.debug("booleanColumn: " + pageReader.getBoolean(column));
                            row.getCustomElements().setValueLocal(column.getName(), (pageReader.getBoolean(column) ? "true" : "false"));
                        }
                    }

                    @Override
                    public void longColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            log.debug("longColumn: null");
                            row.getCustomElements().setValueLocal(column.getName(), "");
                        } else {
                            log.debug("longColumn: " + pageReader.getLong(column));
                            row.getCustomElements().setValueLocal(column.getName(), Long.toString(pageReader.getLong(column)));
                        }
                    }

                    @Override
                    public void doubleColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            log.debug("doubleColumn: null");
                            row.getCustomElements().setValueLocal(column.getName(), "");
                        } else {
                            log.debug("doubleColumn: " + pageReader.getDouble(column));
                            row.getCustomElements().setValueLocal(column.getName(), Double.toString(pageReader.getDouble(column)));
                        }
                    }

                    @Override
                    public void stringColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            log.debug("stringColumn: null");
                            row.getCustomElements().setValueLocal(column.getName(), "");
                        } else {
                            log.debug("stringColumn: " + pageReader.getString(column));
                            row.getCustomElements().setValueLocal(column.getName(), pageReader.getString(column));
                        }
                    }

                    @Override
                    public void timestampColumn(Column column) {
                        if (pageReader.isNull(column)) {
                            log.debug("timestampColumn: null");
                            row.getCustomElements().setValueLocal(column.getName(), "");
                        } else {
                            Timestamp timestamp = pageReader.getTimestamp(column);
                            String strTimestamp = formatter.print(timestamp.toEpochMilli());
                            log.debug("timestampColumn: " + strTimestamp);
                            row.getCustomElements().setValueLocal(column.getName(), strTimestamp);
                        }
                    }
                });

                try {
                    row = service.insert(listFeedUrl, row);
                }
                catch (Exception e){
                    log.warn("can not insert:" + e.toString());
                }
            }
            log.debug("add: end");
        }

        @Override
        public void finish()
        {
            try {
                // TODO
            } finally {
                close();
            }
        }

        @Override
        public void close()
        {
            // TODO do nothing
        }

        @Override
        public void abort()
        {
            //  TODO do nothing
        }

        @Override
        public TaskReport commit()
        {
            return Exec.newTaskReport();
        }
    }
}

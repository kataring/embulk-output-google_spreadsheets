package org.embulk.output.google_spreadsheets;

import java.util.List;
import com.google.common.base.Optional;
import org.embulk.config.TaskReport;
import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.Task;
import org.embulk.config.TaskSource;
import org.embulk.spi.Exec;
import org.embulk.spi.OutputPlugin;
import org.embulk.spi.PageOutput;
import org.embulk.spi.Schema;
import org.embulk.spi.TransactionalPageOutput;
import org.embulk.spi.Page;
import org.embulk.spi.Column;
import org.embulk.spi.ColumnVisitor;
import org.embulk.spi.Exec;
import org.embulk.spi.OutputPlugin;
import org.embulk.spi.Page;
import org.embulk.spi.PageReader;


import com.google.api.client.json.JsonFactory;
import com.google.api.client.http.HttpTransport;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import org.slf4j.Logger;
import java.security.GeneralSecurityException;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;

import com.google.gdata.client.spreadsheet.FeedURLFactory;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.BaseEntry;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.spreadsheet.*;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

public class GoogleSpreadsheetsOutputPlugin
        implements OutputPlugin
{
    public interface PluginTask
            extends Task
    {
        @Config("email")
        public String getEmail();

        @Config("key")
        public String getKey();

        @Config("p12file")
        public String getP12file();

        @Config("sheet")
        @ConfigDefault("0")
        public int getSheet();
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
        //GoogleClientSecrets secret = GoogleClientSecrets.load(jsonFactory, new FileReader("/tmp/embulk.json"));
        List<String> scopes = Arrays.asList(DriveScopes.DRIVE, "https://spreadsheets.google.com/feeds");

        return new GoogleCredential.Builder().setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setServiceAccountId(task.getEmail())
                .setServiceAccountPrivateKeyFromP12File(new File(task.getP12file()))
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

        public GoogleSpreadsheetsPageOutput(PluginTask task) {
            try {
                GoogleCredential credentials = getServiceAccountCredential(task);
                //credentials.refreshToken();
                service = new SpreadsheetService("embulk-test");
                service.setProtocolVersion(SpreadsheetService.Versions.V3);
                service.setOAuth2Credentials(credentials);

                URL entryUrl = new URL("https://spreadsheets.google.com/feeds/spreadsheets/" + task.getKey());
                SpreadsheetEntry spreadsheet = service.getEntry(entryUrl, SpreadsheetEntry.class);

                //worksheet = spreadsheet.getDefaultWorksheet();
                WorksheetFeed worksheetFeed = service.getFeed(spreadsheet.getWorksheetFeedUrl(), WorksheetFeed.class);
                List<WorksheetEntry> worksheets = worksheetFeed.getEntries();
                worksheet = worksheets.get(task.getSheet());
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
            log.info("add: start");

            pageReader.setPage(page);
            URL listFeedUrl = worksheet.getListFeedUrl();

            while (pageReader.nextRecord()) {
                row = new ListEntry();
                pageReader.getSchema().visitColumns(new ColumnVisitor() {
                    @Override
                    public void booleanColumn(Column column) {
                        log.info("b: " + pageReader.getString(column));
                        row.getCustomElements().setValueLocal(column.getName(), pageReader.getString(column));
                    }

                    @Override
                    public void longColumn(Column column) {
                        log.info("l: " + pageReader.getString(column));
                        row.getCustomElements().setValueLocal(column.getName(), pageReader.getString(column));
                    }

                    @Override
                    public void doubleColumn(Column column) {
                        log.info("d: " + pageReader.getString(column));
                        row.getCustomElements().setValueLocal(column.getName(), pageReader.getString(column));
                    }

                    @Override
                    public void stringColumn(Column column) {
                        row.getCustomElements().setValueLocal(column.getName(), pageReader.getString(column));
                    }

                    @Override
                    public void timestampColumn(Column column) {
                        log.info("t: " + pageReader.getString(column));
                        row.getCustomElements().setValueLocal(column.getName(), pageReader.getString(column));
                    }
                });

                try {
                    row = service.insert(listFeedUrl, row);
                }
                catch (Exception e){
                    log.info("can not insert:" + e.toString());
                }
            }

            /*
            try {
                URL listFeedUrl = worksheet.getListFeedUrl();
                ListFeed feed = service.getFeed(listFeedUrl, ListFeed.class);

                String ret = "";
                for (ListEntry row : feed.getEntries()) {
                    for (String tag : row.getCustomElements().getTags()) {
                        ret += row.getCustomElements().getValue(tag) + "|";
                    }
                    log.info(ret);
                }
            }
            catch (Exception e){
                log.info("can not get:" + e.toString());
            }
            */

            log.info("add: end");
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

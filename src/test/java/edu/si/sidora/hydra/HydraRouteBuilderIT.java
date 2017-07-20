package edu.si.sidora.hydra;

import static org.apache.http.auth.AuthScope.ANY;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.MediaType;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.util.IoUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class HydraRouteBuilderIT extends CamelBlueprintTestSupport {
    private static final String BUILD_DIR = System.getProperty("buildDirectory");

    protected static final String FEDORA_URI = System.getProperty("si.fedora.host");

    private static final int FTP_PORT = Integer.parseInt(System.getProperty("edu.si.sidora.hydra.port"));

    private static final String FOXML = BUILD_DIR + "/foxml";

    private static final Logger logger = LoggerFactory.getLogger("Integration tests");

    private static CloseableHttpClient httpClient;

    private static FtpServer server;

    private static final Pattern dsLocation = Pattern.compile("<dsLocation>(.*)</dsLocation>");

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/blueprint.xml";
    }

    @BeforeClass
    public static void startup() throws IOException, FtpException {
        loadObjectsIntoFedora();
        buildFtpServer();
    }

    private static void loadObjectsIntoFedora() throws IOException {
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(ANY, new UsernamePasswordCredentials("fedoraAdmin", "fc"));
        httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
        ingest("genomics:1", Paths.get(FOXML, "genomics_1.xml"));
        ingest("genomics:2", Paths.get(FOXML, "genomics_2.xml"));
    }

    private static void ingest(String pid, Path payload) throws IOException, ClientProtocolException {
        HttpPost ingest = new HttpPost(FEDORA_URI + "/objects/" + pid + "?format=info:fedora/fedora-system:FOXML-1.1");
        ingest.setEntity(new ByteArrayEntity(Files.readAllBytes(payload)));
        ingest.setHeader("Content-type", MediaType.TEXT_XML);
        try (CloseableHttpResponse pidRes = httpClient.execute(ingest)) {
            assertEquals(HttpStatus.SC_CREATED, pidRes.getStatusLine().getStatusCode());
            logger.info("Ingested test object {}", EntityUtils.toString(pidRes.getEntity()));
        }
    }

    private static void buildFtpServer() throws FtpException {
        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(FTP_PORT);
        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setFile(new File(BUILD_DIR + "/ftpserver/user.properties"));
        userManagerFactory.setPasswordEncryptor(new ClearTextPasswordEncryptor());
        serverFactory.setUserManager(userManagerFactory.createUserManager());
        serverFactory.setListeners(mapOf("default", listenerFactory.createListener()));
        server = serverFactory.createServer();
        logger.info("Starting FTP server on port: {}", FTP_PORT);
        server.start();
    }

    @AfterClass
    public static void cleanUp() throws IOException {
        httpClient.close();
        server.stop();
    }

    @Test
    public void testTransmission() throws IOException {
        template().sendBodyAndHeaders("direct:transmission", "", mapOf("pid", "genomics:1", "user", "testUser"));
        FTPClient ftp = new FTPClient();
        ftp.configure(new FTPClientConfig());
        ftp.connect("localhost", FTP_PORT);
        assertTrue("Failed to connect to FTP server!", FTPReply.isPositiveCompletion(ftp.getReplyCode()));
        ftp.login("testUser", "testPassword");
        ftp.changeWorkingDirectory("pool/genomics/testUser");
        try (InputStream testData = ftp.retrieveFileStream("testfile1.fa");) {
            String data = IoUtils.readFully(testData);
            assertEquals("THIS DATA IS SO INCREDIBLY INTERESTING!\n", data);
        }
    }

    @Test
    public void testReception() throws IOException {
        template().sendBodyAndHeaders("direct:reception", "", mapOf("pid", "genomics:2", "user", "testUser"));
        HttpGet request = new HttpGet(FEDORA_URI + "/objects/genomics:2/datastreams/OBJ?format=xml");
        final String externalLocation;
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            try (InputStream content = response.getEntity().getContent()) {
                String xml = IoUtils.readFully(content);
                logger.info("Found datastream profile:\n{}", xml);
                Matcher matcher = dsLocation.matcher(xml);
                matcher.find();
                String fileUri = matcher.group(1);
                externalLocation = fileUri.replace("file:/", "");
                logger.info("Found datastream location:\n{}", externalLocation);
            }
        }
        assertTrue(Files.exists(Paths.get(externalLocation)));
    }

    @SuppressWarnings({ "serial", "unchecked" })
    private static <K, V> Map<K, V> mapOf(Object... mappings) {
        return new HashMap<K, V>(mappings.length / 2) {
            {
                for (int i = 0; i < mappings.length; i = i + 2)
                    put((K) mappings[i], (V) mappings[i + 1]);
            }
        };
    }

    @FunctionalInterface
    public interface UnsafeIO<S, T> extends Function<S, T> {

        T applyThrows(S s) throws IOException;

        @Override
        default T apply(final S elem) {
            try {
                return applyThrows(elem);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

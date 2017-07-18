package edu.si.sidora.hydra;

import static org.apache.http.auth.AuthScope.ANY;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import javax.ws.rs.core.MediaType;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
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
import org.apache.http.client.methods.CloseableHttpResponse;
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

import com.google.common.collect.ImmutableMap;

public class TransmissionRouteBuilderIT extends CamelBlueprintTestSupport {
    private static final String BUILD_DIR = System.getProperty("buildDirectory");

    protected static final String FEDORA_URI = System.getProperty("si.fedora.host");

    private static final int FTP_PORT = Integer.parseInt(System.getProperty("edu.si.sidora.hydra.port"));

    private static final Path FOXML = Paths.get(BUILD_DIR + "/foxml/genomics_1.xml");

    private static final Logger log = LoggerFactory.getLogger(TransmissionRouteBuilderIT.class);

    static CloseableHttpClient httpClient;

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/blueprint.xml";
    }

    @BeforeClass
    public static void startup() throws IOException, FtpException {
        loadObjectIntoFedora();
        buildFtpServer();
    }

    private static void loadObjectIntoFedora() throws IOException {
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(ANY, new UsernamePasswordCredentials("fedoraAdmin", "fc"));
        httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
        HttpPost ingest = new HttpPost(FEDORA_URI + "/objects/genomics:1?format=info:fedora/fedora-system:FOXML-1.1");
        ingest.setEntity(new ByteArrayEntity(Files.readAllBytes(FOXML)));
        ingest.setHeader("Content-type", MediaType.TEXT_XML);
        try (CloseableHttpResponse pidRes = httpClient.execute(ingest)) {
            log.info("Ingested test object {}", EntityUtils.toString(pidRes.getEntity()));
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
        serverFactory.setListeners(ImmutableMap.of("default", listenerFactory.createListener()));
        FtpServer server = serverFactory.createServer();
        log.info("Starting FTP server on port: {}", FTP_PORT);
        server.start();
    }

    @AfterClass
    public static void cleanUp() throws IOException {
        httpClient.close();
    }

    @Test
    public void testTransmission() throws IOException {
        ImmutableMap<String, Object> testHeaders = ImmutableMap.of("pid", "genomics:1", "user", "testUser");
        template().sendBodyAndHeaders("direct:transmission", "", testHeaders);
        FTPClient ftp = new FTPClient();
        ftp.configure(new FTPClientConfig());
        ftp.connect("localhost", FTP_PORT);
        assertTrue("Failed to connect to FTP server!", FTPReply.isPositiveCompletion(ftp.getReplyCode()));
        ftp.login("testUser", "testPassword");
        ftp.changeWorkingDirectory("pool/genomics/testUser");
        try (InputStream testData = ftp.retrieveFileStream("PretendGenomicsResource.fa");) {
            String data = IoUtils.readFully(testData);
            assertFalse(data.isEmpty());
        }
        ftp.completePendingCommand();
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

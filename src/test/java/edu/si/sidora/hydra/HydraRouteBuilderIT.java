package edu.si.sidora.hydra;

import static org.apache.commons.httpclient.HttpStatus.SC_CREATED;
import static org.apache.cxf.helpers.IOUtils.readStringFromStream;
import static org.apache.http.auth.AuthScope.ANY;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.commons.httpclient.HttpStatus;
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
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HydraRouteBuilderIT extends CamelBlueprintTestSupport {
    
    protected static final String FEDORA_URI = System.getProperty("si.fedora.host");

    private static final String BUILD_DIR = System.getProperty("buildDirectory");
    private static final int FTP_PORT = Integer.parseInt(System.getProperty("edu.si.sidora.hydra.port"));
    private static final String FOXML = BUILD_DIR + "/foxml";

    private static final Logger logger = LoggerFactory.getLogger("Integration tests");

    private static CloseableHttpClient httpClient;
    
    @Rule
    public final FtpGear ftp = new FtpGear(new File(BUILD_DIR + "/ftpserver/user.properties"), FTP_PORT);

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/blueprint.xml";
    }


    @BeforeClass
    public static void loadObjectsIntoFedora() throws IOException {
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
            assertEquals("Failed to ingest " + pid + "!", SC_CREATED, pidRes.getStatusLine().getStatusCode());
            logger.info("Ingested test object {}", EntityUtils.toString(pidRes.getEntity()));
        }
    }
    
    @AfterClass
    public static void cleanUpHttpClient() throws IOException {
        httpClient.close();
    }
    
    @Test
    public void testListing() throws IOException {
        // pretend that FOXML is a user
        String results = template().requestBodyAndHeaders("direct:file-listing", "", mapOf("user", "foxml"), String.class);
        String expected = "[ \"genomics_1.xml\", \"genomics_2.xml\" ]";
        assertEquals("Did not find correct file listing!", expected, results);
    }
    
    @Test
    public void testTransmission() throws IOException {
        template().sendBodyAndHeaders("direct:transmission", "", mapOf("pid", "genomics:1", "user", "testUser"));
        ftp.client.changeWorkingDirectory("pool/genomics/testUser");
        String results = readStringFromStream(ftp.client.retrieveFileStream("testfile1.fa"));
        assertEquals("THIS DATA IS SO INCREDIBLY INTERESTING!\n", results);
    }

    @Test
    public void testReception() throws IOException {
        template().sendBodyAndHeaders("direct:reception", "", mapOf("pid", "genomics:2", "user", "testUser"));
        HttpGet request = new HttpGet(FEDORA_URI + "/objects/genomics:2/datastreams/OBJ?format=xml");
        final String xml;
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            xml = EntityUtils.toString(response.getEntity());
        }
        logger.info("Found datastream profile:\n{}", xml);
        String externalLocation = xml.split("<dsLocation>")[1].split("</dsLocation>")[0].replace("file:/", "");
        logger.info("Found datastream location:\n{}", externalLocation); 
        String contents = new String(Files.readAllBytes(Paths.get(externalLocation)));
        assertEquals("THIS DATA IS SO INCREDIBLY FASCINATING!\n", contents);
    }
    
    @SuppressWarnings({ "serial", "unchecked" })
    private static <K, V> Map<K, V> mapOf(Object... mappings) {
        return new HashMap<K, V>(mappings.length / 2) {{
                for (int i = 0; i < mappings.length; i = i + 2) put((K) mappings[i], (V) mappings[i + 1]);
            }};
    }
}

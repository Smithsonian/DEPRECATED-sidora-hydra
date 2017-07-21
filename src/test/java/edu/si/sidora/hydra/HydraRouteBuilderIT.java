package edu.si.sidora.hydra;

import static org.apache.commons.httpclient.HttpStatus.SC_CREATED;
import static org.apache.http.auth.AuthScope.ANY;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.MediaType;

import org.apache.commons.httpclient.HttpStatus;
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

public class HydraRouteBuilderIT extends FtpRouteBuilderIT {
    protected static final String FEDORA_URI = System.getProperty("si.fedora.host");

    private static final String FOXML = System.getProperty("buildDirectory") + "/foxml";

    private static final Logger logger = LoggerFactory.getLogger("Integration tests");

    private static CloseableHttpClient httpClient;

    private static final Pattern dsLocation = Pattern.compile("<dsLocation>(.*)</dsLocation>");

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
    public void testTransmission() throws IOException {
        template().sendBodyAndHeaders("direct:transmission", "", mapOf("pid", "genomics:1", "user", "testUser"));
        ftpClient.changeWorkingDirectory("pool/genomics/testUser");
        try (InputStream testData = ftpClient.retrieveFileStream("testfile1.fa");) {
            assertEquals("THIS DATA IS SO INCREDIBLY INTERESTING!\n", IoUtils.readFully(testData));
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
        String contents = new String(Files.readAllBytes(Paths.get(externalLocation)));
        assertEquals("THIS DATA IS SO INCREDIBLY FASCINATING!\n", contents);
    }
}

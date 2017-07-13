package edu.si.sidora.hydra;

import static java.util.stream.Collectors.toList;
import static org.apache.http.auth.AuthScope.ANY;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.MediaType;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
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
    protected static final String FEDORA_URI = System.getProperty("si.fedora.host");
    
    private static final int FTP_PORT = Integer.parseInt(System.getProperty("ftpPort"));

    private static final Path FOXML_DIR = Paths.get(System.getProperty("buildDirectory") + "/foxml");

    private static final Logger log = LoggerFactory.getLogger(TransmissionRouteBuilderIT.class);

    static CloseableHttpClient httpClient;
    
    static List<String> ingestedPids;

    @Override
    protected String getBlueprintDescriptor() {
        return "OSGI-INF/blueprint/blueprint.xml";
    }

    @BeforeClass
    public static void loadObjectsIntoFedora() throws IOException, FtpException {
        
        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(FTP_PORT);
        serverFactory.setListeners(ImmutableMap.of("testServer", listenerFactory.createListener()));
        FtpServer server = serverFactory.createServer();
        log.info("Starting FTP server on port: {}", FTP_PORT);
        server.start();
        
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(ANY, new UsernamePasswordCredentials("fedoraAdmin", "fc"));
        httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();

        log.info("Using FOXML from: {}", FOXML_DIR);
        try (Stream<Path> foxmls = Files.walk(FOXML_DIR).filter(p -> !p.equals(FOXML_DIR))) {
            ingestedPids = foxmls.peek(p -> log.info("Ingesting: {}", p.getFileName()))
                            .map((UnsafeIO<Path, byte[]>) Files::readAllBytes)
                            .map(((UnsafeIO<byte[], String>) TransmissionRouteBuilderIT::ingest))
                            .collect(toList());
        }
    }

    @AfterClass
    public static void cleanUp() throws IOException {
        httpClient.close();
    }

    static String ingest(byte[] foxml) throws IOException {
        HttpPost ingest = new HttpPost(FEDORA_URI + "/objects/new?format=info:fedora/fedora-system:FOXML-1.1");
        ingest.setEntity(new ByteArrayEntity(foxml));
        ingest.setHeader("Content-type", MediaType.TEXT_XML);
        try (CloseableHttpResponse pidRes = httpClient.execute(ingest)) {
            return EntityUtils.toString(pidRes.getEntity());
        }
    }

    @Test
    public void test() {
        String fileLocation = "file:" +System.getProperty("buildDirectory")+ "/testfile.fa";
        log.info("Testing direct transmission with file: {}", fileLocation);
        ImmutableMap<String, Object> testHeaders = ImmutableMap.of("fileUri", fileLocation, "location", "scratch");
        template().sendBodyAndHeaders("direct:transmit-to-hydra", "a message", testHeaders);
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

    @FunctionalInterface
    public interface UnsafeConsumer<T> extends Consumer<T> {

        void acceptThrows(T t) throws IOException;

        default void accept(T t) {
            try {
                acceptThrows(t);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

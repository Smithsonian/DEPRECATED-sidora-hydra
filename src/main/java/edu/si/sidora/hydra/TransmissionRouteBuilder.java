package edu.si.sidora.hydra;

import static java.util.Collections.singletonMap;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.LoggingLevel.DEBUG;

import java.io.File;
import java.io.FileInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransmissionRouteBuilder extends RouteBuilder {
    
    private static final String LOG_CHANNEL = "edu.si.sidora.hydra";

    private static final Logger log = LoggerFactory.getLogger(LOG_CHANNEL);

    @Override
    public void configure() {

        onException(Throwable.class).to("mock:errorRoute");

        from("cxfrs:bean:transmissionServer?bindingStyle=SimpleConsumer")
        .to("direct:transmission");
        
        from("direct:transmission")
        .to("direct:acquire-file-location")
        .to("direct:transmit-to-hydra");

        from("direct:acquire-file-location")
        .setHeader(HTTP_METHOD, constant("GET"))
        .setHeader(HTTP_URI, simple("{{edu.si.sidora.hydra.fedora.uri}}/objects/${header.pid}/datastreams/OBJ?format=xml"))
        .to("http://dummy?authMethod=Basic&authUsername={{edu.si.sidora.hydra.fedora.user}}&authPassword={{edu.si.sidora.hydra.fedora.password}}&httpClient.authenticationPreemptive=true")
        .setHeader("fileUri")
            .xpath("/management:datastreamProfile/management:dsLocation/text()",
                            singletonMap("management", "http://www.fedora.info/definitions/1/0/management/"));

        from("direct:transmit-to-hydra")
        // get the file from the fileUri header
        .process(exchange -> {
            String fileUri = exchange.getIn().getHeader("fileUri", String.class);
            // dsLocation includes the file:// (or other) protocol for an E datastream
            String substring = fileUri.substring(fileUri.indexOf(":") + 1);
            log.info("Using file name {}", substring);
            File file = new File(substring);
            exchange.getIn().setBody(new FileInputStream(file));
            // rely on the OS for file metadata
            exchange.getIn().setHeader("size", file.length());
            exchange.getIn().setHeader("CamelFileName", file.getName());
         })
        .log(DEBUG, log, "Transmitting file with length: ${header.size}")
        .choice()
            .when(simple("${header.size} >= {{edu.si.sidora.hydra.sizeForScratch}}"))
                .setHeader("hydraLocation", constant("scratch"))
            .when(simple("${header.size} < {{edu.si.sidora.hydra.sizeForScratch}}"))
                .setHeader("hydraLocation", constant("pool"))
            .otherwise().throwException(IllegalArgumentException.class, "Hydra location must be 'scratch' or 'pool'!").end()
        .log(DEBUG, log, "to ${header.hydraLocation}")
        .threads(5)
        .toD("ftp://{{edu.si.sidora.hydra.location}}:{{edu.si.sidora.hydra.port}}/${header.hydraLocation}/genomics/${header.user}?username=testUser&password=testPassword&autoCreate=true")
        .log(DEBUG, "Transmission complete");
        
    }

}

package edu.si.sidora.hydra;

import static java.util.Collections.singletonMap;
import static org.apache.camel.LoggingLevel.INFO;

import java.io.File;
import java.util.Map;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransmissionRouteBuilder extends RouteBuilder {
    
    private static final Logger log = LoggerFactory.getLogger("edu.si.sidora.hydra");

    Map<String, String> namespace = singletonMap("fedora", "http://www.fedora.info/definitions/1/0/management/");

    @Override
    public void configure() {
        String foo = "dsfg";
        foo.substring(0,foo.lastIndexOf('/')+1);
        onException(Throwable.class).to("mock:errorRoute");

        from("cxfrs:bean:transmissionServer?bindingStyle=SimpleConsumer")
        .to("direct:acquire-file-location")
        .to("direct:transmit-to-hydra");

        from("direct:acquire-file-location")
        .toD("{{si.fedora.host}}/objects/${header.pid}/datastreams/OBJ?format=xml")
        // dsLocation includes the file:// (or other) protocol for an E datastream
        .setHeader("fileUri").xpath("/datastreamProfile/dsLocation", namespace);

        from("direct:transmit-to-hydra")
        .to("log:hydra?showAll=true")
            // use the fileName header to find the actual file on disk
            .process(exchange -> {
                String fileUri = exchange.getIn().getHeader("fileUri", String.class);
                exchange.getOut().setHeader("fileDir", fileUri.substring(0, fileUri.lastIndexOf("/")));
                exchange.getOut().setHeader("fileName", fileUri.substring(fileUri.lastIndexOf("/") + 1));
             })
            .pollEnrich().simple("${header.fileDir}?fileName=${header.fileName}")
            .log(INFO, log, "Transmitting ${header.fileName} with length: ${file:length}")
            .choice()
            .when(simple("${file:length} >= {{edu.si.sidora.hydra.sizeForScratch}}"))
                .setHeader("location", constant("scratch"))
            .when(simple("${file:length} < {{edu.si.sidora.hydra.sizeForScratch}}"))
                .setHeader("location", constant("pool"))
            .otherwise().throwException(IllegalArgumentException.class, "Hydra location must be 'scratch' or 'pool'!")
            .log(INFO, log, "to ${header.location}")
            .toD("ftp://{{edu.si.sidora.hydra.location}}{{edu.si.sidora.hydra.port}}/${header.location}/${header.user}?username=testUser&password=testPassword")
        .to("mock:receptacle");
        
    }

}

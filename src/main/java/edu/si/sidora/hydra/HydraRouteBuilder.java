package edu.si.sidora.hydra;

import static java.lang.String.join;
import static java.util.Collections.singletonMap;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.INFO;

import java.io.File;
import java.io.FileInputStream;

import org.apache.camel.builder.RouteBuilder;

public class HydraRouteBuilder extends RouteBuilder {

    @Override
    public void configure() {

        onException(Throwable.class).to("mock:errorRoute");
        
        /// common routing
        
        from("direct:acquire-file-location")
        .description("Find location of external datastream")
        .setHeader(HTTP_METHOD, constant("GET"))
        .setHeader(HTTP_URI, simple("{{edu.si.sidora.hydra.fedora.uri}}/objects/${header.pid}/datastreams/OBJ?format=xml"))
        .to("http://dummyUri?authMethod=Basic&authUsername={{edu.si.sidora.hydra.fedora.user}}&authPassword={{edu.si.sidora.hydra.fedora.password}}&httpClient.authenticationPreemptive=true")
        .setHeader("fileUri")
            .xpath("/management:datastreamProfile/management:dsLocation/text()",
                            singletonMap("management", "http://www.fedora.info/definitions/1/0/management/"))
        .removeHeaders(allOf(HTTP_URI, HTTP_METHOD));

        from("direct:acquire-file")
        .description("Find file on disk and acquire a stream to it")
        // get the file from the fileUri header
        .process(exchange -> {
            String fileUri = exchange.getIn().getHeader("fileUri", String.class);
            // dsLocation includes the file:// (or other) protocol for an E datastream
            String substring = fileUri.substring(fileUri.indexOf(":") + 1);
            log.debug("Using file name {}", substring);
            File file = new File(substring);
            exchange.getIn().setBody(new FileInputStream(file));
            // rely on the OS for file metadata
            exchange.getIn().setHeader("fileSize", file.length());
            exchange.getIn().setHeader("CamelFileName", file.getName());
         });
 
        from("cxfrs:bean:hydraServer?bindingStyle=SimpleConsumer")
        .choice()
            .when(simple("${header.operationName} == \"transmitToHydra\""))
                .to("direct:transmission")
            .when(simple("${header.operationName} == \"receiveFromDropbox\""))
                .to("direct:reception");

        from("direct:transmission")
        .description("Move files from Sidora to Hydra")
        .to("direct:acquire-file-location")
        .to("direct:acquire-file")
        .to("direct:transmit-to-hydra")
        .log(INFO, "${header.pid},${header.user},${header.email},File transfer to Hydra complete!");

        from("direct:transmit-to-hydra")
        .description("Move an acquired datastream stream from Sidora to Hydra")
        .log(DEBUG, "Transmitting file with length: ${header.size}")
        .choice()
            .when(simple("${header.fileSize} >= {{edu.si.sidora.hydra.sizeForScratch}}"))
                .setHeader("hydraStorageChoice", constant("scratch"))
            .when(simple("${header.fileSize} < {{edu.si.sidora.hydra.sizeForScratch}}"))
                .setHeader("hydraStorageChoice", constant("pool"))
            .otherwise().throwException(IllegalArgumentException.class, "Hydra location must be 'scratch' or 'pool'!").end()
        .log(DEBUG, "to ${header.hydraStorageChoice}")
        .threads(5)
        .toD("ftp://{{edu.si.sidora.hydra.location}}:{{edu.si.sidora.hydra.port}}/${header.hydraStorageChoice}/genomics/${header.user}?username=testUser&password=testPassword&autoCreate=true")
        // time and logging level should be inserted into the following log message by the logging config in deployment
        .removeHeader("hydraStorageChoice");

        from("direct:reception")
        .description("Move files from a dropbox to Sidora")
        .to("direct:acquire-file-location")
        .to("direct:acquire-file")
        .to("direct:move-file")
        .to("direct:point-object-to-new-file")
        // time and logging level should be inserted into the following log message by the logging config in deployment
        .log(INFO, "${header.pid},${header.user},${header.email},File transfer to Hydra complete!");

        from("direct:move-file")
        .description("Copy bits from dropbox to Sidora")
        .threads(5)
        .process(e -> e.getIn().setHeader("UUID", getContext().getUuidGenerator().generateUuid()))
        .setHeader("externalStorage").simple("{{edu.si.sidora.hydra.externalStorageLocation}}/${header.user}/${header.UUID}")
        .toD("file:${header.externalStorage}")
        .setBody().constant("");
        
        from("direct:point-object-to-new-file")
        .description("Alter the datastream profile in an object to point at a moved file")
        .setHeader(HTTP_METHOD, constant("PUT"))
        .setHeader("newFileLocation")
            .simple("file:/${header.externalStorage}/${header.CamelFileName}")
        .setHeader(HTTP_URI)
            .simple("{{edu.si.sidora.hydra.fedora.uri}}/objects/${header.pid}/datastreams/OBJ?dsLocation=${header.newFileLocation}")
        .to("http://dummyUri?authMethod=Basic&authUsername={{edu.si.sidora.hydra.fedora.user}}&authPassword={{edu.si.sidora.hydra.fedora.password}}&httpClient.authenticationPreemptive=true")
        .removeHeaders(allOf("newFileLocation", HTTP_METHOD, HTTP_URI, "externalStorage"));
    }

    private static String allOf(String... these) {
        return join("|", these);
    }
}

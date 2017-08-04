/**
 * Copyright 2017 Smithsonian Institution.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.You may obtain a copy of
 * the License at: http://www.apache.org/licenses/
 *
 * This software and accompanying documentation is supplied without
 * warranty of any kind. The copyright holder and the Smithsonian Institution:
 * (1) expressly disclaim any warranties, express or implied, including but not
 * limited to any implied warranties of merchantability, fitness for a
 * particular purpose, title or non-infringement; (2) do not assume any legal
 * liability or responsibility for the accuracy, completeness, or usefulness of
 * the software; (3) do not represent that use of the software would not
 * infringe privately owned rights; (4) do not warrant that the software
 * is error-free or will be maintained, supported, updated or enhanced;
 * (5) will not be liable for any indirect, incidental, consequential special
 * or punitive damages of any kind or nature, including but not limited to lost
 * profits or loss of data, on any basis arising from contract, tort or
 * otherwise, even if any of the parties has been warned of the possibility of
 * such loss or damage.
 *
 * This distribution includes several third-party libraries, each with their own
 * license terms. For a complete copy of all copyright and license terms, including
 * those of third-party libraries, please see the product release notes.
 */
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
import org.apache.camel.model.dataformat.JsonLibrary;

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
                .to("direct:reception")
            .when(simple("${header.operationName} == \"listDropbox\""))
                .to("direct:file-listing");

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
        
        from("direct:file-listing")
        .description("List files in a dropbox")
        .setHeader("dropbox").simple("{{edu.si.sidora.dropboxLocation}}/${header.user}")
        .process(exchange -> {
            String dropboxLoc = exchange.getIn().getHeader("dropbox", String.class);
            File dropbox = new File(dropboxLoc);
            String[] fileList = !dropbox.exists() || !dropbox.isDirectory() ? new String[0] : dropbox.list();
            exchange.getIn().setBody(fileList);
        })
        .marshal().json(JsonLibrary.Jackson, true);
    }

    private static String allOf(String... these) {
        return join("|", these);
    }
}

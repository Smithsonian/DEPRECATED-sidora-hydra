package edu.si.sidora.hydra;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.Listener;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.mina.core.RuntimeIoException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpGear extends ExternalResource {

    private static final Logger logger = LoggerFactory.getLogger("Test FTP Gear");

    private final int port;
    private final File userPropertiesFile;
    FtpServer server;
    FTPClient client;

    FtpGear(File userPropertiesFile, int port) {
        this.userPropertiesFile = userPropertiesFile;
        this.port = port;
    }

    @Override
    protected void before() throws Throwable {
        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(port);
        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setFile(userPropertiesFile);
        userManagerFactory.setPasswordEncryptor(new ClearTextPasswordEncryptor());
        serverFactory.setUserManager(userManagerFactory.createUserManager());
        serverFactory.setListeners(new HashMap<String, Listener>() {{ 
            put("default", listenerFactory.createListener());
        }});
        server = serverFactory.createServer();
        logger.info("Starting FTP server on port: {}", port);
        server.start();

        client = new FTPClient();
        client.configure(new FTPClientConfig());
        client.connect("localhost", port);
        assertTrue("Failed to connect to FTP server!", FTPReply.isPositiveCompletion(client.getReplyCode()));
        client.login("testUser", "testPassword");
    }

    @Override
    protected void after() {
        try {
            client.quit();
            client.disconnect();
        } catch (IOException e) {
            throw new RuntimeIoException(e);
        }
        server.stop();
    }
}

package edu.si.sidora.hydra;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FtpEquippedRouteBuilderIT extends CamelBlueprintTestSupport {

    private static final String BUILD_DIR = System.getProperty("buildDirectory");
    private static final int FTP_PORT = Integer.parseInt(System.getProperty("edu.si.sidora.hydra.port"));
    private static FtpServer ftpServer;
    protected static FTPClient ftpClient;

    private static final Logger logger = LoggerFactory.getLogger("Test FTP Gear");

    @BeforeClass
    public static void buildFtpGear() throws FtpException, IOException {
        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(FTP_PORT);
        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setFile(new File(BUILD_DIR + "/ftpserver/user.properties"));
        userManagerFactory.setPasswordEncryptor(new ClearTextPasswordEncryptor());
        serverFactory.setUserManager(userManagerFactory.createUserManager());
        serverFactory.setListeners(mapOf("default", listenerFactory.createListener()));
        ftpServer = serverFactory.createServer();
        logger.info("Starting FTP ftpServer on port: {}", FTP_PORT);
        ftpServer.start();
        
        ftpClient = new FTPClient();
        ftpClient.configure(new FTPClientConfig());
        ftpClient.connect("localhost", FTP_PORT);
        assertTrue("Failed to connect to FTP ftpServer!", FTPReply.isPositiveCompletion(ftpClient.getReplyCode()));
        ftpClient.login("testUser", "testPassword");
    }

    @AfterClass
    public static void cleanUpFtpGear() throws IOException {
        ftpClient.quit();
        ftpClient.disconnect();
        ftpServer.stop();
    }

    @SuppressWarnings({ "serial", "unchecked" })
    protected static <K, V> Map<K, V> mapOf(Object... mappings) {
        return new HashMap<K, V>(mappings.length / 2) {{
                for (int i = 0; i < mappings.length; i = i + 2) put((K) mappings[i], (V) mappings[i + 1]);
            }};
    }
}

package com.test.PP_Machines_storage;

import com.jcraft.jsch.*;
import org.testng.Assert;
import org.testng.annotations.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Atlas_Annotation_Data {

    private static final Logger logger = Logger.getLogger(Atlas_Annotation_Data.class.getName());

    private Session session;
    private String biosampleId;
    private static final String HOST = "apollo2.humanbrain.in";
    private static final int PORT = 22;
    private static final String USER = "hbp";
    private static final String PASSWORD = "Health#123"; // Consider using a secure method for handling passwords.
    private List<String> files = new ArrayList<>();

    @BeforeClass
    public void setUp() throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(USER, HOST, PORT);
        session.setPassword(PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
    }

    @AfterClass
    public void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    @Parameters("biosampleId")
    @Test(priority = 1)
    public void testListFiles(@Optional String biosampleId) throws Exception {
        this.biosampleId = biosampleId;

        String lsCommand = "cd /store/repos1/iitlab/humanbrain/analytics/" + biosampleId +
                "/appData/atlasEditor/189/NISL && ls";

        executeCommand(lsCommand, line -> files.add(line));

        logger.info("Number of sections (files) in directory: " + files.size());
        Assert.assertTrue(files.size() > 0, "No files found in the directory.");
    }

    @Test(priority = 2, dependsOnMethods = {"testListFiles"})
    public void testPrintFiles() {
        logger.info("Files in directory:");
        int count = 0;
        for (String file : files) {
            System.out.printf("%-10s", file);
            count++;
            if (count % 20 == 0) {
                System.out.println();
            }
        }
        if (count % 20 != 0) {
            System.out.println();
        }
    }

    @Test(priority = 3, dependsOnMethods = {"testListFiles"})
    public void testFlatTreeJsonFiles() throws Exception {
        int jsonFileCount = 0;
        Set<Integer> validSections = new TreeSet<>();

        for (String sectionNumber : files) {
            String grepCommand = "ls -alh /store/repos1/iitlab/humanbrain/analytics/" + biosampleId +
                    "/appData/atlasEditor/189/NISL/" + sectionNumber + " | grep FlatTree";

            boolean foundValidFile = executeCommand(grepCommand, line -> {
                String[] parts = line.split("\\s+");
                String sizeStr = parts[4];
                if (isValidSize(sizeStr, 70)) {
                    logger.info(line);
                    jsonFileCount++;
                }
            });

            if (foundValidFile) {
                try {
                    validSections.add(Integer.parseInt(sectionNumber));
                } catch (NumberFormatException e) {
                    logger.log(Level.SEVERE, "Invalid section number format: " + sectionNumber, e);
                }
            }
        }

        logger.info("                                     *************                                              ");
        logger.info("Total number of FlatTree JSON files with sizes greater than 70 and kb or mb size files: " + jsonFileCount);
        logger.info("Sections with FlatTree JSON files with sizes greater than 70 and kb or mb size files: " + validSections);
        logger.info("                                     *************                                              ");
        Assert.assertTrue(jsonFileCount > 0, "No FlatTree JSON files found with sizes greater than 70 or ending in 'K' or 'M'.");
    }

    private boolean executeCommand(String command, CommandOutputProcessor processor) throws Exception {
        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand(command);
        channel.setInputStream(null);
        ((ChannelExec) channel).setErrStream(System.err);

        InputStream in = channel.getInputStream();
        channel.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        boolean foundValidFile = false;
        while ((line = reader.readLine()) != null) {
            processor.process(line);
            foundValidFile = true;
        }

        channel.disconnect();
        return foundValidFile;
    }

    @FunctionalInterface
    private interface CommandOutputProcessor {
        void process(String line) throws Exception;
    }

    private static boolean isValidSize(String sizeStr, int threshold) {
        if (sizeStr.endsWith("K") || sizeStr.endsWith("M")) {
            return true;
        } else {
            try {
                int size = Integer.parseInt(sizeStr);
                return size > threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}

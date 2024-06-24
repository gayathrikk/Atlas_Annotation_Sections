package com.test.PP_Machines_storage;

import com.jcraft.jsch.*;
import org.testng.Assert;
import org.testng.annotations.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

public class Atlas_Annotation_Data {

    private Session session;
    private String biosampleId;
    private final String host = "apollo2.humanbrain.in";
    private final int port = 22;
    private final String user = "hbp";
    private final String password = "Health#123";
    private List<String> files = new ArrayList<>();

    @BeforeClass
    public void setUp() throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(user, host, port);
        session.setPassword(password);
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

        Channel channelLs = session.openChannel("exec");
        ((ChannelExec) channelLs).setCommand(lsCommand);
        channelLs.setInputStream(null);
        ((ChannelExec) channelLs).setErrStream(System.err);

        InputStream inLs = channelLs.getInputStream();
        channelLs.connect();

        BufferedReader readerLs = new BufferedReader(new InputStreamReader(inLs));
        String line;
        while ((line = readerLs.readLine()) != null) {
            files.add(line);
        }

        channelLs.disconnect();

        System.out.println("Number of sections (files) in directory: " + files.size());
        Assert.assertTrue(files.size() > 0, "No files found in the directory.");
    }

    @Test(priority = 2, dependsOnMethods = {"testListFiles"})
    public void testPrintFiles() {
        System.out.println("Files in directory:");
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

            Channel channelGrep = session.openChannel("exec");
            ((ChannelExec) channelGrep).setCommand(grepCommand);
            channelGrep.setInputStream(null);
            ((ChannelExec) channelGrep).setErrStream(System.err);

            InputStream inGrep = channelGrep.getInputStream();
            channelGrep.connect();

            BufferedReader readerGrep = new BufferedReader(new InputStreamReader(inGrep));
            String line;
            System.out.println("FlatTree JSON files for section " + sectionNumber + ":");
            boolean foundValidFile = false;
            while ((line = readerGrep.readLine()) != null) {
                String[] parts = line.split("\\s+");
                String sizeStr = parts[4];
                if (isValidSize(sizeStr, 70)) {
                    System.out.println(line);
                    jsonFileCount++;
                    foundValidFile = true;
                }
            }

            if (foundValidFile) {
                try {
                    validSections.add(Integer.parseInt(sectionNumber));
                } catch (NumberFormatException e) {
                    System.err.println("Invalid section number format: " + sectionNumber);
                }
            }

            channelGrep.disconnect();
        }

        System.out.println("                                     *************                                              ");
        System.out.println("Total number of FlatTree JSON files with sizes greater than 70 and kb size files: " + jsonFileCount);
        System.out.println("Sections with FlatTree JSON files with sizes greater than 70 and kb size files: " + validSections);
        System.out.println("                                     *************                                              ");
        Assert.assertTrue(jsonFileCount > 0, "No FlatTree JSON files found with sizes greater than 70 or ending in 'K'.");
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

/*
 * Copyright (c) 2019. UnifiedPost, SA. http://www.unifiedpost.com
 * This software is the proprietary information of UnifiedPost, SA.
 * Use is subject to license terms.
 */

package com.generalmobile.googledriveupload;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.common.base.Joiner;
import hudson.model.BuildListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Queue;

import static com.generalmobile.googledriveupload.DriveMockHttpTransport.BatchCreateRequest;
import static com.generalmobile.googledriveupload.DriveMockHttpTransport.FilesCreateRequest;
import static com.generalmobile.googledriveupload.DriveMockHttpTransport.FilesListRequest;
import static com.generalmobile.googledriveupload.DriveMockHttpTransport.FilesUploadRequest;
import static com.generalmobile.googledriveupload.GoogleDriveUploader.APPLICATION_NAME;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoogleDriveManagerTest {

    public static final String FOLDER_ID = "111111";
    public static final String FOLDER_NAME = "testFolder";
    public static final String FILE_ID = "222222";
    public static final String PERMISSION_ID = "33333";

    private static final transient Logger logger = LoggerFactory.getLogger(GoogleDriveManagerTest.class);
    DriveMockHttpTransport mockHttpTransport = new DriveMockHttpTransport();
    ByteArrayOutputStream loggerOutput = new ByteArrayOutputStream();
    Queue<Object[]> errors = new LinkedList<>();
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    private GoogleDriveManager googleDriveManager;

    @Before
    public void setupDriveService() {

        BuildListener mockBuildListener = mock(BuildListener.class);
        when(mockBuildListener.getLogger()).thenReturn(new PrintStream(loggerOutput));
        when(mockBuildListener.error(anyString())).thenAnswer(errorMockAnswer());
        when(mockBuildListener.error(anyString(), any(Exception.class))).thenAnswer(errorMockAnswer());

        Credential mockCredential = mock(Credential.class);
        googleDriveManager = new GoogleDriveManager(
            new Drive.Builder(mockHttpTransport, new JacksonFactory(), mockCredential)
                .setApplicationName(APPLICATION_NAME)
                .build(),
            mockBuildListener);
    }

    private Answer<PrintWriter> errorMockAnswer() {
        return new Answer<PrintWriter>() {
            @Override
            public PrintWriter answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                errors.add(args);
                return mock(PrintWriter.class);
            }
        };
    }

    @Test
    public void uploadFolder_withWithoutSourceFile() {
        mockHttpTransport.mock(FilesListRequest.emptyList());
        mockHttpTransport.mock(FilesCreateRequest.createFolder(FOLDER_ID, FOLDER_NAME));
        mockHttpTransport.mock(BatchCreateRequest.acceptedPermissions(PERMISSION_ID, "me@localtest.me"));
        googleDriveManager.uploadFolder(null, "folder_to_upload", "me@localtest.me");
        assertEquals(Joiner.on("\n").join(
            "userMail me@localtest.me",
            "Write permissions set to Folder folder_to_upload for me@localtest.me",
            ""), loggerOutput.toString());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void uploadFolder_withExistingSourceFile() {
        File fileToUpload = new File("pom.xml");
        mockHttpTransport.mock(FilesListRequest.emptyList());
        mockHttpTransport.mock(FilesCreateRequest.createFolder(FOLDER_ID, FOLDER_NAME));
        mockHttpTransport.mock(BatchCreateRequest.acceptedPermissions(PERMISSION_ID, "me@localtest.me"));
        mockHttpTransport.mock(FilesListRequest.emptyList());
        mockHttpTransport.mock(FilesUploadRequest.initiateCreateUpload(FILE_ID, fileToUpload.getName(), "xxx"));
        mockHttpTransport.mock(FilesUploadRequest.resumeUpload(FILE_ID, fileToUpload.getName(), "xxx"));
        googleDriveManager.uploadFolder(fileToUpload, FOLDER_NAME, "me@localtest.me");
        assertEquals(Joiner.on("\n").join(
            "userMail me@localtest.me",
            "Write permissions set to Folder " + FOLDER_NAME + " for me@localtest.me",
            "Searching for " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Creating new File " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Start uploading " + fileToUpload.getName(),
            "Finished uploading " + fileToUpload.getName(),
            ""), loggerOutput.toString());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void uploadFolder_withNoneExistingSourceFile() {
        File fileToUpload = new File("invalid_file_name.txt");
        mockHttpTransport.mock(FilesListRequest.emptyList());
        mockHttpTransport.mock(FilesCreateRequest.createFolder(FOLDER_ID, FOLDER_NAME));
        mockHttpTransport.mock(BatchCreateRequest.acceptedPermissions(PERMISSION_ID, "me@localtest.me"));
        mockHttpTransport.mock(FilesListRequest.emptyList());
        mockHttpTransport.mock(FilesUploadRequest.initiateCreateUpload(FILE_ID, fileToUpload.getName(), "xxx"));
        googleDriveManager.uploadFolder(fileToUpload, FOLDER_NAME, "me@localtest.me");
        assertEquals(Joiner.on("\n").join(
            "userMail me@localtest.me",
            "Write permissions set to Folder " + FOLDER_NAME + " for me@localtest.me",
            "Searching for " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Creating new File " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Start uploading " + fileToUpload.getName(),
            ""), loggerOutput.toString());
        assertEquals("invalid_file_name.txt (No such file or directory)", Joiner.on("\n").join(errors.remove()));
    }

    @Test
    public void uploadFolder_withExistingFileAndExistDestFolder() {
        File fileToUpload = new File("pom.xml");
        mockHttpTransport.mock(FilesListRequest.findFolder(FOLDER_ID, FOLDER_NAME));
        mockHttpTransport.mock(FilesListRequest.emptyList());
        mockHttpTransport.mock(FilesUploadRequest.initiateCreateUpload(FILE_ID, fileToUpload.getName(), "xxx"));
        mockHttpTransport.mock(FilesUploadRequest.resumeUpload(FILE_ID, fileToUpload.getName(), "xxx"));
        googleDriveManager.uploadFolder(fileToUpload, FOLDER_NAME, "me@localtest.me");
        assertEquals(Joiner.on("\n").join(
            "userMail me@localtest.me",
            "Found folder " + FOLDER_NAME,
            "Searching for " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Creating new File " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Start uploading " + fileToUpload.getName(),
            "Finished uploading " + fileToUpload.getName(),
            ""), loggerOutput.toString());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void uploadFolder_withExistingFileAndExistDestFolderAndExistDestFile() {
        File fileToUpload = new File("pom.xml");
        mockHttpTransport.mock(FilesListRequest.findFolder(FOLDER_ID, FOLDER_NAME));
        mockHttpTransport.mock(FilesListRequest.findFile(FILE_ID, fileToUpload.getName(), "xxx"));
        mockHttpTransport.mock(FilesUploadRequest.initiateUpdateUpload(FILE_ID, fileToUpload.getName(), "xxx"));
        mockHttpTransport.mock(FilesUploadRequest.resumeUpload(FILE_ID, fileToUpload.getName(), "xxx"));
        googleDriveManager.uploadFolder(fileToUpload, FOLDER_NAME, "me@localtest.me");
        assertEquals(Joiner.on("\n").join(
            "userMail me@localtest.me",
            "Found folder " + FOLDER_NAME,
            "Searching for " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Found File: " + fileToUpload.getName() + " (" + FILE_ID + ")",
            "Updating existing File " + fileToUpload.getName() + " in " + FOLDER_NAME + " (" + FOLDER_ID + ")",
            "Start uploading " + fileToUpload.getName(),
            "Finished uploading " + fileToUpload.getName(),
            ""), loggerOutput.toString());
        assertTrue(errors.isEmpty());
    }
}


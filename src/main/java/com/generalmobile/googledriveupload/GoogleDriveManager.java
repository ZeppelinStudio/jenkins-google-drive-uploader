package com.generalmobile.googledriveupload;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequest;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.BuildListener;

import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

class GoogleDriveManager {

    private static final int MB = 0x100000;
    private static final String DRIVE_QUERY_FORMAT = "'%s' in parents and trashed=false";


    private final Drive drive;
    private final BuildListener listener;

    GoogleDriveManager(final  Drive driveService, final BuildListener listener) {
        this.drive = driveService;
        this.listener = listener;
    }
    
    void uploadFolder(java.io.File source, String destFolderName, String userMail) {
        listener.getLogger().printf("userMail %s%n", userMail);
        File destFolder = controlParent(destFolderName, userMail);
        if (destFolder != null) {
            uploadFile(source, destFolder);
        }
    }

    @SuppressFBWarnings
    private void uploadFile(final java.io.File source, File destFolder) {
        try {
            if (source != null && source.isDirectory()) {
                File destSubFolder = findInFolder(destFolder, source.getName())
                    .orElseGet(() -> createNewFolder(destFolder, source.getName()));
                if (destSubFolder != null) {
                    for (java.io.File sourceFile : Objects.requireNonNull(source.listFiles())) {
                        uploadFile(sourceFile, destSubFolder);
                    }
                }
            } else {
                if (source != null) {
                    // Create new File content
                    String type = Files.probeContentType(source.toPath());
                    FileContent newContentInputStream = new FileContent(type, source);
//                    File newContent = createNewFile(destFolder, source.getName(), type);
                    // Update existing or create a new file with the new File content
                    DriveRequest<File> request;
                    Optional<File> existingFile = findInFolder(destFolder, source.getName());
                    if (existingFile.isPresent()) {
                        listener.getLogger().printf("Updating existing File %s in %s (%s)%n", source.getName(), destFolder.getName(), destFolder.getId());
                        File newContent = createNewFile(source.getName(), type);
                        request = drive.files()
                            .update(existingFile.get().getId(), newContent, newContentInputStream)
                            .setAddParents(destFolder.getId());
                    } else {
                        listener.getLogger().printf("Creating new File %s in %s (%s)%n", source.getName(), destFolder.getName(), destFolder.getId());
                        File newContent = createNewFile(destFolder, source.getName(), type);
                        request = drive.files()
                            .create(newContent, newContentInputStream);
                    }
                    executeDriveRequest(request, source.getPath());
                }
            }
        } catch (IOException e) {
            listener.error(e.getMessage());
        }
    }

    private void executeDriveRequest(final DriveRequest<File> request, final String filePath) throws IOException {
        MediaHttpUploader httpUploader = request.getMediaHttpUploader();
        httpUploader.setDirectUploadEnabled(false);
        httpUploader.setChunkSize(2 * MB);
        httpUploader.setProgressListener(uploader -> {
            switch (uploader.getUploadState()) {
                case INITIATION_STARTED:
                    listener.getLogger().printf("Start uploading %s%n", filePath);
                    break;
                case MEDIA_IN_PROGRESS:
                    NumberFormat formatter = new DecimalFormat("#0.00");
                    String progress = formatter.format(uploader.getProgress() * 100);
                    listener.getLogger().println("Uploading in progress %" + progress);
                    break;
                case MEDIA_COMPLETE:
                    listener.getLogger().printf("Finished uploading %s%n", filePath);
                    break;
                default:
                    // Ignore other states
                    break;
            }
        });
        request.execute();
    }

    private Optional<File> findInFolder(File parentFolder, String name) {
        listener.getLogger().printf("Searching for %s in %s (%s)%n", name, parentFolder.getName(), parentFolder.getId());
        try {
            String pageToken = null;
            do {
                FileList result = drive.files().list()
                    .setQ(String.format(DRIVE_QUERY_FORMAT, parentFolder.getId()))
                    .setPageToken(pageToken)
                    .execute();
                for (File file : result.getFiles()) {
                    if (file.getName().equals(name)) {
                        listener.getLogger().printf("Found File: %s (%s)%n", file.getName(), file.getId());
                        return Optional.of(file);
                    }
                }
                pageToken = result.getNextPageToken();
            } while (pageToken != null);
        } catch (IOException e) {
            listener.error(e.getMessage());
        }
        return Optional.empty();
    }
    
    private File createNewFolder(File parentFolder, String name) {
        listener.getLogger().printf("Creating new Folder %s in %s (%s)%n", name, parentFolder.getName(), parentFolder.getId());
        File folder = new File();
        folder.setName(name);
        if (!"-".equals(parentFolder.getId())) {
            folder.setParents(Collections.singletonList(parentFolder.getId()));
        }
        folder.setMimeType("application/vnd.google-apps.folder");
        try {
            return drive.files().create(folder)
                .setFields("id, name")
                .execute();
        } catch (IOException e) {
            listener.error("Error creating new Folder", e);
        }
        return null;
    }
    
    private File createNewFile(String name, String type){
        File file = new File();
        file.setName(name);
        file.setMimeType(type);
        return file;
    }
    
    private File createNewFile(File parentFolder, String name, String type){
        File file = createNewFile(name, type);
        if (!parentFolder.getId().isEmpty() && !"-".equals(parentFolder.getId())) {
            file.setParents(Collections.singletonList(parentFolder.getId()));
        }
        return file;
    }
    
    private File controlParent(String destFolderName, String userMail) {
        String[] mails = userMail.split(";");
        try {

            FileList list = drive.files().list().execute();
            for (File file : list.getFiles()) {
                if (destFolderName.equals(file.getName())) {
                    listener.getLogger().printf("Found folder %s%n", file.getName());
                    return file;
                }
            }

            File file = new File();
            file.setName(destFolderName);
            file.setMimeType("application/vnd.google-apps.folder");

            File inserted = drive.files().create(file).execute();

            BatchRequest batch = drive.batch();

            JsonBatchCallback<Permission> callBack = new JsonBatchCallback<Permission>() {
                @Override
                public void onSuccess(Permission permission, HttpHeaders httpHeaders) throws IOException {
                    listener.getLogger().printf("Write permissions set to Folder %s for %s%n", destFolderName, permission.getEmailAddress());
                }

                @Override
                public void onFailure(GoogleJsonError googleJsonError, HttpHeaders httpHeaders) throws IOException {
                    listener.error("Error assigning permissions to Folder " + destFolderName + " : " + googleJsonError.getMessage());
                }
            };

            for (String mail : mails) {
                Permission userPermission = new Permission()
//                    .setValue(mail)
                    .setType("user")
                    .setRole("writer")
                    .setEmailAddress(mail);
                drive.permissions().create(inserted.getId(), userPermission)
                    .setFields("id")
                    .queue(batch, callBack);
            }
            batch.execute();

            return inserted;
        } catch (IOException e) {
            listener.error("Error accessing Google Drive Folder " + destFolderName, e);
        }
        return null;
    }
}

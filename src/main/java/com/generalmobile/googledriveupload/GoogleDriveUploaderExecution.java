package com.generalmobile.googledriveupload;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.jenkins.plugins.credentials.domains.DomainRequirementProvider;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.remoting.RoleChecker;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class GoogleDriveUploaderExecution extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;

    public static final String APPLICATION_NAME = "Jenkins drive uploader";
    private transient GoogleDriveUploader step;

    protected GoogleDriveUploaderExecution(@Nonnull GoogleDriveUploader step, @Nonnull StepContext context) {
        super(context);

        this.step = step;
    }

    @Override
    protected Void run() throws Exception {
        TaskListener listener = getContext().get(TaskListener.class);
        assert listener != null;
        FilePath ws = getContext().get(FilePath.class);
        assert ws != null;
        Run<?, ?> run = getContext().get(Run.class);
        assert run != null;

        try {
            ws.act(new PerformUpload(Paths.get(ws.getRemote()),
                    getAuthorizeCredentials(listener, run),
                    listener, run.getEnvironment(listener),
                    step.getUploadFolder(),
                    step.getDriveFolderName(),
                    step.getSharedDriveName(),
                    step.getUserMail()));
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
            run.setResult(Result.FAILURE);
        }

        return null;
    }

    static protected Set<Path> getUploadFiles(@Nonnull final Path rootPath, @Nonnull String uploadFolderPatterns, @Nonnull final EnvVars env) throws IOException {
        Set<Path> uploadFilePaths = new HashSet<Path>();
        if (!uploadFolderPatterns.isEmpty()) {
            String[] uploadFilePatterns = env.expand(uploadFolderPatterns).split("\\s*,\\s*");
            for (String uploadFilePattern : uploadFilePatterns) {

                //Hack to fix unit tests on Windows
                String pathMatcherPattern = "glob:" + rootPath.toString() + File.separator + uploadFilePattern;
                if (SystemUtils.IS_OS_WINDOWS) pathMatcherPattern = pathMatcherPattern.replace("\\", "\\\\");
                PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(pathMatcherPattern);
                Files.walkFileTree(rootPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (pathMatcher.matches(dir)) {
                            uploadFilePaths.add(dir);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                        if (pathMatcher.matches(path)) {
                            uploadFilePaths.add(path);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } else {
            uploadFilePaths.add(rootPath);
        }
        return uploadFilePaths;
    }

    private GoogleRobotCredentials getAuthorizeCredentials(TaskListener listener, Run<?, ?> build) throws GeneralSecurityException {
        return CredentialsProvider.findCredentialById(step.getCredentialsId(), GoogleRobotCredentials.class, build)
                .forRemote(step.getRequirement());
    }

    public static final class PerformUpload implements Callable<Void, Exception> {

        private final String sharedDriveName;
        private final GoogleRobotCredentials credentials;
        private final TaskListener listener;
        private final String driveFolderName;
        private final String filePath;
        private final EnvVars envVars;
        private final String uploadFolder;
        private final String userEmail;

        public PerformUpload(@Nonnull Path filePath, @Nonnull final GoogleRobotCredentials credentials, @Nonnull final TaskListener listener, @Nonnull final EnvVars envVars, @Nonnull final String uploadFolder, final String driveFolderName, final String sharedDriveName, final String userEmail) {
            this.credentials = credentials;
            this.listener = listener;
            this.sharedDriveName = sharedDriveName;
            this.driveFolderName = driveFolderName;
            this.filePath = filePath.toString();
            this.envVars = envVars;
            this.uploadFolder = uploadFolder;
            this.userEmail = userEmail;
        }

        @Override
        public Void call() throws Exception {
            try {
                Set<Path> uploadPaths = getUploadFiles(Paths.get(filePath), uploadFolder, envVars);
                if (sharedDriveName.isEmpty()) {
                    GoogleDriveManager driveManager = new GoogleDriveManager(getDriveService(credentials), listener);
                    for (Path uploadFilePath : uploadPaths) {
                        driveManager.uploadFolder(uploadFilePath.toFile(), driveFolderName, userEmail);
                    }
                } else {
                    SharedDriveManager driveManager = new SharedDriveManager(getDriveService(credentials), sharedDriveName, listener);
                    for (Path uploadFilePath : uploadPaths) {
                        driveManager.uploadFolderToSharedDrive(uploadFilePath.toFile(), driveFolderName);
                    }
                }
            } catch (Exception e) {
                listener.error("Inner error : " + e.getMessage() != null ? e.getMessage() : " empty?");
                throw e;
            }

            return (Void) null;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // We know by definition that this is the correct role;
            // the callable exists only in this method context.
        }

        private Drive getDriveService(GoogleRobotCredentials credentials) throws GeneralSecurityException, IOException {
            DriveScopeRequirement req = DomainRequirementProvider.of(getClass(), DriveScopeRequirement.class);
            return new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), new JacksonFactory(), credentials.getGoogleCredential(req))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
    }
}

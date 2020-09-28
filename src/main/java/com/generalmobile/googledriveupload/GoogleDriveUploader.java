package com.generalmobile.googledriveupload;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.jenkins.plugins.credentials.domains.DomainRequirementProvider;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang.SystemUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

@RequiresDomain(value = DriveScopeRequirement.class)
public final class GoogleDriveUploader extends Recorder implements SimpleBuildStep, Serializable {

    public static final String APPLICATION_NAME = "Jenkins drive uploader";
    private final String credentialsId;
    private final String driveFolderName;
    private final String uploadFolder;
    private String sharedDriveName = "";
    private String userMail = "";

    @DataBoundConstructor
    public GoogleDriveUploader(String credentialsId, String driveFolderName, String uploadFolder, String userMail) {
        this.credentialsId = checkNotNull(credentialsId);
        this.driveFolderName = checkNotNull(driveFolderName);
        this.uploadFolder = checkNotNull(uploadFolder);
    }

    @DataBoundSetter
    public void setUserMail(String userMail) {
        this.userMail = checkNotNull(userMail);
    }

    @DataBoundSetter
    public void setSharedDriveName(String sharedDriveName) {
        this.sharedDriveName = checkNotNull(sharedDriveName);
    }

    public FormValidation doCheckUserMail(@QueryParameter String value) {
        return FormValidation.error("Not a number");
           /* int at =StringUtils.countOccurrencesOf(value,"@");
            int semicolon =StringUtils.countOccurrencesOf(value,";");

            if (at-1==semicolon)
                return FormValidation.warning("Please check mail again. If you using multi mail please separate each mail with ;");
            else
                return FormValidation.ok();*/

    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getSharedDriveName() {
        return sharedDriveName;
    }

    public String getUploadFolder() {
        return uploadFolder;
    }

    public String getUserMail() {
        return userMail;
    }

    public String getDriveFolderName() {
        return driveFolderName;
    }

    @Override
    @SuppressFBWarnings
    public void perform(@Nonnull final Run<?, ?> run, @Nonnull final FilePath workspace,
                        @Nonnull final Launcher launcher, @Nonnull final TaskListener listener) throws InterruptedException, IOException {

        if ((run.getResult() == Result.FAILURE || run.getResult() == Result.ABORTED)) {
            return;
        }

        try {
            workspace.act(new PerformUpload(Paths.get(workspace.getRemote()), getAuthorizeCredentials(listener, run), listener, run.getEnvironment(listener), uploadFolder, driveFolderName,sharedDriveName, userMail));
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
            run.setResult(Result.FAILURE);
        }
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
        /*listener.getLogger().println("Getting credentials");

        StupidLookup(listener);
        GoogleRobotCredentials creds =
        listener.getLogger().println("Found creds " + (creds != null ? creds.getId() : "none"));

        listener.getLogger().println("CredId " + getCredentialsId());
        listener.getLogger().println("Google " + GoogleRobotCredentials.getById(getCredentialsId()));
        listener.getLogger().println("ForRemote " + GoogleRobotCredentials.getById(getCredentialsId()).forRemote(getRequirement()));
         */
        return CredentialsProvider.findCredentialById(getCredentialsId(), GoogleRobotCredentials.class, build)
                .forRemote(getRequirement());
    }

    private DriveScopeRequirement getRequirement() {
        return DomainRequirementProvider.of(getClass(), DriveScopeRequirement.class);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    @Symbol("googleDriveUpload")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public String getDisplayName() {
            return "Google Drive Uploader";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }
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

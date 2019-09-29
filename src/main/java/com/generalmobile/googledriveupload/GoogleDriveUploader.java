package com.generalmobile.googledriveupload;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.jenkins.plugins.credentials.domains.DomainRequirementProvider;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

@RequiresDomain(value = DriveScopeRequirement.class)
public final class GoogleDriveUploader extends Recorder {

    public static final String APPLICATION_NAME = "Jenkins drive uploader";
    private final String credentialsId;
    private final String driveFolderName;
    private final String uploadFolder;
    private String sharedDriveName = "";
    private String userMail = "";
    private static HttpTransport httpTransport;

    static {
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
    @DataBoundConstructor
    public GoogleDriveUploader(String credentialsId,  String driveFolderName, String uploadFolder, String userMail) {
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
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        try {
            listener.getLogger().println("Google Drive Uploading Plugin Started.");
            String workspace = Objects.requireNonNull(build.getWorkspace()).getRemote();
            if (uploadFolder.length() > 0) {
                if (uploadFolder.startsWith("$")) {
                    workspace += "/" + build.getEnvironment(listener).get(uploadFolder.replace("$", ""));
                } else {
                    workspace += "/" + uploadFolder;
                }
            }
            listener.getLogger().println("Uploading folder: " + workspace);
            if ( sharedDriveName.isEmpty()) {
                GoogleDriveManager driveManager = new GoogleDriveManager(getDriveService(), listener);
                driveManager.uploadFolder(new File(workspace), getDriveFolderName(), userMail);
            } else {
                SharedDriveManager driveManager = new SharedDriveManager(getDriveService(), sharedDriveName, listener);
                driveManager.uploadFolderToSharedDrive(new File(workspace), getDriveFolderName());
            }
        } catch (GeneralSecurityException e) {
            build.setResult(Result.FAILURE);
            return false;
        }

        return true;
    }
    
    private Drive getDriveService() throws GeneralSecurityException {
        return new Drive.Builder(httpTransport, new JacksonFactory(), getAuthorizeCredentials())
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    private Credential getAuthorizeCredentials() throws GeneralSecurityException {
        return GoogleRobotCredentials
            .getById(getCredentialsId())
            .forRemote(getRequirement())
            .getGoogleCredential(getRequirement());
    }

    private DriveScopeRequirement getRequirement() {
        return DomainRequirementProvider.of(getClass(), DriveScopeRequirement.class);
    }

    private String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
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
}

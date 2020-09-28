package com.generalmobile.googledriveupload;

import com.google.common.collect.ImmutableSet;
import com.google.jenkins.plugins.credentials.domains.DomainRequirementProvider;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

@RequiresDomain(value = DriveScopeRequirement.class)
public final class GoogleDriveUploader extends Step {
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

    DriveScopeRequirement getRequirement() {
        return DomainRequirementProvider.of(getClass(), DriveScopeRequirement.class);
    }

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        return new GoogleDriveUploaderExecution(this,stepContext);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        public DescriptorImpl() {}

        @Override
        public String getDisplayName() {
            return "Google Drive Uploader";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, FilePath.class);
        }

        @Override
        public String getFunctionName() {
            return "googleDriveUpload";
        }
    }


}

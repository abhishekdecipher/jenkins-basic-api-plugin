package io.jenkins.plugins;

import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class BasicApiPlugin extends Notifier {

  private final String fileSearchPattern;
  private final boolean failBuildIfNoFiles;

  @DataBoundConstructor
  public BasicApiPlugin(final String fileSearchPattern, final boolean failBuildIfNoFiles) {
    this.fileSearchPattern = fileSearchPattern;
    this.failBuildIfNoFiles = failBuildIfNoFiles;
  }


  public String getFileSearchPattern() {
    return fileSearchPattern;
  }

  public boolean getFailBuildIfNoFiles() {
    return failBuildIfNoFiles;
  }

  @Override
  public boolean perform(final AbstractBuild build, final Launcher launcher, final BuildListener listener) {
    try {
      listener.getLogger().println("Starting Post Build Action");
      FilePath workspace = Objects.requireNonNull(build.getWorkspace());
      if (workspace.isRemote()) {
        workspace = new FilePath(workspace.getChannel(), workspace.getRemote());
      }

      List<String> searchResult = this.searchFiles(workspace);
      if (StringUtils.isBlank(this.getDescriptor().getFilesUploadApiUrl())) {
        listener.getLogger().println("No API URL set for sending the files");
        build.setResult(Result.FAILURE);
        return Boolean.FALSE;
      } else if (searchResult.isEmpty() && failBuildIfNoFiles) {
        listener.getLogger().println("No Files Found!");
        build.setResult(Result.FAILURE);
        return Boolean.FALSE;
      } else if (searchResult.isEmpty()) {
        listener.fatalError("No Files Found!");
        build.setResult(Result.UNSTABLE);
        return Boolean.TRUE;
      } else if (StringUtils.isNotBlank(this.getDescriptor().getFilesUploadApiUrl())) {

        FileUploadUtility uploadUtility = new FileUploadUtility(this.getDescriptor().getFilesUploadApiUrl());
        for (String file : searchResult) {
          listener.getLogger().println("Processing file " + file);
          uploadUtility.addFile(this.getDescriptor().getFilesUploadApiRequestKey(), new File(workspace.getRemote() + File.separator + file));
        }
        listener.getLogger().println("Response from API Server : " + uploadUtility.sendFiles());
      }
      listener.getLogger().println("Finished Post Build Action");
      build.setResult(Result.SUCCESS);
      return Boolean.TRUE;
    } catch (Exception ex) {
      listener.error("Error occurred while performing Post Build Action", ex);
      build.setResult(Result.FAILURE);
      return Boolean.FALSE;
    }
  }


  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  /**
   * Serach the current job workspace for files according to given file pattern (ant pattern)
   *
   * @param workspace : File path of current job workspace
   * @return : files path
   */
  private List<String> searchFiles(FilePath workspace) {
    DirectoryScanner directoryScanner = new DirectoryScanner();
    directoryScanner.setIncludes(new String[]{this.getFileSearchPattern()});
    directoryScanner.setBasedir(workspace.getRemote());
    directoryScanner.setCaseSensitive(Boolean.TRUE);
    directoryScanner.scan();
    return Lists.newArrayList(directoryScanner.getIncludedFiles());
  }

  @Extension
  public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

    private static final String PLUGIN_NAME = "Custom Basic Api plugin";
    private static final String API_URL = "filesUploadApiUrl";
    private static final String API_KEY = "filesUploadApiRequestKey";

    private String filesUploadApiUrl;

    private String filesUploadApiRequestKey;

    public DescriptorImpl() {
      this.load();
    }

    public String getFilesUploadApiUrl() {
      return filesUploadApiUrl;
    }

    public String getFilesUploadApiRequestKey() {
      return filesUploadApiRequestKey;
    }

    /**
     * Save the Global Config
     *
     * @param req
     * @param formData
     * @return Boolean
     * @throws FormException
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject formData)
      throws FormException {
      filesUploadApiUrl = formData.getString(API_URL);
      filesUploadApiRequestKey = formData.getString(API_KEY);
      req.bindJSON(this, formData);
      this.save();
      return super.configure(req, formData);
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return Boolean.TRUE;
    }

    @Override
    public String getDisplayName() {
      return PLUGIN_NAME;
    }


    /**
     * For Validation Of File Pattern Field In Config Properties
     *
     * @param filePattern : fileSearchPattern input text
     * @return FormValidation
     */
    public FormValidation doCheckFilePattern(@QueryParameter String filePattern) {
      if (filePattern.length() == 0)
        return FormValidation.error("Please input correct pattern");
      if (filePattern.trim().isEmpty())
        return FormValidation.error("Please input correct pattern");
      if (filePattern.length() < 4)
        return FormValidation.warning("Isn't the pattern too short?");
      return FormValidation.ok();
    }

    /**
     * For Validation Of Server Url Field In Global Properties
     *
     * @param filesUploadApiUrl        : API server url
     * @param filesUploadApiRequestKey : API key for file upload
     * @return FormValidation
     */
    public FormValidation doCheckServerUrl(@QueryParameter String filesUploadApiUrl, @QueryParameter String filesUploadApiRequestKey) {
      if (StringUtils.isBlank(filesUploadApiUrl) && StringUtils.isBlank(filesUploadApiRequestKey))
        return FormValidation.error("Please input valid server url and api key");
      if (filesUploadApiUrl.length() < 4)
        return FormValidation.warning("Isn't the url too short?");
      return FormValidation.ok();
    }

  }
}
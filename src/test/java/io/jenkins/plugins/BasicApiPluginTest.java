package io.jenkins.plugins;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.ReflectionUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestNotifier;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.Objects;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;

public class BasicApiPluginTest {

  private static final String FILE_PATTERN = "*.xml";
  private static final String API_URL = "http://localhost:8081/upload";
  private static final String API_KEY = "docs";
  private static final boolean FILE_FOUND = Boolean.TRUE;

  @Rule
  public RestartableJenkinsRule restartableJenkinsRule = new RestartableJenkinsRule();

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testConfigRoundTrip() {
    restartableJenkinsRule.then(jenkins -> {
      FreeStyleProject project = jenkins.createFreeStyleProject();
      project.getPublishersList().add(new BasicApiPlugin(FILE_PATTERN, FILE_FOUND));
      project = jenkins.configRoundtrip(project);

      jenkins.assertEqualDataBoundBeans(new BasicApiPlugin(FILE_PATTERN, FILE_FOUND), project.getPublishersList().get(0));
    });
  }

  /**
   * To check Config properties set properly or not
   */
  @Test
  public void testPluginConfig() {
    restartableJenkinsRule.then(jenkins -> {
      FreeStyleProject project = jenkins.createFreeStyleProject();
      BasicApiPlugin basicApiPluginBefore = new BasicApiPlugin(FILE_PATTERN, FILE_FOUND);
      project.getPublishersList().add(basicApiPluginBefore);
      jenkins.submit(jenkins.createWebClient().getPage(project, "configure").getFormByName("config"));
      BasicApiPlugin basicApiPluginAfter = project.getPublishersList().get(BasicApiPlugin.class);
      jenkins.assertEqualBeans(basicApiPluginBefore, basicApiPluginAfter, "failBuildIfNoFiles,fileSearchPattern");
    });
  }

  /**
   * To Check Global config persist after jenkins restart
   */

  @Test
  public void testGlobalConfig() {

    restartableJenkinsRule.then(jenkinsRule -> {
      BasicApiPlugin.DescriptorImpl descriptor = (BasicApiPlugin.DescriptorImpl) jenkinsRule.jenkins.getDescriptor(BasicApiPlugin.class);
      Method filesUploadApiUrlField = ReflectionUtils.getPublicMethodNamed(BasicApiPlugin.DescriptorImpl.class, "getFilesUploadApiUrl");
      Method filesUploadApiRequestKeyField = ReflectionUtils.getPublicMethodNamed(BasicApiPlugin.DescriptorImpl.class, "getFilesUploadApiRequestKey");

      assertNull("Initial value check for filesUploadApiUrl", filesUploadApiUrlField.getDefaultValue());
      assertNull("Initial value check for filesUploadApiRequestKey", filesUploadApiRequestKeyField.getDefaultValue());

      HtmlForm config = jenkinsRule.createWebClient().goTo("configure").getFormByName("config");
      HtmlTextInput filesUploadApiUrl = config.getInputByName("_.filesUploadApiUrl");
      HtmlTextInput filesUploadApiRequestKey = config.getInputByName("_.filesUploadApiRequestKey");
      filesUploadApiUrl.setText(API_URL);
      filesUploadApiRequestKey.setText(API_KEY);
      jenkinsRule.submit(config);
      assert descriptor != null;

      assertEquals("Global config filesUploadApiUrl test", API_URL, descriptor.getFilesUploadApiUrl());
      assertEquals("Global config filesUploadApiRequestKey test", API_KEY, descriptor.getFilesUploadApiRequestKey());
    });


    restartableJenkinsRule.then(r -> {
      BasicApiPlugin.DescriptorImpl descriptorIml = (BasicApiPlugin.DescriptorImpl) r.jenkins.getDescriptor(BasicApiPlugin.class);
      assert descriptorIml != null;

      assertEquals("Global config filesUploadApiUrl values after restart of Jenkins", API_URL, descriptorIml.getFilesUploadApiUrl());
      assertEquals("Global config filesUploadApiRequestKey values after restart of Jenkins", API_KEY, descriptorIml.getFilesUploadApiRequestKey());
    });
  }

  /**
   * To Check the build Result
   */
  @Test
  public void testBuildWithFailWhenNoFilesCheck() {
    restartableJenkinsRule.then(jenkinsRule -> {
      FreeStyleProject project = jenkinsRule.createFreeStyleProject();

      HtmlForm config = jenkinsRule.createWebClient().goTo("configure").getFormByName("config");
      HtmlTextInput filesUploadApiUrl = config.getInputByName("_.filesUploadApiUrl");
      HtmlTextInput filesUploadApiRequestKey = config.getInputByName("_.filesUploadApiRequestKey");
      filesUploadApiUrl.setText(API_URL);
      filesUploadApiRequestKey.setText(API_KEY);
      jenkinsRule.submit(config);

      project.getPublishersList().add(new BasicApiPlugin(FILE_PATTERN, Boolean.TRUE));
      Objects.requireNonNull(project.scheduleBuild2(0).get().getWorkspace()).createTextTempFile("sample", ".xml", "sample");
      project.getPublishersList().add(new TestNotifier() {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
          listener.getLogger().println("Starting");
          listener.getLogger().println("OK");
          return Boolean.TRUE;
        }
      });
      FreeStyleBuild buildResult = jenkinsRule.buildAndAssertSuccess(project);
      jenkinsRule.assertLogContains("Starting", buildResult);
      jenkinsRule.assertLogContains("OK", buildResult);

    });
  }

  @Test
  public void testFilePattern() {
    assertEquals(FILE_PATTERN, new BasicApiPlugin(FILE_PATTERN, FILE_FOUND).getFileSearchPattern());
  }

}
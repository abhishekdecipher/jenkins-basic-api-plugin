package io.jenkins.plugins;

import hidden.jth.org.apache.http.client.methods.HttpPost;
import hidden.jth.org.apache.http.entity.ContentType;
import hidden.jth.org.apache.http.entity.mime.HttpMultipartMode;
import hidden.jth.org.apache.http.entity.mime.MultipartEntityBuilder;
import hidden.jth.org.apache.http.entity.mime.content.FileBody;
import hidden.jth.org.apache.http.impl.client.CloseableHttpClient;
import hidden.jth.org.apache.http.impl.client.HttpClientBuilder;
import hidden.jth.org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class FileUploadUtility {

  private static final Logger LOGGER = Logger.getLogger(FileUploadUtility.class);

  private MultipartEntityBuilder builder;
  private HttpPost post;

  /**
   * This constructor initializes a new HTTP POST request with content type
   * is set to multipart/form-data
   *
   * @param requestURL
   */
  public FileUploadUtility(String requestURL) {
    LOGGER.info("Building FileuploadUtility");
    this.post = new HttpPost(requestURL);
    this.builder = MultipartEntityBuilder.create();

  }

  /**
   * Adds a upload file section to the request
   *
   * @param fieldName    name attribute
   * @param fileToUpload a File to be uploaded
   */
  public void addFile(String fieldName, File fileToUpload) {
    FileBody fileBody = new FileBody(fileToUpload, ContentType.DEFAULT_BINARY);
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
    builder.addPart(fieldName, fileBody);
  }


  public String sendFiles() throws IOException {
    LOGGER.info("sending request");
    CloseableHttpClient client = HttpClientBuilder.create().build();
    post.setEntity(builder.build());
    return EntityUtils.toString(client.execute(post).getEntity());
  }

}

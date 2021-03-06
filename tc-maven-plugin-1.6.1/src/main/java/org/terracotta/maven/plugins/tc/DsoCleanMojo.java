/*
 * All content copyright (c) 2003-2009 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
/**
 *
 */
package org.terracotta.maven.plugins.tc;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.LogManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.xmlbeans.XmlObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.tc.config.schema.CommonL1Config;
import com.tc.config.schema.CommonL2Config;
import com.tc.config.schema.Config;
import com.tc.config.schema.dynamic.ParameterSubstituter;
import com.tc.config.schema.setup.ConfigurationSetupException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Clean DSO data folder and logs
 * 
 * @author Eugene Kuleshov
 * @goal clean
 * @phase clean
 * @configurator override
 */
public class DsoCleanMojo extends AbstractDsoServerMojo {

  /**
   * Fail build on error
   * 
   * @parameter expression="${failOnError}" default-value="true"
   */
  private boolean failOnError;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    CommonL1Config clientConfig;
    CommonL2Config serverConfig;
    try {
      serverConfig = getServerConfig(serverName);
      clientConfig = getClientConfig();
    } catch (ConfigurationSetupException ex) {
      throw new MojoExecutionException("Unable to read server configuration", ex);
    }

    // make sure no server is running
    String status = null;
    try {
      status = getServerState(getJMXUrl(serverConfig));
      getLog().info("Server Status: " + status);
    } catch (Exception ex) {
      // expected error when server is not running
    }
    if ("OK".equals(status)) {
      stop(true);
    }

    LogManager.shutdown(); // a hack to close all the logs opened while reading configuration

    {
      getLog().info("------------------------------------------------------------------------");

      String logsValue = getValue(clientConfig, "logs");
      getLog().info("Client logs directory template: " + logsValue);

      String statsValue = getValue(clientConfig, "statistics");
      getLog().info("Client stat directory template: " + statsValue);

      List<Startable> startables = getStartables();
      if(startables.isEmpty()) {
        deleteClientFolders(clientConfig, logsValue, statsValue);
      } else {
        for (Startable startable : startables) {
          String nodeName = startable.getNodeName();
          System.setProperty("tc.nodeName", nodeName);
          deleteClientFolders(clientConfig, logsValue, statsValue);
          System.getProperties().remove("tc.nodeName");
        }
      }
    }

    {
      String dataValue = getValue(serverConfig, "data");
      String data = getLocation(dataValue, serverConfig.dataPath());

      getLog().info("------------------------------------------------------------------------");
      getLog().info("Server data directory template: " + dataValue);
      getLog().info("Deleting server data directory: " + data);
      delete("data", data);
    }

    {
      String logsValue = getValue(serverConfig, "logs");
      String logs = getLocation(logsValue, serverConfig.logsPath());

      getLog().info("------------------------------------------------------------------------");
      getLog().info("Server logs directory template: " + logsValue);
      getLog().info("Deleting server logs directory: " + logs);
      delete("logs", logs);
    }
  }

  private void deleteClientFolders(CommonL1Config clientConfig, String logsValue, String statsValue)
      throws MojoFailureException {
    {
      String logs = getLocation(logsValue, clientConfig.logsPath());
      getLog().info("Deleting client logs directory: " + logs);
      delete("logs", logs);
    }
  }

  private String getLocation(String template, File file) {
    if (template == null) {
      return file.getAbsolutePath();
    } else {
      return ParameterSubstituter.substitute(template);
    }
  }

  private void delete(String name, String dir) throws MojoFailureException {
    try {
      FileUtils.deleteDirectory(new File(dir));
    } catch (IOException ex) {
      String msg = "Can't delete " + name + " directory " + dir;
      getLog().error(msg, ex);
      if (failOnError) {
        throw new MojoFailureException(msg);
      }
    }
  }

  private String getValue(Config config, String elementName) {
    XmlObject bean = config.getBean();
    if(bean==null) {
      return null;
    }

    Node node = bean.getDomNode();
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE && elementName.equals(child.getLocalName())) {
        Node textNode = child.getFirstChild();
        if (textNode.getNodeType() == Node.TEXT_NODE) {
          return ((Text) textNode).getData();
        }
      }
    }
    return null;
  }

}

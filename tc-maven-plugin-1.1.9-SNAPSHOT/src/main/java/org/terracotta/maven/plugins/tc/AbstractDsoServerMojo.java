/*
 * All content copyright (c) 2003-2009 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package org.terracotta.maven.plugins.tc;

import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.terracotta.maven.plugins.tc.cl.CommandLineException;
import org.terracotta.maven.plugins.tc.cl.CommandLineUtils;
import org.terracotta.maven.plugins.tc.cl.Commandline;

import com.tc.admin.TCStop;
import com.tc.server.TCServerMain;

public abstract class AbstractDsoServerMojo extends AbstractDsoMojo {
  /**
   * Start DSO Server in a separate JVM
   * 
   * @parameter expression="${spawnServer}" default-value="true"
   */
  private boolean spawnServer;

  /**
   * DSO Server name to start
   * 
   * @parameter expression="${serverName}"
   * @optional
   */
  protected String serverName;

  public AbstractDsoServerMojo() {
  }

  public AbstractDsoServerMojo(AbstractDsoMojo mojo) {
    super(mojo);
  }

  protected void start() throws MojoExecutionException {
    String jmxUrl = null;

    try {
      jmxUrl = getJMXUrl(getServerConfig(serverName));
    } catch (Exception e) {
      getLog().error("Failed to verify DSO server status", e);
      return;
    }

    try {
      String status = getServerStatus(jmxUrl);
      if (status.startsWith("OK")) {
        getLog().info("Server already started: " + status);
        return;
      }
//    } catch (ConfigurationSetupException cse) {
//      getLog().error("Failed to verify DSO server status", cse);
//      return;
    } catch (IOException ioe) {
      // ok - we expect to not connect
    }

    try {
      Commandline cmd = createCommandLine();

      if(jvmargs!=null) {
        cmd.createArgument().setLine(jvmargs);
      }
      
      cmd.createArgument().setValue("-Dtc.classpath=" + createPluginClasspathAsFile());

      cmd.createArgument().setValue("-cp");
      cmd.createArgument().setValue(quoteIfNeeded(createPluginClasspath()));

      cmd.createArgument().setValue(TCServerMain.class.getName());

      if (config.exists()) {
        getLog().debug("tc-config file " + config.getAbsolutePath());
        cmd.createArgument().setValue("-f");
        cmd.createArgument().setFile(config);
      } else {
        getLog().debug("tc-config file doesn't exists " + config.getAbsolutePath());
      }

      if (serverName != null && serverName.length() > 0) {
        cmd.createArgument().setValue("-n");
        cmd.createArgument().setValue(serverName);
        getLog().debug("server serverName = " + serverName);
      }

      ForkedProcessStreamConsumer streamConsumer = new ForkedProcessStreamConsumer("dso start");

      getLog().info("------------------------------------------------------------------------");
      getLog().info("Starting DSO Server");
      getLog().debug("cmd: " + cmd);
      
      Process p = CommandLineUtils.executeCommandLine(cmd, null, streamConsumer, streamConsumer, spawnServer);
      getLog().info("OK");

      if (jmxUrl != null) {
        long time = System.currentTimeMillis();
        String status = null;
        while ((System.currentTimeMillis() - time) < 30 * 1000L && status == null && isRunning(p)) {
          try {
            status = getServerStatus(jmxUrl);
          } catch (IOException ioe) {
            // ok - we're waiting for the connection
          }
        }
        getLog().info("DSO Server status: " + status);
      }
      
      if(!isRunning(p)) {
        int rc = p.exitValue();
        if(rc!=0) {
          String msg = "Failed to start DSO server. Process return code " + rc;
          getLog().error(msg);
          throw new MojoExecutionException(msg);
        }
      }

    } catch (CommandLineException e) {
      String msg = "Failed to start DSO server";
      getLog().error(msg, e);
      throw new MojoExecutionException(msg, e);
    }
  }

  protected void stop() { stop(false); }

  protected void stop(boolean wait) {
    try {
      Commandline cmd = createCommandLine();
      cmd.createArgument().setValue("-Dtc.classpath=" + createPluginClasspathAsFile());

      cmd.createArgument().setValue("-cp");
      cmd.createArgument().setValue(quoteIfNeeded(createPluginClasspath()));
      
      cmd.createArgument().setValue(TCStop.class.getName());
      
      if(config.exists()) {
        cmd.createArgument().setValue("-f");
        cmd.createArgument().setFile(config);
        getLog().debug("tc-config file  = " + config.getAbsolutePath());
      }

      if (serverName != null && serverName.length() > 0) {
        cmd.createArgument().setValue("-n");
        cmd.createArgument().setValue(serverName);
        getLog().debug("server name = " + serverName);
      }

      ForkedProcessStreamConsumer streamConsumer = new ForkedProcessStreamConsumer("dso stop");

      getLog().info("------------------------------------------------------------------------");
      getLog().info("Stopping DSO Server");
      getLog().debug("cmd: " + cmd);
      
      CommandLineUtils.executeCommandLine(cmd, null, streamConsumer, streamConsumer, spawnServer);

      if (wait) {
        getLog().info("Waiting for server to stop...");
        String jmxUrl = getJMXUrl(getServerConfig(serverName));
        String status = null;
        long time = System.currentTimeMillis();
        do {
          try {
            status = getServerStatus(jmxUrl);
          } catch(Throwable ex) {
            status = null;
          }
        }  while ((System.currentTimeMillis() - time) < 30 * 1000L && status != null);
      }
      getLog().info("OK");
    } catch (Exception e) {
      getLog().error("Error stopping DSO server", e);
    }
  }

  private boolean isRunning(Process p) {
    try {
      p.exitValue();
      return false;
    } catch (IllegalThreadStateException e) {
      return true;
    }
  }
  
  //setters for the lifecycle simulation 
  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  public void setSpawnServer(boolean spawn) {
    this.spawnServer = spawn;
  }
}

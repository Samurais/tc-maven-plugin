/*
 * All content copyright (c) 2003-2009 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
/*
 * 
 */
package org.terracotta.maven.plugins.tc;

import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import EDU.oswego.cs.dl.util.concurrent.BrokenBarrierException;
import EDU.oswego.cs.dl.util.concurrent.CyclicBarrier;

/**
 * Run DSO processes
 * 
 * @author Eugene Kuleshov
 * 
 * @goal run
 */
public class DsoRunMojo extends AbstractDsoRunMojo {
  
  protected void onExecute() throws MojoExecutionException, MojoFailureException {
    getLog().info("------------------------------------------------------------------------");
    resolveModuleArtifacts(getAdditionalModules());

    Set<String> activeNodes = getActiveNodes();

    getLog().info("Starting DSO nodes " + (activeNodes.size()==0 ? "" : this.activeNodes) + " in mode '" + this.mode + "'" );

    List<Startable> startables = getStartables();

    int numberofNodes = activeNodes.size() > 0 ? activeNodes.size() : startables.size();
    CyclicBarrier barrier = new CyclicBarrier(numberofNodes + 1);

    for (Startable startable : startables) {
      if (activeNodes.size() == 0 || activeNodes.contains(startable.getNodeName())) {
        fork(startable, barrier);
      }
    }

    if(waitForCompletion()) {
      getLog().info("------------------------------------------------------------------------");
      getLog().info("Waiting completion of the DSO process");
    }
    
    try {
      barrier.barrier();
    } catch (BrokenBarrierException ex) {
      getLog().error(ex);
    } catch (InterruptedException ex) {
      getLog().error(ex);
    }

    if(waitForCompletion()) {
      getLog().info("DSO processes finished");
    }
  }

  private void fork(final Startable startable, final CyclicBarrier barrier) {
    getLog().info("Starting node " + startable.getNodeName() + ": " + startable.toString());
    new Thread() {
      public void run() {
        try {
          startable.start(waitForCompletion());
        } finally {
          getLog().info("Finished node " + startable.getNodeName());
          try {
            barrier.barrier();
          } catch (BrokenBarrierException ex) {
            getLog().error(ex);
          } catch (InterruptedException ex) {
            getLog().error(ex);
          }
        }
      }
    }.start();

    // Thread.yield();
  }

}

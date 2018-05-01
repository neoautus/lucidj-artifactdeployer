/*
 * Copyright 2018 NEOautus Ltd. (http://neoautus.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.lucidj.artdeployer;

import api.lucidj.artdeployer.Artifact;
import api.lucidj.artdeployer.ArtifactDeployer;
import api.lucidj.artdeployer.BundleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Context;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;

@Component (immediate = true, publicFactory = false)
@Instantiate
public class DeploymentScanner implements Runnable
{
    private final static Logger log = LoggerFactory.getLogger (DeploymentScanner.class);

    @Context
    private BundleContext context;

    @Requires
    private ArtifactDeployer artifactDeployer;

    @Requires
    private BundleManager bundleManager;

    private Map<String, Exception> troubled_artifacts = new HashMap<> ();

    private String deploy_dir_config;
    private String watched_dir_uri;
    private File watched_dir_file;
    private Thread poll_thread;
    private int thread_poll_ms = 1000;

    private void poll_repository_for_updates_and_removals ()
    {
        Map<Bundle, Properties> bundles = bundleManager.getBundles ();

        for (Map.Entry<Bundle, Properties> bundle_entry: bundles.entrySet ())
        {
            Bundle bundle = bundle_entry.getKey ();
            String source = bundleManager.getBundleProperty (bundle, BundleManager.BND_SOURCE, null);

            if (source == null || !source.startsWith (watched_dir_uri))
            {
                // Not managed by us
                continue;
            }

            Artifact instance = artifactDeployer.getArtifact (bundle);

            if (instance == null)
            {
                // Not managed by us
                continue;
            }

            if (bundleManager.getManifest (source) == null)
            {
                // The bundle probably was removed
                instance.uninstall ();
            }
            else // Bundle file exists, check for changes
            {
                // We only refresh if the bundle is active
                if (bundle.getState () == Bundle.ACTIVE)
                {
                    try
                    {
                        // Refresh the artifact, but ignore if the DeploymentEngine is not available
                        instance.refresh ();
                    }
                    catch (IllegalStateException ignore) {};
                }
            }
        }
    }

    private void locate_added_bundles ()
    {
        File[] package_list = watched_dir_file.listFiles ();

        if (package_list == null)
        {
            return;
        }

        for (File package_file: package_list)
        {
            String package_uri = package_file.toURI ().toString ();

            log.debug ("INSTALL Scanning {} -> {}", package_uri, package_file);

            Artifact instance = artifactDeployer.getArtifact (package_uri);

            if (instance == null) // The bundle isn't installed yet
            {
                try
                {
                    artifactDeployer.installArtifact (package_uri);
                    troubled_artifacts.remove (package_uri); // Just in case
                }
                catch (Exception e)
                {
                    if (!troubled_artifacts.containsKey (package_uri))
                    {
                        // Show only first time exceptions
                        log.warn ("{}", e.getMessage ());
                    }

                    // Store all last exceptions for every troublesome artifact
                    troubled_artifacts.put (package_uri, e);
                }
            }
        }
    }

    @Validate
    private void validate ()
    {
        // TODO: To be clear, this is crappy. Use ConfigAdmin asap. For now does about the same as Felix main().

        // Try the main property
        if ((deploy_dir_config = context.getProperty (Constants.ARTIFACT_DEPLOY_DIR_PROPERTY)) == null)
        {
            // We only build a deploy_dir_config if ARTIFACT_DEPLOY_DIR_PROPERTY is NOT set.
            // Our mission is leave a reasonable non null deploy_dir_config.
            String system_home = context.getProperty ("system.home");
            deploy_dir_config =
                ((system_home != null)? system_home: ".")
                + "/"
                + Constants.ARTIFACT_DEPLOY_DIR_VALUE;
        }

        // Start things
        poll_thread = new Thread (this);
        poll_thread.setName (this.getClass ().getSimpleName ());
        poll_thread.start ();
        log.info ("DeploymentScanner configured dir: {}", deploy_dir_config);
    }

    @Invalidate
    private void invalidate ()
    {
        try
        {
            // Stop things, wait at most 10 secs for clean stop
            poll_thread.interrupt ();
            poll_thread.join (10000);
        }
        catch (InterruptedException ignore) {};
        log.info ("DeploymentScanner stopped");
    }

    @Override // Runnable
    public void run ()
    {
        long last_complaint = 0;
        long complain_interval = 60 * 5 * 1000;     // Complain every 5 minutes

        while (!poll_thread.isInterrupted ())
        {
            try
            {
                // Check whether current scanning dir is still valid
                if (watched_dir_file != null)
                {
                    // We can have race conditions below, however this check helps a lot the lone operator
                    if (!watched_dir_file.exists() || !watched_dir_file.canRead())
                    {
                        watched_dir_file = null;
                        log.warn ("DeploymentScanner NOT started: Directory {} is missing or unreadable", deploy_dir_config);
                    }
                }

                // Try to validate the configured path to use as scan directory
                if (watched_dir_file == null)
                {
                    File dir = new File (deploy_dir_config);

                    if (dir.exists () && dir.canRead ())
                    {
                        watched_dir_file = dir;
                        watched_dir_uri = watched_dir_file.toURI ().toString ();
                        log.info ("DeploymentScanner started: Scanning directory {}", watched_dir_file);
                    }
                }

                // Do your job
                if (watched_dir_file != null)
                {
                    poll_repository_for_updates_and_removals ();
                    locate_added_bundles ();
                }
                else
                {
                    // Don't be too silent about problems
                    if (last_complaint + complain_interval < System.currentTimeMillis ())
                    {
                        log.warn ("Missing directory {}: {}",
                            Constants.ARTIFACT_DEPLOY_DIR_PROPERTY, deploy_dir_config);
                        last_complaint = System.currentTimeMillis ();
                    }
                }

                synchronized (this)
                {
                    log.debug ("Sleeping for {}ms", thread_poll_ms);
                    wait (thread_poll_ms);
                }
            }
            catch (InterruptedException e)
            {
                // Interrupt status is clear, we should break loop
                break;
            }
            catch (Throwable t)
            {
                try
                {
                    // This will fail if this bundle is uninstalled (zombie)
                    context.getBundle ();
                }
                catch (IllegalStateException e)
                {
                    // This bundle has been uninstalled, exiting loop
                    break;
                }
                log.error ("Artifact deployment exception", t);
            }
        }
    }
}

// EOF

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
import api.lucidj.artdeployer.DeploymentEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.apache.felix.ipojo.annotations.Bind;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Context;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Unbind;
import org.apache.felix.ipojo.annotations.Validate;

@Component (immediate = true, publicFactory = false)
@Instantiate
@Provides (specifications = ArtifactDeployer.class)
public class DefaultArtifactDeployer implements ArtifactDeployer
{
    private final static Logger log = LoggerFactory.getLogger (DefaultArtifactDeployer.class);

    @Context
    private BundleContext context;

    private Map<String, DeploymentEngine> deployment_engines = new ConcurrentHashMap<> ();
    private Map<Bundle, Artifact> bundle_to_instance = new ConcurrentHashMap<> (); // TODO: REMOVE THIS
    private Map<String, Artifact> location_to_instance = new ConcurrentHashMap<>();

    private File get_valid_file (String location)
    {
        // TODO: MOVE THIS TO DeploymentEngine.validBundle()
        if (location.startsWith ("reference:"))
        {
            location = location.substring ("reference:".length ());
        }

        try
        {
            File f = new File (new URI (location));

            if (f.exists () && f.canRead ())
            {
                return (f);
            }
        }
        catch (Exception ignore) {};
        return (null);
    }

    private DeploymentEngine find_deployment_engine (String location)
    {
        DeploymentEngine found_engine = null;
        int level, found_level = 0;

        for (DeploymentEngine engine: deployment_engines.values ())
        {
            if ((level = engine.compatibleArtifact (location)) > found_level)
            {
                found_engine = engine;
                found_level = level;
            }
        }
        return (found_engine);
    }

    @Override // ArtifactDeployer
    public Artifact installArtifact (String location, boolean start_transient)
        throws Exception
    {
        File bundle_file = get_valid_file (location);

        if (bundle_file == null)
        {
            throw (new Exception ("Invalid artifact: " + location));
        }

        DeploymentEngine deployment_engine = find_deployment_engine (location);

        if (deployment_engine == null)
        {
            throw (new Exception ("Deployer service not found for: " + location));
        }

        // These properties will be stored alongside the bundle and other internal properties
        Properties properties = new Properties ();
        properties.setProperty (BundleManager.BND_SOURCE, location);
        properties.setProperty (Constants.PROP_DEPLOYMENT_ENGINE, deployment_engine.getEngineName ());
        properties.setProperty (Constants.PROP_BUNDLE_START,
            start_transient? Constants.BUNDLE_START_TRANSIENT: Constants.BUNDLE_START_NORMAL);

        // Install bundle!
        Artifact new_deploy = deployment_engine.install (location, properties);
        bundle_to_instance.put (new_deploy.getMainBundle (), new_deploy);
        location_to_instance.put (location, new_deploy);

        // Register the bundle controller
        Dictionary<String, Object> props = new Hashtable<>();
        props.put ("@location", location);
        props.put ("@engine", deployment_engine.getEngineName ());
        props.put ("@bundleid", new_deploy.getMainBundle ().getBundleId ());
        props.put ("@bsn", new_deploy.getMainBundle ().getSymbolicName ());
        props.put ("@bundle_start", properties.getProperty (Constants.PROP_BUNDLE_START));
        context.registerService (Artifact.class, new_deploy, props);
        return (new_deploy);
    }

    @Override
    public Artifact installArtifact (String location)
        throws Exception
    {
        return (installArtifact (location, false));
    }

    @Override // ArtifactDeployer
    public Artifact getArtifact (Bundle bundle)
    {
        return (bundle_to_instance.get (bundle));
    }

    @Override // ArtifactDeployer
    public Artifact getArtifact (String location)
    {
        return (location_to_instance.get (location));
    }

    @Bind (aggregate=true, optional=true, specification = DeploymentEngine.class)
    private void bindDeploymentEngine (DeploymentEngine engine)
    {
        log.info ("Adding deployment engine: {}", engine.getEngineName ());
        deployment_engines.put (engine.getEngineName (), engine);
    }

    @Unbind
    private void unbindDeploymentEngine (DeploymentEngine engine)
    {
        log.info ("Removing deployment engine: {}", engine.getEngineName ());
        deployment_engines.remove (engine.getEngineName ());
    }

    @Validate
    private void validate ()
    {
        log.info ("DefaultArtifactDeployer started");
    }

    @Invalidate
    private void invalidate ()
    {
        log.info ("DefaultArtifactDeployer stopped");
    }
}

// EOF

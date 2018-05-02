/*
 * Copyright 2017 NEOautus Ltd. (http://neoautus.com)
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

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.lucidj.api.artdeployer.Artifact;
import org.lucidj.api.artdeployer.BundleManager;
import org.osgi.framework.Bundle;

// TODO: USE ServiceObject
public class DefaultDeploymentInstance implements Artifact
{
    private BundleManager bundleManager;

    private Bundle main_bundle;
    private URI location_uri;

    public DefaultDeploymentInstance (BundleManager bundleManager)
    {
        this.bundleManager = bundleManager;
    }

    @Override
    public String getMimeType ()
    {
        return ("application/x-jar");
    }

    @Override
    public URI getLocation ()
    {
        return (location_uri);
    }

    @Override
    public URI getLocation (String entry_path)
    {
        Path source_path = Paths.get (location_uri).resolve (entry_path);
        return (source_path == null? null: source_path.toUri ());
    }

    @Override
    public Bundle getMainBundle ()
    {
        return (main_bundle);
    }

    @Override
    public int getState ()
    {
        // We handle only simple bundles
        return (main_bundle.getState ());
    }

    @Override
    public int getExtState ()
    {
        return (Artifact.STATE_EX_NONE);
    }

    @Override
    public Bundle install (String location, Properties properties)
        throws Exception
    {
        location_uri = new URI (location);
        main_bundle = bundleManager.installBundle (location, properties);
        return (main_bundle);
    }

    @Override
    public boolean open ()
    {
        return (true);
    }

    @Override
    public boolean close ()
    {
        return (true);
    }

    @Override
    public boolean update ()
    {
        return (bundleManager.updateBundle (main_bundle));
    }

    @Override
    public boolean refresh ()
    {
        return (bundleManager.refreshBundle (main_bundle));
    }

    @Override
    public boolean uninstall ()
    {
        return (bundleManager.uninstallBundle (main_bundle));
    }
}

// EOF

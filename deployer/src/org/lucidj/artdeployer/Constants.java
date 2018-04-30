/*
 * Copyright 2016 NEOautus Ltd. (http://neoautus.com)
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

public interface Constants
{
    String PROP_DEPLOYMENT_ENGINE  = ".Artifact-Deployment-Engine";
    String PROP_LOCATION           = ".Artifact-Location";
    String PROP_LAST_MODIFIED      = ".Artifact-Last-Modified";
    String PROP_BUNDLE_STATE       = ".Artifact-Bundle-State";
    String PROP_BUNDLE_STATE_HUMAN = ".Artifact-Bundle-State-Human";
    String PROP_BUNDLE_START       = ".Artifact-Bundle-Start";

    String BUNDLE_START_TRANSIENT  = "transient";
    String BUNDLE_START_NORMAL     = "normal";
}

// EOF

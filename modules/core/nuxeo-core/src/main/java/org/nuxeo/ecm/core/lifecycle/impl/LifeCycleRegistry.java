/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.core.lifecycle.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.lifecycle.LifeCycle;
import org.nuxeo.ecm.core.lifecycle.LifeCycleState;
import org.nuxeo.ecm.core.lifecycle.extensions.LifeCycleDescriptor;
import org.nuxeo.runtime.model.ContributionFragmentRegistry;

/**
 * Registry for life cycles
 *
 * @since 5.6
 */
public class LifeCycleRegistry extends ContributionFragmentRegistry<LifeCycleDescriptor> {

    private static final Logger log = LogManager.getLogger(LifeCycleRegistry.class);

    protected volatile Map<String, LifeCycle> lookup;

    @Override
    public String getContributionId(LifeCycleDescriptor contrib) {
        return contrib.getName();
    }

    @Override
    public void contributionUpdated(String id, LifeCycleDescriptor contrib, LifeCycleDescriptor newOrigContrib) {
        log.info("Registering lifecycle: {}", contrib::getName);
        lookup = null;
    }

    @Override
    public void contributionRemoved(String id, LifeCycleDescriptor lifeCycleDescriptor) {
        log.info("Unregistering lifecycle: {}", lifeCycleDescriptor::getName);
        lookup = null;
    }

    @Override
    public LifeCycleDescriptor clone(LifeCycleDescriptor orig) {
        return orig.clone();
    }

    @Override
    public void merge(LifeCycleDescriptor src, LifeCycleDescriptor dst) {
        dst.merge(src);
    }

    // API

    public LifeCycle getLifeCycle(String name) {
        return lookup().get(name);
    }

    public Collection<LifeCycle> getLifeCycles() {
        return lookup().values();
    }

    /**
     * Returns a life cycle instance out of the life cycle configuration.
     */
    public LifeCycle getLifeCycle(LifeCycleDescriptor desc) {
        String name = desc.getName();
        String initialStateName = desc.getInitialStateName();
        String defaultInitialStateName = desc.getDefaultInitialStateName();
        if (initialStateName != null) {
            defaultInitialStateName = initialStateName;
            log.warn(
                    "Lifecycle registration of default initial state has changed, change initial=\"{}\" to defaultInitial=\"{}\" in lifecyle '{}' definition",
                    defaultInitialStateName, defaultInitialStateName, name);
        }
        boolean defaultInitialStateFound = false;
        Collection<String> initialStateNames = new HashSet<>();
        Collection<LifeCycleState> states = desc.getStates();
        for (LifeCycleState state : states) {
            String stateName = state.getName();
            if (defaultInitialStateName.equals(stateName)) {
                defaultInitialStateFound = true;
                initialStateNames.add(stateName);
            }
            if (state.isInitial()) {
                initialStateNames.add(stateName);
            }
        }
        if (!defaultInitialStateFound) {
            log.error("Default initial state {} not found on lifecycle {}", defaultInitialStateName, name);
        }
        return new LifeCycleImpl(name, defaultInitialStateName, initialStateNames, states, desc.getTransitions());
    }

    protected Map<String, LifeCycle> lookup() {
        if (lookup == null) {
            synchronized (this) {
                if (lookup == null) {
                    Map<String, LifeCycle> tmpLookup = new HashMap<>();
                    for (var desc : toMap().values()) {
                        if (desc.isEnabled()) {
                            tmpLookup.put(desc.getName(), getLifeCycle(desc));
                        }
                    }
                    lookup = tmpLookup;
                }
            }
        }
        return lookup;
    }

}

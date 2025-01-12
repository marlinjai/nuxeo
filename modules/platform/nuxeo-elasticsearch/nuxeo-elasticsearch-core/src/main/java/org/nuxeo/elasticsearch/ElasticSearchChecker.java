/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */
package org.nuxeo.elasticsearch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.nuxeo.elasticsearch.api.ESClient;
import org.nuxeo.elasticsearch.api.ESClientFactory;
import org.nuxeo.elasticsearch.client.ESRestClientFactory;
import org.nuxeo.elasticsearch.config.ElasticSearchClientConfig;
import org.nuxeo.launcher.config.ConfigurationException;
import org.nuxeo.launcher.config.ConfigurationHolder;
import org.nuxeo.launcher.config.backingservices.BackingChecker;

/**
 * @since 11.3
 */
public class ElasticSearchChecker implements BackingChecker {

    private static final Logger log = LogManager.getLogger(ElasticSearchChecker.class);

    protected static final String ELASTIC_ENABLED_PROP = "elasticsearch.enabled";

    protected static final String ELASTIC_REST_CLIENT_PROP = "elasticsearch.client";

    protected static final String ADDRESS_LIST_OPT = "addressList";

    protected static final String CONFIG_NAME = "elasticsearch-config.xml";

    @Override
    public boolean accepts(ConfigurationHolder configHolder) {
        // not using Boolean.parseValue on purpose, only 'true' must trigger the checker
        if (!"true".equals(configHolder.getProperty(ELASTIC_ENABLED_PROP))) {
            log.debug("Checker skipped because elasticsearch is disabled");
            return false;
        }
        if (!"RestClient".equals(configHolder.getProperty(ELASTIC_REST_CLIENT_PROP))) {
            log.debug("Checker skipped because not using a rest client");
            return false;
        }
        log.debug("Checker accepted");
        return true;
    }

    @Override
    public void check(ConfigurationHolder configHolder) throws ConfigurationException {
        ElasticSearchClientConfig config = getDescriptor(configHolder, CONFIG_NAME, ElasticSearchClientConfig.class,
                // avoid XMap to fail when trying to load class value by removing class attribute
                content -> content.replace("class=", "ignore="));
        String addressList = config.getOption(ADDRESS_LIST_OPT);
        if (addressList == null || addressList.isEmpty()) {
            log.debug("Elasticsearch config check skipped on embedded configuration");
            return;
        }
        log.debug("Check elastic config: {}", config);
        ClusterHealthStatus status = getHealthStatus(config);
        switch (status) {
        case GREEN:
        case YELLOW:
            log.debug("Check is ok, cluster health is {}", status);
            return;
        default:
            throw new ConfigurationException("Elasticsearch cluster is not healthy: " + status);
        }
    }

    protected ClusterHealthStatus getHealthStatus(ElasticSearchClientConfig config) throws ConfigurationException {
        try (ESClient client = getClient(config)) {
            return client.getHealthStatus(null);
        } catch (Exception e) {
            throw new ConfigurationException(
                    "Unable to connect to Elasticsearch: " + config.getOption(ADDRESS_LIST_OPT), e);
        }
    }

    protected ESClient getClient(ElasticSearchClientConfig config) {
        ESClientFactory clientFactory = new ESRestClientFactory();
        return clientFactory.create(null, config);
    }
}

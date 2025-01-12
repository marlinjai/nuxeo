/*
 * (C) Copyright 2017 Nuxeo SA (http://nuxeo.com/) and others.
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
package org.nuxeo.elasticsearch.core;

import static org.nuxeo.runtime.RuntimeMessage.Level.WARNING;
import static org.nuxeo.runtime.RuntimeMessage.Source.CODE;

import java.io.Closeable;
import java.io.IOException;
import java.net.BindException;
import java.util.Collection;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.ExceptionUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.elasticsearch.config.ElasticSearchEmbeddedServerConfig;
import org.nuxeo.runtime.RuntimeMessage;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.analysis.common.CommonAnalysisPlugin;
import org.opensearch.common.settings.Settings;
import org.opensearch.node.Node;
import org.opensearch.node.NodeValidationException;
import org.opensearch.plugins.Plugin;
import org.opensearch.transport.Netty4Plugin;

/**
 * @since 9.3
 */
public class ElasticSearchEmbeddedNode implements Closeable {

    private static final Logger log = LogManager.getLogger(ElasticSearchEmbeddedNode.class);

    private static final int DEFAULT_RETRY = 3;

    protected final ElasticSearchEmbeddedServerConfig config;

    protected Node node;

    protected int retry = DEFAULT_RETRY;

    public ElasticSearchEmbeddedNode(ElasticSearchEmbeddedServerConfig config) {
        this.config = config;
    }

    public void start() {
        log.info("Starting embedded (in JVM) Elasticsearch");
        if (Framework.isInitialized() && !Framework.isTestModeSet()) {
            String message = "Elasticsearch embedded configuration is ONLY for testing purpose. "
                    + "You need to create a dedicated Elasticsearch cluster for production.";
            log.warn(message);
            Framework.getRuntime()
                     .getMessageHandler()
                     .addMessage(new RuntimeMessage(WARNING, message, CODE, getClass().getSimpleName()));
        }
        Settings.Builder buidler = Settings.builder();
        buidler.put("network.host", config.getNetworkHost())
               .put("path.home", config.getHomePath())
               .put("path.data", config.getDataPath())
               .put("cluster.name", config.getClusterName())
               .put("node.name", config.getNodeName())
               .put("discovery.type", "single-node")
               .put("http.netty.worker_count", 4)
               .put("http.cors.enabled", true)
               .put("http.cors.allow-origin", "*")
               .put("http.cors.allow-credentials", true)
               .put("http.cors.allow-headers", "Authorization, X-Requested-With, Content-Type, Content-Length")
               .put("cluster.routing.allocation.disk.threshold_enabled", false)
               .put("http.port", config.getHttpPort());
        if (config.getIndexStorageType() != null) {
            buidler.put("index.store.type", config.getIndexStorageType());
        }
        Settings settings = buidler.build();
        log.debug("Using settings: {}", () -> settings.toDelimitedString(','));

        Collection<Class<? extends Plugin>> plugins = new HashSet<>();
        plugins.add(Netty4Plugin.class);
        plugins.add(CommonAnalysisPlugin.class);
        try {
            node = new PluginConfigurableNode(settings, plugins);
            node.start();
            // try with another home path
            config.setHomePath(null);
        } catch (NodeValidationException e) {
            throw new NuxeoException("Cannot start embedded Elasticsearch: " + e.getMessage(), e);
        } catch (Exception e) {
            Throwable cause = ExceptionUtils.getRootCause(e);
            if (cause != null && cause instanceof BindException) {
                retry--;
                log.error("Cannot bind local Elasticsearch on port: {}, from: {}, retry countdown: {}",
                        config.getHttpPort(), config.getDataPath(), retry);
                try {
                    node.close();
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                    throw new NuxeoException(e1);
                } catch (IOException e1) {
                    throw new NuxeoException(e1);
                }
                if (retry <= 0) {
                    String msg = "Not able to bind to local Elasticsearch after multiple attempts, give up";
                    log.error(msg);
                    throw new IllegalStateException(msg);
                }
                start();
            } else {
                throw e;
            }
        }
        retry = DEFAULT_RETRY;
        log.debug("Elasticsearch node started.");
    }

    @Override
    public void close() throws IOException {
        if (node != null) {
            log.info("Closing embedded (in JVM) Elasticsearch");
            node.close();
            log.info("Node closed: {}", node::isClosed);
        }
        node = null;
    }

    public ElasticSearchEmbeddedServerConfig getConfig() {
        return config;
    }

    public Node getNode() {
        return node;
    }
}

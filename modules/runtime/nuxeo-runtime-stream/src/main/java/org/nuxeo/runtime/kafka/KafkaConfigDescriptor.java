/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.runtime.kafka;

import static java.util.Objects.requireNonNullElse;

import java.util.Properties;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

@XObject("kafkaConfig")
public class KafkaConfigDescriptor implements Descriptor {

    @XObject("consumer")
    public static class ConsumerProperties {
        @XNodeMap(value = "property", key = "@name", type = Properties.class, componentType = String.class)
        protected Properties properties = new Properties();
    }

    @XObject("producer")
    public static class ProducerProperties {
        @XNodeMap(value = "property", key = "@name", type = Properties.class, componentType = String.class)
        protected Properties properties = new Properties();
    }

    // @since 11.1
    @XObject("admin")
    public static class AdminProperties {
        @XNodeMap(value = "property", key = "@name", type = Properties.class, componentType = String.class)
        protected Properties properties = new Properties();
    }

    @XNode("@name")
    public String name;

    @Deprecated
    @XNode("@zkServers")
    public String zkServers;

    @XNode("@topicPrefix")
    public String topicPrefix;

    @XNode("@randomPrefix")
    public Boolean randomPrefix = Boolean.FALSE;

    // @since 2023.18
    @XNode("@copy")
    public String copy;

    @XNode("producer")
    public ProducerProperties producerProperties = new ProducerProperties();

    @XNode("consumer")
    public ConsumerProperties consumerProperties = new ConsumerProperties();

    // @since 11.1
    @XNode("admin")
    public AdminProperties adminProperties = new AdminProperties();

    @Override
    public String getId() {
        return name;
    }

    // initialize copied properties
    public void init(KafkaConfigDescriptor source) {
        if (source == null) {
            return;
        }
        if (source.copy != null) {
            throw new IllegalArgumentException(
                    "KafkaConfigDescriptor: " + getId() + " cannot copy another copied configuration");
        }
        topicPrefix = requireNonNullElse(topicPrefix, source.topicPrefix);
        adminProperties.properties = mergeProperties(source.adminProperties.properties, adminProperties.properties);
        consumerProperties.properties = mergeProperties(source.consumerProperties.properties,
                consumerProperties.properties);
        producerProperties.properties = mergeProperties(source.producerProperties.properties,
                producerProperties.properties);
    }

    protected Properties mergeProperties(Properties source, Properties update) {
        Properties sourceProps = (Properties) source.clone();
        sourceProps.putAll(update);
        return sourceProps;
    }

}

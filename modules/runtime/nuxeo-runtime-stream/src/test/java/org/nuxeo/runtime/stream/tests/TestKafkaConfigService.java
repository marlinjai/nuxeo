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
package org.nuxeo.runtime.stream.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.runtime.kafka.KafkaConfigService;
import org.nuxeo.runtime.stream.RuntimeStreamFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 9.3
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeStreamFeature.class)
@Deploy("org.nuxeo.runtime.stream:test-kafka-config-contrib.xml")
public class TestKafkaConfigService {

    @Inject
    public KafkaConfigService service;

    @Test
    public void testService() {
        assertNotNull(service);
        assertFalse(service.listConfigNames().isEmpty());
        // when using -Pkafka there are 3 config else 2
        assertTrue(service.listConfigNames().toString(), service.listConfigNames().size() >= 2);

        String config1 = "config1";
        assertNotNull(service.getAdminProperties(config1));
        assertNotNull(service.getConsumerProperties(config1));
        assertNotNull(service.getProducerProperties(config1));
        assertEquals("5", service.getConsumerProperties(config1).get("max.poll.records"));
        assertEquals("kafka-broker-1:9092,kafka-broker-2:9092",
                service.getProducerProperties(config1).getProperty("bootstrap.servers"));
        assertNotEquals("RANDOM()", service.getTopicPrefix(config1));

        String config2 = "config2";
        assertNotNull(service.getConsumerProperties(config2));
        assertNotNull(service.getProducerProperties(config2));
        assertEquals("foo", service.getTopicPrefix(config2));

        // config3 is init with a copy of config1
        String config3 = "config3";
        assertEquals(service.getTopicPrefix(config1), service.getTopicPrefix(config3));
        assertEquals(service.getProducerProperties(config1), service.getProducerProperties(config3));
        assertEquals(service.getConsumerProperties(config1).get("heartbeat.interval.ms"),
                service.getConsumerProperties(config3).get("heartbeat.interval.ms"));
        assertNotNull(service.getConsumerProperties(config3));
        // overrides
        assertEquals("1", service.getConsumerProperties(config3).get("max.poll.records"));
        assertEquals("5", service.getConsumerProperties(config1).get("max.poll.records"));
        assertEquals("123", service.getConsumerProperties(config3).get("newProp"));
        assertNull(service.getConsumerProperties(config1).get("newProp"));
    }
}

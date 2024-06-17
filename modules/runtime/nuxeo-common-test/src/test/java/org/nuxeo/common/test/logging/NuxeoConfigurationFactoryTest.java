/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.common.test.logging;

import static org.junit.Assert.assertTrue;
import static org.nuxeo.common.test.ModuleUnderTest.getOutputDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Test;

/**
 * @since 2025.0
 */
public class NuxeoConfigurationFactoryTest {

    private static final Logger log = LogManager.getLogger(NuxeoConfigurationFactoryTest.class);

    @Test
    public void testConfigurationOverride() throws IOException {
        // test the default configuration
        log.debug("A debug message that shouldn't go to trace.log");
        var traces = Files.readString(Path.of(getOutputDirectory()).resolve("trace.log"));
        assertTrue(traces.isEmpty());

        var logConfigurationPath = Path.of(getOutputDirectory()).resolve("test-classes/log4j2-test.xml");
        try {
            // prepare a configuration
            var logConfiguration = """
                    <?xml version="1.0" ?>
                    <Configuration>
                      <Loggers>
                        <Logger name="org.nuxeo" level="DEBUG" />
                      </Loggers>
                    </Configuration>
                    """;
            Files.writeString(logConfigurationPath, logConfiguration);

            // reconfigure log4j
            Configurator.reconfigure();

            // test the configuration override
            log.debug("A debug message that should go to trace.log");
            traces = Files.readString(Path.of(getOutputDirectory()).resolve("trace.log"));
            assertTrue(traces.contains("A debug message that should go to trace.log"));
        } finally {
            Files.deleteIfExists(logConfigurationPath);
        }
    }
}

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

import java.util.ArrayList;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.composite.CompositeConfiguration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LoaderUtil;

/**
 * This {@link ConfigurationFactory} provides a common log configuration for tests.
 * <p>
 * The feature logs some debug information with the {@link StatusLogger} that can be enabled with the system property:
 * 
 * <pre>
 * {@code
 * log4j2.debug = true
 * }
 * </pre>
 * 
 * @since 2025.0
 */
@Plugin(name = "NuxeoConfigurationFactory", category = ConfigurationFactory.CATEGORY)
@Order(100) // larger value means higher priority
@SuppressWarnings("unused") // instantiated by reflection by Log4j
public class NuxeoConfigurationFactory extends ConfigurationFactory {

    // use the StatusLogger instead of standard Logger through LogManager because we're currently configuring the later
    private static final Logger log = StatusLogger.getLogger();

    /**
     * Valid file extensions for XML files.
     */
    public static final String[] SUFFIXES = new String[] { ".xml", "*" };

    /**
     * File name prefix for common test configuration.
     */
    protected static final String TEST_PREFIX = "log4j2-common-test";

    @Override
    protected String[] getSupportedTypes() {
        return SUFFIXES;
    }

    @Override
    protected String getTestPrefix() {
        // override this method in order to be able to inject first our log4j2-common-test.xml file, the mechanism we
        // leverage is at the end of ConfigurationFactory.Factory#getConfiguration when config files are introspected
        return TEST_PREFIX;
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        log.debug("Configuring logging with Nuxeo Common Test configuration");
        var configurations = new ArrayList<AbstractConfiguration>();
        // put our log4j2-common-test.xml
        configurations.add(new XmlConfiguration(loggerContext, source));
        // put the log4j-test.xml file if it exists
        var moduleSource = ConfigurationSource.fromResource(super.getTestPrefix() + ".xml",
                LoaderUtil.getThreadContextClassLoader());
        if (moduleSource != null) {
            log.debug("Loading file: {} to override Nuxeo Common Test configuration", moduleSource);
            configurations.add(new XmlConfiguration(loggerContext, moduleSource));
        }
        return new CompositeConfiguration(configurations);
    }
}

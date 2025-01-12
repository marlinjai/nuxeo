/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Kevin Leturc <kleturc@nuxeo.com>
 */

package org.nuxeo.launcher.config;

import static org.nuxeo.launcher.config.ConfigurationConstants.ENV_NUXEO_ENVIRONMENT;
import static org.nuxeo.launcher.config.ConfigurationConstants.FILE_NUXEO_DEFAULTS;
import static org.nuxeo.launcher.config.ConfigurationGenerator.NUXEO_ENVIRONMENT_CONF_FORMAT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class used to load the configuration for Nuxeo.
 *
 * @since 11.5
 */
public class ConfigurationLoader {

    private static final Logger log = LogManager.getLogger(ConfigurationLoader.class);

    /**
     * Catch values like ${env:PARAM_KEY:defaultValue}
     *
     * @since 9.1
     */
    protected static final Pattern ENV_VALUE_PATTERN = Pattern.compile(
            "\\$\\{env(?<boolean>\\?\\?)?:(?<envparam>\\w*)(:?(?<defaultvalue>.*?)?)?\\}");

    protected final Map<String, String> environment;

    protected final Map<String, String> parametersMigration;

    protected final boolean hideDeprecationWarnings;

    public ConfigurationLoader(Map<String, String> environment, Map<String, String> parametersMigration,
            boolean hideDeprecationWarnings) {
        this.environment = environment;
        this.parametersMigration = parametersMigration;
        this.hideDeprecationWarnings = hideDeprecationWarnings;
    }

    /**
     * Loads the {@code nuxeo.defaults} and {@code nuxeo.NUXEO_ENVIRONMENT} files.
     * <p>
     * This method assumes {@code nuxeo.defaults} exists and is readable.
     */
    public Properties loadNuxeoDefaults(Path directory) throws ConfigurationException {
        // load nuxeo.defaults
        Properties properties = loadProperties(directory.resolve(FILE_NUXEO_DEFAULTS));
        // load nuxeo.NUXEO_ENVIRONMENT
        Path nuxeoDefaultsEnv = directory.resolve(getNuxeoEnvironmentConfName());
        if (Files.exists(nuxeoDefaultsEnv)) {
            loadProperties(properties, nuxeoDefaultsEnv);
        }
        return properties;
    }

    /**
     * @param propsFile Properties file
     * @return new Properties containing trimmed keys and values read in {@code propsFile}
     */
    public Properties loadProperties(Path propsFile) throws ConfigurationException {
        return loadProperties(new Properties(), propsFile);
    }

    protected Properties loadProperties(Properties properties, Path propertiesFile) throws ConfigurationException {
        try (var reader = Files.newBufferedReader(propertiesFile)) {
            Properties p = new Properties();
            p.load(reader);
            p.stringPropertyNames().forEach(k -> {
                String value = p.getProperty(k).trim();
                value = replaceEnvironmentVariables(value);
                value = replaceBackslashes(value);
                properties.put(k, value);
                if (parametersMigration.containsKey(k)) {
                    String newKey = parametersMigration.get(k);
                    log.warn("Parameter: {} present in: {} is deprecated - please use: {} instead", k, propertiesFile,
                            newKey);
                    properties.put(newKey, value);
                }
            });
        } catch (IOException e) {
            throw new ConfigurationException("Unable to read: " + propertiesFile, e);
        }
        return properties;
    }

    /**
     * @return the nuxeo.defaults file for current {@code NUXEO_ENVIRONMENT}
     */
    protected String getNuxeoEnvironmentConfName() {
        return String.format(NUXEO_ENVIRONMENT_CONF_FORMAT, environment.get(ENV_NUXEO_ENVIRONMENT));
    }

    public String replaceEnvironmentVariables(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }

        Matcher matcher = ENV_VALUE_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            boolean booleanValue = "??".equals(matcher.group("boolean"));
            String envVarName = matcher.group("envparam");
            String defaultValue = matcher.group("defaultvalue");

            String envValue = environment.get(envVarName);

            String result;
            if (booleanValue) {
                result = StringUtils.isBlank(envValue) ? "false" : "true";
            } else {
                result = StringUtils.isBlank(envValue) ? defaultValue : envValue;
            }
            matcher.appendReplacement(sb, result);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    protected String replaceBackslashes(String value) {
        if (SystemUtils.IS_OS_WINDOWS && value.matches(".*:\\\\.*")) {
            value = value.replaceAll("\\\\", "/");
        }
        return value;
    }
}

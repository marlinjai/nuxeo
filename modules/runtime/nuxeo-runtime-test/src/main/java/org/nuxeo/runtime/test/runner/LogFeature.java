/*
 * (C) Copyright 2017-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thomas Roger
 */
package org.nuxeo.runtime.test.runner;

import static org.apache.logging.log4j.core.config.AppenderRef.createAppenderRef;

import java.lang.annotation.ElementType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.ConsoleAppender.Target;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.junit.runners.model.FrameworkMethod;

/**
 * @since 9.3
 */
public class LogFeature implements RunnerFeature {

    private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(LogFeature.class);

    protected static final String CONSOLE_APPENDER = "CONSOLE";

    protected static final String CONSOLE_LOG_FEATURE_APPENDER = "CONSOLE_LOG_FEATURE";

    protected ConsoleAppender consoleAppender;

    protected ConsoleAppender hiddenAppender;

    /**
     * Stores the original log level for a given logger name, which allows us to restore the level as defined before
     * launching the tests.
     *
     * @since 11.1
     */
    protected Map<LoggerLevelKey, LoggerLightConfig> originalConfigurationByLogger = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     * <p>
     *
     * @since 11.1
     */
    @Override
    public void initialize(FeaturesRunner runner) {
        originalConfigurationByLogger.clear();
        addOrUpdateLoggerLevel(runner, null);
        addConsoleThresholdLogLevel(runner, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     * @since 11.1
     */
    @Override
    public void stop(FeaturesRunner runner) {
        restoreLoggerLevel(runner, null);
        restoreConsoleThresholdLogLevel(runner, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     * @since 11.1
     */
    @Override
    public void beforeMethodRun(FeaturesRunner runner, FrameworkMethod method, Object test) {
        addOrUpdateLoggerLevel(runner, method);
        addConsoleThresholdLogLevel(runner, method);
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     * @since 11.1
     */
    @Override
    public void afterMethodRun(FeaturesRunner runner, FrameworkMethod method, Object test) {
        restoreLoggerLevel(runner, method);
        restoreConsoleThresholdLogLevel(runner, method);
    }

    /**
     * @deprecated since 11.1. Use {@link ConsoleLogLevelThreshold} with {@link ConsoleLogLevelThreshold#value()} set to
     *             {@code ERROR}.
     */
    @Deprecated(since = "11.1", forRemoval = true)
    public void hideWarningFromConsoleLog() {
        setConsoleLogThreshold(Level.ERROR.toString());
    }

    /**
     * @deprecated since 11.1. Use {@link ConsoleLogLevelThreshold} with {@link ConsoleLogLevelThreshold#value()} set to
     *             {@code FATAL}.
     * @since 9.10
     */
    @Deprecated(since = "11.1", forRemoval = true)
    public void hideErrorFromConsoleLog() {
        setConsoleLogThreshold(Level.FATAL.toString());
    }

    /**
     * @since 9.10
     */
    public void setConsoleLogThreshold(String level) {
        if (consoleAppender != null) {
            return;
        }

        Logger rootLogger = LoggerContext.getContext(false).getRootLogger();
        consoleAppender = (ConsoleAppender) rootLogger.getAppenders().get(CONSOLE_APPENDER);
        rootLogger.removeAppender(consoleAppender);
        ConsoleAppender newAppender = ConsoleAppender.newBuilder()
                                                     .setName(CONSOLE_LOG_FEATURE_APPENDER)
                                                     .setTarget(Target.SYSTEM_OUT)
                                                     .setFilter(ThresholdFilter.createFilter(Level.toLevel(level), null,
                                                             null))
                                                     .build();
        newAppender.start();
        rootLogger.addAppender(newAppender);
        hiddenAppender = newAppender;
    }

    public void restoreConsoleLog() {
        if (consoleAppender == null) {
            return;
        }

        Logger rootLogger = LoggerContext.getContext(false).getRootLogger();
        rootLogger.removeAppender(hiddenAppender);
        rootLogger.addAppender(consoleAppender);
        consoleAppender = null;
        hiddenAppender = null;
    }

    /**
     * Adds the console threshold log level. To be proceed a {@code Class} / {@code Method} should be annotated by
     * 
     * @see ConsoleLogLevelThreshold
     *      <p>
     * @see #setConsoleLogThreshold(String)
     * @param runner the feature runner, cannot be {@code null}
     * @param method the framework method, can be {@code null}
     * @since 11.1
     */
    protected void addConsoleThresholdLogLevel(FeaturesRunner runner, FrameworkMethod method) {
        // Remove the previous threshold if any.
        restoreConsoleLog();
        // Set the new threshold
        runner.getMethodAnnotationsWithClassFallback(ConsoleLogLevelThreshold.class, method)
              .stream()
              .map(ConsoleLogLevelThreshold::value)
              .findFirst()
              .ifPresent(this::setConsoleLogThreshold);
    }

    /**
     * Restores the console threshold log level. Based on if {@code Class} or {@code Method} is annotated by
     * {@link ConsoleLogLevelThreshold}.
     * <p>
     * {@link #restoreConsoleLog()}
     *
     * @since 11.1
     */
    protected void restoreConsoleThresholdLogLevel(FeaturesRunner runner, FrameworkMethod method) {
        runner.getMethodAnnotationsWithClassFallback(ConsoleLogLevelThreshold.class, method)
              .stream()
              .map(ConsoleLogLevelThreshold::value)
              .findFirst()
              .ifPresent(t -> restoreConsoleLog());
    }

    /**
     * Adds or updates the logger level.
     * <p>
     * The definition of {@link LoggerLevel} can be done on a given {@code Class} / {@code Method} test. At the end of
     * the test each overriding logger must be restored to its original value for this the purpose we should save the
     * original level.
     * <p>
     * {@link #restoreLoggerLevel(FeaturesRunner, FrameworkMethod)} to see how the restore part will be happened.
     *
     * @param runner the feature runner, cannot be {@code null}
     * @param method the framework method, can be {@code null}
     * @since 11.1
     */
    protected void addOrUpdateLoggerLevel(FeaturesRunner runner, FrameworkMethod method) {
        var loggerContext = LoggerContext.getContext(false);
        var configuration = loggerContext.getConfiguration();
        boolean updateLoggers = false;
        for (LoggerLevel logger : runner.getMethodOrTestAnnotations(LoggerLevel.class, method)) {
            var loggerName = getLoggerName(logger);
            var loggerConfig = configuration.getLoggerConfig(loggerName);
            // backup original values
            var previouslyConfigured = false;
            var originalLevel = loggerConfig.getLevel();
            var originalAppenderRefs = //
                    loggerConfig.getAppenderRefs().stream().map(AppenderRef::getRef).collect(Collectors.toSet());
            if (loggerName.equals(loggerConfig.getName())) {
                previouslyConfigured = true;
                configuration.removeLogger(loggerName);
            }
            // create a new logger config
            var loggerConfigBuilder = LoggerConfig.newBuilder()
                                                  .withConfig(configuration)
                                                  .withLoggerName(loggerName)
                                                  .withLevel(originalLevel)
                                                  .withRefs(loggerConfig.getAppenderRefs().toArray(AppenderRef[]::new));

            // configure logger level
            if (logger.level() != null) {
                loggerConfigBuilder.withLevel(Level.toLevel(logger.level()));
                updateLoggers = updateLoggers || !loggerConfigBuilder.getLevel().equals(originalLevel);
            }

            // configure logger appender references
            if (logger.appenders().length > 0) {
                var desiredAppenderRefs = new HashSet<>(originalAppenderRefs);
                desiredAppenderRefs.addAll(List.of(logger.appenders()));
                loggerConfigBuilder.withRefs(desiredAppenderRefs.stream()
                                                                .map(a -> createAppenderRef(a, null, null))
                                                                .toArray(AppenderRef[]::new));
                updateLoggers = updateLoggers || originalAppenderRefs.size() < desiredAppenderRefs.size();
            }

            // finalise logger configuration by computing appenders from configured references
            loggerConfig = loggerConfigBuilder.build();
            addAppendersFromRefs(loggerConfig, configuration);
            configuration.addLogger(loggerName, loggerConfig);

            // backup the original values
            originalConfigurationByLogger.put(buildKey(logger, method), new LoggerLightConfig(previouslyConfigured,
                    originalLevel, originalAppenderRefs.toArray(String[]::new)));
        }
        // update loggers if needed
        if (updateLoggers) {
            loggerContext.updateLoggers();
        }
    }

    /**
     * Restores the original value of the logger level.
     * <p>
     * {@link #addOrUpdateLoggerLevel(FeaturesRunner, FrameworkMethod)}} to see how we store the original value and set
     * the new one.
     *
     * @param runner the feature runner, cannot be {@code null}
     * @param method the framework method, can be {@code null}
     * @since 11.1
     */
    protected void restoreLoggerLevel(FeaturesRunner runner, FrameworkMethod method) {
        var loggerContext = LoggerContext.getContext(false);
        var configuration = loggerContext.getConfiguration();
        boolean updateLoggers = false;
        for (LoggerLevel logger : runner.getMethodOrTestAnnotations(LoggerLevel.class, method)) {
            if (logger.level() != null || logger.appenders().length > 0) {
                updateLoggers = true;
                String loggerName = getLoggerName(logger);
                // restore the logger config
                var previousConfig = originalConfigurationByLogger.remove(buildKey(logger, method));
                // could happen if there's duplicate, like a feature extends another one + reference it in @Features
                if (previousConfig != null) {
                    configuration.removeLogger(loggerName);
                    if (previousConfig.configured()) {
                        LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                                .withConfig(configuration)
                                .withLoggerName(loggerName)
                                .withLevel(previousConfig.level())
                                .withRefs(Stream.of(previousConfig.appenderRefs())
                                        .map(a -> createAppenderRef(a, null, null))
                                        .toArray(AppenderRef[]::new))
                                .build();
                        addAppendersFromRefs(loggerConfig, configuration);
                        configuration.addLogger(loggerName, loggerConfig);
                    }
                }
            }
        }
        // update loggers if needed
        if (updateLoggers) {
            loggerContext.updateLoggers();
        }
    }

    protected void addAppendersFromRefs(LoggerConfig loggerConfig, Configuration configuration) {
        for (var appenderRef : loggerConfig.getAppenderRefs()) {
            var appender = configuration.getAppender(appenderRef.getRef());
            if (appender == null) {
                log.error("Unable to locate appender: {} for logger config: {}", appenderRef, loggerConfig);
            } else {
                loggerConfig.addAppender(appender, appenderRef.getLevel(), appenderRef.getFilter());
            }
        }
    }

    /**
     * Gets the logger name from a given {@link LoggerLevel}.
     *
     * @since 11.1
     */
    protected String getLoggerName(LoggerLevel logLevel) {
        return StringUtils.defaultIfBlank(logLevel.name(), logLevel.klass().getName());
    }

    /**
     * Builds the logger key.
     *
     * @since 11.1
     */
    protected LoggerLevelKey buildKey(LoggerLevel logger, FrameworkMethod method) {
        ElementType type = method != null ? ElementType.METHOD : ElementType.TYPE;
        String loggerName = getLoggerName(logger);
        return new LoggerLevelKey(type, loggerName);
    }

    protected record LoggerLightConfig(boolean configured, Level level, String[] appenderRefs) {
    }
}

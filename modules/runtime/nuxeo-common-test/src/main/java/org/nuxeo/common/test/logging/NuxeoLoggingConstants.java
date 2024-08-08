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

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * @since 2025.0
 */
public final class NuxeoLoggingConstants {

    /** A Console appender that prints WARN logs and higher, and logs with {@link #MARKER_CONSOLE_OVERRIDE}. */
    public static final String APPENDER_CONSOLE = "CONSOLE";

    /** A Console appender that prints INFO logs. */
    public static final String APPENDER_CONSOLE_INFO = "CONSOLE-INFO";

    /** A Console appender that prints DEBUG logs. */
    public static final String APPENDER_CONSOLE_DEBUG = "CONSOLE-DEBUG";

    /** A File appender that prints DEBUG logs and higher. */
    public static final String APPENDER_FILE = "FILE";

    public static final Marker MARKER_CONSOLE_OVERRIDE = MarkerManager.getMarker("CONSOLE_OVERRIDE");

    private NuxeoLoggingConstants() {
        // constants class
    }
}

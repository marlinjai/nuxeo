/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.nuxeo.common.http;

/**
 * HTTP standard and custom constants usable in any nuxeo stack layer.
 *
 * @since 2023.14
 */
public final class HttpHeaders {

    public static final String NUXEO_VIRTUAL_HOST = "nuxeo-virtual-host";

    public static final String ORIGIN = "Origin";

    public static final String REFERER = "Referer";

    public static final String X_FORWARDED_HOST = "X-Forwarded-Host";

    public static final String X_FORWARDED_PORT = "X-Forwarded-Port";

    public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

    private HttpHeaders() {
        // Constants class
    }
}

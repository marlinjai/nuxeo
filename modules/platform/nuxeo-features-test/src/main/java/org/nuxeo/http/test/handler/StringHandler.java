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
package org.nuxeo.http.test.handler;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.nuxeo.http.test.HttpResponse;

/**
 * @since 2023.13
 */
public class StringHandler extends AbstractStatusCodeHandler<String> {

    public StringHandler() {
        super();
    }

    public StringHandler(int status) {
        super(status);
    }

    @Override
    protected String doHandleResponse(HttpResponse response) throws IOException {
        return IOUtils.toString(response.getEntityInputStream(), UTF_8);
    }
}
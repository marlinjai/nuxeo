/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     bstefanescu
 *     vpasquier
 */
package org.nuxeo.ecm.webengine.app;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.ws.rs.core.MediaType;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.webengine.JsonFactoryManager;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * @since 6.0
 */
public class JsonWebengineWriter {

    public static final String SHOW_EXCEPTION_MESSAGE = "org.nuxeo.rest.exception.message.enabled";

    static JsonFactoryManager jsonFactoryManager;

    private static JsonFactory getFactory() {
        jsonFactoryManager = Framework.getService(JsonFactoryManager.class);
        return jsonFactoryManager.getJsonFactory();
    }

    private static JsonGenerator createGenerator(OutputStream out) throws IOException {
        return getFactory().createJsonGenerator(out, JsonEncoding.UTF8);
    }

    /**
     * @since 9.3
     */
    public static void writeException(OutputStream out, NuxeoException nuxeoException, MediaType mediaType)
            throws IOException {
        writeException(createGenerator(out), nuxeoException, mediaType);
    }

    /**
     * @since 9.3
     */
    public static void writeException(JsonGenerator jg, NuxeoException nuxeoException, MediaType mediaType)
            throws IOException {
        writeException(jg, nuxeoException, mediaType, nuxeoException.getStatusCode());
    }

    /**
     * @since 9.3
     */
    protected static void writeException(JsonGenerator jg, Throwable t, MediaType mediaType, int statusCode)
            throws IOException {
        jg.writeStartObject();
        jg.writeStringField("entity-type", "exception");
        jg.writeNumberField("status", statusCode);
        jg.writeStringField("message", getExceptionMessage(t.getMessage(), statusCode));
        if (jsonFactoryManager.isStackDisplay()) {
            jg.writeStringField("stacktrace", getStackTraceString(t));
            jg.writeObjectField("exception", t);
        }
        jg.writeEndObject();
        jg.flush();
    }

    /**
     * @since 11.5
     */
    protected static String getExceptionMessage(String exceptionMessage, int statusCode) {
        if (statusCode < SC_INTERNAL_SERVER_ERROR || jsonFactoryManager.isStackDisplay() || Framework.isDevModeSet()) {
            if (Framework.isBooleanPropertyFalse(SHOW_EXCEPTION_MESSAGE)) {
                return "An error occured";
            }
            return exceptionMessage;
        } else {
            return "Internal Server Error";
        }
    }

    protected static String getStackTraceString(Throwable t) throws IOException {
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            t.printStackTrace(pw);
            return sw.toString();
        }
    }

}

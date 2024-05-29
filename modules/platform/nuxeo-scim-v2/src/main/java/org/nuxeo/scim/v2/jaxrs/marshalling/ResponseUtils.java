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
 *     Thierry Delprat
 *     Antoine Taillefer
 */
package org.nuxeo.scim.v2.jaxrs.marshalling;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.unboundid.scim2.common.messages.ErrorResponse;

/**
 * Helper for handling {@link Response}s.
 *
 * @since 2023.13
 */
public final class ResponseUtils {

    private ResponseUtils() {
        // helper class
    }

    public static Response response(Status status, Object entity) {
        return response(status.getStatusCode(), entity);
    }

    public static Response response(int status, Object entity) {
        return Response.status(status).type(APPLICATION_JSON).entity(entity).build();
    }

    public static Response deleted() {
        return Response.status(NO_CONTENT).build();
    }

    public static Response error(ErrorResponse error) {
        return response(error.getStatus(), error);
    }

}

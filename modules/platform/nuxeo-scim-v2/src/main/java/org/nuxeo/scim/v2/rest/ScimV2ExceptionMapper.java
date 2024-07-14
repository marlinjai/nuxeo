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
package org.nuxeo.scim.v2.rest;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.webengine.app.WebEngineExceptionMapper;
import org.nuxeo.runtime.api.Framework;

import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ErrorResponse;
import com.unboundid.scim2.server.utils.ServerUtils;

/**
 * @since 2023.15
 */
@Provider
@Singleton
public class ScimV2ExceptionMapper extends WebEngineExceptionMapper {

    private static final Logger log = LogManager.getLogger(ScimV2ExceptionMapper.class);

    @Context
    protected HttpHeaders headers;

    @Override
    public Response toResponse(Throwable t) {
        ScimException scimException;
        if (t instanceof ScimException e) {
            scimException = e;
        } else if (t.getCause() instanceof ScimException e) {
            scimException = e;
        } else {
            return super.toResponse(t);
        }
        ErrorResponse errorResponse = scimException.getScimError();
        logError(errorResponse.getStatus(), scimException);
        return ServerUtils.setAcceptableType(Response.status(errorResponse.getStatus()).entity(errorResponse),
                headers.getAcceptableMediaTypes()).build();
    }

    protected void logError(int statusCode, Throwable throwable) {
        if (statusCode >= SC_INTERNAL_SERVER_ERROR) {
            log.error(throwable, throwable);
        } else if (Framework.isDevModeSet()) {
            log.warn(throwable, throwable);
        } else {
            log.debug(throwable, throwable);
        }
    }
}

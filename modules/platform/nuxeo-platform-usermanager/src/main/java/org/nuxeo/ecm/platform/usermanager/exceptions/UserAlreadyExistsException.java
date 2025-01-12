/*
 * (C) Copyright 2007 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.usermanager.exceptions;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;

import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * @author <a href="mailto:glefter@nuxeo.com">George Lefter</a>
 */
public class UserAlreadyExistsException extends NuxeoException {

    private static final long serialVersionUID = 1L;

    private static final String defaultMessage = "User already exists";

    public UserAlreadyExistsException() {
        super(defaultMessage, SC_CONFLICT);
    }

    public UserAlreadyExistsException(String message) {
        super(message, SC_CONFLICT);
    }

    public UserAlreadyExistsException(Throwable cause) {
        super(defaultMessage, cause, SC_CONFLICT);
    }

    public UserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause, SC_CONFLICT);
    }
}

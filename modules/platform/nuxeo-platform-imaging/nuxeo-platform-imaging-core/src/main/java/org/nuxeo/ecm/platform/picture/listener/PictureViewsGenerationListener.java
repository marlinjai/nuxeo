/*
 * (C) Copyright 2015-2019 Nuxeo SA (http://nuxeo.com/) and others.
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

package org.nuxeo.ecm.platform.picture.listener;

import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_FACET;
import static org.nuxeo.ecm.platform.picture.recompute.RecomputeViewsAction.ACTION_NAME;
import static org.nuxeo.ecm.platform.picture.recompute.RecomputeViewsAction.PARAM_XPATH;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.event.impl.ShallowDocumentModel;
import org.nuxeo.ecm.platform.picture.PictureViewsHelper;
import org.nuxeo.runtime.api.Framework;

/**
 * Listener updating pre-filling the views of a Picture if the main Blob has changed.
 *
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.5
 */
public class PictureViewsGenerationListener implements PostCommitEventListener {

    public static final String DISABLE_PICTURE_VIEWS_GENERATION_LISTENER = "disablePictureViewsGenerationListener";

    protected PictureViewsHelper pvh = new PictureViewsHelper();

    @Override
    public void handleEvent(EventBundle events) {
        events.forEach(this::handleEvent);
    }

    protected void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }

        Boolean block = (Boolean) event.getContext().getProperty(DISABLE_PICTURE_VIEWS_GENERATION_LISTENER);
        if (Boolean.TRUE.equals(block)) {
            // ignore the event - we are blocked by the caller
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        if (doc instanceof ShallowDocumentModel) {
            // ignore DeletedDocumentModel or unconnected document
            return;
        }
        if (doc.hasFacet(PICTURE_FACET) && pvh.hasPrefillPictureViews(doc) && !doc.isProxy()) {
            String query = "SELECT * FROM Document WHERE ecm:uuid='" + doc.getId() + "'";
            BulkService service = Framework.getService(BulkService.class);
            String username = ctx.getPrincipal().getName();
            service.submit(
                    new BulkCommand.Builder(ACTION_NAME, query, username).param(PARAM_XPATH, "file:content").build());
        }
    }

}

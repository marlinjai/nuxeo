/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nour Al Kotob
 *     Thomas Roger
 */

package org.nuxeo.ecm.restapi.server.jaxrs.management;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.bulk.io.BulkConstants.STATUS_ERROR_COUNT;
import static org.nuxeo.ecm.core.bulk.io.BulkConstants.STATUS_ERROR_MESSAGE;
import static org.nuxeo.ecm.core.bulk.io.BulkConstants.STATUS_HAS_ERROR;
import static org.nuxeo.ecm.core.bulk.io.BulkConstants.STATUS_PROCESSED;
import static org.nuxeo.ecm.core.bulk.io.BulkConstants.STATUS_TOTAL;
import static org.nuxeo.ecm.platform.picture.api.adapters.AbstractPictureAdapter.VIEWS_PROPERTY;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.platform.picture.PictureViewsHelper;
import org.nuxeo.ecm.platform.picture.core.ImagingFeature;
import org.nuxeo.ecm.platform.picture.listener.PictureViewsGenerationListener;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;

/**
 * @since 11.3
 */
@Features(ImagingFeature.class)
@Deploy("org.nuxeo.ecm.platform.picture.rest")
public class TestPicturesObject extends ManagementBaseTest {

    @Inject
    protected CoreSession session;

    protected DocumentRef docRef;

    protected PictureViewsHelper pvh = new PictureViewsHelper();

    @Before
    public void createDocument() throws IOException {
        DocumentModel doc = session.createDocumentModel("/", "pictureDoc", "Picture");
        Blob blob = Blobs.createBlob(FileUtils.getResourceFileFromContext("images/test.jpg"), "image/jpeg",
                StandardCharsets.UTF_8.name(), "test.jpg");
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc.putContextData(PictureViewsGenerationListener.DISABLE_PICTURE_VIEWS_GENERATION_LISTENER, true);
        doc = session.createDocument(doc);
        txFeature.nextTransaction();

        doc = session.getDocument(doc.getRef());

        docRef = doc.getRef();
    }

    @Test
    public void testRecomputePicturesNoQuery() {
        doTestRecomputePictures(null, true);
    }

    @Test
    public void testRecomputePicturesValidQuery() {
        String query = "SELECT * FROM Document WHERE ecm:mixinType = 'Picture'";
        doTestRecomputePictures(query, true);
    }

    @Test
    public void testRecomputePicturesInvalidQuery() {
        String query = "SELECT * FROM nowhere";
        doTestRecomputePictures(query, false);
    }

    protected void doTestRecomputePictures(String query, boolean success) {
        // generating new picture views
        String commandId = httpClient.buildPostRequest("/management/pictures/recompute")
                                     .entity(Map.of("query", Objects.requireNonNullElse(query, "")))
                                     .executeAndThen(new JsonNodeHandler(), node -> {
                                         assertBulkStatusScheduled(node);
                                         return getBulkCommandId(node);
                                     });

        // waiting for the asynchronous picture views recompute task
        txFeature.nextTransaction();

        var pictureViewsRecomputed = query != null;
        httpClient.buildGetRequest("/management/bulk/" + commandId).executeAndConsume(new JsonNodeHandler(), node -> {
            assertBulkStatusCompleted(node);
            if (success) {
                assertEquals(pictureViewsRecomputed ? 1 : 0, node.get(STATUS_PROCESSED).asInt());
                assertFalse(node.get(STATUS_HAS_ERROR).asBoolean());
                assertEquals(0, node.get(STATUS_ERROR_COUNT).asInt());
                assertEquals(pictureViewsRecomputed ? 1 : 0, node.get(STATUS_TOTAL).asInt());
            } else {
                assertEquals(0, node.get(STATUS_PROCESSED).asInt());
                assertTrue(node.get(STATUS_HAS_ERROR).asBoolean());
                assertEquals(1, node.get(STATUS_ERROR_COUNT).asInt());
                assertEquals(0, node.get(STATUS_TOTAL).asInt());
                assertEquals("Invalid query", node.get(STATUS_ERROR_MESSAGE).asText());
            }
        });

        DocumentModel doc = session.getDocument(docRef);
        @SuppressWarnings("unchecked")
        List<Serializable> pictureViews = (List<Serializable>) doc.getPropertyValue(VIEWS_PROPERTY);
        assertNotNull(pictureViews);
        assertFalse(pictureViews.isEmpty());
        if (success && pictureViewsRecomputed) {
            assertFalse(pvh.hasPrefillPictureViews(doc));
        } else {
            assertTrue(pvh.hasPrefillPictureViews(doc));
        }
    }
}

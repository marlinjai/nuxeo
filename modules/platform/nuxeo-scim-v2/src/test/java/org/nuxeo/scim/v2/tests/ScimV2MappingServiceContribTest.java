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
 *     Guillaume Renard
 */
package org.nuxeo.scim.v2.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.scim.v2.api.ScimV2MappingService;

import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.UserResource;

/**
 * @since 2023.4
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
@Deploy("org.nuxeo.scim.v2:test-scim-v2-mapping-contrib.xml")
public class ScimV2MappingServiceContribTest {

    @Inject
    protected ScimV2MappingService mappingService;

    @Inject
    protected UserManager userManager;

    @Test
    public void testUserAddedToMemberGroupOnCreate() throws ScimException {
        var userResource = new UserResource();
        userResource.setUserName("jdoe");
        mappingService.createNuxeoUserFromUserResource(userResource);
        assertTrue(userManager.getPrincipal("jdoe").getAllGroups().contains("members"));
    }

    @Test
    public void testSearchOnEmail() throws ScimException {
        var userResource = new UserResource();
        userResource.setUserName("jdoe");
        List<Email> emails = List.of(new Email().setValue("foo@bar.org"));
        userResource.setEmails(emails);
        mappingService.createNuxeoUserFromUserResource(userResource);
        ListResponse<ScimResource> res = mappingService.queryUsers(0, 1, "userName eq \"foo@bar.org\"", null, false,
                null);
        assertEquals(1, res.getTotalResults());
    }

}

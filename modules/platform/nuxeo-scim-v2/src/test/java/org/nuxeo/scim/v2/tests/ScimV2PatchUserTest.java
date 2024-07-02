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
 *     Antoine Taillefer
 */
package org.nuxeo.scim.v2.tests;

import static com.unboundid.scim2.common.messages.PatchOpType.ADD;
import static com.unboundid.scim2.common.messages.PatchOpType.REPLACE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Objects;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.scim.v2.api.ScimV2MappingService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.messages.PatchOpType;
import com.unboundid.scim2.common.messages.PatchOperation;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.utils.JsonUtils;

/**
 * Tests the SCIM 2.0 patch feature for User resources.
 *
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
@Deploy("org.nuxeo.scim.v2:test-scim-v2-user-schema-override.xml")
@Deploy("org.nuxeo.scim.v2:test-scim-v2-mapping-contrib.xml")
public class ScimV2PatchUserTest {

    protected static final ObjectMapper MAPPER = JsonUtils.createObjectMapper();

    @Inject
    protected ScimV2MappingService mappingService;

    @Inject
    protected UserManager userManager;

    // ------------------------------ Add ------------------------------
    // See https://datatracker.ietf.org/doc/html/rfc7644#section-3.5.2.1

    // If omitted, the target location is assumed to be the resource itself. The "value" parameter contains a set of
    // attributes to be added to the resource.
    @Test
    public void testAdd1() throws JsonProcessingException, ScimException {
        newUserModel("joe", null, null);
        var patch = newPatchRequest(ADD, null, "{\"name\":{\"givenName\":\"Joe\"}}");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", null);
    }

    // RFC example: same as testAdd1 but with a set of attributes, among which a multi-valued attribute
    @Test
    public void testAdd1bis() throws JsonProcessingException, ScimException {
        newUserModel("joe", null, null);
        var patch = newPatchRequest(ADD, null,
                "{\"emails\":[{\"value\":\"babs@jensen.org\",\"type\":\"home\"}],\"name\":{\"givenName\":\"Babs\"}}");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Babs", null, null, "babs@jensen.org");
    }

    // If the target location does not exist, the attribute and value are added.
    @Test
    public void testAdd2() throws JsonProcessingException, ScimException {
        newUserModel("joe", null, null);
        var patch = newPatchRequest(ADD, "name.givenName", "\"Joe\""); // NOSONAR
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", null);
    }

    // If the target location specifies a complex attribute, a set of sub-attributes SHALL be specified in the "value"
    // parameter.
    @Test
    public void testAdd3() throws JsonProcessingException, ScimException {
        newUserModel("joe", null, null);
        var patch = newPatchRequest(ADD, "name", "{\"givenName\":\"Joe\"}");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", null);
    }

    // If the target location specifies a multi-valued attribute, a new value is added to the attribute.
    @Test
    public void testAdd4() throws JsonProcessingException, ScimException {
        newUserModel("joe", null, null);
        var patch = newPatchRequest(ADD, "emails", "[{\"value\":\"joe@devnull.com\"}]"); // NOSONAR
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", null, null, "joe@devnull.com"); // NOSONAR
    }

    // If the target location specifies a single-valued attribute, the existing value is replaced.
    @Test
    public void testAdd5() throws JsonProcessingException, ScimException {
        newUserModel("joe", "Joe", "Doe");
        var patch = newPatchRequest(ADD, "name.familyName", "\"Fat\""); // NOSONAR
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", "Fat", null);
    }

    // If the target location specifies an attribute that does not exist (has no value), the attribute is added with the
    // new value.
    // => Don't understand the difference with testAdd2...

    // If the target location exists, the value is replaced.
    // Let's check it on a complex attribute.
    @Test
    public void testAdd7() throws JsonProcessingException, ScimException {
        newUserModel("joe", "Joe", "Doe");
        var patch = newPatchRequest(ADD, "name", "{\"givenName\":\"Jack\",\"familyName\":\"Fat\"}");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Jack", "Fat");
    }

    // If the target location already contains the value specified, no changes SHOULD be made to the resource, and a
    // success response SHOULD be returned. Unless other operations change the resource, this operation SHALL NOT change
    // the modify timestamp of the resource.
    @Test
    public void testAdd8() throws JsonProcessingException, ScimException {
        newUserModel("joe", "Joe", null);
        var patch = newPatchRequest(ADD, "name.givenName", "\"Joe\"");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", null);
    }

    // ------------------------------ Remove ------------------------------
    // See https://datatracker.ietf.org/doc/html/rfc7644#section-3.5.2.2

    // If "path" is unspecified, the operation fails with HTTP status code 400 and a "scimType" error code of
    // "noTarget".
    @Test
    public void testRemove1() throws ScimException {
        newUserModel("joe", null, null);
        var patch = newRemovePatchRequest(null);
        assertThrows(BadRequestException.class, () -> mappingService.patchNuxeoUser("joe", patch));
    }

    // If the target location is a single-value attribute, the attribute and its associated value is removed, and the
    // attribute SHALL be considered unassigned.".
    @Test
    public void testRemove2() throws ScimException {
        // simple attribute
        newUserModel("joe", "Joe", "Doe");
        var patch = newRemovePatchRequest("name.familyName");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", null);
        // complex attribute
        patch = newRemovePatchRequest("name");
        userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", null, null);
    }

    // If the target location is a multi-valued attribute and no filter is specified, the attribute and all values are
    // removed, and the attribute SHALL be considered unassigned.
    @Test
    public void testRemove3() throws ScimException {
        newUserModel("joe", "Joe", "Doe", "work@devnull.com", "home@devnull.com"); // NOSONAR
        var patch = newRemovePatchRequest("emails");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", "Doe", null, null);
    }

    // If the target location is a multi-valued attribute and a complex filter is specified comparing a "value", the
    // values matched by the filter are removed. If no other values remain after removal of the selected values, the
    // multi-valued attribute SHALL be considered unassigned.
    @Test
    public void testRemove4() throws ScimException {
        newUserModel("joe", "Joe", "Doe", "work@devnull.com", "home@devnull.com");
        var patch = newRemovePatchRequest("emails[type eq \"work\" and value sw \"work@\"]");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", "Doe", null, "home@devnull.com");
        patch = newRemovePatchRequest("emails[type eq \"home\"]");
        userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", "Doe", null, null);
    }

    // If the target location is a complex multi-valued attribute and a complex filter is specified based on the
    // attribute's sub-attributes, the matching records are removed. Sub-attributes whose values have been removed SHALL
    // be considered unassigned. If the complex multi-valued attribute has no remaining records, the attribute SHALL be
    // considered unassigned.
    // => Covered by testRemove4.

    // ------------------------------ Replace ------------------------------
    // See https://datatracker.ietf.org/doc/html/rfc7644#section-3.5.2.3

    // If the "path" parameter is omitted, the target is assumed to be the resource itself. In this case, the "value"
    // attribute SHALL contain a list of one or more attributes that are to be replaced.
    @Test
    public void testReplace1() throws JsonProcessingException, ScimException {
        newUserModel("joe", "Joe", null);
        var patch = newPatchRequest(REPLACE, null, "{\"name\":{\"givenName\":\"Jack\"}}");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Jack", null);
    }

    // If the target location is a single-value attribute, the attributes value is replaced.
    @Test
    public void testReplace2() throws JsonProcessingException, ScimException {
        newUserModel("joe", "Joe", "Doe");
        var patch = newPatchRequest(REPLACE, "name.familyName", "\"Fat\"");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", "Fat");
    }

    // If the target location is a multi-valued attribute and no filter is specified, the attribute and all values are
    // replaced
    @Test
    public void testReplace3() throws JsonProcessingException, ScimException {
        newUserModel("joe", "Joe", "Doe", "work@devnull.com", "home@devnull.com");
        var patch = newPatchRequest(REPLACE, "emails",
                "[{\"type\":\"work\",\"value\":\"joe@devnull.com\"},{\"type\":\"home\",\"value\":\"joe@devnull.com\"}]");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", "Doe", "joe@devnull.com", "joe@devnull.com");
    }

    // If the target location path specifies an attribute that does not exist, the service provider SHALL treat the
    // operation as an "add".
    @Test
    public void testReplace4() throws JsonProcessingException, ScimException {
        newUserModel("joe", null, null);
        var patch = newPatchRequest(REPLACE, "name.givenName", "\"Joe\"");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Joe", null);
    }

    // If the target location specifies a complex attribute, a set of sub-attributes SHALL be specified in the "value"
    // parameter, which replaces any existing values or adds where an attribute did not previously exist. Sub-attributes
    // that are not specified in the "value" parameter are left unchanged.
    @Test
    public void testReplace5() throws JsonProcessingException, ScimException {
        newUserModel("joe", "Joe", null);
        var patch = newPatchRequest(REPLACE, "name", "{\"givenName\":\"Jack\",\"familyName\":\"Fat\"}");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", "Jack", "Fat");
    }

    // If the target location is a multi-valued attribute and a value selection ("valuePath") filter is specified that
    // matches one or more values of the multi-valued attribute, then all matching record values SHALL be replaced.
    @Test
    public void testReplace6() throws JsonProcessingException, ScimException {
        newUserModel("joe", null, null, "work@devnull.com", "home@devnull.com");
        var patch = newPatchRequest(REPLACE, "emails[value ew \"@devnull.com\"]", "{\"value\":\"joe@devnull.com\"}");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", null, null, "joe@devnull.com", "joe@devnull.com");
    }

    // If the target location is a complex multi-valued attribute with a value selection filter ("valuePath") and a
    // specific sub-attribute (e.g., "addresses[type eq "work"].streetAddress"), the matching sub-attribute of all
    // matching records is replaced.
    @Test
    public void testReplace7() throws JsonProcessingException, ScimException {
        newUserModel("joe", null, null, "work@devnull.com", "home@devnull.com");
        var patch = newPatchRequest(REPLACE, "emails[value ew \"@devnull.com\"].value", "\"joe@devnull.com\"");
        var userModel = mappingService.patchNuxeoUser("joe", patch);
        checkUserModel(userModel, "joe", null, null, "joe@devnull.com", "joe@devnull.com");
    }

    // If the target location is a multi-valued attribute for which a value selection filter ("valuePath") has been
    // supplied and no record match was made, the service provider SHALL indicate failure by returning HTTP status code
    // 400 and a "scimType" error code of "noTarget".
    @Test
    public void testReplace8() throws JsonProcessingException, ScimException {
        newUserModel("joe", null, null, "work@devnull.com", "home@devnull.com");
        var patch = newPatchRequest(REPLACE, "emails[value ew \"@nodomain.com\"]", "{\"value\":\"joe@devnull.com\"}");
        assertThrows(BadRequestException.class, () -> mappingService.patchNuxeoUser("joe", patch));
    }

    protected PatchRequest newPatchRequest(PatchOpType op, String path, String value)
            throws JsonProcessingException, ScimException {
        JsonNode node = MAPPER.readTree(value);
        PatchOperation operation = switch (op) {
            case ADD -> path == null ? PatchOperation.add(node) : PatchOperation.add(path, node);
            case REMOVE -> PatchOperation.remove(path);
            case REPLACE -> PatchOperation.replace(path, node);
        };
        return new PatchRequest(operation);
    }

    protected PatchRequest newRemovePatchRequest(String path) throws ScimException {
        PatchOperation operation = PatchOperation.remove(path);
        return new PatchRequest(operation);
    }

    protected DocumentModel newUserModel(String username, String firstName, String lastName) {
        Objects.requireNonNull(username);
        var userModel = userManager.getBareUserModel();
        userModel.setPropertyValue("user:username", username);
        userModel.setPropertyValue("user:firstName", firstName);
        userModel.setPropertyValue("user:lastName", lastName);
        userModel = userManager.createUser(userModel);
        return userModel;
    }

    protected DocumentModel newUserModel(String username, String firstName, String lastName, String email) {
        var userModel = newUserModel(username, firstName, lastName);
        userModel.setPropertyValue("user:email", email);
        userManager.updateUser(userModel);
        return userModel;
    }

    protected DocumentModel newUserModel(String username, String firstName, String lastName, String workEmail,
            String homeEmail) {
        var userModel = newUserModel(username, firstName, lastName);
        userModel.setPropertyValue("user:workEmail", workEmail);
        userModel.setPropertyValue("user:homeEmail", homeEmail);
        userManager.updateUser(userModel);
        return userModel;
    }

    protected void checkUserModel(DocumentModel userModel, String username, String firstName, String lastName) {
        assertEquals(username, userModel.getPropertyValue("user:username"));
        assertEquals(firstName, userModel.getPropertyValue("user:firstName"));
        assertEquals(lastName, userModel.getPropertyValue("user:lastName"));
    }

    protected void checkUserModel(DocumentModel userModel, String username, String firstName, String lastName,
            String email) {
        checkUserModel(userModel, username, firstName, lastName);
        assertEquals(email, userModel.getPropertyValue("user:email"));
    }

    protected void checkUserModel(DocumentModel userModel, String username, String firstName, String lastName,
            String workEmail, String homeEmail) {
        checkUserModel(userModel, username, firstName, lastName);
        assertEquals(workEmail, userModel.getPropertyValue("user:workEmail"));
        assertEquals(homeEmail, userModel.getPropertyValue("user:homeEmail"));
    }
}

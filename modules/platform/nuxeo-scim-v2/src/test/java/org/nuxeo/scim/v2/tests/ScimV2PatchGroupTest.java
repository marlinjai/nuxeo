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

import java.io.Serializable;
import java.util.List;
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
 * Tests the SCIM 2.0 patch feature for Group resources.
 *
 * @since 2023.14
 */
@RunWith(FeaturesRunner.class)
@Features(ScimV2Feature.class)
@Deploy("org.nuxeo.scim.v2:test-scim-v2-user-schema-override.xml")
@Deploy("org.nuxeo.scim.v2:test-scim-v2-mapping-contrib.xml")
public class ScimV2PatchGroupTest {

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
        newGroupModel("people", null); // NOSONAR
        var patch = newPatchRequest(ADD, null, "{\"displayName\":\"People\"}");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People"); // NOSONAR
    }

    // RFC example: same as testAdd1 but with a set of attributes, among which a multi-valued attribute
    @Test
    public void testAdd1bis() throws JsonProcessingException, ScimException {
        newGroupModel("people", null, null, null);
        var patch = newPatchRequest(ADD, null,
                "{\"displayName\":\"People\",\"members\":[{\"value\":\"joe\",\"type\":\"User\"},{\"value\":\"subGroup\",\"type\":\"Group\"}]}");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People", List.of("joe"), List.of("subGroup"));
    }

    // If the target location does not exist, the attribute and value are added.
    @Test
    public void testAdd2() throws JsonProcessingException, ScimException {
        newGroupModel("people", null);
        var patch = newPatchRequest(ADD, "displayName", "\"People\""); // NOSONAR
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People");
    }

    // If the target location specifies a complex attribute, a set of sub-attributes SHALL be specified in the "value"
    // parameter.
    // => Doesn't apply to Group resource

    // If the target location specifies a multi-valued attribute, a new value is added to the attribute.
    @Test
    public void testAdd4() throws JsonProcessingException, ScimException {
        newGroupModel("people", null, List.of("joe"), null);
        var patch = newPatchRequest(ADD, "members", "[{\"value\":\"jack\",\"type\":\"User\"}]"); // NOSONAR
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", null, List.of("joe", "jack"), List.of());
    }

    // If the target location specifies a single-valued attribute, the existing value is replaced.
    @Test
    public void testAdd5() throws JsonProcessingException, ScimException {
        newGroupModel("people", "People");
        var patch = newPatchRequest(ADD, "displayName", "\"Employees\"");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "Employees"); // NOSONAR
    }

    // If the target location specifies an attribute that does not exist (has no value), the attribute is added with the
    // new value.
    // => Don't understand the difference with testAdd2...

    // If the target location exists, the value is replaced.
    // => Covered by testAdd5.

    // If the target location already contains the value specified, no changes SHOULD be made to the resource, and a
    // success response SHOULD be returned. Unless other operations change the resource, this operation SHALL NOT change
    // the modify timestamp of the resource.
    @Test
    public void testAdd8() throws JsonProcessingException, ScimException {
        newGroupModel("people", "People");
        var patch = newPatchRequest(ADD, "displayName", "\"People\"");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People");
    }

    // ------------------------------ Remove ------------------------------
    // See https://datatracker.ietf.org/doc/html/rfc7644#section-3.5.2.2

    // If "path" is unspecified, the operation fails with HTTP status code 400 and a "scimType" error code of
    // "noTarget".
    @Test
    public void testRemove1() throws ScimException {
        newGroupModel("people", null);
        var patch = newRemovePatchRequest(null);
        assertThrows(BadRequestException.class, () -> mappingService.patchNuxeoGroup("people", patch));
    }

    // If the target location is a single-value attribute, the attribute and its associated value is removed, and the
    // attribute SHALL be considered unassigned.".
    @Test
    public void testRemove2() throws ScimException {
        // simple attribute
        newGroupModel("people", "People");
        var patch = newRemovePatchRequest("displayName");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", null);
    }

    // If the target location is a multi-valued attribute and no filter is specified, the attribute and all values are
    // removed, and the attribute SHALL be considered unassigned.
    @Test
    public void testRemove3() throws ScimException {
        newGroupModel("people", "People", List.of("joe", "jack"), null);
        var patch = newRemovePatchRequest("members");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People", List.of(), List.of());
    }

    // If the target location is a multi-valued attribute and a complex filter is specified comparing a "value", the
    // values matched by the filter are removed. If no other values remain after removal of the selected values, the
    // multi-valued attribute SHALL be considered unassigned.
    @Test
    public void testRemove4() throws ScimException {
        newGroupModel("people", "People", List.of("joe", "jack"), null);
        var patch = newRemovePatchRequest("members[value eq \"joe\"]");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People", List.of("jack"), List.of());
        patch = newRemovePatchRequest("members[value eq \"jack\"]");
        groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People", List.of(), List.of());
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
        newGroupModel("people", "People");
        var patch = newPatchRequest(REPLACE, null, "{\"displayName\":\"Employees\"}");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "Employees");
    }

    // If the target location is a single-value attribute, the attributes value is replaced.
    @Test
    public void testReplace2() throws JsonProcessingException, ScimException {
        newGroupModel("people", "People");
        var patch = newPatchRequest(REPLACE, "displayName", "\"Employees\"");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "Employees");
    }

    // If the target location is a multi-valued attribute and no filter is specified, the attribute and all values are
    // replaced
    @Test
    public void testReplace3() throws JsonProcessingException, ScimException {
        newGroupModel("people", "People", List.of("joe", "jack"), null);
        var patch = newPatchRequest(REPLACE, "members", "[{\"value\":\"foo\"},{\"value\":\"bar\"}]");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People", List.of("foo", "bar"), List.of());
    }

    // If the target location path specifies an attribute that does not exist, the service provider SHALL treat the
    // operation as an "add".
    @Test
    public void testReplace4() throws JsonProcessingException, ScimException {
        newGroupModel("people", null, null, null);
        var patch = newPatchRequest(REPLACE, "displayName", "\"People\"");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People");
    }

    // If the target location specifies a complex attribute, a set of sub-attributes SHALL be specified in the "value"
    // parameter, which replaces any existing values or adds where an attribute did not previously exist. Sub-attributes
    // that are not specified in the "value" parameter are left unchanged.
    // => Doesn't apply to Group resource

    // If the target location is a multi-valued attribute and a value selection ("valuePath") filter is specified that
    // matches one or more values of the multi-valued attribute, then all matching record values SHALL be replaced.
    @Test
    public void testReplace6() throws JsonProcessingException, ScimException {
        newGroupModel("people", "People", List.of("joe", "joey"), List.of());
        var patch = newPatchRequest(REPLACE, "members[value ew \"oey\"]", "{\"value\":\"jack\"}");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People", List.of("joe", "jack"), List.of());
    }

    // If the target location is a complex multi-valued attribute with a value selection filter ("valuePath") and a
    // specific sub-attribute (e.g., "addresses[type eq "work"].streetAddress"), the matching sub-attribute of all
    // matching records is replaced.
    @Test
    public void testReplace7() throws JsonProcessingException, ScimException {
        newGroupModel("people", "People", List.of("joe", "joey"), List.of());
        var patch = newPatchRequest(REPLACE, "members[value ew \"oey\"].value", "\"jack\"");
        var groupModel = mappingService.patchNuxeoGroup("people", patch);
        checkGroupModel(groupModel, "people", "People", List.of("joe", "jack"), List.of());
    }

    // If the target location is a multi-valued attribute for which a value selection filter ("valuePath") has been
    // supplied and no record match was made, the service provider SHALL indicate failure by returning HTTP status code
    // 400 and a "scimType" error code of "noTarget".
    @Test
    public void testReplace8() throws JsonProcessingException, ScimException {
        newGroupModel("people", "People", List.of("joe", "joey"), List.of());
        var patch = newPatchRequest(REPLACE, "members[value ew \"foo\"]", "{\"value\":\"bar\"}");
        assertThrows(BadRequestException.class, () -> mappingService.patchNuxeoGroup("people", patch));
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

    protected DocumentModel newGroupModel(String groupName, String groupLabel) {
        Objects.requireNonNull(groupName);
        var groupModel = userManager.getBareGroupModel();
        groupModel.setPropertyValue("group:groupname", groupName);
        groupModel.setPropertyValue("group:grouplabel", groupLabel);
        groupModel = userManager.createGroup(groupModel);
        return groupModel;
    }

    protected DocumentModel newGroupModel(String groupName, String groupLabel, List<String> members,
            List<String> subGroups) {
        var groupModel = newGroupModel(groupName, groupLabel);
        if (members != null) {
            for (String member : members) {
                var userModel = userManager.getBareUserModel();
                userModel.setPropertyValue("username", member);
                userManager.createUser(userModel);
            }
        }
        groupModel.setPropertyValue("group:members", (Serializable) members);
        if (subGroups != null) {
            for (String subGroup : subGroups) {
                var subGroupModel = userManager.getBareGroupModel();
                subGroupModel.setPropertyValue("groupname", subGroup);
                userManager.createGroup(subGroupModel);
            }
        }
        groupModel.setPropertyValue("group:subGroups", (Serializable) subGroups);
        userManager.updateGroup(groupModel);
        return groupModel;
    }

    protected void checkGroupModel(DocumentModel groupModel, String groupName, String groupLabel) {
        assertEquals(groupName, groupModel.getPropertyValue("group:groupname"));
        assertEquals(groupLabel, groupModel.getPropertyValue("group:grouplabel"));
    }

    protected void checkGroupModel(DocumentModel groupModel, String groupName, String groupLabel, List<String> members,
            List<String> subGroups) {
        checkGroupModel(groupModel, groupName, groupLabel);
        assertEquals(members, groupModel.getPropertyValue("group:members"));
        assertEquals(subGroups, groupModel.getPropertyValue("group:subGroups"));
    }
}

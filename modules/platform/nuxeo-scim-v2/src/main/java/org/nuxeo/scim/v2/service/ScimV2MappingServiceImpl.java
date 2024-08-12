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
package org.nuxeo.scim.v2.service;

import static com.unboundid.scim2.common.exceptions.BadRequestException.INVALID_PATH;
import static com.unboundid.scim2.common.exceptions.BadRequestException.INVALID_VALUE;
import static com.unboundid.scim2.common.exceptions.BadRequestException.NO_TARGET;
import static com.unboundid.scim2.common.messages.PatchOpType.REMOVE;
import static com.unboundid.scim2.common.messages.PatchOpType.REPLACE;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.eq;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.gt;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.gte;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.ilike;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.isnotnull;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.like;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.lt;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.lte;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.not;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.noteq;
import static org.nuxeo.ecm.core.query.sql.model.Predicates.notilike;
import static org.nuxeo.scim.v2.api.ScimV2QueryContext.FETCH_GROUP_MEMBERS_CTX_PARAM;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_GROUP;
import static org.nuxeo.scim.v2.api.ScimV2ResourceType.SCIM_V2_RESOURCE_TYPE_USER;

import java.beans.IntrospectionException;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.function.ThrowableFunction;
import org.nuxeo.common.utils.DateUtils;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.QueryBuilder;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.platform.usermanager.NuxeoGroupImpl;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.platform.usermanager.UserManagerHelper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.scim.v2.api.ScimV2Helper;
import org.nuxeo.scim.v2.api.ScimV2Mapping;
import org.nuxeo.scim.v2.api.ScimV2MappingService;
import org.nuxeo.scim.v2.api.ScimV2QueryContext;
import org.nuxeo.scim.v2.api.ScimV2ResourceType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.Path;
import com.unboundid.scim2.common.Path.Element;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.NotImplementedException;
import com.unboundid.scim2.common.exceptions.ResourceConflictException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.ServerErrorException;
import com.unboundid.scim2.common.filters.Filter;
import com.unboundid.scim2.common.filters.NotFilter;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.messages.PatchOpType;
import com.unboundid.scim2.common.messages.PatchOperation;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;
import com.unboundid.scim2.common.types.UserResource;
import com.unboundid.scim2.common.utils.JsonUtils;
import com.unboundid.scim2.common.utils.Parser;
import com.unboundid.scim2.common.utils.SchemaUtils;

/**
 * This service is used to register the SCIM v2 mapping class.
 *
 * @since 2023.14
 */
public class ScimV2MappingServiceImpl extends DefaultComponent implements ScimV2MappingService {

    private static final Logger log = LogManager.getLogger(ScimV2MappingServiceImpl.class);

    public static final String GROUP_MEMBERS = "members";

    public static final String GROUP_MEMBERS_TYPE = "type";

    protected Deque<ScimV2MappingDescriptor> descriptors = new LinkedList<>();

    protected List<String> groupCaseSensitiveFields = new ArrayList<>();

    protected ScimV2Mapping mapping;

    protected List<String> userCaseSensitiveFields = new ArrayList<>();

    @Override
    public void activate(ComponentContext context) {
        log.info("ScimV2MappingService activated");
    }

    @Override
    public DocumentModel createNuxeoGroupFromGroupResource(GroupResource group) throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        // create new group
        DocumentModel newGroup = um.getBareGroupModel();
        newGroup.setProperty(um.getGroupSchemaName(), um.getGroupIdField(), UUID.randomUUID().toString());
        newGroup = getMapping().beforeCreateGroup(newGroup, group);
        newGroup = um.createGroup(newGroup);
        return getMapping().afterCreateGroup(newGroup, group);
    }

    @Override
    public DocumentModel createNuxeoUserFromUserResource(UserResource user) throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        String userId = user.getUserName();
        if (um.getUserModel(userId) != null) {
            throw new ResourceConflictException("Cannot create user with existing uid: " + userId, "uniqueness", null);
        }
        // create new user
        DocumentModel newUser = um.getBareUserModel();
        newUser.setProperty(um.getUserSchemaName(), um.getUserIdField(), userId);
        newUser = getMapping().beforeCreateUser(newUser, user);
        newUser = um.createUser(newUser);
        return getMapping().afterCreateUser(newUser, user);
    }

    @Override
    public void deactivate(ComponentContext context) {
        log.info("ScimV2MappingService deactivated");
    }

    @Override
    public GroupResource getGroupResourceFromNuxeoGroup(DocumentModel groupModel, String baseURL) throws ScimException {
        return getMapping().getGroupResourceFromNuxeoGroup(groupModel, baseURL);
    }

    @Override
    public ScimV2Mapping getMapping() {
        if (mapping == null) {
            Class<?> klass = descriptors.getLast().getScimV2MappingClass();
            if (klass == null) {
                throw new NuxeoException("No class specified for the ScimV2MappingService");
            }
            try {
                mapping = (ScimV2Mapping) klass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new NuxeoException("Failed to instantiate class " + klass, e);
            }
        }
        return mapping;
    }

    @Override
    public UserResource getUserResourceFromNuxeoUser(DocumentModel groupModel, String baseURL) throws ScimException {
        return getMapping().getUserResourceFromNuxeoUser(groupModel, baseURL);
    }

    @Override
    public void patchNuxeoGroup(String uid, PatchRequest patch) throws ScimException {
        // don't load group members and subgroups, potentially large lists
        DocumentModel groupModel = ScimV2Helper.getGroupModel(uid, false);
        patchGroup(groupModel, patch);
    }

    @Override
    public DocumentModel patchNuxeoUser(String uid, PatchRequest patch) throws ScimException {
        DocumentModel userModel = ScimV2Helper.getUserModel(uid);
        UserResource userResource = getUserResourceFromNuxeoUser(userModel, null);
        userResource = (UserResource) patchScimResource(userResource, patch);
        userModel = getMapping().beforeUpdateUser(userModel, userResource);
        Framework.getService(UserManager.class).updateUser(userModel);
        return getMapping().afterUpdateUser(userModel, userResource);
    }

    @Override
    public ListResponse<ScimResource> queryGroups(ScimV2QueryContext queryCtx) throws ScimException {
        return queryResources(queryCtx, SCIM_V2_RESOURCE_TYPE_GROUP);
    }

    @Override
    public ListResponse<ScimResource> queryUsers(ScimV2QueryContext queryCtx) throws ScimException {
        return queryResources(queryCtx, SCIM_V2_RESOURCE_TYPE_USER);
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        descriptors.add((ScimV2MappingDescriptor) contribution);
    }

    @Override
    public void start(ComponentContext context) {
        try {
            userCaseSensitiveFields = SchemaUtils.getAttributes(UserResource.class)
                                                 .stream()
                                                 .filter(a -> a.isCaseExact())
                                                 .map(a -> a.getName().toLowerCase())
                                                 .toList();
            groupCaseSensitiveFields = SchemaUtils.getAttributes(GroupResource.class)
                                                  .stream()
                                                  .filter(a -> a.isCaseExact())
                                                  .map(a -> a.getName().toLowerCase())
                                                  .toList();
        } catch (IntrospectionException e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        descriptors.remove(contribution);
    }

    @Override
    public DocumentModel updateNuxeoGroupFromGroupResource(String uid, GroupResource group) throws ScimException {
        DocumentModel groupModel = ScimV2Helper.getGroupModel(uid, true);
        groupModel = getMapping().beforeUpdateGroup(groupModel, group);
        Framework.getService(UserManager.class).updateGroup(groupModel);
        return getMapping().afterUpdateGroup(groupModel, group);
    }

    @Override
    public DocumentModel updateNuxeoUserFromUserResource(String uid, UserResource user) throws ScimException {
        DocumentModel userModel = ScimV2Helper.getUserModel(uid);
        userModel = getMapping().beforeUpdateUser(userModel, user);
        Framework.getService(UserManager.class).updateUser(userModel);
        return getMapping().afterUpdateUser(userModel, user);
    }

    protected void addMembersToNuxeoGroup(DocumentModel groupModel, List<Member> members, boolean resetGroup) {
        if (resetGroup) {
            resetGroupMembers(groupModel);
        }
        UserManager um = Framework.getService(UserManager.class);
        NuxeoGroup group = new NuxeoGroupImpl(groupModel, um.getGroupConfig());
        members.stream().filter(m -> m.getValue() != null).forEach(member -> {
            String value = member.getValue();
            if (SCIM_V2_RESOURCE_TYPE_GROUP.equalsString(member.getType())) {
                // subgroup
                NuxeoGroup subgroup = um.getGroup(value);
                UserManagerHelper.addGroupToGroup(subgroup, group.getName());
            } else {
                // user
                NuxeoPrincipal principal = um.getPrincipal(value);
                UserManagerHelper.addUserToGroup(principal, group, true);
            }
        });
    }

    protected String getAttribute(Filter filter) {
        var path = filter.getAttributePath();
        if (path == null) {
            return null;
        }
        final StringBuilder builder = new StringBuilder();
        Iterator<Element> it = path.iterator();
        while (it.hasNext()) {
            it.next().toString(builder);
            if (it.hasNext()) {
                builder.append(".");
            }
        }
        return builder.toString();
    }

    protected Predicate getPredicate(Filter filter, ScimV2ResourceType type, BiFunction<String, Object, String> colMap)
            throws ScimException {
        var value = getValue(filter);
        var attribute = getAttribute(filter);
        return switch (filter.getFilterType()) {
            case AND -> new MultiExpression(Operator.AND,
                    filter.getCombinedFilters()
                          .stream()
                          .map(ThrowableFunction.asFunction(f -> getPredicate(f, type, colMap)))
                          .toList());
            case OR -> new MultiExpression(Operator.OR,
                    filter.getCombinedFilters()
                          .stream()
                          .map(ThrowableFunction.asFunction(f -> getPredicate(f, type, colMap)))
                          .toList());
            case NOT -> not(getPredicate(((NotFilter) filter).getInvertedFilter(), type, colMap));
            case PRESENT -> isnotnull(colMap.apply(attribute, value));
            case STARTS_WITH -> {
                if (isCaseSensitive(attribute, type)) {
                    yield like(colMap.apply(attribute, value), value + "%");
                } else {
                    yield ilike(colMap.apply(attribute, value), value + "%");
                }
            }
            case ENDS_WITH -> {
                if (isCaseSensitive(attribute, type)) {
                    yield like(colMap.apply(attribute, value), "%" + value);
                } else {
                    yield ilike(colMap.apply(attribute, value), "%" + value);
                }
            }
            case CONTAINS -> {
                if (isCaseSensitive(attribute, type)) {
                    yield like(colMap.apply(attribute, value), "%" + value + "%");
                } else {
                    yield ilike(colMap.apply(attribute, value), "%" + value + "%");
                }
            }
            case EQUAL -> {
                if (value instanceof String && !isCaseSensitive(attribute, type)) {
                    yield ilike(colMap.apply(attribute, value), value);
                } else {
                    yield eq(colMap.apply(attribute, value), value);
                }
            }
            case NOT_EQUAL -> {
                if (value instanceof String && !isCaseSensitive(attribute, type)) {
                    yield notilike(colMap.apply(attribute, value), value);
                } else {
                    yield noteq(colMap.apply(attribute, value), value);
                }
            }
            case GREATER_THAN -> gt(colMap.apply(attribute, value), value);
            case GREATER_OR_EQUAL -> gte(colMap.apply(attribute, value), value);
            case LESS_THAN -> lt(colMap.apply(attribute, value), value);
            case LESS_OR_EQUAL -> lte(colMap.apply(attribute, value), value);
            default -> throw new NotImplementedException("Unsupported filter type: " + filter.getFilterType());
        };
    }

    protected QueryBuilder getQueryBuilder(Integer startIndex, Integer count, String filterString, String sortBy,
            boolean descending, ScimV2ResourceType type, BiFunction<String, Object, String> columnMapper)
            throws ScimException {
        QueryBuilder queryBuilder = new QueryBuilder();
        queryBuilder.countTotal(true);
        Predicate predicate = parseFilters(filterString, type, columnMapper);
        if (predicate != null) {
            queryBuilder.predicate(predicate);
        }
        if (sortBy != null) {
            queryBuilder.order(new OrderByExpr(new Reference(columnMapper.apply(sortBy, null)), descending));
        }
        var offset = 0;
        if (startIndex != null && startIndex > 0) {
            // startIndex is a 1-based index
            offset = startIndex - 1;
        }
        queryBuilder.offset(offset);
        if (count != null) {
            queryBuilder.limit(count < 0 ? 0 : count);
        }
        return queryBuilder;
    }

    protected QueryBuilder getQueryBuilder(Filter filter, ScimV2ResourceType type,
            BiFunction<String, Object, String> columnMapper) throws ScimException {
        QueryBuilder queryBuilder = new QueryBuilder();
        Predicate predicate = getPredicate(filter, type, columnMapper);
        if (predicate != null) {
            queryBuilder.predicate(predicate);
        }
        return queryBuilder;
    }

    protected Object getValue(Filter filter) {
        ValueNode vn = filter.getComparisonValue();
        if (vn == null) {
            return null;
        } else if (vn instanceof TextNode tn) {
            var v = tn.asText();
            try {
                // may be a string representing an ISO date time
                return DateUtils.parseISODateTime(v);
            } catch (DateTimeException e) {
                return v;
            }
        } else if (vn instanceof NumericNode nn) {
            return nn.numberValue();
        } else if (vn instanceof BooleanNode bn) {
            return bn.asBoolean();
        }
        return vn.toString();
    }

    protected void handleNoGroupMemberMatch(List<Member> members, PatchOperation op) throws BadRequestException {
        if (members.isEmpty()) {
            throw new BadRequestException("Found no member of type Group matching the operation path filter: " + op,
                    INVALID_PATH);
        }
    }

    protected boolean isCaseSensitive(String attribute, ScimV2ResourceType type) {
        return switch (type) {
            case SCIM_V2_RESOURCE_TYPE_USER -> userCaseSensitiveFields.contains(attribute.toLowerCase());
            case SCIM_V2_RESOURCE_TYPE_GROUP -> groupCaseSensitiveFields.contains(attribute.toLowerCase());
            default -> true;
        };
    }

    protected String mapGroupColumnName(String column, Object filterValue) {
        return getMapping().getGroupAttributeName(column, filterValue);
    }

    protected String mapGroupMemberAttributeName(String scimAttribute, Object filterValue) {
        return getMapping().getGroupMemberAttributeName(scimAttribute, filterValue);
    }

    protected String mapUserColumnName(String column, Object filterValue) {
        return getMapping().getUserAttributeName(column, filterValue);
    }

    protected String mapUserMemberAttributeName(String scimAttribute, Object filterValue) {
        return getMapping().getUserMemberAttributeName(scimAttribute, filterValue);
    }

    protected Predicate parseFilters(String filterString, ScimV2ResourceType type,
            BiFunction<String, Object, String> columnMapper) throws ScimException {
        if (StringUtils.isBlank(filterString)) {
            return null;
        }
        return getPredicate(Parser.parseFilter(filterString), type, columnMapper);
    }

    /**
     * Patches the given group model according to the given patch request, e.g.:
     *
     * <pre>{@code
     * {
     *   "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
     *   "Operations":[
     *     {
     *       "op":"replace",
     *       "path":"displayName",
     *       "value":"updatedDisplayName"
     *     },
     *     {
     *       "op":"add",
     *       "path":"members",
     *       "value":[
     *         {
     *           "value":"joe"
     *         },
     *         {
     *           "value":"jack"
     *         }
     *       ]
     *     }
     *   ]
     * }
     * }</pre>
     *
     * The attributes other than "members", such as "displayName", are patched by using JSON patch, as provided by
     * {@link PatchOperation#apply(ObjectNode)}. The patch operation is applied to the {@link GroupResource}
     * representing the group model. Then, the group model is updated following the mapping defined by
     * {@link ScimV2Mapping#patchGroup(DocumentModel, GroupResource)}.
     * <p>
     * The "members" attribute is patched by using directly the {@link UserManager} to update the "members" and
     * "subGroups" properties of the given group model. Basically, we iterate on the member ids passed in the patch
     * request ("value" sub-attribute) to incrementally add/remove/replace each user member (relying in its groups) or
     * subgroup (relying on its parent groups).
     * <p>
     * This allows to never fetch the Nuxeo group's members and subgroups. Thus, it avoids storing potentially large
     * lists of users/group {@link DocumentModel}s in memory and the JSON serialization of such large lists.
     */
    protected void patchGroup(DocumentModel groupModel, PatchRequest patch) throws ScimException {
        Iterator<PatchOperation> operationIt = patch.iterator();
        while (operationIt.hasNext()) {
            PatchOperation op = operationIt.next();
            Path path = op.getPath();
            if (path == null || path.isRoot()) {
                // target location defined in value
                patchGroupWithoutPath(groupModel, op);
            } else {
                patchGroupWithPath(groupModel, op);
            }
        }
    }

    /**
     * Patches the given group model according to the given patch operation using JSON patch.
     */
    protected void patchGroupJSONPatch(DocumentModel groupModel, PatchOperation op) throws ScimException {
        GroupResource groupResource = getGroupResourceFromNuxeoGroup(groupModel, null);
        groupResource = (GroupResource) patchScimResource(groupResource, op);
        groupModel = getMapping().patchGroup(groupModel, groupResource);
        Framework.getService(UserManager.class).updateGroup(groupModel);
    }

    /**
     * Patches the given group model according to the given patch operation without any "path" attribute, e.g.:
     *
     * <pre>{@code
     * {
     *   "op":"add",
     *   "value":{
     *     "displayName":"updatedDisplayName"
     *     "members":[
     *       {
     *         "value":"joe"
     *       },
     *       {
     *         "value":"jack"
     *       }
     *     ]
     *   }
     * }
     * }</pre>
     */
    protected void patchGroupWithoutPath(DocumentModel groupModel, PatchOperation op) throws ScimException {
        PatchOpType opType = op.getOpType();
        if (REMOVE.equals(opType)) {
            throw new BadRequestException("A path must be specified in a \"remove\" patch operation: " + op, NO_TARGET);
        }
        JsonNode value = op.getJsonNode();
        if (value == null) {
            throw new BadRequestException(
                    "A value must be specified in a patch operation with an unspecified path: " + op, INVALID_VALUE);
        }
        if (!value.isObject()) {
            throw new BadRequestException(
                    "The value of a patch operation with an unspecified path must be an object: " + op, INVALID_VALUE);
        }

        if (!value.has(GROUP_MEMBERS)) {
            // we can safely handle all attributes using JSON patch
            patchGroupJSONPatch(groupModel, op);
            return;
        }

        JsonNode membersNode = ((ObjectNode) value).remove(GROUP_MEMBERS);
        if (!membersNode.isArray()) {
            throw new BadRequestException(
                    "The \"members\" attribute in the value of a patch operation must be an array: " + op,
                    INVALID_VALUE);
        }
        // first handle attributes other than "members" using JSON patch
        if (!value.isEmpty()) {
            PatchOperation subOperation = PatchOperation.create(opType, op.getPath(), value);
            patchGroupJSONPatch(groupModel, subOperation);
        }
        // then handle the "members" attribute manually
        try {
            List<Member> members = JsonUtils.nodeToValues((ArrayNode) membersNode, Member.class);
            addMembersToNuxeoGroup(groupModel, members, REPLACE.equals(opType));
        } catch (JsonProcessingException e) {
            throw new ServerErrorException(
                    "The \"members\" attribute in the value of a patch operation with an unspecified path must be an array of Member objects: "
                            + op,
                    INVALID_VALUE, e);
        }
    }

    /**
     * Patches the given group model according to the given patch operation with a "path" attribute, e.g.:
     *
     * <pre>{@code
     * {
     *   "op":"add",
     *   "path":"members"
     *    "value":[
     *      {
     *        "value":"joe"
     *       },
     *       {
     *         "value":"jack"
     *       }
     *    ]
     * }
     * }</pre>
     */
    protected void patchGroupWithPath(DocumentModel groupModel, PatchOperation op) throws ScimException {
        Path path = op.getPath();
        if (path.size() > 1) {
            throw new BadRequestException(
                    "The path of a patch operation on a group resource cannot have more than one element, "
                            + "the only complex multi-valued attribute of a Group resource is \"members\", whose sub-attributes are all immutable: "
                            + op,
                    INVALID_PATH);
        }

        Element firstElement = path.getElement(0);
        String attribute = firstElement.getAttribute();
        if (!GROUP_MEMBERS.equals(attribute)) {
            // we can safely handle all attributes using JSON patch
            patchGroupJSONPatch(groupModel, op);
            return;
        }

        Filter filter = firstElement.getValueFilter();
        if (filter == null) {
            // "path":"members"
            patchGroupWithoutFilter(groupModel, op);
        } else {
            if (filter.toString().contains(GROUP_MEMBERS_TYPE)) {
                // "path":"members[type eq ...]"
                throw new NotImplementedException(
                        "The \"type\" attribute in a value selection filter of a patch operation is not handled");
            }
            // "path":"members[value eq ...]"
            patchGroupWithFilter(groupModel, op, filter);
        }
    }

    /**
     * Patches the given group model according to the given patch operation with a "path" attribute and no value filter
     * specified, e.g.:
     *
     * <pre>{@code
     * {
     *   "op":"remove",
     *   "path":"members"
     * }
     * }</pre>
     */
    protected void patchGroupWithoutFilter(DocumentModel groupModel, PatchOperation op) throws ScimException {
        PatchOpType opType = op.getOpType();
        switch (opType) { // NOSONAR
            case ADD, REPLACE -> {
                try {
                    List<Member> members = op.getValues(Member.class);
                    addMembersToNuxeoGroup(groupModel, members, REPLACE.equals(opType));
                } catch (JsonProcessingException e) {
                    throw new ServerErrorException(
                            "The value of an \"add\" or \"replace\" patch operation with a \"members\" path must be an array of Member objects: "
                                    + op,
                            INVALID_VALUE, e);
                }
            }
            case REMOVE -> resetGroupMembers(groupModel);
        }
    }

    /**
     * Patches the given group model according to the given patch operation with a "path" attribute and a value filter
     * specified, e.g.:
     *
     * <pre>{@code
     * {
     *   "op":"remove",
     *   "path":"members[value sw \"userIdPrefix\"]"
     * }
     * }</pre>
     */
    protected void patchGroupWithFilter(DocumentModel groupModel, PatchOperation op, Filter filter)
            throws ScimException {
        PatchOpType opType = op.getOpType();
        switch (opType) {
            case ADD -> throw new BadRequestException(
                    "The path of an \"add\" patch operation must not include any value selection filters: " + op,
                    INVALID_PATH);
            case REMOVE -> {
                List<Member> members = new ArrayList<>();
                members.addAll(searchUserMembers(filter));
                members.addAll(searchGroupMembers(filter));
                removeMembersFromNuxeoGroup(groupModel, members);
            }
            case REPLACE -> {
                try {
                    Member member = op.getValue(Member.class);
                    if (SCIM_V2_RESOURCE_TYPE_GROUP.equalsString(member.getType())) {
                        List<Member> groupMembers = searchGroupMembers(filter);
                        handleNoGroupMemberMatch(groupMembers, op);
                        removeMembersFromNuxeoGroup(groupModel, groupMembers);
                        addMembersToNuxeoGroup(groupModel, List.of(member), false);
                    } else {
                        List<Member> userMembers = searchUserMembers(filter);
                        handleNoGroupMemberMatch(userMembers, op);
                        removeMembersFromNuxeoGroup(groupModel, userMembers);
                        addMembersToNuxeoGroup(groupModel, List.of(member), false);
                    }
                } catch (JsonProcessingException e) {
                    throw new ServerErrorException(
                            "The value of a \"replace\" patch operation with a \"members\" path must be a Member object: "
                                    + op,
                            INVALID_VALUE, e);
                }
            }
        }
    }

    protected ScimResource patchScimResource(ScimResource resource, PatchRequest patch) throws ScimException {
        GenericScimResource genericResource = resource.asGenericScimResource();
        try {
            patch.apply(genericResource);
        } catch (NullPointerException e) {
            // Case of path not provided for a REMOVE operation
            throw new BadRequestException(
                    "Cannot patch SCIM resource: " + resource.getId() + " with patch request: " + patch, NO_TARGET, e); // NOSONAR
        }
        ObjectNode node = genericResource.getObjectNode();
        try {
            return JsonUtils.nodeToValue(node, resource.getClass());
        } catch (JsonProcessingException e) {
            throw new ServerErrorException(
                    "Cannot patch SCIM resource: " + resource.getId() + " with patch request: " + patch, null, e);
        }
    }

    protected ScimResource patchScimResource(ScimResource resource, PatchOperation patchOperation)
            throws ScimException {
        GenericScimResource genericResource = resource.asGenericScimResource();
        ObjectNode node = genericResource.getObjectNode();
        try {
            patchOperation.apply(node);
        } catch (NullPointerException e) {
            // Case of path not provided for a REMOVE operation
            throw new BadRequestException(
                    "Cannot patch SCIM resource: " + resource.getId() + " with patch operation: " + patchOperation,
                    NO_TARGET, e);
        }
        try {
            return JsonUtils.nodeToValue(node, resource.getClass());
        } catch (JsonProcessingException e) {
            throw new ServerErrorException(
                    "Cannot patch SCIM resource: " + resource.getId() + " with patch operation: " + patchOperation,
                    null, e);
        }
    }

    protected ListResponse<ScimResource> queryResources(ScimV2QueryContext queryCtx, ScimV2ResourceType type)
            throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        DocumentModelList list = switch (type) {
            case SCIM_V2_RESOURCE_TYPE_USER -> um.searchUsers(
                    getQueryBuilder(queryCtx.getStartIndex(), queryCtx.getCount(), queryCtx.getFilterString(),
                            queryCtx.getSortBy(), queryCtx.isDescending(), type, this::mapUserColumnName));
            case SCIM_V2_RESOURCE_TYPE_GROUP -> um.searchGroups(
                    getQueryBuilder(queryCtx.getStartIndex(), queryCtx.getCount(), queryCtx.getFilterString(),
                            queryCtx.getSortBy(), queryCtx.isDescending(), type, this::mapGroupColumnName));
            default -> throw new NotImplementedException("Unsupported resource type: " + type);
        };
        int totalResults;
        try {
            totalResults = Math.toIntExact(list.totalSize());
        } catch (ArithmeticException e) {
            totalResults = -1;
        }
        return new ListResponse<>(totalResults, list.stream().map(ThrowableFunction.asFunction(model -> switch (type) {
            case SCIM_V2_RESOURCE_TYPE_USER -> {
                // searchUsers lazy fetches attributes such as groups
                model = um.getUserModel(model.getId());
                yield queryCtx.getTransform().apply(getUserResourceFromNuxeoUser(model, queryCtx.getBaseURL()));
            }
            case SCIM_V2_RESOURCE_TYPE_GROUP -> {
                if (!queryCtx.isContextParamFalse(FETCH_GROUP_MEMBERS_CTX_PARAM)) {
                    // searchGroups lazy fetches attributes such as members
                    model = ScimV2Helper.getGroupModel(model.getId(), true);
                }
                yield queryCtx.getTransform().apply(getGroupResourceFromNuxeoGroup(model, queryCtx.getBaseURL()));
            }
            default -> throw new NotImplementedException("Unsupported resource type: " + type);
        })).toList(), queryCtx.getStartIndex() != null ? queryCtx.getStartIndex() : 1, queryCtx.getCount());
    }

    protected void removeMembersFromNuxeoGroup(DocumentModel groupModel, List<Member> members) {
        UserManager um = Framework.getService(UserManager.class);
        NuxeoGroup group = new NuxeoGroupImpl(groupModel, um.getGroupConfig());
        members.stream().filter(m -> m.getValue() != null).forEach(member -> {
            String value = member.getValue();
            if (SCIM_V2_RESOURCE_TYPE_GROUP.equalsString(member.getType())) {
                // subgroup
                NuxeoGroup subgroup = um.getGroup(value);
                UserManagerHelper.removeGroupFromGroup(subgroup, group.getName());
            } else {
                // user
                NuxeoPrincipal principal = um.getPrincipal(value);
                UserManagerHelper.removeUserFromGroup(principal, group, true);
            }
        });
    }

    protected void resetGroupMembers(DocumentModel groupModel) {
        UserManager um = Framework.getService(UserManager.class);
        String groupSchemaName = um.getGroupSchemaName();
        groupModel.setProperty(groupSchemaName, um.getGroupMembersField(), List.of());
        groupModel.setProperty(groupSchemaName, um.getGroupSubGroupsField(), List.of());
        um.updateGroup(groupModel);
    }

    protected List<Member> searchGroupMembers(Filter filter) throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        return um.searchGroups(getQueryBuilder(filter, SCIM_V2_RESOURCE_TYPE_GROUP, this::mapGroupMemberAttributeName))
                 .stream()
                 .map(DocumentModel::getId)
                 .map(id -> new Member().setType(SCIM_V2_RESOURCE_TYPE_GROUP.toString()).setValue(id))
                 .toList();
    }

    protected List<Member> searchUserMembers(Filter filter) throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        return um.searchUsers(getQueryBuilder(filter, SCIM_V2_RESOURCE_TYPE_USER, this::mapUserMemberAttributeName))
                 .stream()
                 .map(DocumentModel::getId)
                 .map(id -> new Member().setType(SCIM_V2_RESOURCE_TYPE_USER.toString()).setValue(id))
                 .toList();
    }
}

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

import static com.unboundid.scim2.common.exceptions.BadRequestException.NO_TARGET;
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
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.function.ThrowableFunction;
import org.nuxeo.common.utils.DateUtils;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.QueryBuilder;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.scim.v2.api.ScimV2Mapping;
import org.nuxeo.scim.v2.api.ScimV2MappingService;
import org.nuxeo.scim.v2.api.ScimV2ResourceType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.unboundid.scim2.common.GenericScimResource;
import com.unboundid.scim2.common.Path.Element;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.NotImplementedException;
import com.unboundid.scim2.common.exceptions.ResourceConflictException;
import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.exceptions.ServerErrorException;
import com.unboundid.scim2.common.filters.Filter;
import com.unboundid.scim2.common.filters.NotFilter;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.GroupResource;
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

    protected static final int DEFAULT_QUERY_COUNT = 100;

    public static final int LIMIT_QUERY_COUNT = 1000;

    protected static String getAttribute(Filter filter) {
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

    protected static Object getValue(Filter filter) {
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
        getMapping().beforeCreateGroup(newGroup, group);
        newGroup = um.createGroup(newGroup);
        newGroup = getMapping().afterCreateGroup(newGroup, group);
        return newGroup;
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
        newUser = getMapping().afterCreateUser(newUser, user);
        return newUser;
    }

    @Override
    public void deactivate(ComponentContext context) {
        log.info("ScimV2MappingService deactivated");
    }

    @Override
    public GroupResource getGroupResourceFromNuxeoGroup(DocumentModel userModel, String baseURL) throws ScimException {
        return getMapping().getGroupResourceFromNuxeoGroup(userModel, baseURL);
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
    public DocumentModel patchNuxeoUser(String uid, PatchRequest patch) throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        DocumentModel userModel = getUserModel(um, uid);
        UserResource userResource = getUserResourceFromNuxeoUser(userModel, null);
        userResource = (UserResource) patchScimResource(userResource, patch);
        userModel = getMapping().beforeUpdateUser(userModel, userResource);
        um.updateUser(userModel);
        userModel = getMapping().afterUpdateUser(userModel, userResource);
        return userModel;
    }

    @Override
    public ListResponse<ScimResource> queryGroups(Integer startIndex, Integer count, String filterString, String sortBy,
            boolean descending, String baseURL, UnaryOperator<ScimResource> transform) throws ScimException {
        return queryResources(startIndex, count, filterString, sortBy, descending, baseURL, SCIM_V2_RESOURCE_TYPE_GROUP,
                transform);
    }

    @Override
    public ListResponse<ScimResource> queryUsers(Integer startIndex, Integer count, String filterString, String sortBy,
            boolean descending, String baseURL, UnaryOperator<ScimResource> transform) throws ScimException {
        return queryResources(startIndex, count, filterString, sortBy, descending, baseURL, SCIM_V2_RESOURCE_TYPE_USER,
                transform);
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
        UserManager um = Framework.getService(UserManager.class);
        DocumentModel groupModel = getGroupModel(um, uid);
        groupModel = getMapping().beforeUpdateGroup(groupModel, group);
        um.updateGroup(groupModel);
        groupModel = getMapping().afterUpdateGroup(groupModel, group);
        return groupModel;
    }

    @Override
    public DocumentModel updateNuxeoUserFromUserResource(String uid, UserResource user) throws ScimException {
        UserManager um = Framework.getService(UserManager.class);
        DocumentModel userModel = getUserModel(um, uid);
        userModel = getMapping().beforeUpdateUser(userModel, user);
        um.updateUser(userModel);
        userModel = getMapping().afterUpdateUser(userModel, user);
        return userModel;
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

    protected String mapUserColumnName(String column, Object filterValue) {
        return getMapping().getUserAttributeName(column, filterValue);
    }

    protected Predicate parseFilters(String filterString, ScimV2ResourceType type,
            BiFunction<String, Object, String> columnMapper) throws ScimException {
        if (StringUtils.isBlank(filterString)) {
            return null;
        }
        return getPredicate(Parser.parseFilter(filterString), type, columnMapper);
    }

    protected ListResponse<ScimResource> queryResources(Integer startIndex, Integer count, String filterString, // NOSONAR
            String sortBy, boolean descending, String baseURL, ScimV2ResourceType type,
            UnaryOperator<ScimResource> transform) throws ScimException {
        if (count == null) {
            count = DEFAULT_QUERY_COUNT;
        } else if (count > LIMIT_QUERY_COUNT) {
            throw BadRequestException.tooMany("Maximum value for count is " + LIMIT_QUERY_COUNT);
        }
        UserManager um = Framework.getService(UserManager.class);
        DocumentModelList list = switch (type) {
            case SCIM_V2_RESOURCE_TYPE_USER -> Framework.getService(UserManager.class)
                                                        .searchUsers(getQueryBuilder(startIndex, count, filterString,
                                                                sortBy, descending, type, this::mapUserColumnName));
            case SCIM_V2_RESOURCE_TYPE_GROUP -> Framework.getService(UserManager.class)
                                                         .searchGroups(getQueryBuilder(startIndex, count, filterString,
                                                                 sortBy, descending, type, this::mapGroupColumnName));
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
                yield transform.apply(getUserResourceFromNuxeoUser(model, baseURL));
            }
            case SCIM_V2_RESOURCE_TYPE_GROUP -> {
                // searchGroups lazy fetches attributes such as members
                model = um.getGroupModel(model.getId());
                yield transform.apply(getGroupResourceFromNuxeoGroup(model, baseURL));
            }
            default -> throw new NotImplementedException("Unsupported resource type: " + type);
        })).toList(), startIndex != null ? startIndex : 1, count);
    }

    protected DocumentModel getGroupModel(UserManager userManager, String uid) throws ResourceNotFoundException {
        DocumentModel groupModel = userManager.getGroupModel(uid);
        if (groupModel == null) {
            throw new ResourceNotFoundException("Cannot find group: " + uid);
        }
        return groupModel;
    }

    protected DocumentModel getUserModel(UserManager userManager, String uid) throws ResourceNotFoundException {
        DocumentModel userModel = userManager.getUserModel(uid);
        if (userModel == null) {
            throw new ResourceNotFoundException("Cannot find user: " + uid);
        }
        return userModel;
    }

    protected ScimResource patchScimResource(ScimResource resource, PatchRequest patch) throws ScimException {
        GenericScimResource genericResource = resource.asGenericScimResource();
        try {
            patch.apply(genericResource);
        } catch (NullPointerException e) {
            // Case of path not provided for a REMOVE operation
            throw new BadRequestException(
                    "Cannot patch SCIM resource: " + resource.getId() + " with path request: " + patch, NO_TARGET, e);
        }
        ObjectNode node = genericResource.getObjectNode();
        try {
            return JsonUtils.nodeToValue(node, resource.getClass());
        } catch (JsonProcessingException e) {
            throw new ServerErrorException(
                    "Cannot patch SCIM resource: " + resource.getId() + " with path request: " + patch, null, e);
        }
    }
}
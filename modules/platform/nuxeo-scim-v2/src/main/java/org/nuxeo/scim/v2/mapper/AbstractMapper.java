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
package org.nuxeo.scim.v2.mapper;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.function.ThrowableFunction;
import org.nuxeo.common.utils.DateUtils;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.QueryBuilder;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.scim.v2.api.ScimV2ResourceType;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.unboundid.scim2.common.Path.Element;
import com.unboundid.scim2.common.ScimResource;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.exceptions.NotImplementedException;
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.filters.Filter;
import com.unboundid.scim2.common.filters.NotFilter;
import com.unboundid.scim2.common.messages.ListResponse;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;
import com.unboundid.scim2.common.types.Meta;
import com.unboundid.scim2.common.types.UserResource;
import com.unboundid.scim2.common.utils.Parser;
import com.unboundid.scim2.common.utils.SchemaUtils;

/**
 * Base class for mapping {@link ScimResource} to Nuxeo {@link DocumentModel} and vice versa.
 *
 * @since 2023.14
 */
public abstract class AbstractMapper {

    private static final Logger log = LogManager.getLogger(AbstractMapper.class);

    protected static final int DEFAULT_QUERY_COUNT = 100;

    protected static final int LIMIT_QUERY_COUNT = 1000;

    protected static List<String> groupCaseSensitiveFields = new ArrayList<>();

    protected static List<String> userCaseSensitiveFields = new ArrayList<>();

    static {
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
            log.error(e);
        }
    }

    public abstract DocumentModel createNuxeoUserFromUserResource(UserResource user);

    public abstract DocumentModel updateNuxeoUserFromUserResource(String uid, UserResource user);

    public abstract UserResource getUserResourceFromNuxeoUser(DocumentModel userModel, String baseURL)
            throws URISyntaxException;

    public DocumentModel createNuxeoGroupFromGroupResource(GroupResource group) {
        UserManager um = Framework.getService(UserManager.class);
        // create new group
        DocumentModel newGroup = um.getBareGroupModel();
        newGroup.setProperty(um.getGroupSchemaName(), um.getGroupIdField(), UUID.randomUUID().toString());
        updateGroupModel(newGroup, group);
        return um.createGroup(newGroup);
    }

    public DocumentModel updateNuxeoGroupFromGroupResource(String uid, GroupResource group) {
        UserManager um = Framework.getService(UserManager.class);
        DocumentModel groupModel = um.getGroupModel(uid);
        if (groupModel == null) {
            return null;
        }
        updateGroupModel(groupModel, group);
        um.updateGroup(groupModel);
        return groupModel;
    }

    public GroupResource getGroupResourceFromNuxeoGroup(DocumentModel groupModel, String baseURL)
            throws URISyntaxException {
        UserManager um = Framework.getService(UserManager.class);
        String groupSchemaName = um.getGroupSchemaName();
        String groupId = (String) groupModel.getProperty(groupSchemaName, um.getGroupIdField());

        GroupResource groupResource = new GroupResource();
        groupResource.setId(groupId);
        groupResource.setExternalId(groupId);

        Meta meta = new Meta();
        meta.setResourceType(SCIM_V2_RESOURCE_TYPE_GROUP.toString());
        URI location = new URI(String.join("/", baseURL, groupId));
        meta.setLocation(location);
        meta.setVersion("1");
        groupResource.setMeta(meta);

        String groupLabel = (String) groupModel.getProperty(groupSchemaName, um.getGroupLabelField());
        if (isNotBlank(groupLabel)) {
            groupResource.setDisplayName(groupLabel);
        }

        List<Member> members = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<String> groupMembers = (List<String>) groupModel.getProperty(groupSchemaName, um.getGroupMembersField());
        if (groupMembers != null) {
            members.addAll(groupMembers.stream()
                                       .map(groupMember -> new Member().setType(SCIM_V2_RESOURCE_TYPE_USER.toString())
                                                                       .setValue(groupMember))
                                       .toList());
        }
        @SuppressWarnings("unchecked")
        List<String> groupSubGroups = (List<String>) groupModel.getProperty(groupSchemaName,
                um.getGroupSubGroupsField());
        if (groupSubGroups != null) {
            members.addAll(
                    groupSubGroups.stream()
                                  .map(groupSubGroup -> new Member().setType(SCIM_V2_RESOURCE_TYPE_GROUP.toString())
                                                                    .setValue(groupSubGroup))
                                  .toList());
        }
        groupResource.setMembers(members);

        return groupResource;
    }

    protected UserResource getUserResourceFromUserModel(String userId, String baseURL) throws URISyntaxException {
        UserResource userResource = new UserResource();
        userResource.setId(userId);
        userResource.setExternalId(userId);

        Meta meta = new Meta();
        meta.setResourceType(SCIM_V2_RESOURCE_TYPE_USER.toString());
        URI location = new URI(String.join("/", baseURL, userId));
        meta.setLocation(location);
        meta.setVersion("1");
        userResource.setMeta(meta);

        userResource.setUserName(userId);

        return userResource;
    }

    protected void updateGroupModel(DocumentModel groupModel, GroupResource groupResouce) {
        UserManager um = Framework.getService(UserManager.class);
        String groupSchemaName = um.getGroupSchemaName();

        String displayName = groupResouce.getDisplayName();
        // don't nullify if not provided, need an explicit empty string for this
        if (displayName != null) {
            groupModel.setProperty(groupSchemaName, um.getGroupLabelField(), groupResouce.getDisplayName());
        }

        List<Member> members = groupResouce.getMembers();
        if (members == null) {
            // don't nullify if not provided, need an explicit empty list for this
            return;
        }
        List<String> groupMembers = new ArrayList<>();
        List<String> groupSubGroups = new ArrayList<>();
        members.stream().forEach(member -> {
            String value = member.getValue();
            if (SCIM_V2_RESOURCE_TYPE_GROUP.equals(member.getType())) {
                groupSubGroups.add(value);
            } else {
                groupMembers.add(value);
            }
        });
        groupModel.setProperty(groupSchemaName, um.getGroupMembersField(), groupMembers);
        groupModel.setProperty(groupSchemaName, um.getGroupSubGroupsField(), groupSubGroups);
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

    protected Predicate parseFilters(String filterString, ScimV2ResourceType type, UnaryOperator<String> columnMapper)
            throws ScimException {
        if (StringUtils.isBlank(filterString)) {
            return null;
        }
        return getPredicate(Parser.parseFilter(filterString), type, columnMapper);
    }

    protected Predicate getPredicate(Filter filter, ScimV2ResourceType type, UnaryOperator<String> colMap)
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
            case PRESENT -> isnotnull(colMap.apply(attribute));
            case STARTS_WITH -> {
                if (isCaseSensitive(attribute, type)) {
                    yield like(colMap.apply(attribute), value + "%");
                } else {
                    yield ilike(colMap.apply(attribute), value + "%");
                }
            }
            case ENDS_WITH -> {
                if (isCaseSensitive(attribute, type)) {
                    yield like(colMap.apply(attribute), "%" + value);
                } else {
                    yield ilike(colMap.apply(attribute), "%" + value);
                }
            }
            case CONTAINS -> {
                if (isCaseSensitive(attribute, type)) {
                    yield like(colMap.apply(attribute), "%" + value + "%");
                } else {
                    yield ilike(colMap.apply(attribute), "%" + value + "%");
                }
            }
            case EQUAL -> {
                if (value instanceof String && !isCaseSensitive(attribute, type)) {
                    yield ilike(colMap.apply(attribute), value);
                } else {
                    yield eq(colMap.apply(attribute), value);
                }
            }
            case NOT_EQUAL -> {
                if (value instanceof String && !isCaseSensitive(attribute, type)) {
                    yield notilike(colMap.apply(attribute), value);
                } else {
                    yield noteq(colMap.apply(attribute), value);
                }
            }
            case GREATER_THAN -> gt(colMap.apply(attribute), value);
            case GREATER_OR_EQUAL -> gte(colMap.apply(attribute), value);
            case LESS_THAN -> lt(colMap.apply(attribute), value);
            case LESS_OR_EQUAL -> lte(colMap.apply(attribute), value);
            default -> throw new NotImplementedException("Unsupported filter type: " + filter.getFilterType());
        };
    }

    protected QueryBuilder getQueryBuilder(Integer startIndex, Integer count, String filterString, String sortBy,
            boolean descending, ScimV2ResourceType type, UnaryOperator<String> columnMapper) throws ScimException {
        QueryBuilder queryBuilder = new QueryBuilder();
        queryBuilder.countTotal(true);
        Predicate predicate = parseFilters(filterString, type, columnMapper);
        if (predicate != null) {
            queryBuilder.predicate(predicate);
        }
        if (sortBy != null) {
            queryBuilder.order(new OrderByExpr(new Reference(columnMapper.apply(sortBy)), descending));
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

    public String mapUserColumnName(String column) {
        return switch (column.toLowerCase()) {
            case "id", "username" -> Framework.getService(UserManager.class).getUserIdField();
            case "emails", "emails.value" -> Framework.getService(UserManager.class).getUserEmailField();
            case "givenname" -> "firstName";
            case "familyname" -> "lastName";
            default -> column;
        };
    }

    public String mapGroupColumnName(String column) {
        return switch (column.toLowerCase()) {
            case "id" -> Framework.getService(UserManager.class).getGroupIdField();
            case "displayname" -> Framework.getService(UserManager.class).getGroupLabelField();
            default -> column;
        };
    }

    public ListResponse<ScimResource> queryUsers(Integer startIndex, Integer count, String filterString, String sortBy,
            boolean descending, String baseURL) throws ScimException {
        return queryResources(startIndex, count, filterString, sortBy, descending, baseURL, SCIM_V2_RESOURCE_TYPE_USER);
    }

    public ListResponse<ScimResource> queryGroups(Integer startIndex, Integer count, String filterString, String sortBy,
            boolean descending, String baseURL) throws ScimException {
        return queryResources(startIndex, count, filterString, sortBy, descending, baseURL,
                SCIM_V2_RESOURCE_TYPE_GROUP);
    }

    protected ListResponse<ScimResource> queryResources(Integer startIndex, Integer count, String filterString,
            String sortBy, boolean descending, String baseURL, ScimV2ResourceType type) throws ScimException {
        if (count == null) {
            count = DEFAULT_QUERY_COUNT;
        } else if (count > LIMIT_QUERY_COUNT) {
            throw BadRequestException.tooMany("Maximum value for count is " + LIMIT_QUERY_COUNT);
        }
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
            case SCIM_V2_RESOURCE_TYPE_USER -> getUserResourceFromNuxeoUser(model, baseURL);
            case SCIM_V2_RESOURCE_TYPE_GROUP -> getGroupResourceFromNuxeoGroup(model, baseURL);
            default -> throw new NotImplementedException("Unsupported resource type: " + type);
        })).collect(Collectors.toList()), startIndex != null ? startIndex : 1, count);
    }
}

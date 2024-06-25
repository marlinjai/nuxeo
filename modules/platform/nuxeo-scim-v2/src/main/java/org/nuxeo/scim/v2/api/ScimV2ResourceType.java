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
package org.nuxeo.scim.v2.api;

import org.apache.commons.lang3.StringUtils;

import com.unboundid.scim2.common.exceptions.ResourceNotFoundException;

/**
 * @since 2023.14
 */
public enum ScimV2ResourceType {

    SCIM_V2_RESOURCE_TYPE_SCHEMA("Schema"),

    SCIM_V2_RESOURCE_TYPE_GROUP("Group"),

    SCIM_V2_RESOURCE_TYPE_USER("User"),

    SCIM_V2_RESOURCE_TYPE_RESOURCE_TYPE("ResourceType"),

    SCIM_V2_RESOURCE_TYPE_SERVICE_PROVIDER_CONFIG("ServiceProviderConfig");

    /**
     * The string value for this resource type.
     */
    private String stringValue;

    /**
     * Creates a new resource type with the provided string value.
     *
     * @param stringValue The string value for this resource type.
     */
    ScimV2ResourceType(final String stringValue) {
        this.stringValue = stringValue;
    }

    public boolean equalsString(String stringValue) {
        return this.toString().equals(stringValue);
    }

    /**
     * Retrieves a string representation of this resource type.
     *
     * @return A string representation of this resource type.
     */
    @Override
    public String toString() {
        return stringValue;
    }

    public static ScimV2ResourceType fromValue(String value) throws ResourceNotFoundException {
        if (StringUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Value cannot be null or empty!");
        }

        for (ScimV2ResourceType enumEntry : ScimV2ResourceType.values()) {
            if (enumEntry.toString().equals(value)) {
                return enumEntry;
            }
        }
        throw new ResourceNotFoundException("Cannot find resource type:" + value);
    }

}

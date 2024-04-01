# Nuxeo SCIM 2.0

This module provides an implementation of the [SCIM 2.0](https://scim.cloud/) API on top of Nuxeo `UserManager`.

The goal is to allow a third party Identity Manager (e.g.: Okta, OneLogin, Ping Identity) to provision users and groups directly inside applications based on the Nuxeo Platform.

## Implementation

### Core Objects and Marshalling

The implementation is based on the [UnboundID SCIM 2 SDK for Java](https://github.com/pingidentity/scim2/tree/scim2-3.0.0). Yet, the JAX-RS part relies on the WebEngine/JAX-RS stack that is integrated in the Nuxeo Platform.

### Schemas

The JSON schemas are computed by introspecting the [ScimResource](https://github.com/pingidentity/scim2/blob/scim2-3.0.0/scim2-sdk-common/src/main/java/com/unboundid/scim2/common/ScimResource.java) classes, e.g. [UserResource](https://github.com/pingidentity/scim2/blob/scim2-3.0.0/scim2-sdk-common/src/main/java/com/unboundid/scim2/common/types/UserResource.java) or [GroupResource](https://github.com/pingidentity/scim2/blob/scim2-3.0.0/scim2-sdk-common/src/main/java/com/unboundid/scim2/common/types/GroupResource.java).

### Tests

The SCIM 2.0 compliance tests are based on the [SCIM 2.0 Compliance Test Suite](https://github.com/wso2-incubator/scim2-compliance-test-suite).

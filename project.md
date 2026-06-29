# HT Management New

## Overview

This project manages organization data, leaders, training, certificates, and system access for HT Management.

The backend currently relies on:

- Spring Boot 3
- Spring Data JPA
- PostgreSQL
- Flyway
- Spring Security
- Redis / Spring Session
- JWT for stateless authentication

## Core Model

Most entities inherit from `BaseModel` and share:

- `createdAt`
- `createdBy`
- `updatedAt`
- `updatedBy`
- `status`

Current status rules:

- `status = true` means active
- `status = false` means inactive
- `toggleStatus(id)` flips active/inactive
- CRUD request DTOs do not write `status` directly

## Domain Groups

### Organization

- `Diocese`
- `Deanery`
- `Parish`

Relationships:

- `Diocese` 1 - N `Deanery`
- `Deanery` 1 - N `Parish`
- `Parish` 1 - N `Leader`

Organization unit identity:

- `Diocese`, `Deanery`, and `Parish` use UUID `id` as the primary key
- organization-unit relations reference `*_id`
- organization units no longer use business `code` fields
- diocese must keep unique `name` across the whole table
- deanery must keep unique `name` inside the same `diocese`
- parish must keep unique `name` inside the same `deanery`

Current org-unit relation chain:

- `Deanery.diocese -> Diocese.id`
- `Parish.deanery -> Deanery.id`
- `Leader.parish -> Parish.id`

Flyway migrations already added for the organization-unit refactor:

- `V013__migrate_org_unit_primary_keys_to_uuid.sql`
- `V014__scope_unique_constraints_for_org_units.sql`
- `V015__drop_org_unit_code_columns.sql`

### Personnel

- `Leader`
- `Position`
- `RankHistory`
- `ExecutiveBoardAssignment`

Relationships:

- `Leader` belongs to one `Parish`
- `RankHistory` belongs to one `Leader`
- `ExecutiveBoardAssignment` belongs to one `Leader`
- `ExecutiveBoardAssignment` references one `Position`

### Training

- `CourseRequirement`
- `TrainingCourse`
- `CourseParticipation`

Relationships:

- `TrainingCourse` belongs to one `CourseRequirement`
- `CourseParticipation` belongs to one `Leader`
- `CourseParticipation` belongs to one `TrainingCourse`

### Certification

- `Certificate`

Relationships:

- `Certificate` belongs to one `Leader`
- `Certificate` belongs to one `TrainingCourse`

### System

- `Account`
- `Role`
- `PermissionModule`
- `PermissionResource`
- `PermissionAction`
- `PermissionScope`
- `Permission`
- `AccountRole`
- `AccountPermissionOverride`
- `RolePermission`
- `SystemLog`

Relationships:

- `Account` may reference one `Leader`
- `Account` 1 - N `AccountRole`
- `Account` 1 - N `AccountPermissionOverride`
- `Account` 1 - N `SystemLog`
- `Role` 1 - N `AccountRole`
- `Role` 1 - N `RolePermission`
- `Permission` 1 - N `RolePermission`
- `Permission` 1 - N `AccountPermissionOverride`

## Request DTO Convention

Each module keeps request DTOs under `model/request` and follows:

- `XCreateRequest`
- `XUpdateRequest`

Field naming rules:

- normal fields keep the entity property name
- relation fields receive related IDs instead of nested objects

Examples:

- `leaderId`
- `dioceseId`
- `deaneryId`
- `parishId`
- `courseCode`
- `roleCode`
- `moduleCode`
- `resourceCode`
- `actionCode`
- `scopeCode`
- `username`

Current org-unit contract notes:

- `Diocese` public API uses UUID `id`
- `Deanery` public API uses UUID `id`
- `Parish` and `Leader` now store org-unit relations by UUID internally
- further public API reshaping for `Parish` and `Leader` should follow the UUID model already in storage

## BaseRepo

`BaseRepo` in `common/repository` provides generic data access:

- `create(request)`
- `update(id, request)`
- `findById(id)`
- `findAll(searchRequest)`
- `delete(id)`
- `toggleStatus(id)`

`BaseSearchRequest` supports:

- paging
- `search`
- `searchFields`
- `filters`
- `status`
- `sortBy`
- `sortDirection`

Dynamic search/filter supports nested paths such as:

- `parish.id`
- `leader.parish.deanery.diocese.id`
- `course.requirement.requirementCode`

Request DTOs use relation IDs like `parishId`, `deaneryId`, or `leaderCode`, while `BaseSearchRequest` uses entity paths.

Current behavior notes:

- `BaseRepo` now maps organization-unit relations against UUID-backed entities
- organization-unit code lookup support has been removed together with the dropped `code` columns
- search and filter paths for organization modules should use UUID-backed fields such as `id`, `diocese.id`, and `parish.id`

## Validation Structure

Validation is now split into reusable helpers so services stay focused on orchestration.

Shared helpers:

- `CommonValidationHelper`
- `OrganizationUnitValidationHelper`

`CommonValidationHelper` currently contains:

- required UUID validation
- shared `ApiException` factories for bad request, forbidden, conflict, and not found
- audit-actor extraction from `Authentication`
- generic string normalization
- changed-value detection

`OrganizationUnitValidationHelper` is now org-unit specific and currently only normalizes shared org-unit request fields:

- `name`
- `chaplain`
- `phoneNumber`
- `email`

Module-level helpers:

- `DioceseValidationHelper`
- `DeaneryValidationHelper`
- `DeaneryScopeHelper`
- `ParishValidationHelper`

Current helper responsibilities:

- `DioceseValidationHelper` handles existence checks, input normalization, name uniqueness, and audit-actor resolution
- `DeaneryValidationHelper` handles existence checks for deanery and diocese, input normalization, deanery-name uniqueness inside a diocese, and audit-actor resolution
- `DeaneryScopeHelper` handles create/delete scope enforcement and active diocese/deanery resolution
- `ParishValidationHelper` is prepared for the same pattern so the parish module can follow the same structure in a later pass

Current shared input validation rules for org-unit requests:

- `phoneNumber` must be a Vietnamese phone number with exactly 10 digits and leading `0`
- `email` keeps `@Email` validation and max length rules
- blank optional text values are normalized to `null`
- `patronSaintDay` and `establishmentDate` do not enforce any relationship with each other

## Authentication And Authorization

The project now includes JWT-based authentication and RBAC-based authorization.

### JWT

Implemented flow:

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- `GET /api/auth/admin/summary`

Token behavior:

- stateless JWT
- separate `accessToken` and `refreshToken`
- token contains `username`
- token contains `leaderId`
- token contains role list
- token contains effective permission list
- token contains `tokenType` and `jti`

Configured properties:

- `app.security.jwt.access-secret`
- `app.security.jwt.refresh-secret`
- `app.security.jwt.access-token-expiration-ms`
- `app.security.jwt.refresh-token-expiration-ms`
- `app.security.jwt.issuer`
- `app.security.jwt.audience`

Environment variables:

- `JWT_ACCESS_SECRET`
- `JWT_REFRESH_SECRET`
- `JWT_ACCESS_EXPIRATION`
- `JWT_REFRESH_EXPIRATION`
- `JWT_ISSUER`
- `JWT_AUDIENCE`

### Super Admin Provisioning

RBAC seed and account bootstrap are separate concerns.

RBAC seed behavior:

- Flyway seeds `SUPER_ADMIN`
- Flyway seeds the related auth/system permissions needed for administration
- Flyway grants all currently active permissions to `SUPER_ADMIN`
- Flyway does not create records in `account`
- Flyway does not create records in `account_role`

Optional account bootstrap endpoint:

- `POST /api/auth/bootstrap/super-admin`

Bootstrap behavior:

- public endpoint for first-time setup
- creates account without linking to `Leader`
- creates primary role assignment with `SUPER_ADMIN`
- fails with conflict if the account already exists

Bootstrap configuration comes from environment-backed properties:

- `app.security.bootstrap.super-admin.username`
- `app.security.bootstrap.super-admin.password`
- `app.security.bootstrap.super-admin.role-code`

Environment variables:

- `SUPER_ADMIN_USERNAME`
- `SUPER_ADMIN_PASSWORD`
- `SUPER_ADMIN_ROLE_CODE`

### RBAC

Authorization is based on:

- `account`
- `account_role`
- `role_permission`
- `permission`
- `account_permission_override`

Authority strategy:

- roles are exposed as `ROLE_<ROLE_CODE>`
- permissions are exposed as raw permission codes

Current seeded administrative permissions include:

- `auth.session.manage.all`
- `organization.diocese.create.all`
- `organization.diocese.read.all`
- `organization.diocese.update.all`
- `organization.diocese.delete.all`
- `organization.diocese.read.diocese`
- `organization.diocese.update.diocese`
- `organization.deanery.create.all`
- `organization.deanery.read.all`
- `organization.deanery.update.all`
- `organization.deanery.delete.all`
- `organization.deanery.create.diocese`
- `organization.deanery.read.diocese`
- `organization.deanery.update.diocese`
- `organization.deanery.delete.diocese`
- `organization.deanery.read.deanery`
- `organization.deanery.update.deanery`
- `system.role.manage.all`
- `system.permission.manage.all`
- `system.account.manage.all`
- `system.audit_log.read.all`

Current seeded system role:

- `SUPER_ADMIN`

Current `SUPER_ADMIN` seed behavior:

- receives all active permissions in `permission` through `role_permission`
- remains data-only until an account is created and assigned later

Protected sample endpoint:

- `GET /api/auth/admin/summary`
- requires authority `auth.session.manage.all`

### Diocese CRUD And Scope Rules

Diocese API endpoints:

- `POST /api/dioceses`
- `GET /api/dioceses`
- `GET /api/dioceses/{id}`
- `PUT /api/dioceses/{id}`
- `DELETE /api/dioceses/{id}`

Diocese authorization behavior:

- `SUPER_ADMIN` has full CRUD access across all dioceses through `organization.diocese.*.all`
- diocese-scoped admins use `organization.diocese.read.diocese` and `organization.diocese.update.diocese`
- diocese-scoped admins cannot call `GET /api/dioceses`
- diocese-scoped admins cannot create or delete dioceses
- diocese-scoped access is resolved from `account.leader.parish.deanery.diocese`
- if the current account has no linked leader or no resolvable diocese, diocese-scoped access is denied

Diocese behavior notes:

- `name` is required and unique across all dioceses
- create, read, update, and delete identify the target diocese by UUID `id`
- the old organization-unit code flow has been removed
- create/update validation is delegated to `DioceseValidationHelper`

### Deanery CRUD And Scope Rules

Deanery API endpoints:

- `POST /api/deaneries`
- `GET /api/deaneries`
- `GET /api/deaneries/{id}`
- `PUT /api/deaneries/{id}`
- `DELETE /api/deaneries/{id}`

Deanery authorization behavior:

- `SUPER_ADMIN` has full CRUD access across all deaneries through `organization.deanery.*.all`
- diocese-scoped admins use `organization.deanery.create.diocese`, `organization.deanery.read.diocese`, `organization.deanery.update.diocese`, and `organization.deanery.delete.diocese`
- deanery-scoped admins use `organization.deanery.read.deanery` and `organization.deanery.update.deanery`
- diocese-scoped admins can create, read, update, delete, and list deaneries only inside their own diocese
- deanery-scoped admins can read, update, and list only their own deanery
- deanery-scoped admins cannot create or delete deaneries
- `GET /api/deaneries` is scope-filtered:
- `SUPER_ADMIN` sees all deaneries
- diocese-scoped admins see deaneries in their own diocese
- deanery-scoped admins see only their own deanery
- diocese-scoped access is resolved from `account.leader.parish.deanery.diocese`
- deanery-scoped access is resolved from `account.leader.parish.deanery`
- if the current account has no linked leader or no resolvable scope for the granted permission, access is denied

Deanery response behavior:

- `POST /api/deaneries`, `GET /api/deaneries`, `GET /api/deaneries/{id}`, and `PUT /api/deaneries/{id}` return `DeaneryResponse`
- the response contains deanery scalar fields plus `id` and `dioceseId`
- the response does not expose nested `diocese` objects
- service mapping reuses the same DTO shape for create, list, detail, and update

Deanery update behavior:

- `PUT /api/deaneries/{id}` does not allow changing `dioceseId`
- deanery update only changes deanery-owned fields such as `name`, `associationName`, contact information, and organization dates
- Swagger/OpenAPI for deanery update follows `DeaneryUpdateRequest`, so `dioceseId` is not part of the update request schema

Pending scope rules for future implementation:

- parish may update `deaneryId` only when the target deanery stays inside the same diocese
- leader may update `parishId` only when the target parish stays inside the same diocese
- these scope rules are documented here but are not enforced yet in this pass

Deanery behavior notes:

- `name` is unique only inside the same `dioceseId`
- create request uses `dioceseId`
- update request does not allow changing `dioceseId`
- list/search/filter internals now operate on UUID-backed relations
- create/update validation is delegated to `DeaneryValidationHelper`
- create/delete scope checks are delegated to `DeaneryScopeHelper`

Parish and leader follow-up notes:

- `Parish` relation storage already points to `Deanery.id`
- `Leader` relation storage already points to `Parish.id`
- the future rule remains: parish may move to another deanery only within the same diocese
- the future rule remains: leader may move to another parish only within the same diocese
- these move rules are documented here but not enforced yet

### Docker Development Workflow

Development container behavior:

- `Dockerfile.dev` runs the application through `./gradlew bootRun --continuous`
- `compose.override.yaml` mounts the project source into `/app`
- when code is changed and saved locally, the mounted source is immediately visible inside the container
- Gradle continuous build reloads the app automatically for supported changes during development

Production container behavior:

- `Dockerfile` builds the application jar with `./gradlew bootJar -x test`
- the runtime image starts the packaged jar with `java -jar app.jar`
- the production image no longer depends on a system-installed `gradle` executable inside the runtime container

## Recent Work

The following work was added in this iteration:

1. Added JWT authentication with access and refresh token support.
2. Added security filter chain and method-level authorization.
3. Added RBAC authority resolution from active roles and effective permissions.
4. Added one-time super admin bootstrap service and API.
5. Added Flyway seed migration for `SUPER_ADMIN` and auth permission bootstrap data.
6. Added tests for JWT parsing and protected endpoint behavior.
7. Added CRUD API for `Diocese` with RBAC-based diocese scope enforcement.
8. Added CRUD API for `Deanery` with RBAC-based super-admin, diocese-admin, and deanery-admin scope enforcement.
9. Changed `Deanery` API responses to use `DeaneryResponse` with UUID-based relation fields instead of organization codes.
10. Updated deanery create/update/detail/list flows to use Vietnamese messages and shared DTO mapping.
11. Removed public organization-unit code usage from `diocese`, `deanery`, and `parish`; relations now rely on UUID IDs only.
12. Updated Docker development setup so local code changes are mounted into the container and the app can reload during development, while the production image builds and runs from the Gradle wrapper.
13. Migrated `Diocese`, `Deanery`, and `Parish` to UUID primary keys with UUID-based foreign keys in related organization tables.
14. Added unique-name constraints by scope: diocese globally, deanery within diocese, parish within deanery.
15. Removed the old organization-unit code generator and dropped organization-unit `code` columns from the data model.
16. Updated `Diocese` and `Deanery` APIs, repositories, services, and security evaluators to use UUID identifiers.
17. Refactored org-unit validation out of services into dedicated helpers and added a reusable `CommonValidationHelper`.
18. Standardized org-unit input validation for Vietnamese phone numbers, email, and blank optional-field normalization.
19. Added helper-focused tests for shared validation, diocese validation, and deanery validation/scope handling.

## Technical Notes

- `Account` and `Role` do not shadow `status` from `BaseModel`.
- `RankHistory` does not shadow `updatedBy`.
- `BaseRepo` handles relation mapping from ID-based request DTOs.
- Relation request DTOs should keep using IDs instead of nested payloads.
- JWT authorization is stateless, so permission changes apply immediately on next login or refresh, while existing access tokens remain valid until expiration.
- Organization modules no longer rely on generated slug codes or business `code` values.
- If a later session continues organization work, start from the helper structure under `common/validation`, `modules/diocese/helper`, `modules/deanery/helper`, and `modules/parish/helper` instead of moving validation back into services.
- If a later session extends parish or leader update flows, preserve the UUID-based storage model and implement same-diocese move validation rather than reintroducing code-based relations.

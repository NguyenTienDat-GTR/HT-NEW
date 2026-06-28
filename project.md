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

Organization unit code strategy:

- `dioceseCode` is generated from a slug of `unionName` instead of UUID
- shared code generation is intended for `Deanery` and `Parish` as well
- child unit codes should include parent scope in the generated code to reduce collisions across different dioceses

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

- `leaderCode`
- `parishCode`
- `deaneryCode`
- `courseCode`
- `roleCode`
- `moduleCode`
- `resourceCode`
- `actionCode`
- `scopeCode`
- `username`

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

- `parish.parishCode`
- `leader.parish.deanery.dioceseCode`
- `course.requirement.requirementCode`

Request DTOs still use relation IDs like `parishCode` or `leaderCode`, while `BaseSearchRequest` uses entity paths.

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
- `GET /api/dioceses/{dioceseCode}`
- `PUT /api/dioceses/{dioceseCode}`
- `DELETE /api/dioceses/{dioceseCode}`

Diocese authorization behavior:

- `SUPER_ADMIN` has full CRUD access across all dioceses through `organization.diocese.*.all`
- diocese-scoped admins use `organization.diocese.read.diocese` and `organization.diocese.update.diocese`
- diocese-scoped admins cannot call `GET /api/dioceses`
- diocese-scoped admins cannot create or delete dioceses
- diocese-scoped access is resolved from `account.leader.parish.deanery.diocese`
- if the current account has no linked leader or no resolvable diocese, diocese-scoped access is denied

### Deanery CRUD And Scope Rules

Deanery API endpoints:

- `POST /api/deaneries`
- `GET /api/deaneries`
- `GET /api/deaneries/{deaneryCode}`
- `PUT /api/deaneries/{deaneryCode}`
- `DELETE /api/deaneries/{deaneryCode}`

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

- `POST /api/deaneries`, `GET /api/deaneries`, `GET /api/deaneries/{deaneryCode}`, and `PUT /api/deaneries/{deaneryCode}` return `DeaneryResponse`
- the response contains deanery scalar fields and `dioceseCode`
- the response does not expose nested `diocese` objects
- service mapping loads `dioceseCode` from the relation and reuses the same mapper for create, list, detail, and update

Deanery update behavior:

- `PUT /api/deaneries/{deaneryCode}` does not allow changing `dioceseCode`
- deanery update only changes deanery-owned fields such as `name`, `associationName`, contact information, and organization dates
- Swagger/OpenAPI for deanery update follows `DeaneryUpdateRequest`, so `dioceseCode` is no longer part of the update request schema

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
9. Changed `Deanery` API responses to use `DeaneryResponse` so list and detail endpoints return `dioceseCode` instead of nested `diocese`.
10. Updated deanery create/update/detail/list flows to use Vietnamese messages and shared DTO mapping.
11. Removed `dioceseCode` from `DeaneryUpdateRequest`, so deanery update cannot move a deanery to another diocese.
12. Updated Docker development setup so local code changes are mounted into the container and the app can reload during development, while the production image builds and runs from the Gradle wrapper.

## Technical Notes

- `Account` and `Role` do not shadow `status` from `BaseModel`.
- `RankHistory` does not shadow `updatedBy`.
- `BaseRepo` handles relation mapping from ID-based request DTOs.
- Relation request DTOs should keep using IDs instead of nested payloads.
- JWT authorization is stateless, so permission changes apply immediately on next login or refresh, while existing access tokens remain valid until expiration.

# HT Management New

## Session Rules

- Tất cả message trả về cho người dùng, message validation, message success hoặc failure, và message cảnh báo liên quan tới thao tác nghiệp vụ phải dùng tiếng Việt.
- Mọi session sau khi thêm hoặc sửa message phải lưu file bằng UTF-8 và tự kiểm tra lại để tránh mojibake.

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
- every toggle-status request must include the exact confirmation text `Xác nhận`
- toggle-status requests fail when the confirmation text is missing or does not match exactly
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
- `ScoreComponentTemplate`
- `ScoreFormulaTemplate`
- `ScoreFormulaComponent`
- `CourseParticipationComponentScore`

Relationships:

- `TrainingCourse` belongs to one `CourseRequirement`
- `TrainingCourse` belongs to one `ScoreFormulaTemplate`
- `CourseParticipation` belongs to one `Leader`
- `CourseParticipation` belongs to one `TrainingCourse`
- `ScoreFormulaTemplate` belongs to one scoped owner: `Diocese` or `Deanery`
- `ScoreFormulaTemplate` references many `ScoreComponentTemplate` rows through `ScoreFormulaComponent`
- `CourseParticipationComponentScore` belongs to one `CourseParticipation`
- `CourseParticipationComponentScore` belongs to one `ScoreComponentTemplate`

### Certification

- `Certificate`

Relationships:

- `Certificate` belongs to one `Leader`
- `Certificate` belongs to one `TrainingCourse`

Current certification workflow notes:

- the system now auto-creates `PENDING` certificate proposals for leaders whose participation reaches the course passing score
- certificate approval is still a user action and requires the existing scoped certificate approve permissions
- approvers may still approve a certificate for a leader who did not reach the passing score, but they must provide an approval reason

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

Request DTOs use relation IDs like `parishId`, `deaneryId`, or `leaderId`, while `BaseSearchRequest` uses entity paths.

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
- exact confirmation-text validation for status toggle requests

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
- `ParishValidationHelper` handles existence checks for parish and deanery, input normalization, parish-name uniqueness inside a deanery, and audit-actor resolution

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

Effective permission behavior:

- role-derived permissions are effective only when `account`, `account_role`, `role`, `role_permission`, and `permission` are all active
- direct account overrides are effective only when `account`, `account_permission_override`, and `permission` are all active
- expired `account_role`, `role_permission`, and `account_permission_override` records are excluded from effective permission resolution
- a background scheduler now automatically flips expired `role_permission` and `account_permission_override` records to `status = false`
- toggling any of the records above to `status = false` removes that grant or override from the next login or refresh result

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
- `organization.parish.create.all`
- `organization.parish.read.all`
- `organization.parish.update.all`
- `organization.parish.delete.all`
- `organization.parish.create.diocese`
- `organization.parish.read.diocese`
- `organization.parish.update.diocese`
- `organization.parish.delete.diocese`
- `organization.parish.create.deanery`
- `organization.parish.read.deanery`
- `organization.parish.update.deanery`
- `organization.parish.delete.deanery`
- `organization.parish.read.parish`
- `organization.parish.update.parish`
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

### Role, Permission, And Override Administration

System administration endpoints:

- `POST /api/system/roles`
- `GET /api/system/roles`
- `GET /api/system/roles/{roleCode}`
- `PUT /api/system/roles/{roleCode}`
- `DELETE /api/system/roles/{roleCode}`
- `PATCH /api/system/roles/{roleCode}/toggle-status`
- `POST /api/system/permissions`
- `GET /api/system/permissions`
- `GET /api/system/permissions/{permissionCode}`
- `PUT /api/system/permissions/{permissionCode}`
- `DELETE /api/system/permissions/{permissionCode}`
- `PATCH /api/system/permissions/{permissionCode}/toggle-status`
- `POST /api/system/role-permissions`
- `GET /api/system/role-permissions`
- `GET /api/system/role-permissions/{id}`
- `PUT /api/system/role-permissions/{id}`
- `DELETE /api/system/role-permissions/{id}`
- `PATCH /api/system/role-permissions/{id}/toggle-status`
- `POST /api/system/account-permissions`
- `GET /api/system/account-permissions`
- `GET /api/system/account-permissions/{id}`
- `PUT /api/system/account-permissions/{id}`
- `DELETE /api/system/account-permissions/{id}`
- `PATCH /api/system/account-permissions/{id}/toggle-status`

Administration behavior:

- `SUPER_ADMIN` may CRUD roles and permissions, assign or revoke permissions for any role, and manage direct account permission overrides system-wide
- `ADMIN_DIOCESE` may also CRUD roles and permissions through the seeded role/permission admin authorities
- `ADMIN_DIOCESE` may assign or revoke role permissions and account overrides only for non-`all` permission scopes
- `ADMIN_DIOCESE` may not assign a permission whose `scopeCode` is `all`
- `ADMIN_DIOCESE` may manage role-permission mappings only for roles whose `displayOrder` is equal to or lower than `ADMIN_DIOCESE`
- `ADMIN_DIOCESE` may manage account-permission overrides only for accounts in the same active diocese
- role and permission create or update requests do not write `status`; active or inactive state changes only through toggle-status
- new business roles may be created through the role API and stay permission-empty until role-permission APIs assign explicit permissions
- direct account overrides continue to use `ALLOW` and `DENY`, with optional `expiresAt`

### Diocese CRUD And Scope Rules

Diocese API endpoints:

- `POST /api/dioceses`
- `GET /api/dioceses`
- `GET /api/dioceses/{id}`
- `PUT /api/dioceses/{id}`
- `DELETE /api/dioceses/{id}`
- `PATCH /api/dioceses/{id}/toggle-status`

Diocese authorization behavior:

- `SUPER_ADMIN` has full CRUD access across all dioceses through `organization.diocese.*.all`
- only `SUPER_ADMIN` may call `PATCH /api/dioceses/{id}/toggle-status`
- diocese-scoped admins use `organization.diocese.read.diocese` and `organization.diocese.update.diocese`
- diocese-scoped admins cannot call `GET /api/dioceses`
- diocese-scoped admins cannot create or delete dioceses
- diocese-scoped admins cannot toggle their own diocese status
- diocese-scoped access is resolved from `account.leader.parish.deanery.diocese`
- if the current account has no linked leader or no resolvable diocese, diocese-scoped access is denied

Diocese behavior notes:

- `name` is required and unique across all dioceses
- create, read, update, and delete identify the target diocese by UUID `id`
- `PATCH /api/dioceses/{id}/toggle-status` requires confirmation text `Xác nhận`
- the old organization-unit code flow has been removed
- create/update validation is delegated to `DioceseValidationHelper`

### Deanery CRUD And Scope Rules

Deanery API endpoints:

- `POST /api/deaneries`
- `GET /api/deaneries`
- `GET /api/deaneries/{id}`
- `PUT /api/deaneries/{id}`
- `DELETE /api/deaneries/{id}`
- `PATCH /api/deaneries/{id}/toggle-status`

Deanery authorization behavior:

- `SUPER_ADMIN` has full CRUD access across all deaneries through `organization.deanery.*.all`
- diocese-scoped admins use `organization.deanery.create.diocese`, `organization.deanery.read.diocese`, `organization.deanery.update.diocese`, and `organization.deanery.delete.diocese`
- deanery-scoped admins use `organization.deanery.read.deanery` and `organization.deanery.update.deanery`
- diocese-scoped admins can create, read, update, delete, and list deaneries only inside their own diocese
- diocese-scoped admins can toggle deaneries inside their own diocese
- deanery-scoped admins can read, update, and list only their own deanery
- deanery-scoped admins cannot create or delete deaneries
- deanery-scoped admins cannot toggle their own deanery status
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

- leader may update `parishId` only when the target parish stays inside the same diocese
- the leader move rule is documented here but is not enforced yet in this pass

Deanery behavior notes:

- `name` is unique only inside the same `dioceseId`
- create request uses `dioceseId`
- update request does not allow changing `dioceseId`
- `PATCH /api/deaneries/{id}/toggle-status` requires confirmation text `Xác nhận`
- list/search/filter internals now operate on UUID-backed relations
- create/update validation is delegated to `DeaneryValidationHelper`
- create/delete scope checks are delegated to `DeaneryScopeHelper`

### Parish CRUD And Scope Rules

Parish API endpoints:

- `POST /api/parishes`
- `GET /api/parishes`
- `GET /api/parishes/{id}`
- `PUT /api/parishes/{id}`
- `DELETE /api/parishes/{id}`
- `PATCH /api/parishes/{id}/toggle-status`

Parish authorization behavior:

- `SUPER_ADMIN` has full CRUD access across all parishes through `organization.parish.*.all`
- diocese-scoped admins use `organization.parish.create.diocese`, `organization.parish.read.diocese`, `organization.parish.update.diocese`, and `organization.parish.delete.diocese`
- deanery-scoped admins use `organization.parish.create.deanery`, `organization.parish.read.deanery`, `organization.parish.update.deanery`, and `organization.parish.delete.deanery`
- parish-scoped admins use `organization.parish.read.parish` and `organization.parish.update.parish`
- diocese-scoped admins can create, read, update, delete, and list parishes only inside their own diocese
- diocese-scoped admins can toggle parishes inside their own diocese
- deanery-scoped admins can create, read, update, delete, and list only parishes inside their own deanery
- deanery-scoped admins can toggle parishes inside their own deanery
- parish-scoped admins can read, update, and list only their own parish
- parish-scoped admins cannot create or delete parishes
- parish-scoped admins cannot toggle their own parish status
- `GET /api/parishes` is scope-filtered:
- `SUPER_ADMIN` sees all parishes
- diocese-scoped admins see parishes in their own diocese
- deanery-scoped admins see parishes in their own deanery
- parish-scoped admins see only their own parish
- diocese-scoped access is resolved from `account.leader.parish.deanery.diocese`
- deanery-scoped access is resolved from `account.leader.parish.deanery`
- parish-scoped access is resolved from `account.leader.parish`
- if the current account has no linked leader or no resolvable scope for the granted permission, access is denied

Parish response behavior:

- `POST /api/parishes`, `GET /api/parishes`, `GET /api/parishes/{id}`, and `PUT /api/parishes/{id}` return `ParishResponse`
- the response contains parish scalar fields plus `id`, `deaneryId`, and `chapterName`
- the response does not expose nested `deanery` objects
- service mapping reuses the same DTO shape for create, list, detail, and update

Parish update behavior:

- `PUT /api/parishes/{id}` keeps `deaneryId` in `ParishUpdateRequest`
- parish update may change `deaneryId` only when the target deanery stays inside the same diocese
- parish-scoped admins cannot change `deaneryId`
- deanery-scoped admins cannot move a parish outside their current deanery
- diocese-scoped admins and higher scopes may move a parish only within the current diocese

Parish behavior notes:

- `Parish` relation storage already points to `Deanery.id`
- `name` is unique only inside the same `deaneryId`
- create request uses `deaneryId`
- update request keeps `deaneryId` and enforces same-diocese move validation
- `PATCH /api/parishes/{id}/toggle-status` requires confirmation text `Xác nhận`
- list/search/filter internals now operate on UUID-backed relations such as `deanery.id` and `deanery.diocese.id`
- create/update validation is delegated to `ParishValidationHelper`
- create/delete/deanery-change scope checks are delegated to `ParishScopeHelper`

Leader follow-up notes:

- `Leader` relation storage already points to `Parish.id`
- the future rule remains: leader may move to another parish only within the same diocese
- the leader move rule is documented here but not enforced yet

### Leader CRU And Scope Rules

Leader API endpoints:

- `POST /api/leaders`
- `GET /api/leaders`
- `GET /api/leaders/{id}`
- `PUT /api/leaders/{id}`
- `PATCH /api/leaders/{id}/toggle-status`

Leader request/response behavior:

- `Leader` now includes `gender` enum with values `NAM` and `NU`
- `POST /api/leaders`, `GET /api/leaders`, `GET /api/leaders/{id}`, and `PUT /api/leaders/{id}` return `LeaderResponse`
- the response contains leader scalar fields plus UUID `id`, `fullName`, and `parishId`
- the response does not expose nested `parish` objects

Leader authorization behavior:

- only parish-scoped leader admins may call the current Leader endpoints
- current leader permissions are `organization.leader.create.parish`, `organization.leader.read.parish`, and `organization.leader.update.parish`
- `GET /api/leaders` is scope-filtered to the active parish resolved from `account.leader.parish`
- read, update, and toggle-status are allowed only when the target leader belongs to the active parish of the current account
- if the current account has no linked active leader or no resolvable active parish, access is denied
- this pass does not grant Leader API access to `SUPER_ADMIN`

Leader validation and update behavior:

- create requires `holyName`, `fullName`, `birthDate`, `email`, `gender`, `leaderLevel`, and `parishId`
- request `fullName` is split internally so the last token becomes `firstName` and the remaining prefix becomes `lastName`
- `phoneNumber` must be a Vietnamese phone number with exactly 10 digits and leading `0`
- optional blank text values are normalized to `null`
- `email` must remain unique across leaders
- update request no longer accepts `leaderLevel`
- `leaderLevel` is only set during the initial create flow in this pass
- `LeaderLevel` currently includes `NONE`, `HT_XU`, `DU_TRUONG`, `HT_I`, `HT_II`, `HT_III`, `HLV_I`, `HLV_II`, and `HLV_III`
- update may change personal fields, `gender`, `email`, `phoneNumber`, and `parishId`
- `parishId` may only remain the same active parish of the current parish-scoped admin; moving a leader to another parish is rejected
- `PATCH /api/leaders/{id}/toggle-status` requires confirmation text `Xác nhận`

Leader permission seed notes:

- Flyway migration `V017__add_leader_gender_and_permissions.sql` seeds the new `leader` permission resource and parish-scoped leader permissions
- the current repo does not yet define a stable seed role code for parish-admin role assignment, so this pass seeds permissions only and documents the expected parish-admin usage here

### Executive Board Assignment Scope Rules

Executive board assignment API endpoints:

- `POST /api/executive-board/assignments`
- `GET /api/executive-board/assignments`
- `GET /api/executive-board/assignments/{id}`
- `PUT /api/executive-board/assignments/{id}`
- `PATCH /api/executive-board/assignments/{id}/toggle-status`

Assignment request contract:

- `unitType`
- `unitId`
- `positionCode`
- `leaderId`
- `startDate`
- `endDate`

Assignment response behavior:

- returns assignment `id` as UUID
- returns `unitType`, `unitId`, `dioceseId`, `deaneryId`, and `parishId`
- returns `leaderId`, `leaderFullName`, and `leaderParishId`
- returns `positionCode` and `positionName`
- returns audit timestamps and audit actors

Assignment business rules:

- `position.positionType` must match request `unitType`
- `unitId` must resolve to an active real unit of that `unitType`
- a leader may hold multiple different positions
- the same `positionCode` may be assigned in many different concrete units
- each concrete `unitType + unitId + positionCode` may have only one current active assignment
- current active assignment means `status = true` and `endDate IS NULL`
- ended historical assignments may coexist for the same concrete unit and position
- leader membership must match the target scope exactly:
- `DIOCESE`: leader parish -> deanery -> diocese must equal `unitId`
- `DEANERY`: leader parish -> deanery must equal `unitId`
- `PARISH`: leader parish must equal `unitId`

Assignment authorization behavior:

- `SUPER_ADMIN` inherits the seeded assignment permissions through its existing all-permission role grant
- diocese-scoped admins use `organization.executive_board_assignment.create.read.update.diocese` for diocesan assignments
- diocese-scoped admins may also manage descendant deanery and parish assignments inside their own diocese
- deanery-scoped admins use `organization.executive_board_assignment.create.read.update.deanery`
- deanery-scoped admins may also manage descendant parish assignments inside their own deanery
- parish-scoped admins use `organization.executive_board_assignment.create.read.update.parish`
- parish-scoped admins may manage only assignments in their own parish
- `GET /api/executive-board/assignments` is scope-filtered by resolved `dioceseId`, `deaneryId`, or `parishId`
- `PATCH /api/executive-board/assignments/{id}/toggle-status` requires confirmation text `Xác nhận`

Persistence and migration notes:

- `ExecutiveBoardAssignment.unitId` is now UUID-backed
- `ExecutiveBoardAssignment` now stores denormalized `diocese_id`, `deanery_id`, and `parish_id` for scope filtering and audit backfill
- migration `V020__executive_board_assignment_cache_and_audit_scope.sql` adds scope columns, indexes, and partial uniqueness for active current assignments

### Training Registration Batch Workflow

Training course registration window:

- `TrainingCourse` now stores `registrationStartAt` and `registrationEndAt`
- create, update, and response contracts for `/api/training/courses` now expose both fields
- batch registration is allowed only when `registrationStartAt <= now <= registrationEndAt`
- if either field is missing, registration submission is rejected until the course is fully configured

Training registration API contracts:

- `POST /api/training/courses/{courseCode}/registrations` now accepts batch payload:
- `leaderIds`
- `recipientUsernames`
- `exceptionReasonsByLeaderId`
- the response returns batch summary fields: sender unit, total count, predicted auto-approved count, pending count, and the created registrations
- `POST /api/training/courses/{courseCode}/deanery-submit` may optionally receive `recipientUsernames` and now returns the same batch-summary shape

Submission and approval routing:

- parish submission always uses the active parish scope from the current account
- DT courses notify deanery approvers in the target deanery and RabbitMQ auto-approves eligible registrations at the deanery stage
- non-DT courses submitted from parish to deanery notify deanery aggregators with `training.participation.submit.deanery` in the target deanery and do not auto-approve yet
- when a deanery submits a non-DT course to diocese, the sender unit in the notification becomes the deanery, recipients must have `training.participation.approve.diocese` in the target diocese, and RabbitMQ auto-approves eligible registrations at the diocese stage
- auto-approval is idempotent and skips records already approved or rejected for the target stage

Notification delivery:

- notifications are persisted in `training_notification`
- inbox APIs live under `/api/system/notifications`
- realtime delivery uses WebSocket/STOMP with endpoint `/ws/notifications` and per-user destination `/user/queue/notifications`
- selected `recipientUsernames` are valid only when the target account is active, has the required effective permission, and resolves to the matching deanery or diocese scope
- when `recipientUsernames` is omitted, the backend resolves all active accounts whose effective permissions and scopes match the submission or approval stage

### Training Score Formula Workflow

Training score templates:

- score components are managed under `/api/training/score-components`
- score formulas are managed under `/api/training/score-formulas`
- each component and formula belongs to exactly one scope owner: a diocese or a deanery
- each formula stores:
- a reusable ordered component list
- an arithmetic expression using component codes as variables
- a `passingScore`

Training course score configuration:

- each `TrainingCourse` must reference exactly one `scoreFormulaId`
- deanery-managed courses may only use deanery-owned formulas in the same deanery
- diocese-managed courses may only use diocese-owned formulas in the same diocese

Participation scoring behavior:

- `PATCH /api/training/participations/{id}/score` now accepts `componentScores` instead of a direct `totalScore`
- every participation in a course must be scored with the exact component set defined by that course's formula
- the backend rejects missing, extra, duplicate, or over-max component scores
- `totalScore` is calculated by the backend from the formula expression
- pass or fail is determined by the formula `passingScore`
- when a participation becomes passing and no certificate exists yet for the same leader and course, the system creates one `PENDING` certificate proposal automatically

### API Cache Policy

Cache implementation notes:

- API read caching now uses Redis with key prefix `ht:api-cache:`
- cache keys include normalized request payload, current username, authority hash, and resolved scope ids
- scope-aware keys prevent one admin from reading another scope's cached result
- audit-log APIs are intentionally not cached

Current cache groups and TTLs:

- org-unit lists/details: 10 minutes
- position lists/details: 10 minutes
- leader lists/details: 5 minutes
- executive-board assignment lists/details: 5 minutes
- training-course, requirement, and certificate read models: 3 minutes

Current cache invalidation behavior:

- org-unit write and toggle-status flows evict their own list/detail caches
- leader write and toggle-status flows evict leader caches
- position write and toggle-status flows evict position caches
- executive-board assignment create/update/toggle-status evicts assignment caches
- training-course, requirement, certificate, and workflow mutations evict their related read-model caches
- eviction is broad by cache group so in-flight stale scoped pages are not served after writes

### Audit Log Read Scope Rules

Audit log API endpoints:

- `GET /api/system/audit-logs`
- `GET /api/system/audit-logs/{id}`

Audit log permission model:

- `system.audit_log.read.all`
- `system.audit_log.read.diocese`
- `system.audit_log.read.deanery`
- `system.audit_log.read.parish`

Audit log scope behavior:

- `system_log` now stores nullable `unit_type`, `unit_id`, `diocese_id`, `deanery_id`, and `parish_id`
- existing logs are backfilled where scope can be derived from the resource type and resource id
- logs that remain unscoped stay visible only to `system.audit_log.read.all`
- diocese-scoped readers see logs in their own diocese plus descendant deanery/parish activity tied to that diocese
- deanery-scoped readers see logs in their own deanery plus descendant parish activity tied to that deanery
- parish-scoped readers see only logs in their own parish
- detail access follows the same scope rules as list access

Audit logging implementation notes:

- `ApiAuditLoggingFilter` records API requests after the filter chain completes
- resource scope is resolved from direct account scope first, then from the resource id when the target resource is scope-bound
- `system_log.username` is written only when the username exists as a persisted `account`, which keeps security/integration tests and unauthenticated flows from violating the legacy foreign key
- audit-log reads use `SystemLogAccessEvaluator` plus scope-filtered repository search instead of cache

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
20. Added CRUD API for `Parish` with RBAC-based super-admin, diocese-admin, deanery-admin, and parish-admin scope enforcement.
21. Added `ParishResponse` and UUID-based parish list/detail/create/update mapping without nested deanery objects.
22. Added parish permission seed migration for all, diocese, deanery, and parish scopes and extended account scope resolution down to the active parish.
23. Enforced parish `deaneryId` update rules so moves stay inside the same diocese and parish admins cannot change deanery scope.
24. Added parish-focused service, helper, security, and controller integration tests.
25. Implemented the parish feature on branch `feature/parish-crud-rbac` so the next session can continue from the feature branch instead of `main`.
26. Added the missing parish backend layers: `ParishController`, `ParishService`, `ParishRepository`, `ParishScopeHelper`, `ParishAccessEvaluator`, and `ParishResponse`.
27. Added `V016__seed_parish_permissions.sql` and granted the new parish permissions to `SUPER_ADMIN`.
28. Extended account scope resolution with active parish lookup to support parish-scoped read/update authorization.
29. Added scope-filtered parish list behavior using UUID-backed fields `deanery.diocese.id`, `deanery.id`, and `id`.
30. Fixed mojibake introduced during the first parish implementation pass and normalized all newly added Vietnamese messages/assertions back to UTF-8.
31. Added `PATCH /api/dioceses/{id}/toggle-status`, `PATCH /api/deaneries/{id}/toggle-status`, and `PATCH /api/parishes/{id}/toggle-status` on branch `feature/org-unit-toggle-status`.
32. Enforced exact confirmation text `Xác nhận` for every toggle-status request through a shared `StatusToggleRequest` contract and common validation helper.
33. Added scoped toggle-status RBAC so `SUPER_ADMIN` can toggle all, diocese admins can toggle child deaneries and descendant parishes, deanery admins can toggle child parishes, and no scoped admin can toggle its own org unit.
34. Extended toggle-status persistence to write audit actor data through `BaseRepo.toggleStatus(id, actor)` and added service, evaluator, and controller security tests for the new endpoints.
35. Created branch `feature/leader-management` and added parish-scoped Leader CRU plus toggle-status endpoints.
36. Added `LeaderGender` with values `NAM` and `NU`, exposed it in Leader create/update/response contracts, and kept the new source strings UTF-8 safe.
37. Added `LeaderController`, `LeaderService`, `LeaderRepository`, `LeaderValidationHelper`, `LeaderScopeHelper`, `LeaderAccessEvaluator`, and `LeaderResponse`.
38. Enforced parish-scoped Leader RBAC so only accounts with leader parish permissions can create, list, read, update, and toggle leaders inside their own parish.
39. Removed manual `leaderLevel` updates from the Leader update request; level remains writable only during initial create in this pass.
40. Added `V017__add_leader_gender_and_permissions.sql` to add the `gender` column and seed parish-scoped leader permissions.
41. Added leader-focused helper, service, evaluator, and controller security tests.
42. Changed Leader primary key from legacy `leader_code` string to UUID `id` and added `V018__migrate_leader_primary_key_to_uuid.sql` to migrate dependent foreign keys.
43. Changed Leader API contract so requests now send `fullName`; the backend splits it into `lastName` and `firstName`, with the final token treated as `firstName`.
44. Extended `LeaderLevel` with `NONE` and `HT_XU`.
45. Added Redis-backed API rate limiting with tiered auth/write/read/workflow limits and wired the filter into the Spring Security chain.
46. Enabled RabbitMQ AMQP support, completed `rabbitMQConfig`, and added post-commit domain-event publishing plus an audit consumer hook.
47. Added `V019__account_direct_scope_and_training_permissions.sql` to seed `ADMIN_DIOCESE`, `ADMIN_DEANERY`, `ADMIN_PARISH`, direct account scope columns, and training/position/account-role permissions.
48. Extended `Account` with optional direct `diocese`, `deanery`, and `parish` scope references, and updated account scope resolution to prefer direct scope before falling back through `Leader`.
49. Added `POST /api/system/account-roles` with scoped assignment rules so only `SUPER_ADMIN` can assign `ADMIN_DIOCESE`, while diocese and deanery admins can assign equal-or-lower roles inside their own scope.
50. Added CRUD API for `TrainingCourse` under `/api/training/courses` with deanery-only handling for `SMTL_DT` and `SMKN_DT`, and diocese-only handling for the remaining `CourseType` values.
51. Added CRUD API for `CourseRequirement` under `/api/training/requirements`, scoped by course ownership so DT requirements belong to deaneries and the remaining requirements belong to dioceses.
52. Added workflow APIs for training registration, deanery submission, deanery approval, diocese approval, and score updates under `/api/training`.
53. Added registration workflow state to `CourseParticipation`, including eligibility, exception reason, parish submit time, deanery submit time, multi-level approval status, and certificate request state.
54. Added CRUD API for `Certificate` under `/api/certificates` plus an approval endpoint that requires a passing score, updates `Leader.leaderLevel`, and appends `RankHistory`.
55. Added CRUD API for `Position` under `/api/positions` with permission checks mapped to `UnitType.DIOCESE`, `UnitType.DEANERY`, and `UnitType.PARISH`.
56. Added focused unit tests for scoped role assignment, training registration and scoring workflow, and certificate approval with leader promotion.
57. Added scoped `ExecutiveBoardAssignment` CRUD and toggle-status APIs so one active current leader can hold a given position per concrete unit while the same position may exist across many different units.
58. Added Redis-backed scope-safe caching for selected org-unit, leader, position, executive-board, training, requirement, and certificate read APIs, with write-side invalidation.
59. Added `GET /api/system/audit-logs` and `GET /api/system/audit-logs/{id}` with scoped visibility for diocese, deanery, and parish admins.
60. Added `V020__executive_board_assignment_cache_and_audit_scope.sql` to migrate assignment scope columns, backfill audit-log scope columns, add indexes, enforce active-current uniqueness, and seed assignment plus scoped audit-log permissions.
61. Updated the API audit logging filter to derive scope from account/resource context and to persist `system_log.username` only when the actor exists as an `account`.
62. Added assignment service tests, assignment controller security tests, and audit-log scope service tests, then verified the full suite with `./gradlew.bat test`.
63. Added CRUD and toggle-status APIs for `Role` and `Permission` under `/api/system`.
64. Added role-permission management APIs so permissions can be assigned to or revoked from both seeded and newly created roles.
65. Added account-level permission override APIs for direct `ALLOW` and `DENY` grants with optional expiration.
66. Added `V021__seed_role_permission_admin_permissions.sql` to seed role, permission, role-permission, and account-permission administration authorities.
67. Tightened `v_role_permission_matrix` and `v_account_effective_permission` so inactive `role`, `permission`, `role_permission`, `account_role`, and `account_permission_override` records no longer produce effective permissions.
68. Added automatic expiry revocation for `role_permission` and `account_permission_override`, driven by a scheduled background job that marks expired records inactive and preserves audit history with a system actor.
69. Added `V022__training_registration_window_and_notifications.sql` to store registration windows on `training_course` and persist inbox notifications in `training_notification`.
70. Changed `POST /api/training/courses/{courseCode}/registrations` from single registration to batch submission with `leaderIds`, optional `recipientUsernames`, and optional `exceptionReasonsByLeaderId`.
71. Added registration-window enforcement so parish submissions are accepted only while the course is inside its configured registration period.
72. Added inbox notification APIs under `/api/system/notifications` plus WebSocket/STOMP delivery through `/ws/notifications` and `/user/queue/notifications`.
73. Added RabbitMQ workflow consumption for training batch submissions so DT courses auto-approve eligible registrations at deanery level, while non-DT courses auto-approve only after deanery submission to diocese.
74. Added focused tests for batch registration flow, workflow auto-approval consumer behavior, and notification persistence plus realtime push, then re-verified the full suite with `./gradlew.bat test`.
75. Added automatic `system_log` retention so audit logs older than 10 days are deleted by a scheduled cleanup job.
76. Added reusable scoped score component templates and score formula templates for dioceses and deaneries, plus `V023__training_score_formula_and_certificate_proposals.sql` to persist them.
77. Extended `TrainingCourse` so each course must reference one score formula, and restricted deanery or diocese courses to formulas in the same owning scope.
78. Reworked `PATCH /api/training/participations/{id}/score` so the request now sends component scores, the backend validates them against the course formula, and `totalScore` is computed from the formula expression.
79. Added automatic `PENDING` certificate proposal creation when a participation reaches the course passing score, while keeping final certificate approval as a scoped user action.
80. Extended `Certificate` with `systemSuggested`, `approvedByException`, and `exceptionApprovalReason` so pending proposals and exception approvals are visible in the read model.
81. Changed certificate approval so leaders below the passing score can still be approved manually when the approver supplies a reason.
82. Added focused unit coverage for formula-based scoring and certificate approval with the new request shape.
83. Updated the FE technical guides so training score templates, formula-based scoring, certificate proposals, and approval-exception flows are documented for frontend integration.

## Technical Notes

- `Account` and `Role` do not shadow `status` from `BaseModel`.
- `RankHistory` does not shadow `updatedBy`.
- `BaseRepo` handles relation mapping from ID-based request DTOs.
- Relation request DTOs should keep using IDs instead of nested payloads.
- Bắt buộc sử dụng UTF-8 cho mọi dòng code, string literal, và comment bằng tiếng Việt; không được để phát sinh mojibake trong source.
- JWT authorization is stateless, so permission changes apply immediately on next login or refresh, while existing access tokens remain valid until expiration.
- Organization modules no longer rely on generated slug codes or business `code` values.
- If a later session continues organization work, start from the helper structure under `common/validation`, `modules/diocese/helper`, `modules/deanery/helper`, and `modules/parish/helper` instead of moving validation back into services.
- If a later session extends parish or leader update flows, preserve the UUID-based storage model and implement same-diocese move validation rather than reintroducing code-based relations.
- `Leader` currently treats `leaderLevel` as create-only input; do not re-open direct update of this field from the normal Leader update API.
- Training scope resolution now prefers `account.diocese`, `account.deanery`, and `account.parish` before using the `Account -> Leader -> Parish -> Deanery -> Diocese` fallback chain.
- Training registration now uses a two-step non-DT flow: parish -> deanery only aggregates and notifies deanery recipients, while deanery -> diocese is the first point where eligible non-DT registrations may auto-approve.
- For DT courses, parish submission may auto-approve eligible registrations at deanery stage through RabbitMQ, but non-eligible records remain pending for manual deanery review.
- Training scoring now treats the course formula as the source of truth: every participation must send the exact component set from the course formula before the backend computes `totalScore`.
- Score formulas use arithmetic expressions over component codes; if a formula changes, future scoring requests must follow the updated component set and variable names.
- Notification recipients are permission-driven rather than role-name-driven; custom roles such as `nghienhuan-liendoan` work as long as their effective permissions and resolved scopes match the current workflow step.
- Training and certificate approval currently assume the progression map encoded in `TrainingWorkflowSupport`; if `CourseType` expands, update that helper first.
- Certificate approval now owns leader promotion and `RankHistory` creation, so normal `Leader` update APIs should continue treating `leaderLevel` as non-editable business state.
- Certificate proposals may be auto-created by the system when a leader passes a course, but only a user with the scoped certificate approve permission can finalize the certificate.
- Exception certificate approvals for non-passing leaders must always record `exceptionApprovalReason`.
- `ExecutiveBoardAssignment` update now explicitly saves the entity after scope and uniqueness validation so changed leader/position/unit fields are persisted consistently.
- `SystemLog` scope columns are now the source of truth for scoped audit-log reads; later resource modules should backfill these fields when they introduce new audit-visible resources.
- `SystemLog` retention now deletes records whose `loggedAt` is older than 10 days through the `SystemLogRetentionScheduler` background job.
- Role and permission administration now use `/api/system/roles`, `/api/system/permissions`, `/api/system/role-permissions`, and `/api/system/account-permissions` rather than folding everything into the original `system.role.manage.all` and `system.permission.manage.all` permissions.
- The effective-permission SQL views are now part of the security contract: if a record with authorization impact is toggled to inactive, it must disappear from the next JWT login or refresh result without needing extra Java-side filtering.


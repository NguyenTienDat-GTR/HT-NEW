# BE

## Dashboard phân tích theo scope

- API: `GET /api/analytics/overview`, `/organization`, `/leaders`, `/courses`, `/certificates`.
- Filter dùng chung: `fromDate`, `toDate` (ISO `yyyy-MM-dd`), `timeBucket` (`month`, `quarter`, `year`, mặc định `month`) và `groupBy` (`deanery`, `parish`, `courseType`, `leaderLevel`, `gender`, `status`, `approvalStatus`, `year`). Backend tự resolve scope từ account, không nhận ID scope từ client.
- Quyền: `analytics.dashboard.read.all`, `.diocese`, `.deanery`, `.parish`.
- `SUPER_ADMIN` chỉ có thể gọi overview với đúng bốn KPI toàn hệ thống: `totalDioceses`, `totalDeaneries`, `totalParishes`, `totalLeaders`; các endpoint chi tiết trả 403.
- Diocese chỉ xem dữ liệu thuộc giáo phận và các giáo hạt/giáo xứ con; deanery chỉ xem giáo hạt và giáo xứ con; parish chỉ xem dữ liệu giáo xứ hiện tại. Response chuẩn gồm `scope`, `filters`, `kpis`, `breakdowns`, `trends`, `comparisons`.
- Response còn có `comparisons`: chuỗi so sánh theo năm trong khoảng lọc, mỗi chỉ số có `GrowthRate` so với năm trước để FE dựng biểu đồ cột/đường tăng trưởng.
- `courses` phân tích số khóa mở trong scope (trừ parish), đăng ký, tham dự, hoàn thành, đạt, hủy, tồn đọng duyệt, điểm trung bình, tỷ lệ tham dự/hoàn thành/đạt/hủy và funnel yêu cầu-duyệt chứng nhận; breakdown luôn có theo từng khóa và có thể nhóm thêm theo loại khóa, giáo hạt, giáo xứ hoặc năm.
- `certificates` phân tích số chứng nhận, trạng thái duyệt/chờ/từ chối, hệ thống gợi ý, duyệt ngoại lệ, tỷ lệ duyệt, tỷ lệ chuyển đổi từ lượt đạt khóa học và số giờ duyệt trung bình; breakdown theo trạng thái duyệt, theo từng khóa học và các nhóm được yêu cầu.
- `leaders` luôn trả cơ cấu theo giới tính, nhóm tuổi, cấp bậc, trạng thái và thâm niên; KPI gồm quy mô, tỷ lệ hoạt động, nam/nữ, nhóm dưới 25 tuổi, nhóm từ 45 tuổi và tuổi trung bình. Insight giải thích trực tiếp sức khỏe nhân sự, cân bằng giới tính và nhu cầu kế thừa.
- Response analytics chỉ trả dữ liệu cấu trúc: `scope`, `filters`, `kpis`, `breakdowns`, `trends`, `comparisons`. Không trả `insights`, message, label, định nghĩa KPI hoặc ghi chú diễn giải; FE ánh xạ các `type`/`key` và hiển thị biểu đồ theo nhu cầu.
- Phân biệt thời gian: snapshot tổ chức/leader phản ánh hiện tại; biểu đồ leader dùng ngày tạo để thể hiện nhân sự mới; course dùng ngày đăng ký hoặc ngày bắt đầu khóa; certificate dùng ngày cấp, hoặc ngày tạo khi hồ sơ chưa được cấp để không bỏ sót trạng thái chờ duyệt.

## Tắt đơn vị tổ chức (giáo phận / giáo hạt / giáo xứ)

- Tắt đơn vị **không ghi đè** `status` của đơn vị con, tài khoản hay huynh trưởng; trạng thái hiệu lực được suy ra khi truy vấn (đơn vị hoặc đơn vị cha có `status=false`). Bật lại đơn vị là khôi phục đúng nguyên trạng.
- Tài khoản thuộc đơn vị bị khóa chỉ đăng nhập và đọc thông báo (`UnitOperationalAccessFilter`).
- `StatusToggleRequest` có thêm `reason` (tối đa 500 ký tự); **bắt buộc khi tắt** đơn vị đang hoạt động. Lịch sử ghi vào bảng `org_unit_status_history` (migration `V088`): trạng thái mới, lý do, người đổi, thời điểm, số tài khoản và số đơn vị con bị ảnh hưởng, kèm dòng `BASELINE` ban đầu.
- `GET /api/{dioceses|deaneries|parishes}/{id}/toggle-impact` (quyền toggle) trả số đơn vị con, tài khoản, huynh trưởng, khóa chưa kết thúc, đăng ký và chứng nhận chờ duyệt; `GET .../{id}/status-history` (quyền đọc) trả lịch sử trạng thái. Logic ở `UnitStatusAuditService`.
- Đóng băng ghi dữ liệu: `CourseActivityGuard.requireActive` chặn mọi thao tác khóa học / đăng ký / chấm điểm / duyệt chứng nhận khi đơn vị tổ chức khóa bị khóa; `requireCourseEnabled` (dùng cho thu hồi chứng nhận) không chặn theo đơn vị. Không đăng ký mới cho huynh trưởng thuộc giáo xứ bị khóa nhưng hồ sơ đã có vẫn được đơn vị tổ chức hoàn tất. `LeaderService` chặn tạo/sửa trong giáo xứ bị khóa (chuyển sang giáo xứ đang hoạt động thì được phép); `ExecutiveBoardAssignmentService.create` chặn gán chức vụ trong đơn vị bị khóa.
- Chứng nhận đã cấp vẫn hợp lệ khi đơn vị bị tắt; chỉ thu hồi bằng chức năng thu hồi có lý do.
- `DeaneryResponse` / `ParishResponse` có `effectiveStatus`; danh sách huynh trưởng chọn được chỉ gồm giáo xứ hiệu lực.
- Analytics có `includeInactiveUnits` (mặc định `false`, nằm trong cache key): số liệu snapshot (số đơn vị, huynh trưởng hiện tại, cơ cấu) loại đơn vị bị khóa; số liệu sự kiện lịch sử (đăng ký, chứng nhận, huynh trưởng mới theo thời gian) luôn đầy đủ. Thêm KPI `inactiveDeaneries`, `inactiveParishes`, `leadersInInactiveUnits`.

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
- `CourseParticipationComponentScore`

Relationships:

- `TrainingCourse` belongs to one `CourseRequirement`
- `TrainingCourse` stores its own `passingScore`; its score components are the active `ScoreComponentTemplate` rows with the same course type and same owner (diocese/deanery)
- `CourseParticipation` belongs to one `Leader`
- `CourseParticipation` belongs to one `TrainingCourse`
- `ScoreComponentTemplate` belongs to one scoped owner (`Diocese` or `Deanery`) and one `TrainingCourseType`
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
- changing a certificate's `leaderId` or `courseCode` revalidates that the leader belongs to the target course owner scope and that the updater owns that target deanery for DT courses or target diocese for regular courses; a scoped updater cannot move a certificate across scopes

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

Controller list endpoints accept `BaseSearchRequest` through `BaseSearchRequestArgumentResolver`; do not annotate it with `@ModelAttribute`, otherwise Spring's default `Map` binding can split the JSON `filters` payload and reject valid filter values.

The request boundary limits `page` to a non-negative value (at most `1,000,000`), `size` to `1..100`, `searchFields` to 10 entries, and `filters` to 20 entries before any repository query is built.

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

- JWT access and refresh tokens are now backed by Redis session state
- separate `accessToken` and `refreshToken`
- token contains `username`
- token contains `leaderId`
- token contains role list
- token contains effective permission list
- token contains `tokenType`, `jti`, and `sessionId`
- Redis keeps `auth:session:{sessionId}` as the source of truth for active tokens
- Redis keeps `auth:user:{username}:sessions` to enforce login limits per account

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

Current login session policy:

- the backend identifies the login source by `HttpServletRequest.getRemoteAddr()`
- each account may keep at most 2 active IP-based sessions
- each IP may keep only 1 active session for the same account
- a new login from the same IP revokes both the previous access token and refresh token on that IP
- a login from a third IP revokes the oldest active IP session for that account
- `POST /api/auth/refresh` still reads the refresh token from the HttpOnly cookie
- successful refresh rotates both access token and refresh token for the existing `sessionId` through an atomic Redis compare-and-set/Lua operation; only one concurrent use of the same refresh token can succeed
- normal access-token activity updates only the session ordering score, so it cannot overwrite a concurrent refresh rotation with stale session data
- protected APIs now validate `sessionId + accessToken jti` against Redis on every request
- token parsing requires the configured JWT issuer and audience in addition to the correct signing key and token type
- updating, disabling, or deleting an account revokes all of that account's Redis sessions immediately; previously issued access tokens then fail the Redis `sessionId + jti` check

### Super Admin Provisioning

RBAC seed and account bootstrap are separate concerns.

RBAC seed behavior:

- Flyway seeds `SUPER_ADMIN`
- Flyway seeds the related auth/system permissions needed for administration
- Flyway grants all currently active permissions to `SUPER_ADMIN`
- Flyway does not create records in `account`
- Flyway does not create records in `account_role`

Super-admin provisioning behavior:

- there is no public HTTP bootstrap endpoint; `/api/auth/bootstrap/super-admin` remains blocked by the security configuration
- the bootstrap service is an internal deployment operation only
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
- permission CRUD endpoints now require `ROLE_SUPER_ADMIN` directly, even if an account still carries legacy `system.permission.*.all` authorities

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
- `organization.deanery.toggle.diocese`
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
- `organization.parish.toggle.deanery`
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
- `SUPER_ADMIN` is the only role allowed to call `/api/system/permissions`
- permission create requests no longer accept `permissionCode` or `isSystem`; the backend derives `permissionCode` as `<module>.<resource>.<action>.<scope>` and saves user-created permissions with `isSystem = false`
- permission update requests are metadata-only and currently allow `permissionName`, `description`, and `displayOrder`
- system permissions (`isSystem = true`) may not be deleted through the permission API
- `ADMIN_DIOCESE` may still manage roles through the seeded role admin authorities, but may not CRUD permissions
- role-permission mappings may be changed by `SUPER_ADMIN`, or by an account that has the matching role-permission assign/revoke authority when the target role is lower than the actor's highest active role, the permission scope is not higher than the actor's assignment scope, and the actor already owns the permission being assigned or changed
- scoped admins may read only manageable roles, non-`all` permissions at or below their scope, and the corresponding role-permission rows; list filtering is applied before response pagination and out-of-scope detail reads return `403`
- scoped account-permission administrators may manage and read overrides only for target accounts in their own resolved scope and only for permissions they already hold at an equal-or-lower scope; list filtering is applied before response pagination and out-of-scope detail reads return `403`
- `SUPER_ADMIN` retains global list/detail reads for roles, permissions, accounts, role-permissions, and account-permission overrides
- role and permission create or update requests do not write `status`; active or inactive state changes only through toggle-status
- new business roles may be created through the role API and stay permission-empty until role-permission APIs assign explicit permissions
- direct account overrides continue to use `ALLOW` and `DENY`, with optional `expiresAt`
- API access is enforced directly by controller `@PreAuthorize` rules and each module's scoped `AccessEvaluator`; the legacy `api_permission_policy` registry is removed

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
- diocese-scoped admins may call `GET /api/dioceses`, but the list is filtered to their own managed diocese before pagination
- diocese-scoped admins cannot create or delete dioceses
- diocese-scoped admins cannot toggle their own diocese status
- diocese-scoped access is resolved from direct `account.diocese` first, then fallback `account.leader.parish.deanery.diocese`
- if the current account has no direct/fallback resolvable diocese, diocese-scoped access is denied

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

- `SUPER_ADMIN` has full CRUD access across all deaneries through `organization.deanery.*.all`; status toggle is allowed through `organization.deanery.update.all`
- seeded diocese-scoped admins use `organization.deanery.read.diocese`, `organization.deanery.create.diocese`, and `organization.deanery.toggle.diocese` for direct-child deaneries inside their own diocese
- deanery-scoped admins use `organization.deanery.read.deanery` and `organization.deanery.update.deanery`
- seeded diocese-scoped admins cannot update or delete deaneries, and cannot operate on same-level dioceses except updating their own managed diocese
- deanery-scoped admins can read, update, and list only their own deanery
- deanery-scoped admins cannot create or delete deaneries
- deanery-scoped admins cannot toggle their own deanery status
- `GET /api/deaneries` is scope-filtered:
- `SUPER_ADMIN` sees all deaneries
- diocese-scoped admins see deaneries in their own diocese
- deanery-scoped admins see only their own deanery
- diocese-scoped access is resolved from direct `account.diocese` first, then fallback `account.leader.parish.deanery.diocese`
- deanery-scoped access is resolved from direct `account.deanery` first, then fallback `account.leader.parish.deanery`
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
- create/delete scope checks are delegated to `DeaneryScopeHelper`; toggle authorization is delegated to `DeaneryAccessEvaluator`

### Parish CRUD And Scope Rules

Parish API endpoints:

- `POST /api/parishes`
- `GET /api/parishes`
- `GET /api/parishes/{id}`
- `PUT /api/parishes/{id}`
- `DELETE /api/parishes/{id}`
- `PATCH /api/parishes/{id}/toggle-status`

Parish authorization behavior:

- `SUPER_ADMIN` has full CRUD access across all parishes through `organization.parish.*.all`; status toggle is allowed through `organization.parish.update.all`
- seeded diocese-scoped admins use `organization.parish.read.diocese` to list parishes inside their own diocese
- seeded deanery-scoped admins use `organization.parish.read.deanery`, `organization.parish.create.deanery`, and `organization.parish.toggle.deanery` for direct-child parishes inside their own deanery
- parish-scoped admins use `organization.parish.read.parish` and `organization.parish.update.parish`
- seeded diocese-scoped admins cannot create, update, delete, or toggle parishes because parish is a grandchild unit for diocese scope
- seeded deanery-scoped admins cannot update or delete parishes
- parish-scoped admins can read, update, and list only their own parish
- parish-scoped admins cannot create or delete parishes
- parish-scoped admins cannot toggle their own parish status
- `GET /api/parishes` is scope-filtered:
- `SUPER_ADMIN` sees all parishes
- diocese-scoped admins see parishes in their own diocese
- deanery-scoped admins see parishes in their own deanery
- parish-scoped admins see only their own parish
- diocese-scoped access is resolved from direct `account.diocese` first, then fallback `account.leader.parish.deanery.diocese`
- deanery-scoped access is resolved from direct `account.deanery` first, then fallback `account.leader.parish.deanery`
- parish-scoped access is resolved from direct `account.parish` first, then fallback `account.leader.parish`
- if the current account has no linked leader or no resolvable scope for the granted permission, access is denied

Parish response behavior:

- `POST /api/parishes`, `GET /api/parishes`, `GET /api/parishes/{id}`, and `PUT /api/parishes/{id}` return `ParishResponse`
- the response contains parish scalar fields plus `id`, `deaneryId`, and `chapterName`
- the response does not expose nested `deanery` objects
- service mapping reuses the same DTO shape for create, list, detail, and update

Parish update behavior:

- `PUT /api/parishes/{id}` keeps `deaneryId` in `ParishUpdateRequest`
- scoped admins may update only their own managed parish through `organization.parish.update.parish`
- scoped admins cannot change `deaneryId`
- changing a parish's `deaneryId` is reserved for `SUPER_ADMIN` through `organization.parish.update.all`

Parish behavior notes:

- `Parish` relation storage already points to `Deanery.id`
- `name` is unique only inside the same `deaneryId`
- create request uses `deaneryId`
- update request keeps `deaneryId` and enforces same-diocese move validation
- `PATCH /api/parishes/{id}/toggle-status` requires confirmation text `Xác nhận`
- list/search/filter internals now operate on UUID-backed relations such as `deanery.id` and `deanery.diocese.id`
- create/update validation is delegated to `ParishValidationHelper`
- create/delete/deanery-change scope checks are delegated to `ParishScopeHelper`; toggle authorization is delegated to `ParishAccessEvaluator`

Leader follow-up notes:

- `Leader` relation storage already points to `Parish.id`
- the future rule remains: leader may move to another parish only within the same diocese
- the leader move rule is documented here but not enforced yet

### Leader CRUD And Scope Rules

Leader API endpoints:

- `POST /api/leaders`
- `GET /api/leaders`
- `GET /api/leaders/{id}`
- `PUT /api/leaders/{id}`
- `DELETE /api/leaders/{id}`
- `PATCH /api/leaders/{id}/toggle-status`

Leader request/response behavior:

- `Leader` now includes `gender` enum with values `NAM` and `NU`
- `POST /api/leaders`, `GET /api/leaders`, `GET /api/leaders/{id}`, and `PUT /api/leaders/{id}` return `LeaderResponse`
- the response contains leader scalar fields plus UUID `id`, `fullName`, nullable `imageUrl`, and `parishId`
- the response does not expose nested `parish` objects

Leader authorization behavior:

- scoped leader admins may read leaders at their assigned scope: `organization.leader.read.diocese`, `organization.leader.read.deanery`, or `organization.leader.read.parish`
- `GET /api/leaders` is scope-filtered before pagination to `parish.deanery.diocese.id`, `parish.deanery.id`, or `parish.id` according to the effective read permission
- `GET /api/leaders/{id}` allows diocese, deanery, or parish scoped reads only when the target leader belongs to the resolved active scope of the current account
- create supports `ADMIN_DIOCESE`, `ADMIN_DEANERY`, and `ADMIN_PARISH` through `organization.leader.create.diocese`, `organization.leader.create.deanery`, and `organization.leader.create.parish`
- update, delete, and toggle-status remain parish-scoped through `organization.leader.update.parish` and `organization.leader.delete.parish`
- if the current account has no active direct/fallback scope required by the granted permission, access is denied
- this pass does not grant Leader API access to `SUPER_ADMIN`

Leader validation and update behavior:

- create requires `holyName`, `fullName`, `birthDate`, `email`, `gender`, `leaderLevel`, and `parishId`
- request `fullName` is split internally so the last token becomes `firstName` and the remaining prefix becomes `lastName`
- `phoneNumber` must be a Vietnamese phone number with exactly 10 digits and leading `0`
- `imageUrl` belongs to `Leader`, is optional, and may be `null`
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
- Flyway migration `V036__seed_scoped_organization_menu_permissions.sql` adds `organization.leader.read.diocese`, `organization.leader.read.deanery`, and backfills missing scoped organization/leader grants for `ADMIN_DIOCESE`, `ADMIN_DEANERY`, and `ADMIN_PARISH` so `/api/auth/me.permissions` can drive FE menu visibility correctly

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
- ~~`POST /api/training/courses/{courseCode}/deanery-submit`~~ đã bị gỡ (xem "Training registration redesign 2026-10-19": bỏ bước giáo hạt tổng hợp)

Submission and approval routing:

- (Cập nhật 2026-10-19, xem mục "Training registration redesign 2026-10-19" ở cuối file; các gạch đầu dòng cũ về bước giáo hạt tổng hợp đã được thay thế.)
- người gửi: tài khoản giáo xứ, giáo phận hoặc giáo hạt; đơn vị gửi được chọn theo phạm vi gắn trực tiếp của tài khoản (`TrainingWorkflowSupport.resolveRegistrationSender`)
- manual approval belongs only to the owning scope: a direct deanery owner approves DT registrations and a direct diocese owner approves regular registrations
- auto-approval is idempotent and skips records already approved or rejected

Notification delivery:

- notifications are persisted in `training_notification`
- inbox APIs live under `/api/system/notifications`
- realtime delivery uses WebSocket/STOMP with endpoint `/ws/notifications` and per-user destination `/user/queue/notifications`
- selected `recipientUsernames` are valid only when the target account is active, has the required effective permission, and resolves to the matching deanery or diocese scope
- when `recipientUsernames` is omitted, the backend resolves all active accounts whose effective permissions and scopes match the submission or approval stage

### Training Score Component Workflow (V057, thay thế module công thức điểm)

Module `scoreFormulaTemplate` và `TrainingScoreFormulaEvaluator` (biểu thức) đã bị XÓA. Điểm tổng kết chỉ dựa vào thành phần điểm có hệ số.

Score components (`/api/training/score-components`):

- each component belongs to one course type (`courseTypeCode`); `unitType` and the owner (diocese/deanery of the creator) are derived from the course type's `managementUnitType`, the create request has no `unitType`; create is authorized by `training.score_component.create.<scope>` matching the course type's level
- `GET /api/training/course-types` is scoped by account: only `read.deanery` -> deanery-managed types, only `read.diocese` -> diocese-managed types (feeds the course-type dropdowns)
- list/detail of components are `@Transactional(readOnly)` because `spring.jpa.open-in-view=false` (lazy `courseType` caused a LazyInitializationException on list)
- fields: `name`, `maxScore`, `coefficient` (> 0, hệ số), `includeInFinal` (flag, default true), `courseTypeCode`, `displayOrder`, `description`
- `includeInFinal = false` means the component is still entered/stored per participation but is NOT counted in the final score
- `courseTypeCode` and `unitType` cannot be changed on update

Course configuration:

- `TrainingCourse.passingScore` (required on create, replaces `scoreFormulaId`; cannot change once participations exist)
- the components of a course = active components with the course's `courseType` and the course's owner (deanery for deanery-managed courses, else diocese)

Participation scoring behavior:

- `PATCH /api/training/participations/{id}/score` accepts `componentScores` (one score per applicable component, all required)
- a participation may be scored only after the required approval is `APPROVED`: deanery approval for DT courses and diocese approval for non-DT courses
- manual approval accepts only `PENDING` or `SUBMITTED` records; rejected records are terminal unless a future explicit reopen flow is introduced
- one leader has exactly one participation per course
- the backend rejects missing, extra, duplicate, or over-max component scores
- `totalScore` = weighted average: sum(score x coefficient) / sum(coefficient) over components with `includeInFinal = true`, rounded to 2 decimals (HALF_UP). Example: skill coefficient 1, spirit coefficient 2 -> (skill + 2 x spirit) / 3. Error if no component is included.
- pass or fail is determined by `TrainingCourse.passingScore`
- response `componentScores[]` also returns `coefficient` and `includeInFinal`
- `passingScore` of a course must be <= the max final score of its course type: weighted average (by coefficient) of `maxScore` of active components with `includeInFinal = true` for the course type + owner. Skipped when the type has no included component. `GET /api/training/courses/passing-score-limit?courseTypeCode=` returns `{maxPassingScore, componentCount}` for the FE tip. (Weighted, not a plain average, so the limit is the real maximum reachable score.)
- permissions `training.score_formula.*` are deactivated by migration V057 (data tables `score_formula_template`/`score_formula_component` dropped; legacy components without a resolvable course type are set inactive)
- when a participation becomes passing and no certificate exists yet for the same leader and course, the system creates one `PENDING` certificate proposal automatically
- one leader has exactly one certificate per course; code generation is serialized per certificate-code prefix

### Training Course Registration Control and Recipients (V058)

- (V059 replaced the V058 boolean `registration_open`.) `training_course.registration_status` (`CourseRegistrationStatus`: `NOT_STARTED`, `OPEN`, `CLOSED` = manual close, `ENDED`) and `training_course.organizing_status` (`CourseOrganizingStatus`: `UPCOMING`, `ONGOING`, `ENDED` from `startDate/endDate`, Asia/Ho_Chi_Minh) are persisted so lists can filter by equality (`registrationStatus`, `organizingStatus` filters). Response also returns `registrationOpen` (= status OPEN). Logic lives in `TrainingCourseStatusSupport` (static): `ENDED` once `now > registrationEndAt`; `CLOSED` is preserved until manual open or end; else `NOT_STARTED` before start, `OPEN` inside the window. Registration is accepted only when the computed status is OPEN (independent of scheduler lag).
- `PATCH /api/training/courses/{courseCode}/registration` body `{ "open": true|false }` (permission = course update scope). Allowed only while the status is OPEN/CLOSED (inside the window): 409 before start (system auto-opens) and after end (extend `registrationEndAt`, which recomputes the status on update, then reopen manually if needed). Each real change publishes a RabbitMQ domain event.
- `CourseRegistrationScheduler` (`@EnableScheduling`, every `training.registration.auto-close-interval-ms`, default 60000) calls `TrainingCourseService.refreshCourseStatuses()`: auto-opens (`NOT_STARTED -> OPEN`) when the registration window starts, auto-closes (`OPEN -> ENDED`) when it ends, moves `organizing_status`, evicts course caches, and publishes events for the auto open/close transitions. Creating or updating a course recomputes both statuses.
- Event-driven notifications (RabbitMQ -> STOMP push): events `training.course.registration_opened` / `training.course.registration_closed` (payload `courseCode`, `courseName`, `open`, `reason` MANUAL|AUTO, `deaneryManaged`, `dioceseId`, `deaneryId`) go through `DomainEventPublisher` (afterCommit) to RabbitMQ. A DEDICATED queue `ht.domain.events.course-registration` (+ DLQ) is bound to the domain exchange (do not reuse the workflow queue: several listeners on one queue would compete for messages). `CourseRegistrationEventConsumer` notifies accounts holding any of `submit.parish`, `submit.deanery`, `approve.deanery`, `approve.diocese` inside the course scope (deanery-managed course: same deanery; otherwise same diocese) using `TrainingNotificationService.notifyUsers`: the notification is stored in DB and, after commit, pushed to online users over STOMP `/user/queue/notifications`. The push happens only when a RabbitMQ event arrives (the socket carries no polling traffic besides STOMP heartbeats); offline users read the stored notification via REST. Note: the STOMP broker is the in-memory simple broker, so with several BE instances a push only reaches users connected to the instance that consumed the event (use a broker relay to RabbitMQ STOMP if scaling out).
- Recipients: `training_course_recipient(course_code, username)` (`TrainingCourse.recipientUsernames`; create/update request field `recipientUsernames`, update: null keeps, list replaces). Each username must have `training.participation.approve.deanery` (deanery-managed course, in the course's deanery) or `training.participation.approve.diocese` (otherwise, in the course's diocese) - validated by `TrainingNotificationRecipientResolver`.
- `GET /api/training/courses/recipient-candidates?courseTypeCode=` lists eligible accounts (`username`, `fullName`, `roleName` = active role names joined by ", ", primary first) in the caller's scope for the FE picker (authorized like course create for that type).
- Notification routing: registration submit of a deanery-managed course and the deanery submit of a diocese course notify the course's recipients (still-eligible ones; falls back to all eligible accounts if none chosen/valid). Parish submit of a diocese course still notifies all eligible deanery accounts. `recipientUsernames` in `TrainingRegistrationRequest`/`TrainingSubmissionRecipientRequest` is now ignored.
- Course response adds `dioceseName`, `deaneryName`, `registrationOpen`, `recipientUsernames`, `createdBy`, `updatedBy` (createdBy/updatedBy were already persisted, just not returned).

### WebSocket handshake security

- `/ws/notifications` is `permitAll` in `securityConfig` (both public matcher variants). Browsers cannot send an `Authorization` header on a WebSocket upgrade, so the HTTP handshake must be public; authentication happens at the STOMP `CONNECT` frame in `WebSocketConfig.NotificationInboundChannelInterceptor` (Bearer token + active Redis session), and only `/user/queue/notifications` and the auth-events queue can be subscribed. Before this fix `anyRequest().authenticated()` rejected the handshake with 401, so NO realtime push (notifications, auth refresh events) ever reached the browser. Verify with a raw upgrade request: expect `101`, not `401`.

### Rate limit response encoding

- `ApiRateLimitFilter` writes the 429 body by hand; `Content-Type: application/json` has no charset so the servlet writer defaulted to ISO-8859-1 and the Vietnamese message came out as mojibake. It now calls `setCharacterEncoding(UTF-8)` (covered by `ApiRateLimitFilterEncodingTest`). Any filter that writes a JSON body manually must set UTF-8 (the other filters already do).
- Read tier is 120 requests/minute per user (`app.rate-limit.read.*`); the FE must keep requests per screen low (see `FE/agent.md`).

### API Cache Policy

Cache implementation notes:

- API read caching now uses Redis with key prefix `ht:api-cache:`
- cache keys include normalized request payload, current username, authority hash, and resolved scope ids
- scope-aware keys prevent one admin from reading another scope's cached result
- Redis serialization keeps cached `PageImpl` list responses readable on cache hits, including Java time fields
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
- `BaseModel` owns automatic audit metadata through a JPA entity listener: every JPA persist fills missing `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, and default `status=true`; every JPA update refreshes `updatedAt` and fills missing `updatedBy`. Actor resolution uses `super-admin`, then current `leaderId`, then username, and finally `SYSTEM` for background work. Services may still set explicit audit actors when they need a business-specific actor; the listener preserves non-blank actor fields.
- Relation request DTOs should keep using IDs instead of nested payloads.
- Bắt buộc sử dụng UTF-8 cho mọi dòng code, string literal, và comment bằng tiếng Việt; không được để phát sinh mojibake trong source.
- Role and permission mapping changes take effect on the next login or refresh. Account update, disable, and delete operations are stricter: they revoke the account's Redis sessions immediately, so its existing access tokens no longer pass the server-side session check.
- Organization modules no longer rely on generated slug codes or business `code` values.
- If a later session continues organization work, start from the helper structure under `common/validation`, `modules/diocese/helper`, `modules/deanery/helper`, and `modules/parish/helper` instead of moving validation back into services.
- Parish moves must preserve the UUID-based storage model and stay inside the same diocese. Leader moves are stricter: a parish-scoped administrator may only manage leaders in the active parish and may not move a leader to another parish.
- Training participation queue/detail APIs are available at `GET /api/training/participations` and `GET /api/training/participations/{id}`. Both are scope-filtered by the owning course and support `BaseSearchRequest` for the list endpoint.
- Refresh tokens are transport-only credentials: they are written to the HttpOnly refresh cookie and intentionally excluded from JSON response bodies.
- Every `TrainingCourse` must reference a score formula. Flyway `V028` maps legacy courses only when their owner scope has exactly one active formula, then enforces `score_formula_id NOT NULL`; ambiguous or missing mappings stop migration for an explicit business decision.
- A course requirement must have the same `courseType` and owner scope as its training course. Changing `courseType` requires a matching new formula and is forbidden after registrations exist.
- Public account create/update chỉ cho `ADMIN_DIOCESE` dùng direct `dioceseId`; mọi account khác bắt buộc gắn Leader và resolve scope từ Leader. Các cột direct deanery/parish cũ không còn nằm trong request contract.
- Tạo account giờ bắt buộc gửi `roleCode` và backend tạo primary `AccountRole` trong cùng transaction. `SUPER_ADMIN` chỉ được tạo/quản lý account `ADMIN_DIOCESE` có direct `dioceseId`; không được tạo account cấp thấp hơn.
- `Account` không còn giữ `avatarUrl`; ảnh đại diện leader dùng field nullable `leader.imageUrl`, và migration `V032__move_account_avatar_to_leader_image.sql` sẽ backfill từ account cũ sang leader theo `leader_id` trước khi drop cột `account.avatar_url`.
- `ADMIN_DIOCESE`, `ADMIN_DEANERY`, `ADMIN_PARISH` dùng lần lượt `system.account.manage.diocese|deanery|parish` để tạo account role thấp hơn và đọc/update/delete/toggle account khác có role bằng hoặc thấp hơn trong đúng scope của mình; không được tự quản lý account đang đăng nhập.
- Gán role cho account cho phép role bằng hoặc thấp hơn actor trong cùng scope. Gán quyền cho role qua role-permission chỉ cho phép role đích thấp hơn actor, actor phải có authority assign/revoke tương ứng, permission không được có scope cao hơn authority đó, và actor phải đang sở hữu chính permission cần gán. `SUPER_ADMIN` không còn gán `ADMIN_DIOCESE` qua API account-role mà phải dùng luồng tạo account để hệ thống sinh và gửi credential; parish dùng `system.account_role.assign.parish`. Direct permission override cũng bị giới hạn theo account đích; scoped admin chỉ được gán permission mình đang sở hữu và permission đó không được có scope cao hơn actor.
- `Leader` currently treats `leaderLevel` as create-only input; do not re-open direct update of this field from the normal Leader update API.
- Training scope resolution now prefers `account.diocese`, `account.deanery`, and `account.parish` before using the `Account -> Leader -> Parish -> Deanery -> Diocese` fallback chain.
- Training registration now uses a two-step non-DT flow: parish -> deanery only aggregates and notifies deanery recipients, while deanery -> diocese is the first point where eligible non-DT registrations may auto-approve.
- For DT courses, parish submission may auto-approve eligible registrations at deanery stage through RabbitMQ, but non-eligible records remain pending for manual deanery review.
- Training scoring now treats the course formula as the source of truth: every participation must send the exact component set from the course formula before the backend computes `totalScore`.
- Score formulas use arithmetic expressions over component codes. Once any participation using a formula has a non-null `totalScore`, that formula is locked and `PUT /api/training/score-formulas/{id}` returns conflict; create a new formula for future courses instead of rewriting scored history.
- Notification recipients are permission-driven rather than role-name-driven; custom roles such as `nghienhuan-liendoan` work as long as their effective permissions and resolved scopes match the current workflow step.
- Training and certificate approval currently assume the progression map encoded in `TrainingWorkflowSupport`; if `CourseType` expands, update that helper first.
- Certificate approval now owns leader promotion and `RankHistory` creation, so normal `Leader` update APIs should continue treating `leaderLevel` as non-editable business state.
- Certificate proposals may be auto-created by the system when a leader passes a course, but only a user with the scoped certificate approve permission can finalize the certificate.
- Exception certificate approvals for non-passing leaders must always record `exceptionApprovalReason`.
- Certificate approval requires a scored, non-cancelled participation whose required workflow stage is approved. Certificate promotion is monotonic: an approved late certificate never lowers a Leader level, and approved certificate identity/result fields are immutable.
- Participation scores may be edited while the related certificate is not approved. Once `certificateApprovalStatus` is `APPROVED`, `PATCH /api/training/participations/{id}/score` returns conflict and the score becomes immutable.
- `ExecutiveBoardAssignment` update now explicitly saves the entity after scope and uniqueness validation so changed leader/position/unit fields are persisted consistently.
- `SystemLog` scope columns are now the source of truth for scoped audit-log reads; later resource modules should backfill these fields when they introduce new audit-visible resources.
- `SystemLog` retention now deletes records whose `loggedAt` is older than 10 days through the `SystemLogRetentionScheduler` background job.
- Role and permission administration now use `/api/system/roles`, `/api/system/permissions`, `/api/system/role-permissions`, and `/api/system/account-permissions` rather than folding everything into the original `system.role.manage.all` and `system.permission.manage.all` permissions.
- The effective-permission SQL views are now part of the security contract: if a record with authorization impact is toggled to inactive, it must disappear from the next JWT login or refresh result without needing extra Java-side filtering.
- Auth hardening local July 2026: refresh token chỉ còn claim tối thiểu và chỉ được set bằng `refreshToken` HttpOnly cookie để tránh `HeadersTooLargeException`; `/api/auth/bootstrap/super-admin` không public; generic 500, malformed JSON, và missing resource responses đã được sanitize để không lộ chi tiết nội bộ. Swagger UI và `/v3/api-docs/**` chỉ anonymous khi `APP_SECURITY_SWAGGER_PUBLIC_ENABLED=true` (development override); production Compose luôn đặt flag `false`.
- Compose production chỉ publish cổng backend. PostgreSQL, Redis, RabbitMQ AMQP và RabbitMQ Management chỉ trao đổi trong Docker network; override development bind các cổng hỗ trợ vào `127.0.0.1`.
- Auth session hardening local July 2026: refresh rotation giữ hợp lệ access-token JTI liền trước trong Redis session state để client không rơi `401` ngay sau khi vừa refresh, dùng atomic Redis compare-and-set để chặn refresh replay/concurrency, và giới hạn đúng 2 IP với 1 session/account/IP.
- Kết quả kiểm thử tải cục bộ ngày 2026-07-09 đã xác nhận read surge không phát sinh `401` hay `5xx`; hệ thống tự shed load bằng `429` dưới áp lực thay vì sập dịch vụ.

### Flow audit 2026-07-11

- Chấm điểm và duyệt chứng chỉ là hai nghiệp vụ loại trừ `SUPER_ADMIN`: migration `V030` vô hiệu bốn grant score/approve của vai trò này, bảo đảm grant đúng cho `ADMIN_DIOCESE` và `ADMIN_DEANERY`, còn access evaluator chặn `ROLE_SUPER_ADMIN` ngay cả khi access token cũ vẫn mang authority.
- Quyền chấm điểm gồm `training.participation.score.diocese` cho khóa giáo phận và `.deanery` cho khóa DT giáo hạt; quyền duyệt chứng chỉ tương ứng là `training.certificate.approve.diocese` và `.deanery`. Cả hai luôn kiểm tra đúng owner scope của khóa/hồ sơ.
- Flow chấm điểm chỉ cho hồ sơ không hủy đã được duyệt đúng cấp; request phải gửi đúng và đủ component của formula, không trùng, không vượt max, backend tự tính total. Đạt điểm tạo proposal chứng chỉ `PENDING`; chứng chỉ `APPROVED` khóa sửa điểm.
- Flow duyệt chứng chỉ chỉ nhận participation đã chấm, không hủy, được duyệt đúng cấp; dưới passing score bắt buộc `approvalReason`, approval cập nhật trạng thái, promotion đơn điệu và ghi `RankHistory`.
- Flow đăng ký chỉ nhận khóa, requirement và leader đang hoạt động, trong cửa sổ đăng ký, đúng parish scope và chưa có hồ sơ trùng. Eligibility kiểm tra current level, tuổi, số năm hoạt động và khóa tiên quyết; thiếu ngày sinh/ngày bắt đầu hoạt động khi requirement tương ứng tồn tại được xem là không đủ điều kiện và bắt buộc exception reason.
- `PUT /api/training/courses/{courseCode}` và `PUT /api/training/requirements/{requirementCode}` kiểm tra cả scope nguồn lẫn quyền `update` của scope đích khi đổi `courseType`; không thể chuyển ownership giữa deanery và diocese chỉ nhờ quyền update của resource cũ. Requirement update chạy trong transaction để thay đổi hợp lệ được persist.

- `GET /api/analytics/certificates` now joins `training_course` in every certificate breakdown query before applying course-owner scope; scoped dashboards must not reference a missing `tc` alias.
- Executive-board assignment authorization is hierarchical: diocese assignment permissions cover descendant deanery/parish targets, and deanery permissions cover descendant parish targets; update luôn kiểm tra quyền `update.*` tại target scope (không dùng quyền `create.*`) trước khi persist, nên không thể đổi assignment sang scope cao hơn.
- Executive-board assignment rejects an `endDate` earlier than `startDate`; FE must apply the same validation before submit.
- `PositionCreateRequest.positionType` is mandatory and must be one of `DIOCESE`, `DEANERY`, or `PARISH`.
- Position list được lọc theo từng `organization.position.read.<scope>` trước pagination, thống nhất với rule detail theo `positionType`.
- Roles created through the public role-management API are always non-system roles; `isSystem` is backend-owned metadata and normal role updates must not change it.
- Leader management includes `DELETE /api/leaders/{id}` guarded by `organization.leader.delete.parish`; FE should prefer status deactivation when audit/history retention is required.
- The detailed FE integration contract, state machines, UX rules, endpoint payloads, response expectations, RBAC notes, test matrix, and open product decisions are maintained in `../HT-MANAGEMENT-FE-Technical-Guide.html` and `../HT-MANAGEMENT-FE-Technical-Guide.docx`.
- Product decision finalized: scores remain editable only while the related certificate is not `APPROVED`; approved certificates lock scoring. Score formulas become immutable as soon as at least one participation using them has scoring data (`totalScore != null`). FE must disable the corresponding edit actions and still handle backend conflict responses.

### Scoped leader and account credential flow 2026-07-11

- `ADMIN_DIOCESE`, `ADMIN_DEANERY`, và `ADMIN_PARISH` được tạo leader trong đúng scope; create luôn bắt buộc `parishId`. Chỉ `ADMIN_PARISH` đúng giáo xứ được update hoặc toggle status leader.
- Normal leader update không nhận `fullName`, `birthDate`, hoặc `leaderLevel`; `holyName`, email, phoneNumber, imageUrl, gender, và parishId vẫn được phép cập nhật trong scope giáo xứ.
- `POST /api/system/accounts` nhận `roleCode`, `leaderId?`, `dioceseId?`, `credentialEmail?`; client không gửi username hoặc password.
- Account không phải `ADMIN_DIOCESE` bắt buộc gắn leader. `ADMIN_DIOCESE` luôn có direct `dioceseId`, có thể gắn leader cùng giáo phận hoặc không gắn leader; mỗi giáo phận chỉ có một account `ADMIN_DIOCESE`, không phụ thuộc trạng thái account.
- Account có leader tự sinh username bằng `firstName + parishName + day + month`; account `ADMIN_DIOCESE` không leader dùng `dioceseName + admin`. Chuỗi được viết thường, bỏ dấu/ký tự đặc biệt và thêm hậu tố `2`, `3`, ... khi trùng.
- Account không leader bắt buộc `credentialEmail`; field này chỉ dùng gửi mail và không lưu DB. Account có leader nhận credential qua `Leader.email`.
- Mật khẩu tạm là chuỗi ngẫu nhiên 12 ký tự từ `SecureRandom`, được BCrypt hash trước khi lưu. Account mới có `mustChangePassword=true`; access token của account đó chỉ được dùng cho `/api/auth/change-password` (và `/api/auth/me`) cho đến khi đổi mật khẩu. Email tạo account luôn chứa username và mật khẩu tạm; lỗi SMTP làm rollback account và primary role.
- `SUPER_ADMIN` chỉ create, update và toggle account `ADMIN_DIOCESE` qua luồng credential; nhưng vẫn có quyền đọc list/detail account toàn hệ thống để thực hiện giám sát và RBAC read scope.
- `POST /api/auth/change-password` nhận `{oldPassword,newPassword}`; mật khẩu mới dài 8-72 ký tự, khác mật khẩu cũ, được hash trước khi lưu và thành công sẽ revoke toàn bộ Redis session của account.
- Gmail SMTP dùng `smtp.gmail.com:587` với STARTTLS. Runtime cần `MAIL_USERNAME`, `MAIL_PASSWORD` là Google App Password, và tùy chọn `MAIL_FROM`; không commit secret vào Git.

### FE V1 backend support 2026-07-14

- Auth thêm `POST /api/auth/logout` public/idempotent: đọc refresh token từ HttpOnly cookie và Bearer access token nếu client còn giữ access token trong memory; token hợp lệ đầu tiên chỉ revoke đúng Redis `sessionId` của phiên đang logout bằng `revokeSession(sessionId)`, không gọi `revokeAllSessions` nên các phiên/IP khác của cùng account vẫn giữ nguyên. Token thiếu/sai/hết hạn vẫn xem là đã logout và luôn clear refresh cookie cùng path/SameSite/Secure/HttpOnly với `MaxAge=0`.
- Security matcher và JWT filter bỏ qua `/api/auth/logout` để FE có thể gọi logout kể cả access token trong memory đã mất hoặc hết hạn.
- WebSocket/STOMP `/ws/notifications` yêu cầu origin exact qua `APP_WEBSOCKET_ALLOWED_ORIGINS` (`app.websocket.allowed-origins`, mặc định `http://localhost:3000`), không chấp nhận origin rỗng hoặc wildcard.
- STOMP `CONNECT` yêu cầu native header `Authorization: Bearer <accessToken>`, parse access JWT theo issuer/audience/type, kiểm Redis `sessionId + username + jti`, chặn `mustChangePassword`, và set authenticated user cho STOMP session.
- STOMP chỉ cho subscribe `/user/queue/notifications`; mọi `SEND` từ client bị từ chối. Outbound per-message ERROR/close hardening và time-to-first-message tuning vẫn cần pass riêng nếu muốn đạt đủ mức kiểm tra phiên trên từng outbound frame.
- `CourseParticipationResponse` enrich thêm `leaderFullName` và `courseName`.
- `CertificateResponse` enrich thêm `leaderFullName`, `courseName`, `totalScore`, `passingScore`, `passed`, và `approvalReasonRequired`; các field này lấy từ participation cùng leader/course và không đổi certificate workflow.
- `ScoreFormulaTemplateResponse` thêm `locked`, tính bằng `hasScoredParticipations(id)`; rule update conflict hiện có không đổi.
- `AccountResponse` enrich thêm `leaderFullName`, `primaryRoleCode`, và resolve `dioceseId`, `deaneryId`, `parishId` từ direct account scope hoặc fallback `Leader -> Parish -> Deanery -> Diocese`.
- Biến môi trường mới: `APP_WEBSOCKET_ALLOWED_ORIGINS`. FE và deployment không được dùng wildcard trong production.
- Kiểm thử đã chạy: `.\gradlew.bat testClasses` thành công; `.\gradlew.bat test --tests phucuong.ht.contract.EndpointContractMatrixTest` thành công sau khi thêm route logout vào matrix; `.\gradlew.bat clean test` thành công.
- Logout current-session hardening ngày 2026-07-14 chạy trên branch `feature/logout-current-session`: `AuthService.logout(refreshToken, accessToken)` ưu tiên session từ refresh cookie, fallback access token khi refresh thiếu; unit test xác nhận chỉ gọi `revokeSession` và không gọi `revokeAllSessions`; controller test xác nhận `/api/auth/logout` public, nhận cookie/header và clear refresh cookie. Kiểm thử đã chạy: `.\gradlew.bat test --tests phucuong.ht.modules.auth.service.AuthServiceTest`, `.\gradlew.bat test --tests phucuong.ht.modules.auth.controller.AuthControllerSecurityIntegrationTest`, và `.\gradlew.bat clean test` đều thành công.
- BE còn thiếu so với plan V1: account `leader-options`, account `role-options`, training `registration-candidates`, outbound STOMP revalidation/ERROR close đầy đủ, heartbeat/time-to-first-message property, và targeted/full tests cho các endpoint mới.

### Admin Diocese Menu Permission Fix 2026-07-14

- Branch `fix/admin-diocese-menu-permissions` starts from `main`.
- Root cause: FE sidebar reads `/api/auth/me.permissions`; `ADMIN_DIOCESE` had direct `dioceseId` scope but the role seed did not grant scoped organization read permissions or leader read permissions, so Organization and Huynh trưởng routes were hidden for real logins.
- Added `V036__seed_scoped_organization_menu_permissions.sql` to seed `organization.leader.read.diocese`, `organization.leader.read.deanery`, and backfill scoped organization/leader grants for `ADMIN_DIOCESE`, `ADMIN_DEANERY`, and `ADMIN_PARISH`.
- Added `V037__direct_child_organization_permissions.sql` to grant direct-child create/toggle permissions only where allowed: `ADMIN_DIOCESE` can create/toggle deaneries in its own diocese, and `ADMIN_DEANERY` can create/toggle parishes in its own deanery.
- Scoped admin seed rule: admin roles may read descendant organization units for menu/listing and update only their own managed unit. Higher scopes may create/toggle only direct child units they manage; they cannot create/delete same-level units and cannot operate on grandchildren.
- List cache remains enabled. Redis cache serialization now restores cached `PageImpl` responses correctly on repeated list reads.
- `GET /api/leaders` now supports diocese/deanery/parish read scopes and filters by `parish.deanery.diocese.id`, `parish.deanery.id`, or `parish.id` before pagination; leader update/toggle remains parish-scoped.
- Kiểm thử đã chạy: `.\gradlew.bat testClasses` thành công; targeted diocese/deanery/parish/leader tests thành công; container hiện tại đã apply Flyway tới `037` và xác nhận `/api/dioceses` cache hit trả `200`.

### All-module form workflow support 2026-07-15

- Branch `feature/all-module-form-api-support` starts from `origin/main` and stays local until a separate commit/push request.
- Added `GET /api/analytics/work-queue`, guarded by dashboard read permission, returning scoped real counts for pending registration approvals, pending scoring, courses closing registration within 7 days, and pending certificates.
- Dashboard notifications continue to come from `GET /api/system/notifications`; no dashboard work-queue or notification counts should be hardcoded in FE.
- Added `GET /api/leaders/{id}/rank-history`, guarded by existing leader read evaluator, returning read-only rank history. Promotion remains owned by certificate approval.
- Added `GET /api/system/permissions/taxonomy`, guarded by `SUPER_ADMIN`, returning active permission modules, resources, actions, and scopes for permission create/edit forms.
- Added data-only Flyway migration `V038__form_workflow_support_policy.sql` for the new analytics, leader rank-history, and permission taxonomy routes. No schema change.
- Verification run in this session: `.\gradlew.bat compileJava`, `.\gradlew.bat testClasses`, `.\gradlew.bat test --tests phucuong.ht.contract.EndpointContractMatrixTest`, and full `.\gradlew.bat test` passed.

### SUPER_ADMIN RBAC scope tightening 2026-07-15

- Branch `feature/super-admin-rbac-scope` starts from `origin/main` and keeps SUPER_ADMIN scope explicit across organization, account, role, role-permission, and account-permission flows.
- SUPER_ADMIN can read dioceses/deaneries/parishes, create dioceses, and toggle dioceses; SUPER_ADMIN cannot update/delete dioceses and cannot create/update/delete/toggle deaneries or parishes.
- SUPER_ADMIN account list is restricted to `SUPER_ADMIN` and `ADMIN_DIOCESE`; `AccountResponse` now includes `dioceseName` while preserving `dioceseId` for filters/internal values.
- `POST /api/system/accounts` accepts `roleCode=SUPER_ADMIN` with `credentialEmail`, rejects `leaderId`/`dioceseId` for that case, and generates username from the email local part with duplicate suffixing.
- Role list returns system roles plus custom roles created by `super-admin`; system roles cannot be deleted or toggled.
- Follow-up correction: the "system roles plus custom roles created by `super-admin`" list restriction applies only when the actor is `SUPER_ADMIN`; non-SUPER_ADMIN role list/detail flows continue to use the existing scoped authorization helper without this extra visibility filter.
- Role-permission and account-permission effective state is based on `effect` plus `expiresAt`; `status` remains true and is no longer the enable/disable signal.
- Added `POST /api/system/role-permissions/bulk` with `{roleCode, permissionCodes, effect, conditions?, assignedAt?, expiresAt?}`.
- Added `POST /api/system/account-permissions/bulk` with `{username, permissionCodes, effect, reason, expiresAt?}`; valid targets are accounts with `ADMIN_DIOCESE`, `ADMIN_DEANERY`, `ADMIN_PARISH`, or a custom role created by `super-admin`.
- Added `GET /api/system/accounts/permission-targets` to support the account-permission target dropdown.
- Flyway `V039__super_admin_rbac_scope_and_effect_contract.sql` normalizes permission assignment status to true, seeds SUPER_ADMIN deny mappings for removed modules/actions, and recreates effective-permission views to derive validity from `effect`/expiry rather than assignment status.
- Flyway `V040__super_admin_bulk_permission_api_policies.sql` registers API permission policies for permission-target and bulk permission endpoints.
- Verification run in this session: `.\gradlew.bat compileJava`, `.\gradlew.bat testClasses`, targeted RBAC/controller tests, and `.\gradlew.bat test --tests phucuong.ht.contract.EndpointContractMatrixTest` passed; full `.\gradlew.bat test` should be re-run before merge after documentation edits are complete.

### Account role and role-permission assignment UX fix 2026-07-15

- `POST /api/system/account-roles/bulk` accepts `{username, roleCodes, primaryRoleCode?, assignedAt?, expiresAt?}` so one account can receive multiple roles in a single request. Existing assignments are reactivated/updated instead of duplicating the `(username, role_code)` unique key.
- `GET /api/system/account-roles/assignable-roles` returns active roles the current actor may assign. SUPER_ADMIN sees only `SUPER_ADMIN`, `ADMIN_DIOCESE`, and custom roles created by `super-admin`; scoped admins see roles at or below their assignable hierarchy.
- SUPER_ADMIN account-role assignment now follows managed-account scope instead of being hard-blocked: target accounts must already be in the SUPER_ADMIN-managed set (`SUPER_ADMIN`, `ADMIN_DIOCESE`, or custom roles created by `super-admin`), and self-assignment is still forbidden.
- Role-permission bulk assignment remains `POST /api/system/role-permissions/bulk`, but FE now exposes it as the default assignment action for any actor with `system.role_permission.assign.*`, not only SUPER_ADMIN.
- API policy migration `V041__account_role_bulk_assignment_api_policies.sql` registers account-role bulk and assignable-role helper endpoints for `all`, `diocese`, `deanery`, and `parish` scopes.
- Verification run in this session: `.\gradlew.bat compileJava`, `.\gradlew.bat testClasses`, `.\gradlew.bat test --tests phucuong.ht.modules.system.service.AccountRoleServiceTest`, `.\gradlew.bat test --tests phucuong.ht.modules.system.controller.RolePermissionControllerSecurityIntegrationTest`, `.\gradlew.bat test --tests phucuong.ht.contract.EndpointContractMatrixTest`, and full `.\gradlew.bat cleanTest test` passed.
- Follow-up filtering correction: assign option endpoints now exclude already assigned records for the selected target. `GET /api/system/account-roles/assignable-roles?username=...` removes roles already present on the account, `GET /api/system/role-permissions/assignable-permissions?roleCode=...` removes permissions already assigned to the role, and `GET /api/system/account-permissions/assignable-permissions?username=...` removes direct overrides already assigned to the account.
- API policy migration `V042__unassigned_assignment_option_api_policies.sql` registers the new unassigned-permission option endpoints for role-permission and account-permission forms.
- Menu visibility for `Gán vai trò` is driven by `system.account_role.assign.*` in `/api/auth/me.permissions`; migration `V043__ensure_account_role_assignment_menu_permissions.sql` ensures `SUPER_ADMIN`, `ADMIN_DIOCESE`, `ADMIN_DEANERY`, and `ADMIN_PARISH` keep the exact scoped assign permission needed for their managed accounts.
- `/api/auth/me` now reloads the current principal from the database before returning roles/permissions, so menu visibility reflects updated RBAC rows instead of a stale permission snapshot embedded in the current access token.
- Root cause for `phucuongadmin` not seeing `Gán vai trò`: an `account_permission_override` direct `DENY` on `system.account_role.assign.diocese` overrode the role-level `ALLOW`. Migration `V044__remove_admin_account_role_assignment_denies.sql` removes stale direct denies for required admin-unit account-role assignment permissions, and the account-permission service now rejects recreating those denies.
- Re-enabling a role only restores `role.status=true`; it does not recreate `role_permission` rows. If a custom role such as `LD_TRUONG` currently has zero `role_permission` records, accounts assigned to that role still resolve zero effective permissions after login until permissions are granted back through role-permission management.

### Account management guard follow-up 2026-07-16

- Branch `feature/super-admin-account-filter-drawer` adds an explicit service authorization guard for account management targets with active `SUPER_ADMIN` role.
- `AccountManagementAuthorizationHelper.requireCanManage(...)` still rejects self-management first, so no role can update or toggle the currently logged-in account through account management APIs.
- Account targets with active `SUPER_ADMIN` role are rejected before the normal SUPER_ADMIN-managed `ADMIN_DIOCESE` branch, so toggle/delete flows cannot mutate SUPER_ADMIN accounts through this service path.
- Follow-up filter binding fix: `BaseSearchRequestArgumentResolver` now owns list query binding for `page`, `size`, `search`, `searchFields`, `filters`, `status`, `sortBy`, and `sortDirection`; account filter regression coverage verifies JSON `filters` values for `roleCode` and `dioceseId`.
- Account edit is no longer supported by the account management API; `PUT /api/system/accounts/{username}` was removed. Account creation accepts `roleCodes` plus `primaryRoleCode` for non-super-admin management screens, rejects `SUPER_ADMIN`/`ADMIN_DIOCESE` in that multi-role flow, and guarantees one selected primary role.
- `ADMIN_DIOCESE` uniqueness is based on active accounts only: a diocese may create another `ADMIN_DIOCESE` account when all existing admin-diocese accounts in that diocese are inactive, but creation is rejected while any active admin-diocese account exists.
- `AccountResponse` now exposes display fields for FE tables/details: `primaryRoleName`, `roleNames`, `secondaryRoleNames`, `dioceseName`, `deaneryName`, and `parishName` alongside ids/codes for filtering and internal values.
- `GET /api/system/accounts` accepts account filter keys `roleCode`, `dioceseId`, `deaneryId`, and `parishId`; role filtering matches any active assigned role, not only the primary role.
- `GET /api/leaders/account-candidates` returns scoped leaders without an existing account for the account-create leader dropdown; the endpoint pages through scoped leader results until it fills the requested candidate size.
- FE contract follow-up: scoped admin account create should send `roleCodes`, `primaryRoleCode`, and `leaderId`; `dioceseId` and `credentialEmail` are reserved for the SUPER_ADMIN `ADMIN_DIOCESE` create flow.
- Verification run in this session: `.\gradlew.bat test --tests phucuong.ht.modules.system.service.AccountManagementServiceTest --tests phucuong.ht.modules.system.service.AccountManagementAuthorizationHelperTest --tests phucuong.ht.modules.system.controller.AccountControllerFilterBindingTest`, `.\gradlew.bat testClasses`, and `git diff --check` passed.

### Role management contract cleanup 2026-07-17

- Branch `feature/role-management-contract-cleanup` starts from `origin/main`.
- `POST /api/system/roles` no longer relies on client-provided `roleCode`; the backend derives `roleCode` from `roleName` by removing Vietnamese accents, uppercasing, replacing separators with `_`, and appending numeric suffixes `2`, `3`, ... when the generated code already exists.
- `PUT /api/system/roles/{roleCode}` now runs inside a transaction and explicitly saves the role after applying changes, so role name, description, display order, and audit metadata persist before the success response is returned.
- `DELETE /api/system/roles/{roleCode}` was removed from the controller and endpoint contract matrix; role management is status-based only.
- New role toggle rule supersedes the earlier note that re-enabling a role only flips `role.status`: toggling a non-system role now also sets every attached `role_permission.status` to the same active/inactive value and updates audit metadata on those mappings.
- `V045__seed_scoped_role_cru_toggle_permissions.sql` seeds `system.role.create/read/update/toggle.{all,diocese,deanery,parish}`, grants unit-scoped CRU/toggle permissions to `ADMIN_DIOCESE`, `ADMIN_DEANERY`, and `ADMIN_PARISH`, moves role toggle API policy to `system.role.toggle.*`, and disables the old role delete permission/policy.
- Scoped role management now accepts unit-scoped authorities in `RoleController`; service authorization still prevents non-SUPER_ADMIN users from managing roles above their role hierarchy or mutating system roles.
- `RoleResponse` now includes `createdBy` and `updatedBy` so FE detail drawers can show creator/updater information for role records.
- Verification run in this session: `.\gradlew.bat testClasses`, `.\gradlew.bat test --tests phucuong.ht.modules.system.service.RoleManagementServiceTest --tests phucuong.ht.modules.system.controller.RoleControllerSecurityIntegrationTest --tests phucuong.ht.modules.system.service.ScopedSystemAdministrationReadTest`, and `git diff --check` passed. Local Docker app was rebuilt; `/api/auth/me` on port `1007` returned `401`, Flyway `045` was applied successfully, and DB verification showed 4 active scoped role CRU/toggle grants for each admin unit role.

### Delete-to-toggle contract cleanup 2026-07-21

- Branch `feature/toggle-delete-rbac-permissions` removes CRUD hard-delete entry points from module controllers/services and the shared `BaseRepo`; record lifecycle is status/effect based.
- All former delete-gated module actions now use `PATCH .../toggle-status` with `*.toggle.*` permissions. Existing Redis session deletion and system-log retention cleanup remain non-CRUD infrastructure behavior.
- Role-permission and account-permission rows are effect-based: toggle flips `ALLOW`/`DENY` while keeping assignment `status=true`, so effective permission views continue to derive access from `effect` and expiry.
- Flyway `V046__replace_delete_permissions_with_toggle.sql` creates toggle permissions from existing delete permissions, copies role/account permission assignments, disables old delete permissions/policies, and registers toggle API policies.
- Role-permission administration now includes read/assign/revoke scopes for all/diocese/deanery/parish so scoped admin roles can see and manage permission rows for roles they are allowed to manage.
- Verification run in this session: `.\gradlew.bat testClasses`, targeted security/contract tests, and full `.\gradlew.bat test` passed.

### RBAC assignment rules cleanup 2026-07-22

- Branch `feature/rbac-assignment-rules` starts from up-to-date `main` in both BE and FE.
- System roles can only be edited or receive role-permission changes from `SUPER_ADMIN`. Scoped admins cannot assign permissions to `ADMIN_DIOCESE`, `ADMIN_DEANERY`, `ADMIN_PARISH`, or any other system role.
- Scoped admins may assign owned permissions only to custom roles whose `displayOrder` is equal to or lower than their admin role and only within their permission scope.
- Account-role assignment keeps system role delegation hierarchical: `ADMIN_DIOCESE` may assign `ADMIN_DEANERY` and `ADMIN_PARISH` to accounts in its managed diocese, `ADMIN_DEANERY` may assign `ADMIN_PARISH` in its managed deanery, and `ADMIN_PARISH` cannot assign admin system roles. SUPER_ADMIN can still assign/toggle `SUPER_ADMIN` and `ADMIN_DIOCESE` within the managed account rules, including re-enabling an inactive `ADMIN_DIOCESE` account-role assignment for a diocese-scoped account.
- Flyway `V054__ensure_scoped_admin_lower_role_assignment.sql` reasserts the scoped account-role assignment grants for `ADMIN_DIOCESE`, `ADMIN_DEANERY`, and `ADMIN_PARISH`, and removes stale direct `DENY` overrides that would block lower-admin-role delegation.
- Flyway `V055__seed_parish_participation_read_permission.sql` grants `training.participation.read.parish` to `ADMIN_PARISH`; parish admins may read participation records only for leaders in their own parish, including registrations to deanery-managed and diocese-managed courses.
- `POST /api/system/permissions` and the backend create-permission service/repository helper were removed. Flyway `V049__disable_permission_create_contract.sql` disables `system.permission.create.all` and its role/account grants because permission rows are now managed by seed/migration, not ad-hoc API creation.
- Account-permission bulk assignment remains `POST /api/system/account-permissions/bulk` for any actor with `system.account_permission.manage.*`, matching the multi-role assignment flow with multiple permission codes in one request.
- `AccountRoleServiceTest` covers the scoped admin-system-role delegation rule, including `ADMIN_DIOCESE` assigning `ADMIN_DEANERY`, assignable-role options exposing `ADMIN_DEANERY`/`ADMIN_PARISH`, and rejection of `ADMIN_DIOCESE` self-level assignment.
- Verification run in this session: `.\gradlew.bat testClasses` passed; targeted `RolePermissionManagementServiceTest`, `AccountRoleServiceTest`, `AccountPermissionOverrideManagementServiceTest`, `PermissionManagementServiceTest`, `EndpointContractMatrixTest`, `AccountPermissionOverrideControllerSecurityIntegrationTest`, `RoleControllerSecurityIntegrationTest`, and `RolePermissionControllerSecurityIntegrationTest` passed. A full `.\gradlew.bat test` attempt hit PostgreSQL `FATAL: sorry, too many clients already`; rerunning the affected controller slice separately passed.

### Vietnamese permission metadata 2026-07-22

- Branch `feature/vietnamese-permission-labels` starts from up-to-date `main` in both BE and FE.
- Flyway `V050__vietnamese_permission_metadata.sql` translates `permission_module`, `permission_resource`, `permission_action`, `permission_scope`, and `permission.permission_name/description` into Vietnamese while preserving `permission_code`, module/resource/action/scope codes, status, and assignment semantics.
- Permission names are derived from taxonomy codes and special-cased for session management, dashboard read, role-permission assignment/revoke/read, account-role assignment, and account-permission management so labels match the HT Management RBAC context.
- `GET /api/system/permissions` accepts `filters.effect=ALLOW|DENY`; the service removes `effect` before repository entity filtering, then keeps only permissions with an active role-permission or account-permission assignment using that effect.
- `PATCH /api/system/permissions/{permissionCode}/toggle-status` requires both role `SUPER_ADMIN` and authority `system.permission.toggle.all`; Flyway `V051__seed_permission_toggle_for_super_admin.sql` creates this permission and grants it to `SUPER_ADMIN` if missing.
- Other roles cannot toggle permission catalog rows even if they carry stale `system.permission.toggle.*` or `system.permission.update.*` authorities.
- Verification run in this session: `git diff --check` passed; `.\gradlew.bat test --tests phucuong.ht.HtManagementNewApplicationTests` passed and loaded the new migration in the Spring test context; targeted `PermissionManagementServiceTest` and `PermissionControllerSecurityIntegrationTest` passed for effect filtering/binding and non-super-admin toggle rejection.

### Unit status lock and toggle notifications 2026-07-22

- Branch `feature/unit-status-lock-notifications` starts from `origin/main` in both BE and FE.
- Diocese, Deanery, and Parish disable behavior is effective/inherited only. Toggling a parent does not rewrite child `status`; descendants become usable again automatically when all ancestors are active, except objects directly disabled on their own row.
- `UnitOperationalStatusService` resolves account scope from direct `Account.diocese/deanery/parish` first and then `Account -> Leader -> Parish`. If any unit in that chain is inactive, `/api/auth/me` returns `unitLocked=true` and empty `roles`/`permissions`.
- Locked accounts may still login, refresh, call `GET /api/auth/me`, connect notification WebSocket, call `GET /api/system/notifications`, `GET /api/system/notifications/unread-count`, and `PATCH /api/system/notifications/{id}/read`; business APIs return `403`.
- Writes targeting data in a locked unit are blocked for organization unit management and account/account-role/account-permission target flows. Unit toggles are allowed only when ancestors are active, so child units must be re-enabled from parent to child.
- Toggle notifications are persisted in `training_notification` for role, permission, account-role, role-permission, account-permission override, Diocese, Deanery, and Parish toggles. Recipients are captured before the status/effect change, deduplicated, limited to active accounts, and reused for auth-refresh STOMP events.
- `TrainingNotificationService` now saves notifications in the current transaction and publishes `/user/queue/notifications` only after commit; existing training notification history and payload fields remain compatible.
- Added `GET /api/system/notifications/unread-count` returning `{ unreadCount }`.
- `GET /api/system/notifications` now returns a paged `Page<TrainingNotificationResponse>` using `BaseSearchRequest` paging. Notification reads and unread counts only include rows from the last 30 days; older `training_notification` rows are purged on notification list/count/read/dispatch paths.
- Permission catalog, role-permission, and account-permission search/filter/sort normalize FE-facing fields to entity paths (`module.moduleCode`, `permission.module.moduleCode`, `account.username`, etc.) so permission search and taxonomy filters do not trigger repository path errors.
- Account, account-role, account-permission, role, and role-permission write/toggle paths compare hierarchy against the actor's highest active role from the database, not merely the authority scope embedded in the current token; an actor may not affect an account or target role that is higher than the actor's real active role.
- When an account has multiple roles and its primary `account_role` is disabled, the backend clears that primary flag and automatically promotes the highest remaining active role (`displayOrder` ascending) as the new primary role. If no other active role remains, the account has no primary role until another role is enabled/assigned.
- Account-role assignment/update paths clear and flush the previous primary role before setting a different role as primary, preserving the `uq_account_one_primary_role` database constraint and avoiding transient duplicate-primary conflicts.
- Verification run in this session: `.\gradlew.bat compileJava`, `.\gradlew.bat testClasses`, targeted `AuthUserDetailsServiceTest`/`TrainingNotificationServiceTest`, and `EndpointContractMatrixTest` passed. Full `.\gradlew.bat test` was attempted but local PostgreSQL/RabbitMQ-backed Spring contexts failed during infrastructure startup; rerun the full suite when those services are healthy.
- Follow-up verification: `.\gradlew.bat compileJava`, `.\gradlew.bat test --tests phucuong.ht.modules.notification.service.TrainingNotificationServiceTest --tests phucuong.ht.modules.notification.service.ToggleNotificationDispatcherTest --tests phucuong.ht.contract.EndpointContractMatrixTest` passed after notification paging/retention and permission filter mapping updates.


### Training Course Type catalog refactor 2026-10-19

- Branch `feature/training-course-type-refactor` (BE + FE), bắt đầu từ `main` và giữ local cho tới khi có yêu cầu commit/push riêng.
- Bỏ enum cứng `phucuong.ht.modules.training.enums.CourseType`. Loại khóa huấn luyện giờ là catalog quản lý được trong bảng mới `training_course_type` (module `modules/training/courseType`).
- `training_course_type` là catalog TOÀN HỆ THỐNG (không gắn owner diocese/deanery). Mỗi loại khóa mang: `type_code` (PK), `type_name`, `management_unit_type` (DIOCESE|DEANERY — thay cho khái niệm "khóa DT do giáo hạt quản lý"), điều kiện tham dự đã gộp (`required_current_level`, `required_min_age`, `required_years_active`), khóa tiên quyết tự tham chiếu (`prerequisite_type_code`), cấp thăng khi duyệt chứng chỉ (`resulting_level`), và mã viết tắt sinh mã chứng chỉ (`certificate_short_code`).
- Gộp điều kiện khóa: bảng `course_requirement` và toàn bộ module `modules/training/courseRequirement` (controller/service/repo/helper/evaluator/DTO) đã bị XÓA. Điều kiện tham dự giờ nằm trực tiếp trên loại khóa.
- `TrainingCourse` bỏ enum `courseType` và FK `requirement`; thay bằng đúng một FK `course_type_code -> training_course_type`. Owner scope của khóa (diocese/deanery) suy ra từ `courseType.managementUnitType` + scope tài khoản khi tạo; đổi `managementUnitType` của loại khóa bị chặn khi đã có khóa dùng loại đó.
- `TrainingWorkflowSupport` nhận `TrainingCourseType` thay cho enum: `isDeaneryManaged` đọc `managementUnitType==DEANERY`, `prerequisiteOf` đọc `prerequisiteType`, `certificateLevelFor` đọc `resultingLevel`. `CertificateCodeGenerator.courseShortCode` đọc `certificate_short_code` (fallback về `type_code`).
- Eligibility (`CourseParticipationService.isEligible`) đọc điều kiện từ `course.getCourseType()`; khóa tiên quyết kiểm qua `existsApprovedParticipationByLeaderAndCourseType(leaderId, prerequisiteTypeCode)`. Payload RabbitMQ batch thêm cờ `deaneryManaged` để consumer tự quyết auto-approve mà không cần enum.
- Analytics: cột SQL `training_course.course_type`/`certificate` join đổi sang `course_type_code`; `groupBy=courseType` map `c.course_type_code`. Key metric FE vẫn là `courseType` (giá trị là type code).
- API mới: `/api/training/course-types` (POST/GET list/GET {typeCode}/PUT/PATCH toggle-status). Endpoint `/api/training/requirements` đã bị gỡ.
- RBAC: thêm resource `course_type` và quyền `training.course_type.{create,read,update,toggle}.{diocese,deanery}` (nhãn tiếng Việt). READ mở cho cả diocese/deanery (để chọn loại khi tạo khóa); CREATE/UPDATE/TOGGLE theo `managementUnitType` của loại khóa. Quyền `training.requirement.*` bị vô hiệu (status=false), grant role/account tương ứng được chuyển sang `training.course_type.*`.
- Migration `V056__replace_course_type_enum_with_catalog.sql`: tạo bảng, seed 14 loại khóa hiện hữu (giữ nguyên scope/tiên quyết/cấp thăng/mã viết tắt), backfill điều kiện từ `course_requirement`, thêm `training_course.course_type_code` + FK rồi drop `requirement_code`/`course_type`, drop bảng `course_requirement`, seed/di trú quyền.
- `AuditScopeResolver` bỏ nhánh scope cho `requirement` (catalog loại khóa là toàn hệ thống nên audit log của nó không gắn scope diocese/deanery).
- Kiểm thử: đã cập nhật/viết lại các unit test liên quan (TrainingCourseUpdateScopeTest, CourseParticipationServiceTest, CourseParticipationAccessEvaluatorTest, TrainingRegistrationAutoApprovalConsumerTest, CertificateServiceTest) và xóa CourseRequirementUpdateScopeTest. CHƯA chạy được `gradlew` trong môi trường agent (proxy chặn tải Gradle distribution, VM chỉ có JDK 11); cần chạy `.\gradlew.bat clean test` trên môi trường Windows để xác nhận biên dịch + test.


### Training registration redesign 2026-10-19

Branch `feature/course-years-active-registration-lists` (BE + FE, từ `main`). Migration V060-V063.

Số năm hoạt động của khóa (V060):

- `training_course.years_active` (INTEGER NOT NULL DEFAULT 0, CHECK >= 0); create/update request `yearsActive` (`@Min(0)`), response trả `yearsActive`.
- Điều kiện kèm theo khóa tiên quyết: huynh trưởng phải đạt khóa tiên quyết, và `năm tổ chức khóa - năm đạt khóa tiên quyết >= yearsActive`. Năm tổ chức = `hostYear` (fallback năm `startDate`, rồi năm hiện tại). Năm đạt = sớm nhất của các hồ sơ `COMPLETED` thuộc loại tiên quyết: ưu tiên năm `certificateApprovedAt`, rồi `hostYear`, rồi năm `endDate` của khóa đó (`CourseParticipationRepository.findEarliestCompletedYearByLeaderAndCourseType`).
- Loại khóa không có khóa tiên quyết thì bỏ qua điều kiện năm. Điều kiện cũ `required_years_active` của loại khóa (tính từ ngày tạo huynh trưởng) KHÔNG còn được kiểm tra (cột vẫn còn trong catalog).

Danh sách đăng ký và danh sách ngoại lệ:

- `GET /api/training/courses/{courseCode}/registration-candidates?mode=ELIGIBLE|EXCEPTION&search&deaneryId&parishId&excludeLeaderIds&page&size`: huynh trưởng đang hoạt động trong phạm vi tài khoản gửi (giáo xứ: giáo xứ mình; giáo hạt: giáo hạt mình; giáo phận: giáo phận mình), chưa có hồ sơ trong khóa. `ELIGIBLE` = đủ điều kiện; `EXCEPTION` = không đủ điều kiện và KHÔNG có cấp cao hơn "cấp độ đạt được gần nhất" của loại khóa (cấp thăng của khóa tiên quyết, nếu không có thì cấp hiện tại yêu cầu; so theo thứ tự `LeaderLevel`). Tìm kiếm theo tên, tên thánh, giáo xứ, giáo hạt.
- `POST /courses/{code}/registrations`: `leaderIds` (danh sách đăng ký, phải đủ điều kiện) và `exceptionReasonsByLeaderId` (danh sách ngoại lệ, mỗi người kèm lý do, phải không đủ điều kiện và không vượt cấp nêu trên). Hai danh sách không được trùng người (400). Cần ít nhất một người.

Ai được gửi đăng ký (sender):

- giáo xứ (`submit.parish`): khóa giáo phận của giáo phận mình và khóa giáo hạt của giáo hạt mình, đang mở đăng ký.
- giáo phận (`training.participation.submit.diocese`, V061, gán cho `ADMIN_DIOCESE`/`SUPER_ADMIN`): bất kỳ huynh trưởng nào trong giáo phận, chỉ vào khóa thuộc giáo phận.
- giáo hạt (`submit.deanery`): bất kỳ huynh trưởng nào trong giáo hạt, chỉ vào khóa thuộc giáo hạt.
- đơn vị gửi chọn theo phạm vi gắn trực tiếp của tài khoản trước (vì `ADMIN_DIOCESE` cũng giữ `submit.parish`): `TrainingWorkflowSupport.resolveRegistrationSender`.
- `GET /api/training/courses/registrable`: khóa đang mở đăng ký đúng phạm vi người gửi. Tài khoản giáo xứ cũng gọi được `GET /api/training/courses` (chỉ thấy khóa đang mở trong phạm vi) và xem chi tiết các khóa đó.

Duyệt:

- Bỏ bước giáo hạt tổng hợp (`deanery-submit`, event `deanery_batch_submitted`). Khóa giáo hạt do giáo hạt duyệt, khóa giáo phận do giáo phận duyệt.
- V062 gộp `deanery_approval_status`/`diocese_approval_status`/`deanery_submitted_at` thành một cột `approval_status`. Response có `approvalStatus` và `managementUnitType`.
- Hồ sơ đủ điều kiện được hệ thống tự duyệt ngay khi gửi (consumer RabbitMQ); hồ sơ ngoại lệ chờ duyệt tay. Thông báo gửi tới tài khoản duyệt của đơn vị sở hữu khóa.
- Trạng thái tham dự (V063): duyệt -> `ATTENDED`, từ chối -> `REJECTED` (giá trị enum mới); chấm điểm đạt -> `COMPLETED`.
- `POST /registrations/{id}/deanery-approval|diocese-approval` nhận `approved` true/false (từ chối cùng quyền với duyệt).
- `POST /api/training/registrations/bulk-approval {ids, approved, review}`: duyệt/từ chối nhiều hồ sơ; kiểm tra quyền và trạng thái chờ duyệt từng hồ sơ, hồ sơ lỗi được bỏ qua và trả về trong `failures`.
- `DELETE /api/training/registrations/{id}` (thu hồi): chỉ người đã gửi (so `createdBy` với audit actor), chỉ khi còn chờ duyệt; xóa hồ sơ để huynh trưởng đăng ký lại được.
- `GET /api/training/participants` (+ `/{id}`): danh sách tham dự, luôn lọc `approvalStatus = APPROVED` (hồ sơ chờ duyệt/từ chối không nằm trong danh sách). `DELETE /api/training/participants/{id}`: người có quyền duyệt đúng cấp xóa hồ sơ khỏi danh sách tham dự kể cả đã có điểm, trừ hồ sơ đã có chứng nhận được duyệt (chứng nhận đề xuất chưa duyệt bị xóa theo).
- Response hồ sơ có thêm `createdBy`, `parishId/Name`, `deaneryId/Name` của huynh trưởng.

Kiểm thử: `CourseParticipationServiceTest`, `TrainingRegistrationAutoApprovalConsumerTest`, `EndpointContractMatrixTest` (ma trận 122 route) pass. Một số test khác fail sẵn từ trước (lệch câu chữ thông báo; test tích hợp lỗi hết kết nối DB).

### Training scoring, locks and participant removal 2026-10-20

Branch `feature/course-years-active-registration-lists` (tiếp nối mục "Training registration redesign 2026-10-19"). Migration V064.

Chấm điểm (`PATCH /api/training/participations/{id}/score`):

- chỉ chấm được khi khóa đang trong thời gian diễn ra: `startDate <= hôm nay <= endDate` (Asia/Ho_Chi_Minh, `TrainingCourseStatusSupport.organizingStatus`, tính trực tiếp theo ngày, không chờ job cập nhật trạng thái); ngoài khoảng đó trả 409. Khóa chưa có `startDate` coi như chưa diễn ra.
- khóa/mở chỉnh sửa điểm: `training_course.score_locked` (V064) và `PATCH /api/training/courses/{courseCode}/score-lock {locked}`; quyền `training.participation.lock.diocese` (khóa giáo phận) / `.deanery` (khóa giáo hạt), gán cho `ADMIN_DIOCESE`/`ADMIN_DEANERY`/`SUPER_ADMIN`, kiểm tra theo phạm vi sở hữu khóa (`TrainingCourseAccessEvaluator.canLockScore`). Khi khóa thì chấm điểm trả 409. Response khóa có `scoreLocked`. Bật/tắt khóa không giới hạn theo thời gian (việc nhập điểm vẫn bị giới hạn bởi thời gian diễn ra).
- sửa điểm cập nhật tại chỗ các dòng `course_participation_component_score` đã có (không `clear()` rồi thêm lại: Hibernate chèn trước khi xóa nên vi phạm unique `(participation_id, component_id)` và bị báo "xung đột dữ liệu"); thành phần không còn áp dụng thì bỏ dòng cũ.
- hồ sơ có chứng nhận đã duyệt (cờ trên hồ sơ hoặc bản ghi `Certificate` có `approvalStatus = APPROVED`) không được sửa điểm (409).
- sửa điểm xuống dưới `passingScore`: chứng nhận đang `PENDING`/`SUBMITTED` của hồ sơ bị xóa (`CertificateRepository.delete`), trạng thái chứng nhận của hồ sơ về `NOT_REQUIRED`.
- `GET /api/training/courses/{courseCode}/score-components`: thành phần điểm áp dụng cho khóa (id, tên, điểm tối đa, hệ số, tính tổng) để FE dựng các cột nhập điểm; quyền chấm điểm đúng cấp quản lý của khóa (`canScoreCourse`).

Cập nhật khóa (`PUT /api/training/courses/{courseCode}`):

- khi khóa đã có hồ sơ tham gia, không đổi được loại khóa, năm tổ chức (`hostYear`), số năm hoạt động (`yearsActive`), điểm đạt (`passingScore`): đổi giá trị thì 409, gửi lại đúng giá trị cũ thì chấp nhận (`requireNoParticipations`). Các trường khác vẫn sửa được.
- chi tiết/cập nhật khóa trả `hasParticipations`; cache chi tiết khóa (`TRAINING_COURSE_DETAIL`) bị xóa khi đăng ký/thu hồi/xóa hồ sơ để cờ này không cũ.

Danh sách tham dự:

- `DELETE /api/training/participants/{id}`: người có quyền duyệt đúng cấp quản lý của khóa xóa hồ sơ khỏi danh sách tham dự, bất kể đã có điểm hay chưa, trừ hồ sơ đã có chứng nhận được duyệt (409); chứng nhận đề xuất chưa duyệt bị xóa theo; ghi sự kiện `training.participant.removed`.

Ma trận endpoint (`endpoint-contract-matrix.psv`) hiện có 124 route.

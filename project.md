# HT Management New

## Mục tiêu dữ liệu

Project này đang dùng một `BaseRepo` dùng chung để thao tác DB theo kiểu CRUD, đọc trang, search, filter và toggle `status` cho mọi entity.

## Base model

`BaseModel` là lớp nền cho hầu hết entity:

- `createdAt`
- `createdBy`
- `updatedAt`
- `updatedBy`
- `status`

Quy ước hiện tại:

- `status = true` nghĩa là active
- `status = false` nghĩa là inactive
- `toggleStatus(id)` chỉ đổi qua lại giữa hai trạng thái này
- `status` không nhận trực tiếp từ DTO CRUD

## Nhóm thực thể

### Khối tổ chức

- `Diocese`
- `Deanery`
- `Parish`

Quan hệ:

- `Diocese` 1 - N `Deanery`
- `Deanery` 1 - N `Parish`
- `Parish` 1 - N `Leader`

### Khối nhân sự

- `Leader`
- `Position`
- `RankHistory`
- `ExecutiveBoardAssignment`

Quan hệ:

- `Leader` thuộc một `Parish`
- `RankHistory` thuộc một `Leader`
- `ExecutiveBoardAssignment` thuộc một `Leader`
- `ExecutiveBoardAssignment` gắn với `Position`

### Khối đào tạo

- `CourseRequirement`
- `TrainingCourse`
- `CourseParticipation`

Quan hệ:

- `TrainingCourse` thuộc một `CourseRequirement`
- `CourseParticipation` thuộc một `Leader`
- `CourseParticipation` thuộc một `TrainingCourse`
- `Certificate` cũng liên kết tới `Leader` và `TrainingCourse`

### Khối chứng nhận

- `Certificate`

Quan hệ:

- `Certificate` thuộc một `Leader`
- `Certificate` thuộc một `TrainingCourse`

### Khối hệ thống

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

Quan hệ:

- `Account` 1 - 1 `Leader`
- `Account` 1 - N `AccountRole`
- `Account` 1 - N `AccountPermissionOverride`
- `Account` 1 - N `SystemLog`
- `Role` 1 - N `AccountRole`
- `Role` 1 - N `RolePermission`
- `PermissionModule` 1 - N `PermissionResource`
- `PermissionModule` 1 - N `Permission`
- `PermissionResource` 1 - N `Permission`
- `PermissionAction` 1 - N `Permission`
- `PermissionScope` 1 - N `Permission`
- `Permission` 1 - N `RolePermission`
- `Permission` 1 - N `AccountPermissionOverride`

## Convention request

Mỗi module có thư mục `model/request` với bộ DTO riêng:

- `XCreateRequest`
- `XUpdateRequest`

Quy ước đặt tên field:

- field thường giữ nguyên tên entity
- field quan hệ nhận id của entity liên quan, ví dụ:
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

`BaseRepo` ở `common/repository` xử lý:

- `create(request)`
- `update(id, request)`
- `findById(id)`
- `findAll(searchRequest)`
- `delete(id)`
- `toggleStatus(id)`

### Search request

`BaseSearchRequest` hỗ trợ:

- phân trang
- `search`
- `searchFields`
- `filters`
- `status`
- `sortBy`
- `sortDirection`

### Search/filter động

- `searchFields` là danh sách field path để search
- `filters` là map động theo field path
- hỗ trợ path lồng nhau, ví dụ:
  - `parish.parishCode`
  - `leader.parish.deanery.dioceseCode`
  - `course.requirement.requirementCode`

DTO request vẫn dùng id của relation, ví dụ `parishCode`, `leaderCode`, `courseCode`, `roleCode`, còn `BaseSearchRequest` dùng path của entity.

## Ghi chú kỹ thuật

- `Account` và `Role` không còn shadow `status` của `BaseModel`
- `RankHistory` không còn shadow `updatedBy`
- CU trong `BaseRepo` đọc DTO động, nên module chỉ cần validate dữ liệu ở request
- Các DTO relation chỉ cần truyền id của entity liên quan, không cần nested object

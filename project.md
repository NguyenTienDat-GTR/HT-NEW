# BE

> Cách dùng file này: đây là tài liệu tra cứu dài (khoảng 200 KB), không đọc từ đầu đến cuối trước mỗi việc. Tìm theo từ khóa (tên module, endpoint, tên quyền, số migration) rồi chỉ đọc đúng mục liên quan. Mã nguồn và migration là nguồn sự thật; mục nào lệch với mã thì mã đúng và phải sửa lại mục đó. Quy tắc bắt buộc cho mọi phiên nằm ở mục "Session Rules".

## Dashboard phân tích theo scope

- API (quyền `analytics.dashboard.read.all|diocese|deanery|parish`; backend tự resolve scope từ account, không nhận ID scope từ client):
  - `GET /api/analytics/overview` (trang Tổng quan), `/work-queue` (số việc cần xử lý theo key, FE gắn nhãn).
  - `GET /api/analytics/organization|leaders|courses|certificates` (trang Phân tích, 4 tab).
  - `GET /api/analytics/filter-options` (giáo hạt, giáo xứ, loại khóa, khóa, cấp bậc trong scope).
  - `GET /api/analytics/records?dataset=leaders|participations|certificates&dimension=&value=&page=&size=` (drill-down khi bấm biểu đồ) và `/records/export` (tối đa 20.000 dòng, FE xuất CSV).
- Bộ lọc chung: `fromDate`, `toDate` (mặc định 12 tháng gần nhất, tối đa 10 năm), `timeBucket` (`month|quarter|year`), `compare` (mặc định `true`, so sánh kỳ liền trước cùng độ dài), `groupBy`, `deaneryId`, `parishId`, `courseType`, `courseCode`, `leaderLevel`, `gender`, `includeInactiveUnits`. Bộ lọc luôn được AND sau phạm vi quyền nên không bao giờ mở rộng dữ liệu được xem. Mọi giá trị đi qua tham số có tên (không nối chuỗi SQL).
- Response: `scope`, `filters` (kỳ thực tế đã tính + kỳ so sánh), `kpis[]` (`key`, `unit` COUNT|PERCENT|SCORE|HOURS, `value`, `previous`, `delta`, `deltaPct`, `numerator`, `denominator`, `sparkline`), `breakdowns[]` (`type`, `key`, `label` là tên thực thể từ dữ liệu, `metrics`), `trends[]` (liên tục, kỳ không phát sinh = 0), `previousTrends[]` (cùng độ dài để chồng lên), `funnel[]`, `alerts[]` (`key`, `severity`, `count`, `items[]`). Không trả văn bản diễn giải; FE ánh xạ `key`.
- Quy ước tính để số liệu không lệch:
  - Tỷ lệ luôn là tử/mẫu trên cùng một tập; mẫu số 0 trả `null` (không giả 0%). `delta` của tỷ lệ tính bằng điểm phần trăm, `deltaPct` chỉ có với số đếm/điểm/giờ khi kỳ trước khác 0.
  - Khóa huấn luyện: cohort là các khóa có `start_date` trong kỳ. Phễu `registered ⊇ valid (không hủy/từ chối) ⊇ attended (ATTENDED|COMPLETED) ⊇ passed (result=COMPLETED) ⊇ certificate (đã duyệt)` đếm trên cùng tập hồ sơ nên mọi bước ≤ bước trước. Hồ sơ tính theo huynh trưởng thuộc scope (kể cả khóa do cấp khác tổ chức); bảng theo khóa gồm cả khóa chưa có hồ sơ khi không lọc theo đối tượng.
  - Chứng nhận: mốc ngày là ngày cấp, hoặc ngày tạo khi chưa cấp. `approvalRate = approved / (approved + rejected)`; `REVOKED` tính riêng. `conversionRate` = hồ sơ đạt (cohort khóa trong kỳ) đã có chứng nhận `APPROVED` / hồ sơ đạt. Trạng thái dựa vào `approval_status`, không dựa vào `revoked_at` (giữ lại làm lịch sử khi cấp lại).
  - Huynh trưởng: cơ cấu là ảnh chụp hiện tại; "huynh trưởng mới" dựa vào `created_at` (ngày nhập hồ sơ vào hệ thống, không phải ngày gia nhập phong trào) và "thăng cấp" lấy từ `rank_history` chưa bị hoàn tác. Số liệu theo kỳ dùng múi giờ `Asia/Ho_Chi_Minh`.
  - Cảnh báo dùng ngưỡng cố định và mẫu tối thiểu 5 để không báo động từ mẫu nhỏ (tỷ lệ đạt < 50%, hủy > 20%, từ chối > 20%, chứng nhận chờ > 7 ngày, hồ sơ chờ duyệt > 3 ngày, khóa sắp đóng đăng ký 7 ngày, tỷ lệ hoạt động < 80%, dưới 25 tuổi < 10%, lệch giới tính < 30%).
- `SUPER_ADMIN` chỉ gọi được overview với đúng bốn KPI toàn hệ thống: `totalDioceses`, `totalDeaneries`, `totalParishes`, `totalLeaders`; các endpoint chi tiết trả 403.
- Tài khoản có nhiều quyền phạm vi (giáo phận, giáo hạt, giáo xứ) chọn phạm vi xem bằng tham số `scope` (`diocese|deanery|parish`, mặc định phạm vi rộng nhất); chỉ được chọn phạm vi mình có quyền, ngược lại trả 403. `GET /api/analytics/scopes` liệt kê các phạm vi được xem.
- Giáo xứ không tổ chức khóa: tổng quan chỉ có nhân sự và chứng nhận của giáo xứ, `/courses` và danh sách hồ sơ đăng ký trả 403; `GET /api/analytics/open-courses` trả các khóa đang mở đăng ký (của giáo hạt và giáo phận trực thuộc), khóa còn tối đa 72 giờ có `urgency=closing`, để FE dẫn tới `/training/courses/{mã}?tab=registrations`.
- Diocese chỉ xem dữ liệu thuộc giáo phận và đơn vị con; deanery chỉ xem giáo hạt và giáo xứ con; parish chỉ xem giáo xứ hiện tại.
- Breakdown bổ sung: `scoreBucket` (phân bố điểm thang 10), `participationStatus`, `heat` (đơn vị × kỳ, khóa `p:<kỳ>`), `approvalTime` (ngưỡng thời gian duyệt), `agePyramid` (nhóm tuổi × giới tính), `promotionLevel`; trend của leaders/organization có thêm `cumulative` (hồ sơ tích lũy, chưa trừ người đã nghỉ).
- Cache theo tài khoản + toàn bộ bộ lọc + ngày hiện tại (`AnalyticsFilterRequest.cacheKey()`); các nghiệp vụ ghi dữ liệu xóa cache qua `@EvictCaches.Analytics`.

## Tắt đơn vị tổ chức (giáo phận / giáo hạt / giáo xứ)

- Tắt đơn vị **không ghi đè** `status` của đơn vị con, tài khoản hay huynh trưởng; trạng thái hiệu lực được suy ra khi truy vấn (đơn vị hoặc đơn vị cha có `status=false`). Bật lại đơn vị là khôi phục đúng nguyên trạng.
- Tài khoản thuộc đơn vị bị khóa chỉ đăng nhập và đọc thông báo (`UnitOperationalAccessFilter`).
- `StatusToggleRequest` có thêm `reason` (tối đa 500 ký tự); **bắt buộc khi tắt** đơn vị đang hoạt động. Lịch sử ghi vào bảng `org_unit_status_history` (migration `V088`): trạng thái mới, lý do, người đổi, thời điểm, số tài khoản và số đơn vị con bị ảnh hưởng, kèm dòng `BASELINE` ban đầu.
- `GET /api/{dioceses|deaneries|parishes}/{id}/toggle-impact` (quyền toggle) trả số đơn vị con, tài khoản, huynh trưởng, khóa chưa kết thúc, đăng ký và chứng nhận chờ duyệt; `GET .../{id}/status-history` (quyền đọc) trả lịch sử trạng thái. Logic ở `UnitStatusAuditService`.
- Đóng băng ghi dữ liệu: `CourseActivityGuard.requireActive` chặn mọi thao tác khóa học / đăng ký / chấm điểm / duyệt chứng nhận khi đơn vị tổ chức khóa bị khóa; `requireCourseEnabled` (dùng cho thu hồi chứng nhận) không chặn theo đơn vị. Không đăng ký mới cho huynh trưởng thuộc giáo xứ bị khóa nhưng hồ sơ đã có vẫn được đơn vị tổ chức hoàn tất. `LeaderService` chặn tạo/sửa trong giáo xứ bị khóa (chuyển sang giáo xứ đang hoạt động thì được phép); `ExecutiveBoardAssignmentService.create` chặn gán chức vụ trong đơn vị bị khóa.
- Chứng nhận đã cấp vẫn hợp lệ khi đơn vị bị tắt; chỉ thu hồi bằng chức năng thu hồi có lý do.
- `DeaneryResponse` / `ParishResponse` có `effectiveStatus`; danh sách huynh trưởng chọn được chỉ gồm giáo xứ hiệu lực.
- Analytics có `includeInactiveUnits` (mặc định `false`, nằm trong cache key): số liệu snapshot (số đơn vị, huynh trưởng hiện tại, cơ cấu) loại đơn vị bị khóa; số liệu sự kiện lịch sử (đăng ký, chứng nhận, huynh trưởng mới theo thời gian) luôn đầy đủ. Thêm KPI `inactiveDeaneries`, `inactiveParishes`, `leadersInInactiveUnits`.

## Chia đội khóa huấn luyện (khóa thường, không áp dụng khóa quá khứ)

- Cột `course_participation.team_name` (migration `V100`); `CourseParticipationResponse` có thêm `teamName`, `leaderGender`. Lọc theo đội bằng filter `teamName` của `/api/training/participants`.
- API (`CourseTeamController`, quyền như duyệt đăng ký đúng đơn vị sở hữu khóa; xem tổng quan/đội theo quyền đọc hồ sơ tham dự): `GET /api/training/courses/{code}/teams/overview` (tổng hồ sơ, nam/nữ, số đơn vị đang hoạt động, sĩ số từng đội), `GET .../teams/options` (tên đội để lọc), `POST .../teams/divide`, `DELETE .../teams`.
- `divide` nhận `teamCount`, `teamSize` (sĩ số tối đa mỗi đội), `namingMode` (`SEQUENTIAL` = "Đội 1..N" | `CUSTOM` + `teamNames`), `assignmentMode` (`RANDOM` | `UNIT_LIMIT` + `maxUnitsPerTeam`). `UNIT_LIMIT`: mỗi đội chỉ gồm hồ sơ của tối đa N đơn vị (giáo hạt với khóa giáo phận, giáo xứ với khóa giáo hạt), N không vượt số đơn vị đang hoạt động. Chia lại ghi đè đội cũ.
- Thuật toán `TeamDivisionPlanner`: sĩ số chênh nhau tối đa 1; hạn ngạch nam/nữ từng đội theo tỉ lệ toàn danh sách (giới tính chưa có xếp riêng) nên cân bằng nhất có thể; với `UNIT_LIMIT` dồn cùng đơn vị vào cùng đội, thử tối đa 300 lần ngẫu nhiên, không chia được thì báo lỗi gợi ý tăng N.
- Gán đội thủ công: `PATCH .../teams/assignments` (`assignments[{participationId, teamName}]`, tên rỗng = bỏ khỏi đội, tên không phân biệt hoa thường dùng lại cách viết của đội đã có). Hồ sơ bị xóa / từ chối khỏi danh sách tham dự (`applyApprovalDecision(false)`) bị xóa `teamName`; hồ sơ được duyệt xuất hiện ở tab chia đội vì tab dùng chính danh sách tham dự.
- Xuất Excel tab chia đội: `POST /api/training/participants/teams/export` (job `EXPORT_TEAMS`), tên khóa ở dòng tiêu đề, cột Tên thánh, Họ và tên, Giáo xứ, Giáo hạt, Đội.
- Import: `GET /api/training/courses/teams/import-template`, `POST /api/training/courses/{code}/teams/import` (job `IMPORT_TEAM_PARTICIPANTS`). Giống import danh sách tham dự khóa thường nhưng thêm cột bắt buộc `Tên đội`, không có sheet Ngoại lệ; hồ sơ vào thẳng danh sách tham dự (đã duyệt, không kiểm tra điều kiện khóa) và gắn đội.

## Thẻ tham dự, điểm danh QR và email thông tin tham dự (khóa thường, không áp dụng khóa quá khứ)

- Migration `V101`: `training_course.attendance_enabled` / `auto_send_invitation` (mặc định tắt); `course_participation.checked_in_at/by`, `invitation_sent_at`, `invitation_error`; bảng `badge_config`; quyền `training.badge_config.{read,create,update,delete}.{diocese,deanery}` (gán cho SUPER_ADMIN/ADMIN_DIOCESE cấp giáo phận, ADMIN_DEANERY cấp giáo hạt). `TrainingCourseCreateRequest/UpdateRequest/Response` có thêm hai cờ; cờ null khi sửa = giữ nguyên.
- **Cấu hình thẻ** (`/api/badges/config`, `BadgeConfigController`): thư viện theo phạm vi sở hữu giống cấu hình chứng nhận (SYSTEM | DIOCESE | DEANERY, tên duy nhất không phân biệt hoa thường trong phạm vi, mỗi phạm vi một cấu hình chung, giáo hạt kế thừa của giáo phận/hệ thống chỉ được dùng). Mỗi cấu hình có khổ thẻ (mm, 20–400), hướng thẻ (rộng/cao được đổi theo hướng), khổ giấy A3/A4/A5/Letter/Legal + hướng giấy, lề (0–50) và khoảng cách thẻ (0–30), viền cắt, `hasBack`, ảnh nền + bố cục riêng cho mặt trước và mặt sau (JSONB, dùng lại `CertificateLayoutItem`: FIELD / TEXT / QR). Số cột × hàng do `BadgeSheetLayout` tính (căn giữa vùng in); thẻ không xếp vừa một thẻ nào bị từ chối khi lưu. Trường mail merge (`BadgeMergeField`): tên thánh, họ tên, tên thánh + họ tên, giáo xứ, giáo hạt, giáo phận, **tên đội**, tên khóa, mã khóa, năm tổ chức, ngày bắt đầu / kết thúc, thời gian tổ chức, địa điểm (không có người ký / mã chứng nhận / kết quả).
- **Xuất thẻ** (`/api/training/courses/{code}/badges`, quyền như xem hồ sơ tham dự đúng cấp của khóa): `GET /configs` (cấu hình chọn được cho khóa, cấu hình áp dụng mặc định = cấu hình chung gần khóa nhất theo chuỗi giáo hạt → giáo phận → hệ thống; không cần quyền cấu hình thẻ), `POST /export/jobs` + `GET /export/jobs/{jobId}` + `GET .../file` (job nền như xuất PDF chứng nhận). Body tùy chọn: `configId`, `participationIds`, `teamName`, `includeBack`, `flipEdge` (LONG | SHORT). Mỗi trang giấy xếp nhiều thẻ; thẻ có mặt sau thì sau mỗi trang mặt trước là một trang mặt sau xếp **đối xứng** (đổi cột khi lật cạnh dài của giấy đứng / cạnh ngắn của giấy ngang, ngược lại đổi hàng) để in hai mặt khớp nhau. Chữ tự thu nhỏ vừa bề ngang thẻ, phần tràn bị cắt theo khung thẻ, ảnh nền chỉ nhúng một lần. Tối đa 2000 thẻ mỗi lần; sắp xếp theo đội (số tự nhiên) rồi tên.
- **Mã QR điểm danh** (`AttendanceTokenService`): chuỗi `HTATT1.<mã hồ sơ>.<chữ ký>`, chữ ký HMAC-SHA256 (16 byte đầu) với `app.attendance.qr-secret` (mặc định dùng `app.security.jwt.access-secret`); không phải URL. QR là phần tử `QR` trong bố cục thẻ (dùng được cả hai mặt, kể cả khi khóa chưa bật điểm danh) và ảnh PNG nhúng + đính kèm trong email. Không lưu ảnh QR.
- **Điểm danh** (`/api/training/courses/{code}/attendance`): `GET /summary`, `GET /participants` (lọc `search`, `team`, `checkedIn`, `invitation` = SENT|PENDING|FAILED|NO_EMAIL, phân trang phía server; quyền đọc), `GET /participants/{id}` (chi tiết hồ sơ: điểm danh, email, người tạo / duyệt / điểm danh kèm họ tên; quyền đọc), `POST /lookup` (`{token}`, tra cứu để xác nhận, không ghi), `POST /scan` (`{token}`, ghi điểm danh), `PUT /participants/{id}` (`{checkedIn}`, thủ công). Lookup / scan / PUT dùng quyền RIÊNG `training.attendance.scan.{diocese|deanery}` (migration `V102`, gán cho ADMIN_DIOCESE / ADMIN_DEANERY), đúng đơn vị sở hữu khóa, không dùng quyền duyệt đăng ký. Chỉ khóa thường có bật điểm danh, còn hoạt động, **đã tới ngày bắt đầu**, chưa kết thúc và **chưa khóa chỉnh sửa điểm** (`score_locked`); lookup / scan / PUT (kể cả bỏ điểm danh) đều bị chặn bằng 409 với thông báo rõ, ví dụ `Khóa "…" chưa diễn ra (bắt đầu ngày dd/MM/yyyy) nên chưa thể điểm danh` hoặc `… đã khóa chỉnh sửa điểm nên không thể điểm danh` (nút quét ở header hiển thị đúng thông báo này); mỗi hồ sơ điểm danh một lần (quét lại trả `ALREADY_CHECKED_IN` kèm giờ đầu, không ghi đè); hồ sơ bị loại khỏi danh sách tham dự bị xóa trạng thái điểm danh và email đã gửi.
- **Email thông tin tham dự** (`/api/training/courses/{code}/invitations`, quyền như duyệt đăng ký): `POST /send` (`participationIds` tùy chọn, `includeQr`, `resend`) tạo job nền, `GET /jobs/{jobId}` trả kết quả `{total, sent, skippedAlreadySent, failed, includeQr, issues[]}`. `includeQr` chỉ hợp lệ khi khóa bật điểm danh (ngược lại 409). Hồ sơ không có email, email sai định dạng hoặc SMTP lỗi được ghi vào `issues` và `course_participation.invitation_error` (hiện ở danh sách tham dự) nhưng không chặn việc gửi cho các hồ sơ khác; hồ sơ đã gửi thành công được bỏ qua trừ khi `resend = true`. Nội dung: tên thánh, họ tên, giáo xứ, giáo hạt, giáo phận, tên khóa, thời gian tổ chức, địa điểm, đội, mã QR (nếu chọn); mọi giá trị động được escape HTML và loại ký tự điều khiển.
- **Tự gửi khi duyệt**: khóa bật `autoSendInvitation` thì mọi hồ sơ vừa vào danh sách tham dự (duyệt từng hồ sơ, duyệt hàng loạt, duyệt lại sau từ chối, hệ thống tự duyệt, import danh sách tham dự / chia đội) được gửi email sau khi giao dịch commit (`CourseInvitationAutoTrigger` → luồng `invitationMailExecutor`); kèm QR khi khóa bật điểm danh, không gửi lại hồ sơ đã gửi, lỗi từng hồ sơ ghi vào `invitation_error`. Hồ sơ đã duyệt từ trước khi bật cờ không tự gửi: FE hỏi gửi bù qua `POST /send` (`resend = false`).
- `globalExceptionHandler` trả 400 (không còn 500) cho tham số truy vấn sai kiểu hoặc thiếu (`MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`).
- Kiểm thử (`CourseAttendanceServiceTest` có thêm ca chưa tới ngày bắt đầu và khóa chỉnh sửa điểm; dữ liệu khóa trong `AttendeeBadgeAttendanceInvitationIntegrationTest` bắt đầu từ hôm qua): `BadgeSheetLayoutTest`, `BadgeConfigServiceTest`, `BadgePdfRendererTest`, `BadgeExportServiceTest`, `BadgeExportJobServiceTest`, `AttendanceTokenServiceTest`, `CourseAttendanceServiceTest`, `InvitationMailTemplateTest`, `CourseInvitationServiceTest`, `CourseInvitationAutoTriggerTest`, `CourseInvitationJobServiceTest`, `CourseParticipantSupportTest`, `TrainingCourseAttendanceFlagsTest` (đơn vị) và `AttendeeBadgeAttendanceInvitationIntegrationTest` (Postgres + Redis + RabbitMQ chạy bằng Testcontainers: migration, JSONB, quyền, xuất PDF, quét QR, gửi / ghi nhận lỗi / không gửi trùng, tự gửi khi duyệt và khi import). Ma trận route `endpoint-contract-matrix.psv` đã bổ sung các route mới và các route chia đội còn thiếu.

## Quyền sửa huynh trưởng cấp giáo phận / giáo hạt (nhánh `feature/attendance-window-and-table-display`, migration `V113`)

- `V113__seed_leader_update_diocese_deanery_permissions.sql` thêm `organization.leader.update.diocese` và `organization.leader.update.deanery` (idempotent, `ON CONFLICT DO UPDATE`). Migration **không gán** cho vai trò nào: super admin cấp qua màn phân quyền (ví dụ `ADMIN_DIOCESE`, `ADMIN_DEANERY`); chưa cấp thì không ai dùng được.
- `LeaderAccessEvaluator.canUpdate`: `ADMIN_DIOCESE` + quyền diocese với huynh trưởng thuộc giáo phận đang hoạt động của tài khoản; `ADMIN_DEANERY` + quyền deanery với huynh trưởng thuộc giáo hạt đang hoạt động; `ADMIN_PARISH` giữ nguyên. Ngoài phạm vi hoặc thiếu quyền bị từ chối.
- `LeaderScopeHelper.enforceUpdateParishScope`: khi đổi `parishId`, giáo xứ đích phải còn trong giáo phận (hoặc giáo hạt) của người sửa; quản trị giáo xứ vẫn không được chuyển ra ngoài giáo xứ của mình.
- Kiểm thử: `LeaderAccessEvaluatorTest` (đúng/sai phạm vi, thiếu quyền). Số migration đặt `V113` để không trùng `V110`–`V112` của nhánh `feature/cloud-media-storage`; hai nhánh phải gộp theo thứ tự có V110–V112 trước.

## Ảnh đại diện của tài khoản không gắn huynh trưởng và sửa lỗi log container (migration `V114`)

- `V114__account_avatar.sql`: cột `account.image_asset_id` (FK `media_asset`). Loại ảnh mới `MediaCategory.ACCOUNT_AVATAR` (thư mục `account-avatars`, cùng biến thể thumb/medium khổ dọc 4x6 như ảnh huynh trưởng); mọi tài khoản đã đăng nhập tải được (`MediaAccessEvaluator`). `PUT /api/auth/me/avatar` (`{imageAssetId}`) chỉ dành cho tài khoản **không** gắn huynh trưởng (gắn huynh trưởng → 400, dùng `PUT /api/auth/me/profile`); chỉ thay, không xóa; ảnh cũ được `release`. Ảnh nằm trong thư mục giáo phận của tài khoản, hoặc `system` nếu tài khoản không thuộc giáo phận nào. `MyProfileResponse` có thêm `imageUrl`, `imageThumbUrl`, `imageAssetId`. Job dọn ảnh mồ côi (`findUnreferencedActive`) tính cả `account.image_asset_id`.
- Sửa lỗi log: (1) `compose.override.yaml` đặt `SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS=*:missing,*:future` cho container dev để DB đã chạy migration của nhánh khác không làm app dừng khi đổi nhánh; (2) `CourseParticipationRepository.findByIdWithLeaderAndCourse` bỏ `setMaxResults(1)` (cảnh báo HHH90003004: phân trang trong bộ nhớ khi có join fetch tập hợp); (3) `securityConfig.authenticationProvider()` không còn là bean (cảnh báo "Global AuthenticationManager configured with an AuthenticationProvider bean"); (4) `CertificateService.findByLeader` trả `ArrayList` để cache Redis đọc lại được danh sách rỗng.

## Sao chép danh sách tham dự từ khóa tiên quyết (nhánh `feature/copy-prerequisite-attendees`)

- Module `modules/training/courseCopy` (`CourseCopyController`, `CourseCopyService`, `CourseCopyModels`). Quyền: người duyệt đăng ký đúng đơn vị sở hữu khóa (`canManageCourseParticipants`). Không có migration.
- Khóa nguồn: khóa thường thuộc loại khóa tiên quyết (`courseType.prerequisiteType`), còn bật, **đã kết thúc thủ công** (`finishedAt` khác null, vì kết quả chung `finalResult` chỉ chốt khi kết thúc), trong phạm vi khóa đích: khóa giáo hạt lấy khóa của chính giáo hạt đó hoặc của giáo phận chứa nó; khóa giáo phận lấy mọi khóa trong giáo phận. Khóa đích phải là khóa thường, còn bật, chưa kết thúc, đơn vị tổ chức đang hoạt động.
- `GET /api/training/courses/{code}/prerequisite-copy/sources`: `prerequisiteTypeName`, `available`, `sources[]` (mã, tên, năm, ngày, đơn vị, số hồ sơ tham dự, số đạt). FE chỉ hiện nút khi `available` và có ít nhất một nguồn.
- `GET .../candidates?sourceCourseCodes=..&sourceCourseCodes=..&includeFailed=`: hồ sơ trong danh sách tham dự (APPROVED, không CANCELLED) của các khóa nguồn đã chọn; `includeFailed=false` chỉ lấy `finalResult=PASSED`. Gộp theo huynh trưởng: ưu tiên hồ sơ đạt rồi khóa kết thúc gần nhất. Chỉ huynh trưởng thuộc phạm vi khóa đích. `blockedReason`: đã có hồ sơ trong khóa (mọi trạng thái, bỏ qua), huynh trưởng ngưng hoạt động, giáo xứ bị khóa. `eligible` chỉ để tham khảo (không chặn). Bộ lọc giáo hạt / giáo xứ / kết quả làm ở FE.
- `POST .../prerequisite-copy` (`sourceCourseCodes`, `includeFailed`, `leaderIds`): khóa hàng khóa, tính lại danh sách ứng viên phía server, tạo hồ sơ vào thẳng danh sách tham dự (APPROVED / ATTENDED, `eligible=true`, không kiểm tra điều kiện, `review` ghi khóa nguồn). Trả `{copied, skipped[]}`. Sau đó như duyệt đăng ký: `CourseInvitationAutoTrigger.afterApproved` (khóa bật `autoSendInvitation` thì hồ sơ vừa sao chép được tự gửi email, cả khi sao chép từ form tạo / sửa khóa hoặc nút ở tab Danh sách tham dự) và thông báo `REGISTRATION_APPROVED` qua `TrainingDecisionNotifier.notifyRegistrations(..., submitterById)`; người nhận là người đã gửi đăng ký hồ sơ gốc ở khóa tiên quyết, admin giáo xứ và tài khoản của huynh trưởng.
- Kiểm thử: `CourseCopyServiceTest`; ma trận route bổ sung 3 route.

## Khóa giáo phận dành cho giáo hạt (đợt), gửi liên giáo hạt và ghi chú hồ sơ (nhánh `feature/copy-prerequisite-attendees`, migration `V104`)

- **Khóa dành cho giáo hạt**: bảng `training_course_target_deanery` (`TrainingCourse.targetDeaneryIds`), chỉ khóa giáo phận (khóa giáo hạt gửi kèm → 400), giáo hạt phải đang hoạt động và thuộc giáo phận của khóa; rỗng = mở cho tất cả. `TrainingCourseCreate/UpdateRequest.targetDeaneryIds` (sửa: null = giữ nguyên, [] = bỏ hết), `TrainingCourseResponse.targetDeaneryIds/targetDeaneries[{id,name}]`. Mỗi đợt là một khóa riêng nên chia đội / điểm danh / chấm điểm / chứng nhận / kết thúc khóa không đổi.
- **Gửi ngoài đợt**: `TrainingWorkflowSupport.isOffTarget(course, leader)`. `register` nhận thêm `offTargetReasonsByLeaderId` (bắt buộc, ≤500 ký tự, cho từng hồ sơ có giáo hạt ngoài `targetDeaneryIds`; thiếu → 400 nêu tên huynh trưởng) và `notesByLeaderId` (tùy chọn, ≤1000). Lưu `course_participation.off_target_reason`. `registration-candidates` trả `offTarget` để FE cảnh báo trước. **Luôn duyệt thủ công**: `CourseParticipationService.requiresManualApproval` (ngoài đợt hoặc liên giáo hạt) bị `TrainingRegistrationAutoApprovalConsumer` và `previewParishSubmissionAutoApprovedCount` bỏ qua dù đủ điều kiện và khóa bật tự duyệt; duyệt hàng loạt vẫn dùng được.
- **Liên giáo hạt** (module `modules/training/crossDeanery`, prefix `/api/training/cross-deanery`): giáo xứ ở giáo hạt B (hoặc giáo hạt B gửi thẳng) gửi huynh trưởng sang khóa giáo hạt của giáo hạt A cùng giáo phận. Giáo xứ gửi → `cross_deanery_request` PENDING (+ `cross_deanery_request_item`: `reason` bắt buộc cho từng hồ sơ, `exceptionReason` nếu chưa đủ điều kiện, `note`), **chưa tạo hồ sơ đăng ký**; người có quyền mới `training.participation.cross_approve.deanery` (gán ADMIN_DEANERY) của giáo hạt B đồng ý (cả yêu cầu hoặc từng hồ sơ; từ chối / đồng ý một phần phải có ghi chú) thì mới sinh `course_participation` PENDING bình thường (`crossDeaneryReason`, `homeDeaneryId`, `homeDeaneryApprovedBy/At`, `createdBy` = người gửi gốc) và báo giáo hạt A qua `CourseParticipationService.announceSubmission`; giáo hạt A duyệt như hồ sơ thường. Giáo hạt B gửi thẳng = coi như đã đồng ý. Không thêm trạng thái mới vào `WorkflowApprovalStatus` / `ParticipationStatus`. Hết hạn đăng ký mà chưa xử lý → trạng thái hiển thị EXPIRED (suy ra khi đọc, không đồng ý được). Endpoint: `GET /courses` (khóa đang mở của giáo hạt khác), `GET /courses/{code}/candidates`, `POST /courses/{code}/requests`, `GET /requests?status=`, `GET /requests/{id}`, `POST /requests/{id}/decision`, `DELETE /requests/{id}` (giáo xứ thu hồi khi còn chờ). Thông báo: `training.cross_deanery.requested|approved|rejected`.
- **Ghi chú hồ sơ đăng ký**: `course_participation.registration_note` (≤1000) ở mọi khóa; `PATCH /api/training/participations/{id}/registration-note` (người duyệt đúng đơn vị sở hữu khóa, hoặc người gửi khi hồ sơ còn chờ duyệt; khóa vô hiệu hóa / đã kết thúc chỉ xem). Import Excel danh sách tham dự (khóa thường, chia đội, khóa quá khứ) có cột tùy chọn `Ghi chú` (bỏ trống được; file cũ không có cột vẫn import); xuất Excel danh sách tham dự / chia đội có cột Ghi chú. Sao chép từ khóa tiên quyết không chép ghi chú cũ.
- Kiểm thử: `CrossDeaneryServiceTest`, `TrainingWorkflowSupportOffTargetTest`; ma trận route bổ sung 8 route (số controller 35, route 239).

## Nhật ký hệ thống (audit log)

- Mỗi yêu cầu `/api/**` ghi một dòng `system_log` (`ApiAuditLoggingFilter`). Để truy vết: `resource` (loại đối tượng, luôn có cho mọi đường dẫn đã biết, đường dẫn lạ lấy đoạn tài nguyên đầu), `resource_id` (chỉ mã thật: UUID, số hoặc mã có chữ số; các đoạn như `me`, `options` không còn bị ghi nhầm thành mã), `resource_name` (tên dễ đọc, tra trước khi xử lý để sửa/xóa vẫn giữ tên lúc bị tác động; huynh trưởng luôn kèm tên thánh), `operation` (LIST, VIEW, CREATE, UPDATE, DELETE hoặc hành động như APPROVE, TOGGLE_STATUS), `query_string` (bộ lọc yêu cầu, bỏ `password`, `token`, `otp`, `secret`, `code`), `duration_ms`.
- Tra `resource_name` qua `AuditResourceNamer` (danh sách trắng, chỉ tra khóa chính, lỗi không ảnh hưởng nghiệp vụ). Migration `V094`/`V095` điền ngược `resource`, `operation` cho dòng cũ.

## Session Rules

- Tất cả message trả về cho người dùng, message validation, message success hoặc failure, và message cảnh báo liên quan tới thao tác nghiệp vụ phải dùng tiếng Việt.
- Mọi session sau khi thêm hoặc sửa message phải lưu file bằng UTF-8 và tự kiểm tra lại để tránh mojibake.

## Overview

This project manages organization data, leaders, training, certificates, and system access for HT Management.

The backend currently relies on:

- Spring Boot 3.5 on Java 21 (Gradle wrapper)
- Spring Data JPA
- PostgreSQL 16
- Flyway
- Spring Security
- Redis / Spring Session
- RabbitMQ (Spring AMQP) for async messaging (notifications, registration auto-approval)
- WebSocket (STOMP) for realtime notifications
- JWT for stateless authentication
- Cloudinary for media storage (see `docs/media-storage.md`)

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

- the backend identifies the login source by the optional `deviceId` in the login body (FE generates it once and keeps it in localStorage); when absent it falls back to `HttpServletRequest.getRemoteAddr()`. Two devices behind the same NAT/proxy share one IP, so keying by IP alone made the 2nd device kick out the 1st; `deviceId` fixes that
- each account may keep at most 2 active sessions (source key = `device:<deviceId>` or the IP)
- each source key may keep only 1 active session for the same account
- a new login from the same source key revokes both the previous access token and refresh token of that source
- a login from a third source revokes the oldest active session for that account
- `POST /api/auth/refresh` still reads the refresh token from the HttpOnly cookie
- successful refresh rotates both access token and refresh token for the existing `sessionId` through an atomic Redis compare-and-set/Lua operation; only one concurrent use of the same refresh token can succeed
- normal access-token activity updates only the session ordering score, so it cannot overwrite a concurrent refresh rotation with stale session data
- `app.security.cookie.expose-access-token-in-body` (env `AUTH_EXPOSE_ACCESS_TOKEN_IN_BODY`): default `true` in dev, `false` in the prod profile (test/production). When `false`, `/auth/login` and `/auth/refresh` omit `accessToken` from the JSON body; it only travels in the HttpOnly access cookie (refresh token is never in the body). The JWT filter already accepts the cookie, and the WebSocket handshake copies the access cookie so STOMP `CONNECT` works without an `Authorization` header. FE stores a `COOKIE_SESSION` marker instead of a token and sends no Bearer header. HttpOnly cookies are still visible in DevTools > Application (browser limitation) but not readable by JS/XSS
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
- `POST /api/system/account-permissions`
- `GET /api/system/account-permissions`
- `GET /api/system/account-permissions/{id}`
- `PUT /api/system/account-permissions/{id}`
- `DELETE /api/system/account-permissions/{id}`

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
- direct account overrides and role-permission rows no longer have an `effect` (`ALLOW`/`DENY`) or a toggle: every row grants the permission; see "Permission Assignment Lifecycle" below
- API access is enforced directly by controller `@PreAuthorize` rules and each module's scoped `AccessEvaluator`; the legacy `api_permission_policy` registry is removed

### Permission Assignment Lifecycle (phân quyền vai trò / tài khoản)

Áp dụng cho `role_permission` (phân quyền vai trò) và `account_permission_override` (phân quyền tài khoản). Hai màn hình này chỉ có **phân quyền** và **thu hồi quyền**; không có bật/tắt (toggle) và không còn trường `effect` (migration V091 xóa cột, xóa các bản ghi DENY cũ).

**Cấp quyền (phân quyền)**

- `SUPER_ADMIN` cấp được cho mọi vai trò / tài khoản trong phạm vi quản lý của mình.
- Phân quyền / thu hồi quyền cho vai trò theo cấp: cấp giáo phận (authority `*.diocese`) được phân quyền và thu hồi cho vai trò hệ thống `ADMIN_DEANERY` và `ADMIN_PARISH`; cấp giáo hạt (`*.deanery`) cho `ADMIN_PARISH`; cấp giáo xứ không phân quyền cho vai trò hệ thống nào. Vai trò tùy biến chỉ quản lý được khi do chính cấp mình tạo (cùng đơn vị), nên vai trò do admin giáo hạt / giáo xứ tạo thì cấp giáo phận không phân quyền được. Mọi trường hợp vai trò đích không được cao hơn vai trò cao nhất của người thao tác.
- Dropdown vai trò ở form phân quyền / thu hồi quyền vai trò dùng `GET /api/system/role-permissions/manageable-roles?action=assign|revoke`, chỉ trả vai trò mà người dùng được phép thao tác (đã loại vai trò cấp cao hơn).
- Admin cấp cao hơn chỉ cấp được cho role / tài khoản của admin cấp thấp hơn, và chỉ trong bộ quyền mình đang có: vai trò đích phải thấp hơn vai trò cao nhất của người cấp, phạm vi quyền không cao hơn phạm vi người cấp được phép, và người cấp phải đang sở hữu đúng quyền đó.
- Bản ghi lưu `assignedBy` / `createdBy` (người cấp) và `assignedAt`.
- `expiresAt` tùy chọn: để trống thì quyền có hiệu lực vĩnh viễn.

**Hiệu lực** (trường `validity` trả về ở hai danh sách, cột "Tình trạng quyền")

| `validity` | Điều kiện | Hồi phục được |
| --- | --- | --- |
| `ACTIVE` | chưa thu hồi, chưa quá hạn, quyền gốc đang bật | - |
| `EXPIRED` | quá `expiresAt` (job đánh dấu `revoked_at`, lý do `EXPIRED`) | Không |
| `REVOKED` | thu hồi thủ công (`revoked_at`, lý do `MANUAL`) | Không |
| `PERMISSION_DISABLED` | quyền gốc (bảng `permission`) bị toggle disable | Có, bật lại quyền gốc là hiệu lực lại |

- Quyền gốc bị toggle disable cũng đồng nghĩa phân quyền đó không còn hiệu lực. Dòng phân quyền không bị đụng tới nên bật lại quyền gốc thì tự có hiệu lực.
- `EXPIRED` và `REVOKED` là vĩnh viễn: bản ghi được giữ lại để hiển thị lịch sử, không sửa, không bật lại. Muốn cấp lại phải phân quyền mới; bản ghi cũ bị thay bằng bản ghi mới (ràng buộc duy nhất theo vai trò/tài khoản + quyền).
- Job `PermissionExpiryRevocationScheduler` chạy mỗi 60 giây để đánh dấu `EXPIRED`. Tính quyền hiệu lực (view `v_account_effective_permission`) luôn kiểm tra `expires_at` và `revoked_at IS NULL`, nên không phụ thuộc độ trễ của job.

**Thu hồi quyền** (`POST /api/system/role-permissions/revoke`, `POST /api/system/account-permissions/revoke`; nút thu hồi ở từng dòng cũng gọi API này)

- Người thu hồi phải có quyền thu hồi tương ứng và có quyền quản lý đích như khi cấp.
- Admin không thu hồi quyền của chính vai trò mình đang giữ, và không thu hồi quyền của chính tài khoản mình.
- Role nào cấp quyền thì chỉ role đó được gỡ, mở rộng theo thứ tự role (`role.displayOrder`, số nhỏ hơn là cấp cao hơn): người gỡ phải có role cùng cấp hoặc cao hơn role cao nhất đang hoạt động của người cấp (`assignedBy`, hoặc `createdBy` khi không có). Ví dụ role A do `ADMIN_DIOCESE` tạo và có quyền phân quyền cho `ADMIN_DEANERY` / `ADMIN_PARISH`: các quyền do tài khoản mang role A cấp được `ADMIN_DIOCESE` và các role cùng cấp với A gỡ, role thấp hơn A không gỡ được; tương tự cho các cấp role khác.
- Quyền do `SUPER_ADMIN` cấp có thứ tự cao nhất nên chỉ `SUPER_ADMIN` gỡ được. Quyền do hệ thống (`system`) cấp, hoặc người cấp không còn role hoạt động, cũng chỉ `SUPER_ADMIN` gỡ được.
- Mỗi dòng danh sách trả `revocable` (tính theo người đang đăng nhập); FE chỉ hiện nút thu hồi khi `revocable = true`. Danh sách "quyền có thể thu hồi" cho thu hồi hàng loạt lọc theo cùng điều kiện.
- Thu hồi dòng đã `EXPIRED` / `REVOKED` trả `409`.

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
- diocese-scoped admins can create a parish in a deanery of their own diocese when granted `organization.parish.create.diocese` (`ParishScopeHelper.enforceCreateScope` checks the deanery's diocese); they still cannot update or delete parishes (toggle follows `toggle.diocese`)
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

## Hồ sơ cá nhân và đổi mật khẩu bằng OTP (nhánh `feature/account-menu-change-password`)

- `V089__account_contact_email.sql`: thêm cột `account.email` (nullable). Tạo tài khoản SUPER_ADMIN / ADMIN_DIOCESE không gắn Huynh Trưởng thì lưu `credentialEmail` vào cột này. Email nhận OTP: ưu tiên email Huynh Trưởng, sau đó `account.email` (`AccountEmailResolver`). Tài khoản cũ không gắn Huynh Trưởng chưa có email thì không đổi được mật khẩu bằng OTP cho tới khi cập nhật email.
- `GET /api/auth/me/profile`: hồ sơ của chính mình, không cần quyền đọc Huynh Trưởng: `username`, `roles` (`code`, `name` lấy từ bảng role, `primary`; vai trò chính đứng đầu), `email`, `hasLeader`, `leader`, `rankHistory`, `certificates`.
- `GET /api/auth/me/certificates/{code}` và `/preview`: chi tiết và bản xem trước chứng nhận, chỉ khi chứng nhận thuộc Huynh Trưởng của tài khoản (ngược lại 404).
- Đổi mật khẩu chủ động 3 bước (`PasswordChangeService`, dữ liệu tạm trên Redis qua `PasswordOtpStore`, chỉ lưu hash):
  - `POST /api/auth/password/otp` `{ oldPassword }`: kiểm tra mật khẩu hiện tại (sai 5 lần / 15 phút thì khóa tạm, 429), sinh OTP 6 số bằng `SecureRandom`, hạn 5 phút, gửi lại sau 60 giây, gửi email. Trả `{ email, expiresInSeconds, resendAfterSeconds }`.
  - `POST /api/auth/password/otp/verify` `{ otp }`: tối đa 5 lần sai thì hủy OTP; đúng thì trả `changeToken` dùng một lần, hạn 10 phút.
  - `POST /api/auth/password/change` `{ changeToken, newPassword, confirmPassword }`: mật khẩu 8–72 ký tự, khớp xác nhận, khác mật khẩu cũ; giữ phiên hiện tại, thu hồi các phiên khác (`RedisTokenSessionService.revokeOtherSessions`).
  - `/api/auth/password/**` dùng hạng giới hạn tốc độ `auth`.
- Luồng bắt buộc đổi mật khẩu tạm lần đầu (`POST /api/auth/change-password`) giữ nguyên, không OTP.
- Test: `PasswordChangeServiceTest`.

### Gửi email: transaction và rollback

- Chỉ có 2 luồng gửi email: tạo tài khoản (mật khẩu tạm) và OTP đổi mật khẩu.
- Tạo tài khoản (`AccountManagementService.create`, `@Transactional(rollbackFor = Exception.class)`): `saveAndFlush` tài khoản, ghi vai trò rồi `flush`, dựng response, gửi email là bước cuối. Lỗi ở bất kỳ bước nào (kể cả SMTP) thì rollback toàn bộ; gửi email lỗi trả 503 "Tài khoản chưa được tạo" và ghi log nguyên nhân (`AccountCredentialMailService`).
- OTP (Redis, không có transaction DB) dùng bù trừ:
  - gửi OTP: chụp OTP cũ trước khi ghi mã mới; email lỗi thì khôi phục OTP cũ, bỏ thời gian chờ gửi lại, giữ phiên đổi mật khẩu cũ, trả 503.
  - xác thực OTP: lưu change token trước rồi mới hủy OTP; lỗi thì gỡ token, OTP vẫn dùng lại được.
  - đổi mật khẩu (`@Transactional(rollbackFor = Exception.class)`): `saveAndFlush`, hủy change token, thu hồi phiên khác; lỗi thì DB rollback và trả lại change token để thử lại không cần OTP mới. Phiên khác đã bị thu hồi trước khi lỗi thì không khôi phục (an toàn hơn giữ lại).
- Test: `AccountCreateMailRollbackIntegrationTest` (DB thật, email lỗi thì không còn tài khoản/vai trò), thêm ca rollback trong `AccountManagementServiceTest`, `AccountCredentialMailServiceTest`, `PasswordChangeServiceTest`.

## Audit 4 trường người tạo/cập nhật/thời gian và tên người thực hiện (nhánh `feature/audit-fields-filters-drawer-vi`)

- `BaseAuditEntityListener.beforeUpdate`: có người dùng đăng nhập thì luôn ghi `updatedBy` = người đó (không còn giữ giá trị cũ khi service quên set); không có người dùng (consumer, scheduler) giữ giá trị service đã set, trống thì `SYSTEM`.
- `AuditActorNameResolver`: giải mã actor thành tên (leaderId, `super-admin`, tác nhân hệ thống, username qua huynh trưởng liên kết), có cache 60s.
- Response bổ sung `createdAt/createdByName/updatedAt/updatedByName` (và `assignedByName`, `revokedByName`, `approvedByName`, `roleName`, `leaderFullName` tùy loại) cho tài khoản, vai trò, quyền, vai trò tài khoản, quyền vai trò/tài khoản, thành phần điểm, huynh trưởng, khóa, cấp độ, chứng nhận, hồ sơ tham dự, lịch sử cấp bậc.
- `TrainingCourseService.refreshCourseStatuses` (scheduler) nay ghi `updatedBy`.
- Test: `BaseAuditEntityListenerTest`, `AuditActorNameResolverTest`.

## Xuất / import Excel và khóa huấn luyện quá khứ (nhánh `feature/excel-export-import`)

### Xuất Excel theo bộ lọc
- `GET /api/{dioceses|deaneries|parishes|leaders|certificates|training/courses|training/participations|training/participants}/export` (cùng `@PreAuthorize` với danh sách, cùng tham số `search/searchFields/filters/status/sortBy/sortDirection`; `page/size` bị bỏ qua). Không lọc thì xuất tất cả trong phạm vi quyền.
- `ExcelExportSupport.collectAll` gọi lại đúng hàm `findAll` của danh sách (nên giữ nguyên phạm vi quyền và bộ lọc nền của từng tab) theo trang 100 dòng; tối đa 50.000 dòng/lần, vượt thì trả 400 yêu cầu lọc bớt. Tên file bỏ dấu + ngày xuất (`ExcelResponses`).
- Cột nằm ở `common/excel/OrganizationExcelExports` và `TrainingExcelExports`; enum xuất bằng nhãn tiếng Việt (`ExcelLabels`).

### Import Excel
- Hạ tầng `common/excel`: `ExcelWorkbookReader` (đọc `.xlsx`, nhận cột theo tên tiêu đề không phân biệt hoa thường/dấu/dấu `*`, bỏ dòng trống, tối đa 5.000 dòng), `ImportErrors` → `ImportValidationException` (HTTP 400, `data` = danh sách `{sheet,row,column,message}`).
- **Có bất kỳ dòng lỗi nào thì không lưu gì** (kiểm tra toàn bộ file trước khi ghi, trong một transaction).
- `POST /api/leaders/import` (quyền tạo huynh trưởng, `LeaderImportService`): cột Tên thánh, Họ và tên, Ngày sinh, Giới tính, Email, Số điện thoại, Cấp độ (tên hoặc mã), Giáo hạt, Giáo xứ. Trùng email/SĐT (với DB hoặc giữa các dòng) là lỗi dòng; giáo xứ phải nằm trong phạm vi tài khoản và đang hoạt động (dùng lại `LeaderScopeHelper.enforceCreateScope`).
- `POST /api/training/courses/{courseCode}/participants/import` (quyền `training.course.create_past.diocese`, `PastCourseParticipantImportService`): chỉ cho **khóa quá khứ**. Huynh trưởng tìm theo **email hoặc SĐT** (không dùng UUID); cột Email, Số điện thoại, Tổng điểm, Kết quả (Đạt/Không đạt), Nhận xét. Học viên Đạt của khóa có cấp độ đạt được → chứng nhận đề xuất (PENDING, mã `GP{viết tắt}-QK-{năm}-{số}`); không đạt → `finalResult = FAILED` ngay.
- `DELETE /api/training/courses/{courseCode}/participants/{participationId}`: xóa học viên nhập nhầm khỏi khóa quá khứ (kèm chứng nhận chưa duyệt); học viên đã có chứng nhận được duyệt thì phải thu hồi trước.
- **SĐT huynh trưởng là duy nhất** (migration `V096`, kiểm tra khi tạo/sửa).

### Khóa huấn luyện quá khứ
- Quyền riêng `training.course.create_past.diocese` (migration `V097`), **không gán cho vai trò nào**: super-admin cấp trực tiếp cho từng tài khoản qua màn "Phân quyền tài khoản" (mã dùng `create_past` để không trùng tiền tố `training.course.create.` của nút "Tạo khóa").
- `POST /api/training/courses/past`, `PUT /api/training/courses/past/{courseCode}` (`PastTrainingCourseService`): chỉ cần tên khóa và năm tổ chức (≤ năm hiện tại); ngày bắt đầu/kết thúc, địa điểm, điểm đạt (mặc định 0), cấp độ đạt được (theo danh mục cấp độ của giáo phận) là tuỳ chọn. Không có loại khóa và thời gian đăng ký (`course_type_code` nay có thể NULL, ràng buộc `chk_training_course_type_required`); khóa thuộc giáo phận của tài khoản, đã kết thúc ngay (`finishedAt`, `scoreLocked`, trạng thái ENDED) và không phát thông báo. Đã có danh sách tham dự thì không đổi năm tổ chức, cấp độ, điểm đạt. Sửa khóa quá khứ bằng endpoint thường bị từ chối.
- Cấp độ đạt được lấy từ `TrainingCourse.resultingLevel` (`TrainingWorkflowSupport.certificateLevelFor(course)` ưu tiên giá trị này với khóa quá khứ, khóa thường vẫn theo loại khóa).
- **Duyệt chứng nhận khóa quá khứ** (tab "Duyệt chứng nhận", quyền duyệt hiện có): `CertificateApproveRequest.approvalDate` (và trong duyệt hàng loạt) **bắt buộc**, không sau hôm nay và không trước năm tổ chức; dùng làm `issueDate`/`approvedAt`, không lấy ngày hiện tại. Khóa quá khứ vẫn duyệt/từ chối được dù đã kết thúc; kết quả Đạt từ Excel là căn cứ (không cần điểm).
- **Lịch sử cấp độ**: khi chứng nhận được duyệt, `RankPromotionService.recordPastCourse` luôn thêm một dòng `rank_history` (ngày = ngày duyệt); cấp hiện tại của huynh trưởng chỉ đổi khi cấp mới cao hơn theo thứ tự cấp độ đã cấu hình (`displayOrder`), ví dụ đã HT cấp I mà duyệt khóa Dự trưởng thì chỉ thêm lịch sử. Thu hồi chứng nhận hạ cấp như khóa thường, hoặc chỉ đánh dấu dòng lịch sử đã thu hồi nếu nó không làm đổi cấp.
- Giới hạn đã biết: khóa quá khứ không có loại khóa nên không tính vào điều kiện khóa tiên quyết khi đăng ký khóa thường (cấp độ vẫn được ghi qua lịch sử cấp độ).
- Test: `ExcelRoundTripTest`, `ExcelImportExportIntegrationTest` (Postgres/Redis/RabbitMQ chạy riêng bằng Testcontainers), `EndpointContractMatrixTest`.

### Tác vụ Excel chạy nền, Idempotency-Key và chống race (migration `V098`)
- **Xuất / nhập Excel là tác vụ nền** (không khóa giao diện): `POST …/export` (các danh sách) và `POST /api/leaders/import`, `POST /api/training/courses/{code}/participants/import` trả `202` + tác vụ (`ExcelJobResponse`). Theo dõi ở `GET /api/excel-jobs[/{id}]`, tải kết quả `GET /api/excel-jobs/{id}/file`, hủy `POST /api/excel-jobs/{id}/cancel` (chỉ chủ tác vụ thấy được). Bảng `excel_job` giữ 3 ngày rồi `ExcelJobService.purgeExpired` xóa dòng và file (thư mục `app.excel-jobs.dir`, mặc định `${java.io.tmpdir}/ht-excel-jobs`).
- **Bắt buộc header `Idempotency-Key`** khi tạo tác vụ: `(người tạo, loại, key)` là duy nhất; gửi lại cùng key trả lại đúng tác vụ cũ (`200` + `Idempotent-Replay: true`), cùng key khác nội dung → 409. Mỗi người chỉ có một tác vụ đang chờ/chạy cho cùng `(loại, đích)` (partial unique index `uk_excel_job_active`). Import lại cùng file chỉ **cảnh báo** (`warning`), dữ liệu trùng vẫn bị chặn bởi kiểm tra và ràng buộc duy nhất.
- Worker (`ExcelJobWorker`): nhận job bằng `UPDATE … FOR UPDATE SKIP LOCKED`, chạy với ảnh chụp quyền người tạo (`JobPrincipal`), lease 300s + heartbeat, hết lease thì đưa lại hàng đợi (tối đa 3 lượt). **Nhập chạy trong một transaction và đánh dấu SUCCEEDED trong chính transaction đó** nên chạy lại sau gián đoạn là an toàn; xung đột dữ liệu lúc ghi (ràng buộc duy nhất) → rollback, chạy lại một lần để báo đúng dòng trùng. Cache xóa sau commit. `CourseScoreVisibility` tính người xem cho cả luồng nền nên xuất không lộ điểm.
- **Duyệt / từ chối / thu hồi / cấp lại chứng nhận và duyệt hàng loạt**: nhận `Idempotency-Key` (không bắt buộc) qua `CertificateIdempotentActions`; bản ghi `idempotency_record` nằm cùng transaction với thao tác nên thao tác lỗi không giữ key, gửi lại sau khi thành công nhận lại kết quả cũ. Nhiều người cùng thao tác một chứng nhận: `EntityLockService` khóa hàng chứng nhận rồi huynh trưởng (`SELECT … FOR UPDATE` + nạp lại) trước khi kiểm tra trạng thái và đổi cấp độ nên chỉ một người thành công, không ghi lịch sử cấp độ hai lần, không mất cập nhật cấp độ; duyệt hàng loạt xử lý theo thứ tự mã để không deadlock. Khóa khóa huấn luyện khi import/xóa học viên/sửa khóa quá khứ.
- Email huynh trưởng duy nhất không phân biệt hoa thường (`uk_leader_email_lower`).
- Test: `ExcelImportExportIntegrationTest` (Testcontainers) gồm các kịch bản đồng thời: 8 request cùng key, 8 người cùng duyệt một chứng nhận, hai chứng nhận của một huynh trưởng duyệt cùng lúc, thu hồi/cấp lại đồng thời.

### Admin giáo hạt tạo khóa quá khứ và tạo huynh trưởng ngay trong file danh sách tham dự (migration `V099`)
- Quyền mới `training.course.create_past.deanery` (không gán cho vai trò nào). Chuỗi cấp quyền: super-admin cấp `…create_past.diocese` cho admin giáo phận (super-admin không xem được tài khoản admin giáo hạt) → admin giáo phận **tự cấp lại** `…create_past.deanery` cho admin giáo hạt thuộc giáo phận mình qua màn "Phân quyền tài khoản" (`PermissionDelegation`: người giữ quyền cấp giáo phận được trao lại đúng quyền cùng loại cấp giáo hạt; không áp dụng cho quyền khác, vẫn phải có quyền quản lý phân quyền tài khoản và tài khoản đích thuộc phạm vi của mình).
- Khóa quá khứ của admin giáo hạt gắn với giáo hạt (không gắn trực tiếp giáo phận) như khóa giáo hạt thường; mã chứng nhận `GH{viết tắt giáo hạt}-QK-…`; cấp độ lấy theo danh mục của giáo phận chứa giáo hạt. `TrainingWorkflowSupport.isDeaneryManaged(course)` coi mọi khóa gắn giáo hạt là khóa giáo hạt (không cần loại khóa). Mỗi tài khoản chỉ thao tác khóa quá khứ của đúng đơn vị mình (giáo phận hoặc giáo hạt).
- **Import danh sách tham dự khóa quá khứ** (`PastCourseParticipantImportService`): huynh trưởng tìm theo email hoặc SĐT; **đã có hồ sơ thì chỉ thêm vào khóa** (không tạo mới, không sửa hồ sơ, các cột thông tin huynh trưởng bị bỏ qua); **chưa có thì tạo mới** từ các cột Tên thánh, Họ và tên, Ngày sinh, Giới tính, Email, Giáo xứ (tuỳ chọn Số điện thoại, Cấp độ, Giáo hạt) dùng chung quy tắc kiểm tra với import hồ sơ huynh trưởng (`LeaderImportService.parseNewLeader/buildLeader`). Giáo xứ của huynh trưởng mới phải thuộc giáo phận / giáo hạt của khóa và đang hoạt động; huynh trưởng có sẵn cũng phải thuộc đơn vị của khóa. Không cần quyền tạo huynh trưởng riêng: quyền tạo khóa quá khứ cho phép tạo huynh trưởng trong phạm vi đơn vị của khóa. Hồ sơ mới và danh sách tham dự cùng một transaction nên lỗi ở bất kỳ dòng nào thì không tạo gì. Import hồ sơ huynh trưởng (`POST /api/leaders/import`) giữ nguyên logic cũ.

### File Excel mẫu và thông báo thu hồi quyền / kết quả hồ sơ
- **File mẫu tạo từ hệ thống**: `GET /api/leaders/import-template` (quyền tạo huynh trưởng) và `GET /api/training/courses/past/import-template` (quyền tạo khóa quá khứ) trả `.xlsx` do `ImportTemplates` dựng bằng đúng hằng số tên cột của bộ đọc import (nên mẫu luôn khớp). Sheet đầu là sheet dữ liệu (tiêu đề đỏ = bắt buộc, vàng = tuỳ chọn, cột số điện thoại để Text, ngày dd/MM/yyyy), có dòng ví dụ, danh sách chọn Giới tính / Kết quả / Cấp độ (không chặn nhập tay), sheet "Danh mục" (cấp độ của giáo phận người tải) và "Hướng dẫn". Giao diện: hộp thoại Import Excel có nút "Tải file mẫu" cạnh phần tải file lên.
- **Thu hồi quyền có thông báo như phân quyền**: `PermissionGrantNotifier.notifyRoleRevoked / notifyAccountRevoked` (sự kiện `ROLE_PERMISSION_REVOKED`, `ACCOUNT_PERMISSION_REVOKED`, payload `kind = PERMISSION_REVOKE`). Một request thu hồi gửi đúng một thông báo kèm danh sách quyền bị thu hồi: vai trò → mọi tài khoản đang giữ vai trò, tài khoản → chỉ tài khoản đó. Bấm vào thông báo mở bảng chi tiết và nút "Xem quyền hiện có".
- **Thông báo kết quả hồ sơ / chứng nhận** (`WorkflowDecisionNotifier`): ngoài người gửi đăng ký và admin giáo xứ, **chính huynh trưởng có tài khoản đang hoạt động cũng nhận** thông báo khi hồ sơ đăng ký của mình được duyệt / từ chối / bị xóa khỏi danh sách và khi chứng nhận được duyệt / từ chối / thu hồi / cấp lại. **Thông báo lẻ (dưới 3 hồ sơ) cũng có bảng chi tiết** (khóa, huynh trưởng, giáo xứ hoặc mã chứng nhận, lý do, người thực hiện) như thông báo gộp, kèm nút mở khóa / chứng nhận liên quan.

### Bổ sung: quy tắc cột bắt buộc, thông báo hết hạn quyền, lọc cấp độ, khóa đăng ký
- **Import hồ sơ huynh trưởng**: mọi cột bắt buộc trừ **Cấp độ** (trống = Chưa có cấp). Thêm Số điện thoại và Giáo hạt vào cột bắt buộc; **Giáo hạt và Giáo xứ phải đã có trong hệ thống** (không tự tạo), giáo xứ phải thuộc đúng giáo hạt đã nhập, nếu không dòng đó báo lỗi ("Giáo hạt '…' chưa có trong hệ thống", "Giáo xứ '…' chưa có trong hệ thống", "…không thuộc giáo hạt …"). Đối chiếu qua `LeaderImportService.ParishIndex`.
- **Import danh sách tham dự khóa quá khứ**: mọi dòng phải nhập đủ Email, Số điện thoại, Tổng điểm, Kết quả, Tên thánh, Họ và tên, Ngày sinh, Giới tính, Giáo hạt, Giáo xứ (chỉ Cấp độ và Nhận xét được trống), kể cả huynh trưởng đã có hồ sơ (thông tin chỉ để kiểm tra, hồ sơ có sẵn không bị sửa; chưa có thì tạo mới). Giáo xứ ngoài phạm vi của khóa báo "không thuộc giáo hạt / giáo phận của khóa". Các file mẫu (`ImportTemplates`) đánh dấu đỏ đúng các cột bắt buộc; mũi tên dropdown hiện nhờ `setSuppressDropDownArrow(true)` (cờ của POI ngược nghĩa).
- **Thu hồi quyền tự động khi hết hạn cũng gửi thông báo**: `PermissionExpiryRevocationService` chọn (khóa ghi) các gán quyền vai trò / ngoại lệ tài khoản sắp hết hạn trước khi vô hiệu hóa rồi gọi `PermissionGrantNotifier.notifyRoleExpired / notifyAccountExpired` (sự kiện `ROLE_PERMISSION_EXPIRED`, `ACCOUNT_PERMISSION_EXPIRED`).
- **Lọc cấp huynh trưởng** trong "Lập danh sách" và "Thêm ngoại lệ" của gửi đăng ký: tham số `leaderLevel` của `GET …/registration-candidates` (`CourseParticipationService.matchesLevelFilter`, huynh trưởng chưa có cấp tính là `NONE`).
- **Gửi đăng ký trong tab "Đăng ký tham dự" của chi tiết khóa**: ô chọn khóa bị khóa (luôn là khóa đang xem) và hiện tên khóa thay vì mã (`readOnlyOnCreate`, `currentLabel`).

### Phân quyền vai trò admin, tìm tài khoản và lọc quyền
- **Vai trò hệ thống ADMIN_DIOCESE / ADMIN_DEANERY / ADMIN_PARISH chỉ SUPER_ADMIN được phân quyền hoặc thu hồi** (`SystemAdminAuthorizationHelper.requireRolePermissionRoleAccess`); admin giáo phận / giáo hạt không còn phân quyền cho vai trò admin cấp dưới. Cấp trên vẫn phân quyền được cho **từng tài khoản** mang các vai trò này (phân quyền tài khoản, không đổi).
- `GET /api/system/accounts/permission-targets?search=` tìm theo tên đăng nhập, tên thánh / họ tên huynh trưởng, vai trò, giáo xứ, giáo hạt; `AccountResponse` có thêm `leaderHolyName`.
- FE: ô chọn tài khoản rộng hết nhóm (`FormFieldSpec.wide`), mỗi tài khoản hiện vai trò, huynh trưởng (tên thánh + họ tên), giáo xứ, giáo hạt; danh sách tick quyền (phân quyền / thu hồi, vai trò / tài khoản) có bộ lọc phân hệ, đối tượng, thao tác, phạm vi và tìm không dấu.


## Ghi nhớ đăng nhập, quên mật khẩu, admin đặt lại mật khẩu (nhánh `feature/remember-me-forgot-reset-password`)

### Ghi nhớ đăng nhập
- `POST /api/auth/login` nhận thêm `rememberMe` (mặc định `false`). Phản hồi có `rememberMe`.
- Phiên Redis (`TokenSessionState`) lưu `rememberMe` và `absoluteExpiresAt`. Hạn phiên = `min(bây giờ + idle, absoluteExpiresAt)`, gia hạn trượt mỗi lần refresh nhưng không bao giờ vượt hạn tuyệt đối:
  - Không ghi nhớ: idle 8 giờ, tuyệt đối 12 giờ, cookie `refreshToken` là cookie phiên (không `Max-Age`, đóng trình duyệt là mất).
  - Có ghi nhớ: idle 7 ngày, tuyệt đối 30 ngày, cookie lưu 30 ngày tính theo thời gian còn lại của phiên.
  - Cookie `accessToken` luôn có `Max-Age` bằng hạn access token (15 phút) để SSR hoạt động như cũ.
- Cấu hình: `app.security.jwt.session-idle-ms`, `session-absolute-ms`, `remember-me-idle-ms`, `remember-me-absolute-ms` (env `JWT_SESSION_IDLE_MS`, `JWT_SESSION_ABSOLUTE_MS`, `JWT_REMEMBER_ME_IDLE_MS`, `JWT_REMEMBER_ME_ABSOLUTE_MS`; có mặc định trong code). `refresh-token-expiration-ms` chỉ còn là giá trị dự phòng cho `jwtUtils.generateRefreshToken` 3 tham số.
- Phiên tạo trước khi nâng cấp (thiếu `rememberMe`/`absoluteExpiresAt`) được coi là có ghi nhớ, hạn tuyệt đối tính từ `issuedAt` + 30 ngày.
- Refresh vẫn xoay vòng token bằng Lua compare-and-set; refresh một phiên đã hết hạn tuyệt đối trả 401 `INVALID_TOKEN`.
- Đổi mật khẩu, quên mật khẩu, admin đặt lại mật khẩu đều thu hồi các phiên (đổi mật khẩu khi đang đăng nhập giữ phiên hiện tại).

### Quên mật khẩu (công khai, `/api/auth/forgot-password`)
- `POST /otp {username}`: gửi OTP 6 số tới email Huynh Trưởng gắn với tài khoản (ưu tiên) hoặc email của tài khoản (`AccountEmailResolver`). Trả `maskedEmail` dạng `ng***@g***.com` (`EmailMasker`).
- `POST /otp/verify {username, otp}`: trả `resetToken` dùng một lần, sống 10 phút.
- `POST /reset {username, resetToken, newPassword, confirmPassword}`: đặt mật khẩu mới (8–72 ký tự, khác mật khẩu hiện tại), xóa cờ `mustChangePassword` và hạn mật khẩu tạm, thu hồi mọi phiên, gửi email thông báo sau khi commit (lỗi gửi thông báo chỉ ghi log).
- Quy tắc nghiệp vụ: OTP hạn 5 phút, chỉ lưu hash trong Redis, sai 5 lần thì hủy; chờ 60 giây giữa hai lần gửi; tối đa 5 mã/giờ/tài khoản; giới hạn 20 yêu cầu/15 phút/IP (tier rate-limit `forgot`).
- Tài khoản không tồn tại, bị khóa hoặc không có email cùng trả một thông báo 400 chung để không dò được tài khoản.
- Rollback: gửi OTP lỗi thì khôi phục OTP cũ, bỏ thời gian chờ, hoàn lại lượt gửi và trả 503. Bước đặt lại mật khẩu chạy trong transaction; lỗi thu hồi phiên thì DB rollback và reset token được trả lại.
- OTP/token của luồng này dùng tiền tố khóa `forgot:` nên không đè luồng đổi mật khẩu khi đang đăng nhập.
- `POST /api/auth/password/otp` (đổi mật khẩu khi đang đăng nhập) giờ cũng chỉ trả email đã che.

### Admin đặt lại mật khẩu
- `POST /api/system/accounts/{username}/reset-password` (không body). Quyền riêng `system.account.reset_password.{all,diocese,deanery,parish}` (migration `V103`), mặc định gán cho mọi vai trò đang có `system.account.manage.<cùng phạm vi>`; có thể cấp riêng qua phân quyền.
- Kiểm tra: `AccountManagementAuthorizationHelper.requireCanResetPassword` (không tự đặt lại mình, không đặt lại SUPER_ADMIN, đúng phạm vi giáo phận/giáo hạt/giáo xứ, vai trò đích không cao hơn người thao tác; SUPER_ADMIN cần quyền `.all` và chỉ với tài khoản do super-admin tạo) cộng `requireOwnedByActorScope` giống thao tác khóa/mở tài khoản. Tài khoản bị khóa hoặc thuộc đơn vị bị khóa không đặt lại được.
- Mật khẩu tạm do `TemporaryPasswordGenerator`: đúng 8 chữ cái hoa/thường (bỏ `I l O o`), có ít nhất 1 hoa và 1 thường, không số/ký tự đặc biệt. Chỉ gửi qua email (Huynh Trưởng ưu tiên, sau đó email tài khoản), không bao giờ trả qua API; phản hồi chỉ có `maskedEmail` và `expiresAt`.
- `account.temporary_password_expires_at` = bây giờ + 10 phút và `must_change_password = true`. Đăng nhập bằng mật khẩu tạm quá hạn trả 401 "Mật khẩu tạm thời đã hết hạn"; đăng nhập đúng hạn bị buộc đổi mật khẩu như lần đăng nhập đầu. Đổi mật khẩu hoặc quên mật khẩu xóa hạn này. Tài khoản tạo mới giữ `NULL` (không hết hạn) như cũ.
- Thứ tự trong transaction: ghi DB + flush, gửi email, rồi mới thu hồi mọi phiên. Gửi email lỗi thì DB rollback, mật khẩu cũ giữ nguyên và phiên đang đăng nhập không bị ảnh hưởng.

### Test liên quan
`ForgotPasswordServiceTest`, `EmailMaskerTest`, `TemporaryPasswordGeneratorTest`, `AuthServiceTest` (hạn mật khẩu tạm, rememberMe), `RedisTokenSessionServiceTest` (hạn trượt/tuyệt đối), `AccountManagementServiceTest` + `AccountManagementAuthorizationHelperTest` (đặt lại mật khẩu), `AccountPasswordResetIntegrationTest` (rollback trên DB thật), `ForgotPasswordControllerSecurityIntegrationTest`, `AuthControllerSecurityIntegrationTest` (cookie phiên / cookie lưu), `EndpointContractMatrixTest`.

## 2026-10-26 Mã QR điểm danh có mã khóa (nhánh `feature/responsive-unified-qr-scan`)

- `AttendanceTokenService`: token đổi sang `HTATT2.<mã khóa>.<mã hồ sơ>.<chữ ký>` (HMAC phủ cả mã khóa); `verify` trả `Verified(courseCode, participationId)`; mã `HTATT1` cũ bị từ chối với thông báo xuất lại thẻ / gửi lại email. Thẻ tham dự và email thông tin tham dự tạo mã mới.
- Thêm `POST /api/training/attendance/lookup|scan` (`AttendanceScanController`): khóa lấy từ mã QR, quyền kiểm tra bằng `canScanAttendance` theo khóa đó. Endpoint theo khóa vẫn giữ và báo lỗi nếu mã thuộc khóa khác.
- Thông báo khi khóa không điểm danh được nêu rõ tên khóa: vô hiệu hóa / đã kết thúc / chưa bật điểm danh. `ScanResult` có thêm `courseCode`, `courseName`.

## 2026-10-27 SUPER_ADMIN cấp tài khoản vai trò toàn hệ thống, sửa email hồ sơ (nhánh `feature/superadmin-create-accounts`)

- `POST /api/system/accounts` khi người tạo là SUPER_ADMIN: ngoài SUPER_ADMIN và ADMIN_DIOCESE, được cấp tài khoản cho vai trò toàn hệ thống = vai trò không phải vai trò hệ thống (`isSystem=false`) và do super-admin tạo (`AccountManagementAuthorizationHelper.isSystemWideCustomRole`). Không cấp được cho ADMIN_DEANERY, ADMIN_PARISH hay vai trò do các giáo phận tạo.
- Cách tạo giống ADMIN_DIOCESE không gắn Huynh Trưởng: bắt buộc `credentialEmail`, không gửi `leaderId`/`dioceseId`, username sinh từ phần trước `@` (thêm hậu tố số nếu trùng), email lưu ở `account.email`, mật khẩu tạm gửi qua email. Email không duy nhất nên được trùng với tài khoản khác (kể cả ADMIN_DIOCESE).
- Danh sách tài khoản của SUPER_ADMIN hiển thị thêm các tài khoản không gắn Huynh Trưởng/đơn vị có vai trò toàn hệ thống.
- `AccountResponse.email`: chỉ điền khi người xem là SUPER_ADMIN (danh sách, chi tiết, tạo, khóa/mở).
- `PUT /api/auth/me/email` (`MyEmailService`): tài khoản không gắn Huynh Trưởng tự đổi `account.email`, bắt buộc `currentPassword`; dùng chung bộ đếm sai mật khẩu với đổi mật khẩu (5 lần / 15 phút). Tài khoản gắn Huynh Trưởng bị từ chối vì dùng email Huynh Trưởng.

## 2026-10-27 Loại khóa huấn luyện đi riêng theo giáo phận (nhánh `feature/course-type-per-diocese`)

- Migration `V105__course_type_per_diocese.sql`: `training_course_type.diocese_id` (bắt buộc). Danh mục hiện có được sao chép cho mọi giáo phận (mã UUID mới); khóa huấn luyện, thành phần điểm, khóa tiên quyết và gán cấu hình chứng nhận được chuyển sang bản sao của đúng giáo phận (gán `SYSTEM` áp dụng cho mọi bản sao). Loại khóa gốc dùng chung bị xóa. Giáo phận tạo sau bắt đầu với danh mục rỗng.
- `CourseTypeDioceseGuard`: tài khoản chỉ xem / dùng / sửa loại khóa của giáo phận mình (super admin không thuộc giáo phận nào thì đọc được tất cả nhưng không tạo được). Áp dụng ở `TrainingCourseTypeService`, tạo/sửa khóa (`TrainingCourseService`), thành phần điểm, cấu hình chứng nhận.
- Danh sách loại khóa lọc theo `dioceseId`; khóa tiên quyết phải cùng giáo phận; thống kê sử dụng cấp độ và danh sách loại khóa của analytics cũng tính theo giáo phận.

## 2026-10-28 Sửa lỗi tạo huynh trưởng và cho admin giáo phận tạo giáo xứ (nhánh `fix/leader-create-diocese-parish-create`)

- `LeaderService.create` không còn đọc `parish.getDeanery().getDiocese()` qua proxy lazy ngoài phiên (gây `LazyInitializationException` → 500). Giáo phận của giáo xứ lấy bằng `ParishRepository.findDioceseIdById` (qua `LeaderValidationHelper.findDioceseIdOfParish`).
- `POST /api/parishes`: `ParishAccessEvaluator.canCreate` chấp nhận thêm `organization.parish.create.diocese`; `ParishScopeHelper.enforceCreateScope` chỉ cho tạo trong giáo hạt thuộc giáo phận của tài khoản (ngoài phạm vi → 403). SUPER_ADMIN vẫn không tạo được. Test: `ParishAccessEvaluatorTest.canCreateShouldAllowDioceseScopedPermissionAndRejectSuperAdmin`.

## 2026-10-28 Giáo họ / giáo điểm (nhánh `feature/parish-sub-units`, migration `V106`)

- Giáo họ (`GIAO_HO`) và giáo điểm (`GIAO_DIEM`) là **dòng trong bảng `parish`** với `unit_type` (mặc định `PARISH`) và `parent_parish_id` (giáo xứ cha, bắt buộc với họ/điểm, cấm với giáo xứ; CHECK ở DB). `deanery_id` của họ/điểm bằng giáo hạt của giáo xứ cha. Không có controller/route/quyền mới: dùng `/api/parishes` và quyền `organization.parish.*`.
- Huynh trưởng và tài khoản gắn `parish_id` vào chính giáo họ/điểm nên toàn bộ phạm vi `*.parish` (danh sách, đăng ký, chứng nhận, ban điều hành, thông báo, thống kê) tự áp dụng cho họ/điểm; **admin giáo xứ cha không có quyền trên giáo họ/điểm của nó**, admin giáo họ dùng vai trò ADMIN_PARISH trong phạm vi giáo họ. Giáo hạt/giáo phận vẫn thấy vì cùng chuỗi `deanery`.
- `ParishCreateRequest.unitType` + `parentParishId` (đổi loại/cha sau khi tạo không hỗ trợ, `ParishUpdateRequest` không có hai trường này). `ParishValidationHelper.resolveParent`: cha phải là giáo xứ (`PARISH`) cùng giáo hạt; họ/điểm không làm cha. Chỉ tài khoản có quyền tạo giáo xứ ở cấp giáo hạt/giáo phận tạo được (admin giáo xứ không có `create.parish`).
- `ParishResponse` thêm `unitType`, `unitTypeLabel`, `parentParishId`, `parentParishName`, `subUnitCount`. **Tắt giáo xứ cha không ảnh hưởng giáo họ / giáo điểm**: chúng vẫn hoạt động bình thường (`effectiveStatus`, khóa tài khoản, `impact` không xét giáo xứ cha).
- `parishCount` ở giáo hạt/giáo phận và KPI `totalParishes` của Superadmin chỉ đếm `PARISH`. Phân tích theo đơn vị cơ sở vẫn liệt kê cả họ/điểm.
- Trường trộn thẻ/chứng nhận: `leaderParishName` (nhãn "Giáo xứ") luôn in tên **giáo xứ** (giáo họ/giáo điểm → giáo xứ mẹ) nên khớp nhãn "Giáo xứ" in sẵn trên ảnh nền và cấu hình cũ không đổi. Hai trường mới `leaderSubUnitName` ("Giáo họ X"/"Giáo điểm Y", rỗng với giáo xứ) và `leaderUnitFullName` ("Giáo họ X – Giáo xứ Z"). Logic ở `parish/model/ParishUnitNames`. Export cột Loại, Thuộc giáo xứ; file import vẫn nhận tên giáo họ/điểm.
- `WorkflowDecisionNotifier.findParishAdmins` khớp cả tài khoản có phạm vi qua huynh trưởng (`leader.parish`), không chỉ `account.parish_id`.
- Test: `ParishValidationHelperSubUnitTest`.
- Đổi giáo xứ cha: `PATCH /api/parishes/{id}/parent` (`ParishParentChangeRequest.parentParishId`), chỉ với giáo họ / giáo điểm, giáo xứ cha mới phải là `PARISH` cùng giáo hạt và đang hoạt động. Quyền: `ParishAccessEvaluator.canChangeParent` = quyền tạo giáo xứ (`create.all`/`create.diocese`/`create.deanery`) đúng phạm vi giáo phận / giáo hạt của đơn vị; Superadmin và admin giáo xứ / giáo họ không đổi được. Huynh trưởng và tài khoản vẫn gắn vào chính giáo họ / giáo điểm nên phạm vi quyền không đổi.
- Nâng thành giáo xứ: `PATCH /api/parishes/{id}/promote` (cùng quyền `canChangeParent`) đặt `unit_type=PARISH` và bỏ `parent_parish_id`; giáo họ / giáo điểm trở thành giáo xứ độc lập, huynh trưởng và tài khoản giữ nguyên (admin giáo họ thành admin giáo xứ). Chỉ áp dụng cho giáo họ / giáo điểm; tên vẫn duy nhất trong giáo hạt nên không cần đổi tên.

## 2026-10-28 Tìm kiếm nhanh ở header (nhánh `feature/header-quick-search`, migration `V109`)
- `GET /api/search/quick?q=&limit=5` (module `modules/search`): tìm huynh trưởng, giáo phận, giáo hạt, giáo xứ, khóa huấn luyện, chứng chỉ bằng PostgreSQL thay Elasticsearch: `unaccent` + `pg_trgm` (`like` và `word_similarity` `<%`), xếp hạng theo khớp đầu chuỗi rồi độ tương đồng.
- `V109` tạo extension `pg_trgm`, hàm `immutable_unaccent(text)` (IMMUTABLE, đ thành d) và các index GIN trigram trên đúng biểu thức mà câu truy vấn dùng. Đổi biểu thức tìm kiếm thì phải đổi index tương ứng.
- Từ khóa tối thiểu 2 ký tự sau khi bỏ dấu (ngắn hơn trả danh sách rỗng), `limit` tối đa 10, từ khóa được thoát `%` `_`.
- Quyền: mỗi nhóm chỉ chạy khi tài khoản có `*.read.*` tương ứng và luôn lọc theo phạm vi của tài khoản (`findActive*IdByUsername`), không nhận ID từ client. SUPER_ADMIN chỉ thấy giáo phận/giáo hạt/giáo xứ; tài khoản bị khóa đơn vị không xác định được phạm vi nên không có kết quả. Khóa huấn luyện và chứng chỉ chỉ cho quyền cấp giáo phận/giáo hạt (giống danh sách).

## 2026-10-29 Import avatar kèm Excel (nhánh `feature/excel-import-avatar`, migration `V115`)

- Import huynh trưởng và danh sách tham dự khóa quá khứ nhận thêm part `images` là ZIP tùy chọn (tối đa 50MB); hash idempotency gồm cả Excel và ZIP. File mẫu thêm cột tùy chọn `Tên file ảnh` và hướng dẫn ảnh JPG/PNG/WEBP tối đa 5MB.
- `AvatarZip` chỉ đọc entry trong ZIP, không giải nén ra đĩa; bỏ path traversal, file ẩn/`__MACOSX`, giới hạn 5.000 entry và 300MB sau giải nén, đối chiếu basename không phân biệt hoa thường và hỗ trợ tên không có đuôi. Tên trùng ở nhiều thư mục được báo mơ hồ.
- Pha transaction chỉ import dữ liệu và lưu `avatar_plan`/cảnh báo cùng trạng thái job. `ExcelAvatarProcessor` chỉ chạy sau commit, tạo asset ACTIVE rồi gắn bằng cập nhật có điều kiện `image_asset_id is null`; hồ sơ đã có ảnh giữ ảnh cũ, asset không gắn được được release.
- Trạng thái ảnh (`PENDING/RUNNING/DONE`) và lease tách khỏi trạng thái dữ liệu `SUCCEEDED`; lỗi từng ảnh không rollback dữ liệu, được trả rõ dòng, họ tên, tên file và lý do cùng tiến độ `avatarProcessed/avatarTotal/avatarSucceeded`. ZIP được xóa khi pha ảnh hoàn tất hoặc job hết hạn.

## 2026-10-30 Xóa cache khi dữ liệu liên quan đổi (nhánh `feature/excel-import-avatar`)

- Rà soát mọi nơi ghi dữ liệu mà cache danh sách / chi tiết đang nhúng. Thêm chú thích gộp `EvictCaches.Deaneries`, `Parishes`, `ExecutiveAssignments` (cùng `Courses`, `Certificates`, `Leaders`, `Analytics` đã có).
- Sửa các chỗ thiếu: `LeaderService.update` / `updateOwnProfile` xóa thêm chứng nhận và phân công ban điều hành (nhúng tên, ngày sinh, giáo xứ); `DeaneryService.update` xóa giáo xứ, huynh trưởng, khóa, chứng nhận, phân công; `DioceseService.update` xóa giáo hạt, giáo xứ, huynh trưởng, khóa, chứng nhận, phân công; `ParishService.update` xóa huynh trưởng, chứng nhận, phân công; `PositionService.update` / `toggleStatus` xóa phân công; `LeaderLevelService` create / update / reorder / delete xóa khóa, chứng nhận, huynh trưởng và thống kê (tên cấp độ nằm trong các response đó).
- `CacheConfig` bật `transactionAware()`: xóa và ghi cache chờ commit, tránh request đọc xen vào lúc transaction ngoài (như `CertificateIdempotentActions`) chưa commit nạp lại dữ liệu cũ cho cả TTL.
- `ExcelAvatarProcessor` xóa cache huynh trưởng sau mỗi 25 ảnh gắn xong và luôn xóa ở cuối job (kể cả khi `finishAvatar` không cập nhật được), để danh sách thấy ảnh mới ngay.
- Bỏ chú thích cache vô hiệu trên phương thức private `LeaderService.requireParishOperational`.
- Test tích hợp (`ExcelImportExportIntegrationTest`): job ảnh xóa cache huynh trưởng (chi tiết hiện ảnh mới ngay); cache chờ commit và không xóa khi rollback; sửa huynh trưởng / giáo xứ / giáo hạt / giáo phận xóa đủ cache phụ thuộc; sửa cấp độ xóa cache khóa, chứng nhận, huynh trưởng, thống kê.

## Refactor SOLID / design pattern (nhánh `refactor/solid-design-patterns`, 2026-10-30)

- Không đổi API hay hành vi; test hiện có vẫn pass (964 test).
- Helper dùng chung thay cho các bản private lặp ở nhiều service: `common/repository/SearchRequests.copy`, `common/repository/InMemoryPager`, `common/utils/LeaderNames.fullName`, `common/security/AuthorityChecks.hasAuthority`. Các bản có logic khác nhau được giữ nguyên.
- `ApiException` chuyển từ `modules/auth/exception` sang `common/exception`.
- Excel job: Strategy + Registry. `ExcelExportHandler` / `ExcelImportHandler` (một handler cho mỗi `ExcelJobKind`, khai báo trong `common/excel/job/handler`), `ExcelJobHandlerRegistry` kiểm tra đủ handler lúc khởi động; `ExcelJobHandlers` chỉ còn chuyển việc. Thêm loại xuất mới = thêm một bean handler.
- `CourseParticipationService`: tách `ScoreCalculator` / `WeightedScoreCalculator` (điểm tổng kết), `ParticipationEligibilityChecker` (điều kiện tham dự); `modules/training/ApprovalTransitionPolicy` dùng chung cho hồ sơ tham dự và `CertificateService.rejectInternal`. Constructor của service đổi (thêm hai collaborator, bỏ `RankHistoryRepository`); chữ ký public không đổi.
- Test thêm: `ExcelJobHandlerRegistryTest`, `WeightedScoreCalculatorTest`, `ApprovalTransitionPolicyTest`.
- `common/utils/TextFolding` (filterText/filterBoolean/foldText/normalize/uniqueName) dùng chung cho `CertificateConfigService` và `BadgeConfigService`.
- `common/security/UnitToggleSupport`: gom 5 collaborator tùy chọn và các bước kiểm tra/ghi lịch sử/thông báo khi khóa-mở giáo phận, giáo hạt, giáo xứ (thay các field và hàm private lặp ở ba service). `modules/system/service/AccountUnits` thay 6 hàm `resolveDiocese/Deanery/Parish` của `AccountManagementService`.
- Test thêm: `TextFoldingTest`, `UnitToggleSupportTest`, `AccountUnitsTest`; toàn bộ 977 test pass.
- `DomainEventMessage.of(...)` thay 6 chỗ dựng sự kiện bằng builder lặp (`CertificateService`, `CertificateLifecycleService`, `CourseParticipationService`, `TrainingCourseService`); test `DomainEventMessageTest`. Toàn bộ 978 test pass.
- `RegistrationSubmissionNotifier`: tách khỏi `CourseParticipationService` việc báo người duyệt/giáo xứ khi gửi danh sách đăng ký và phát sự kiện `batch_submitted`; service giữ `announceSubmission` làm hàm chuyển tiếp cho `CrossDeaneryService`. Constructor của service đổi (thay `TrainingNotificationRecipientResolver` + `TrainingNotificationService` bằng một notifier). Test `RegistrationSubmissionNotifierTest`; toàn bộ 979 test pass.
- Thư viện cấu hình chứng nhận/thẻ: `CertificateConfig` và `BadgeConfig` cùng implement `common/layoutconfig/LayoutConfig`, hai repo implement `LayoutConfigStore`; `LayoutConfigRules` (đặt cấu hình chung, kiểm tra quyền sửa, chặn xóa cấu hình chung, danh sách nhìn thấy) thay phần chép lặp trong hai service. Test `LayoutConfigRulesTest`; toàn bộ 984 test pass.
- `RegistrationDecisionNotifier` (thông báo duyệt/từ chối/xóa hồ sơ + gửi email thông tin tham dự) tách khỏi `CourseParticipationService`; hai collaborator tùy chọn chuyển vào đó.
- `common/excel/AbstractExcelImporter` (Template Method: đọc/kiểm tra, báo tiến độ, ghi từng dòng, tổng kết); `LeaderImportService` và `PastCourseParticipantImportService` kế thừa, trạng thái mỗi lần chạy nằm trong `ImportRun`.
- `config/audit/AuditScopeSource` (Strategy) + `AuditScopeSources` thay `switch` theo loại tài nguyên trong `AuditScopeResolver`.
- `RoleAssignmentPolicy`: toàn bộ luật gán vai trò tách khỏi `AccountRoleService`; ba cấp giáo phận/giáo hạt/giáo xứ dùng chung một luật qua bảng `Level`. Constructor của `AccountRoleService` không đổi.
- Test thêm: `RegistrationDecisionNotifierTest`, `AbstractExcelImporterTest`, `AuditScopeResolverTest`, `RoleAssignmentPolicyTest`. Toàn bộ 997 test pass.
- Không làm: Strategy theo `UnitType` cho `CrossDeaneryService`/`TrainingWorkflowSupport`/`CourseParticipationService` — nhánh theo cấp ở đó gắn chặt với quy trình nghiệp vụ, gom lại dễ đổi hành vi mà không có test bao phủ.

## Ảnh đại diện kèm Excel danh sách tham dự khóa thường (2026-10-30)

- Import danh sách tham dự khóa thường nhận cột tùy chọn "Tên file ảnh" và file ZIP ảnh giống khóa quá khứ: `PastCourseParticipantImportService` đọc cột này cho mọi khóa trừ import chia đội (`withTeam`), hồ sơ đã có ảnh giữ ảnh cũ, ảnh lỗi chỉ tạo cảnh báo.
- `ImportTemplates.regularParticipants`: thêm cột "Tên file ảnh" ở sheet "Danh sách tham dự" và sheet "Ngoại lệ", thêm dòng hướng dẫn số 13. File Excel mẫu cũ (không có cột ảnh) vẫn import được. Mẫu chia đội không đổi.
- Test: `ImportTemplatesTest` (mẫu khóa thường có cột ảnh ở cả hai sheet, mẫu chia đội không có).
- Import chia đội cũng nhận ảnh: endpoint `/teams/import` nhận phần `images`, `ExcelImportHandlerConfig` truyền ZIP cho `IMPORT_TEAM_PARTICIPANTS`, mẫu chia đội có cột "Tên file ảnh". `PastCourseParticipantImportService.importParticipants(..., withTeam, images)` là phương thức public có đủ `@Transactional`/`@CacheEvict`.
- Dọn trùng lặp: `ImportTemplates.avatarColumn()` / `avatarGuide(...)` thay 4 bản định nghĩa cột và dòng hướng dẫn ảnh; việc gom ảnh của một dòng nằm ở `collectAvatar(...)` thay vì trong thân vòng ghi.

## 2026-11-04 Chính sách bảo vệ dữ liệu cá nhân và bằng chứng đồng ý (nhánh `feature/privacy-policy-consent`, migration `V126`)

- Bảng `privacy_consent`: ai đồng ý (`SELF`) hoặc ai xác nhận hộ (`ADMIN_ATTESTATION`), phiên bản chính sách, chức năng (`source`), IP, user agent, thời điểm, thời điểm rút (`withdrawn_at`). Căn cứ: Điều 9, 10 Luật 91/2025/QH15; Điều 6 NĐ 356/2025/NĐ-CP.
- Module `modules/privacy`: `PrivacyConsentService` ghi và tra cứu bằng chứng (JdbcTemplate); phiên bản chính sách lấy từ `app.privacy.policy-version` (biến `APP_PRIVACY_POLICY_VERSION`, mặc định 1.0).
- Endpoint: `GET /api/privacy/policy` (công khai), `GET` / `POST /api/auth/me/privacy-consent`, `POST /api/auth/me/privacy-consent/withdraw` (đã thêm vào bảng hợp đồng endpoint).
- Request tự sửa (`MyProfileUpdateRequest`, `EmailUpdateRequest`, `MyAvatarUpdateRequest`) kế thừa `SelfConsentRequest`: bắt buộc `privacyConsent = true`, thiếu thì 400.
- Request admin nhập hộ (`LeaderCreateRequest`, `LeaderUpdateRequest`, `AccountCreateRequest`, `OrganizationUnitCreateRequest`, `OrganizationUnitUpdateRequest`) kế thừa `SubjectConsentRequest`: bắt buộc `subjectConsentConfirmed = true`. Ba endpoint import (`/leaders/import`, `/training/courses/{code}/participants/import`, `/teams/import`) nhận tham số multipart `subjectConsentConfirmed` và trả 400 nếu thiếu.
- Controller ghi bằng chứng sau khi lưu thành công; rút đồng ý chỉ đánh dấu `withdrawn_at`, không xóa dữ liệu.
- Test: `PrivacyConsentIntegrationTest`; cập nhật test tích hợp leader/giáo phận/giáo hạt/giáo xứ, `MyProfileUpdateIntegrationTest`, `OrganizationUnitValidationHelperTest`, `EndpointContractMatrixTest`.

## Email huynh trưởng tùy chọn, cờ mở đăng ký, hiển thị điểm khóa quá khứ, sao chép danh sách tham dự khi tạo/sửa khóa (migration `V130`)

- `leader.email` cho phép NULL (unique giữ nguyên, NULL không bị coi là trùng). Tạo/sửa huynh trưởng không bắt buộc email; sửa hồ sơ gửi `clearFields: ["email"]` để xóa email. File Excel mẫu huynh trưởng và danh sách tham dự (khóa thường, khóa quá khứ, chia đội) không còn bắt buộc cột Email (cột có thể để trống hoặc bỏ hẳn). Import danh sách tham dự tìm huynh trưởng theo Số điện thoại (bắt buộc) và Email (nếu có). Tạo tài khoản gắn huynh trưởng vẫn cần email để gửi thông tin đăng nhập; gửi email thông tin tham dự bỏ qua hồ sơ không có email như trước.
- Khóa quá khứ có cờ `scoreVisible` (`PastTrainingCourseRequest`): null = tắt khi tạo, giữ nguyên khi sửa (khóa quá khứ cũ giữ giá trị hiện có, trước đây luôn bật).
- Cờ `registration_enabled` (mặc định TRUE, khóa quá khứ FALSE): tắt thì thời gian đăng ký bị xóa, `registrationStatus` luôn `CLOSED` (không mở thủ công được: `PATCH /registration` trả 409) và `requireRegistrationWindowOpen` từ chối đăng ký. Bật lại khi sửa thì tính lại trạng thái theo thời gian đăng ký. Response có `registrationEnabled`.
- Sao chép danh sách tham dự khi tạo/sửa khóa thường: `copySourceCourseCodes` + `copyMode` (`ALL` | `PASSED_ONLY`) trong `TrainingCourseCreateRequest/UpdateRequest`, thực hiện cùng giao dịch lưu khóa (cần quyền duyệt đăng ký đúng cấp khóa; hồ sơ trùng/đã có/ngưng hoạt động/ngoài phạm vi bị bỏ qua). Response có `copiedParticipantCount`, `copySkippedCount`. `GET /api/training/courses/copy-sources?courseCode=` (sửa khóa) hoặc `?courseTypeCode=` (tạo khóa) liệt kê khóa nguồn: khóa thường đã kết thúc hoặc khóa quá khứ cùng giáo phận (khóa giáo hạt chỉ nhận khóa của chính giáo hạt đó và khóa cấp giáo phận), kèm loại khóa, năm tổ chức, số hồ sơ và số hồ sơ đạt.

## Thăng cấp theo chứng nhận, chứng nhận ngoại lệ, thu hồi không đổi kết quả (migration `V131`)

- Khóa có cấp độ đạt được: kết quả chung và thăng cấp **chỉ dựa vào chứng nhận đã duyệt** khi kết thúc khóa (`CourseFinishService`), không xét điểm. Khi kết thúc khóa, chứng nhận còn chờ duyệt (PENDING / SUBMITTED) tự chuyển sang REJECTED (lý do "Khóa huấn luyện đã kết thúc khi chứng nhận chưa được duyệt"), hồ sơ không đạt, không thăng cấp. Khóa không có cấp độ đạt được vẫn xét kết quả điểm.
- Chứng nhận ngoại lệ: `POST /api/training/participations/{id}/exception-certificate` (quyền chấm điểm đúng cấp khóa) tạo chứng nhận chờ duyệt cho hồ sơ đã chấm điểm nhưng không đạt (khóa thường có cấp độ, chưa kết thúc, hồ sơ chưa có chứng nhận hoặc chứng nhận đã bị từ chối). Cột `certificate.exception_requested`; response chứng nhận có `exceptionRequested`, `approvalReasonRequired`. Duyệt chứng nhận này bắt buộc `approvalReason`. Sửa điểm không xóa chứng nhận ngoại lệ đang chờ duyệt.
- Thu hồi chứng nhận không còn đặt kết quả chung = không đạt (chỉ hạ cấp và đổi trạng thái). Cấp lại / bật lại khóa ép thăng cấp lại (`reevaluateIfFinished(..., true)`).
- Test: `CourseFinishServiceTest`, cập nhật `CertificateRevokeTest`.

- Sao chép danh sách tham dự ở form tạo / sửa khóa chỉ lấy từ **khóa tiên quyết đã kết thúc** của loại khóa đang chọn (cùng giáo phận; khóa giáo hạt chỉ nhận khóa của giáo hạt đó và cấp giáo phận): `GET /api/training/courses/copy-sources` và `/copy-sources/candidates` (mỗi huynh trưởng một dòng, kèm `blockedReason`), `copyLeaderIds` trong request tạo / sửa khóa để chọn riêng từng người. Không còn nút sao chép từ khóa nguồn bất kỳ ở tab Danh sách tham dự; nút "Sao chép từ khóa tiên quyết" cũ vẫn giữ.

- Khóa đã diễn ra (tới ngày bắt đầu hoặc đã kết thúc; `TrainingCourseStatusSupport.isCourseStarted`, `CourseActivityGuard.requireNotStarted`): trạng thái đăng ký luôn `ENDED` dù cờ `registration_enabled` còn bật, không mở lại thủ công được, gửi đăng ký bị từ chối; không sao chép vào danh sách tham dự (`CourseCopyService.requireWritable`) và không nhập Excel danh sách tham dự / chia đội (`PastCourseParticipantImportService.requireImportAllowed`, khóa thường). Thời gian đăng ký (khi mở đăng ký) không được sau ngày bắt đầu của khóa (`validateRegistrationWindow`).


## Năm đạt cấp độ ở danh sách ứng viên đăng ký (feat/registration-achieved-year)

- `GET /training/courses/{code}/registration-candidates` trả thêm `levelAchievedYear`: năm đạt cấp độ HIỆN TẠI của huynh trưởng, chỉ tra cho các dòng của trang trả về (một truy vấn gộp `RankHistoryRepository.findAchievedYearsByLeaders`, gọi qua `ParticipationEligibilityChecker.achievedYears`).
- Năm đạt lấy từ dòng lịch sử cấp bậc chưa hạ cấp có `newLevel` = cấp hiện tại, năm lớn nhất nếu có nhiều dòng: năm duyệt chứng nhận (`certificateApprovedAt`) của khóa sinh ra dòng đó; khóa không cấp chứng nhận thì `hostYear` (thiếu thì năm `endDate`); dòng không gắn khóa thì năm `promotionDate`. Huynh trưởng chưa có lịch sử cấp bậc (cấp nhập thẳng) trả null.
- Test: `RankHistoryAchievedYearTest`.
## Gửi sự kiện RabbitMQ không làm hỏng request (nhánh fix/domain-event-publish-nonfatal)

- `DomainEventPublisher` bắt `AmqpException` khi gửi sự kiện (sau commit hoặc ngoài giao dịch) và chỉ ghi log lỗi; trước đây RabbitMQ lỗi (ví dụ vhost không tồn tại) làm request đã lưu dữ liệu vẫn trả 500 "Lỗi không xác định". Test: `DomainEventPublisherTest`.
## Khóa tiên quyết liên cấp và chủ sở hữu loại khóa (nhánh feat/cross-level-prerequisite)

- `training_course_type` có `owner_unit_type` (DIOCESE | DEANERY) và `owner_deanery_id` (migration V134). Loại khóa giáo phận tạo (cấp giáo phận, hoặc cấp giáo hạt dùng chung): mọi giáo hạt thấy và dùng, chỉ tài khoản giáo phận sửa / đổi trạng thái. Loại khóa giáo hạt tự tạo: chỉ giáo hạt đó thấy và sửa, giáo phận không thấy. Quy tắc nằm ở `CourseTypeOwnership`; danh sách lọc bằng `scopeAnyOf`; response có `ownerUnitType`, `editable`.
- Khóa tiên quyết: loại cấp giáo phận chọn được loại cấp giáo phận hoặc cấp giáo hạt do giáo phận tạo (dùng chung); loại cấp giáo hạt chỉ chọn cấp giáo hạt dùng chung hoặc của chính giáo hạt. Chặn vòng lặp và loại đã ngừng hoạt động. API lựa chọn: `GET /training/course-types/prerequisite-options`. Cách tính năm đạt không đổi (năm duyệt chứng nhận, không có thì năm tổ chức khóa).
- Test: `CourseTypeOwnershipTest`, `CrossLevelPrerequisiteTest`.

## Bỏ trống cấp độ khi hoàn thành, quy tắc đạt khóa tiên quyết (nhánh fix/course-type-level-clear-prerequisite-rule)

- Sửa loại khóa: `clearResultingLevel = true` xóa cấp độ khi hoàn thành (ưu tiên hơn `resultingLevel`); trước đây không có cách nào xóa vì `resultingLevel` null nghĩa là giữ nguyên. `requiredMinAge` bằng 0 được lưu thành trống (không yêu cầu tuổi), số âm bị từ chối; `ParticipationEligibilityChecker` coi tuổi tối thiểu trống hoặc 0 là không yêu cầu nên không đòi ngày sinh.
- Đạt khóa tiên quyết (`TrainingWorkflowSupport.PASSED_AS_PREREQUISITE_JPQL`): loại khóa có cấp chứng nhận tính theo chứng nhận được duyệt (`certificateApprovalStatus = APPROVED`; chờ duyệt, từ chối, thu hồi đều không tính), loại khóa không cấp chứng nhận tính theo kết quả khóa (`result = COMPLETED`). Áp dụng cho `findLatestCompletedYearByLeaderAndCourseType` (điều kiện tham dự) và `countCompletedLeadersByType` (ô chọn khóa tiên quyết). Trước đây cả hai chỉ xét kết quả khóa. Bỏ `existsApprovedParticipationByLeaderAndCourseType` (không còn nơi gọi).
- `CourseTypeOwnership.scope()` ghi nhớ theo request cho cả request ghi (`RequestMemo.getInAnyRequest`), nên kiểm tra quyền ở controller, kiểm tra chủ sở hữu và dựng response dùng chung một lần tra; `update` tra giáo phận của người thao tác một lần. Request đọc vốn đã được `AccountLookupMemoConfig` ghi nhớ.
- `CourseApproverRule.canApprove` giữ quy tắc "người duyệt đăng ký đúng cấp và đúng đơn vị của khóa" cho cả `CourseParticipationService.canApproveCourse` và danh sách khóa đăng ký được (`TrainingCourseService.findRegistrable`); ở danh sách, tài khoản có quyền duyệt nhưng không gắn đơn vị đúng cấp thì bỏ qua khóa thay vì báo lỗi cả danh sách.
- Test: `CrossLevelPrerequisiteTest`, `CourseTypeOwnershipTest`, `CourseApproverRuleTest`, `ParticipationEligibilityMinAgeTest`, `PrerequisitePassRuleIntegrationTest` (Testcontainers).
- Khóa đã kết thúc không sửa được: `TrainingCourseService.update` chặn khi `TrainingCourseStatusSupport.isCourseEnded` (kết thúc thủ công hoặc quá ngày kết thúc), báo 409 "Khóa huấn luyện đã kết thúc nên không thể sửa". Khóa quá khứ vẫn sửa ở chức năng riêng.
- Khóa cấp giáo phận chỉ dùng loại khóa cấp giáo phận: tài khoản gắn trực tiếp giáo phận (không gắn giáo hạt) tạo khóa, và khóa cấp giáo phận đổi loại khi sửa, đều bị từ chối nếu loại khóa có `managementUnitType = DEANERY` (kể cả loại do giáo phận tạo dùng chung). Kiểm tra ở `TrainingCourseService.requireDioceseLevelType`.
- Test: `TrainingCourseUpdateScopeTest` (khóa đã kết thúc, đổi sang loại giáo hạt, tạo khóa với loại giáo hạt).

## fix/deanery-see-open-diocese-courses
- Admin giáo hạt (chỉ có `training.participation.submit.deanery`, không kèm quyền giáo xứ) trước đây gửi đăng ký được cho khóa giáo phận nhưng không thấy khóa đó ở danh sách khóa và bị từ chối khi mở chi tiết, vì chỉ `submit.parish` mới được xem khóa đang mở đăng ký. Nay `submit.deanery` cũng được: `TrainingCourseAccessEvaluator.canSeeOpenCourses` (dùng cho `canList`) và `canReadOpenCourseAsSubmitter` (dùng cho `canRead`, cần giáo xứ hoặc giáo hạt của tài khoản còn hoạt động), `TrainingCourseService.findAll` thêm nhóm khóa đang mở theo giáo phận / giáo hạt của tài khoản.
- Vẫn chỉ xem: khóa `OPEN`, `status = true`, đúng giáo phận (khóa thường) hoặc đúng giáo hạt (khóa giáo hạt); không có quyền sửa, kết thúc, khóa điểm.
- Test: `TrainingCourseAccessEvaluatorTest` (admin giáo hạt thuần thấy khóa mở của giáo phận mình; không thấy khóa đóng, vô hiệu, giáo phận khác, hoặc khi giáo hạt ngưng hoạt động).

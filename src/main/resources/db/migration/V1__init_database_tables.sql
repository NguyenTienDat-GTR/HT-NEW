-- DATABASE DDL SPECIFICATION - POSTGRESQL DIALECT
-- TARGET DATABASE: ht_management
-- VERSION: 3.0 (Integrated OAuth2 & Course Requirements)

-- =============================================================================
-- 0. CLEANUP EXISTING TABLES (IF ANY - DROP IN REVERSE ORDER OF DEPENDENCIES)
-- =============================================================================
DROP TABLE IF EXISTS system_log CASCADE;
DROP TABLE IF EXISTS account CASCADE;
DROP TABLE IF EXISTS permission_role CASCADE;
DROP TABLE IF EXISTS permission CASCADE;
DROP TABLE IF EXISTS role CASCADE;
DROP TABLE IF EXISTS certificate CASCADE;
DROP TABLE IF EXISTS course_participation CASCADE;
DROP TABLE IF EXISTS training_course CASCADE;
DROP TABLE IF EXISTS course_requirement CASCADE;
DROP TABLE IF EXISTS rank_history CASCADE;
DROP TABLE IF EXISTS leader CASCADE;
DROP TABLE IF EXISTS position CASCADE;
DROP TABLE IF EXISTS parish CASCADE;
DROP TABLE IF EXISTS deanery CASCADE;
DROP TABLE IF EXISTS diocese CASCADE;

-- =============================================================================
-- 1. DIOCESE TABLE (Giáo Phận)
-- =============================================================================
CREATE TABLE diocese (
                         diocese_code VARCHAR(20) PRIMARY KEY,
                         diocese_name VARCHAR(100) NOT NULL,
                         union_name VARCHAR(100) NOT NULL, -- Tên Liên Đoàn (e.g., Liên đoàn Gioan Phaolô II)
                         patron_saint_day DATE,
                         establishment_date DATE,
                         chaplain VARCHAR(100), -- Cha tuyên úy Giáo phận
                         phone_number VARCHAR(15),
                         email VARCHAR(100) UNIQUE,
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 2. DEANERY TABLE (Giáo Hạt)
-- =============================================================================
CREATE TABLE deanery (
                         deanery_code VARCHAR(20) PRIMARY KEY,
                         diocese_code VARCHAR(20) NOT NULL,
                         deanery_name VARCHAR(100) NOT NULL,
                         league_name VARCHAR(100) NOT NULL, -- Tên Hiệp Đoàn (e.g., Hiệp đoàn Phú Cường)
                         patron_saint_day DATE,
                         establishment_date DATE,
                         chaplain VARCHAR(100), -- Cha tuyên úy Giáo hạt
                         phone_number VARCHAR(15),
                         email VARCHAR(100) UNIQUE,
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         CONSTRAINT fk_deanery_diocese FOREIGN KEY (diocese_code) REFERENCES diocese(diocese_code) ON DELETE RESTRICT
);

-- =============================================================================
-- 3. PARISH TABLE (Giáo Xứ)
-- =============================================================================
CREATE TABLE parish (
                        parish_code VARCHAR(20) PRIMARY KEY,
                        deanery_code VARCHAR(20) NOT NULL,
                        parish_name VARCHAR(100) NOT NULL,
                        chapter_name VARCHAR(100) NOT NULL, -- Tên Xứ Đoàn
                        patron_saint_day DATE,
                        establishment_date DATE,
                        chaplain VARCHAR(100), -- Cha tuyên úy kiêm Chánh xứ
                        chapter_leader VARCHAR(100), -- Xứ đoàn trưởng đương nhiệm
                        phone_number VARCHAR(15),
                        email VARCHAR(100) UNIQUE,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_parish_deanery FOREIGN KEY (deanery_code) REFERENCES deanery(deanery_code) ON DELETE RESTRICT
);

-- =============================================================================
-- 4. POSITION TABLE (Chức Vụ Ban Thường Vụ các cấp)
-- =============================================================================
CREATE TABLE position (
                          position_code VARCHAR(20) PRIMARY KEY,
                          position_name VARCHAR(100) NOT NULL,
                          position_type VARCHAR(50) NOT NULL CHECK (position_type IN ('DIOCESE', 'DEANERY', 'PARISH'))
);

-- =============================================================================
-- 5. LEADER TABLE (Hồ sơ Huynh Trưởng / Dự Trưởng)
-- =============================================================================
CREATE TABLE leader (
                        leader_code VARCHAR(20) PRIMARY KEY,
                        parish_code VARCHAR(20) NOT NULL,
                        position_code VARCHAR(20),
                        holy_name VARCHAR(50) NOT NULL, -- Tên Thánh (e.g., Giuse, Maria)
                        full_name VARCHAR(100) NOT NULL,
                        birth_year INT NOT NULL CHECK (birth_year > 1900),
                        email VARCHAR(100) UNIQUE NOT NULL,
                        phone_number VARCHAR(15),
    -- Cấp bậc hiện tại trong phong trào
                        leader_level VARCHAR(50) NOT NULL CHECK (
                            leader_level IN ('YOUTH_LEADER', 'LEVEL_1', 'LEVEL_2', 'LEVEL_3', 'PROBATIONARY', 'TAP_SU')
                            ),
                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'LEAVE')),
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_leader_parish FOREIGN KEY (parish_code) REFERENCES parish(parish_code) ON DELETE RESTRICT,
                        CONSTRAINT fk_leader_position FOREIGN KEY (position_code) REFERENCES position(position_code) ON DELETE SET NULL
);

-- =============================================================================
-- 6. RANK HISTORY TABLE (Lịch Sử Biến Động Cấp Bậc)
-- =============================================================================
CREATE TABLE rank_history (
                              id BIGSERIAL PRIMARY KEY,
                              leader_id VARCHAR(20) NOT NULL,
                              old_level VARCHAR(50) NOT NULL,
                              new_level VARCHAR(50) NOT NULL,
                              promotion_date DATE NOT NULL,
                              note TEXT,
                              updated_by VARCHAR(50) NOT NULL, -- Định danh người thực hiện cập nhật
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_history_leader FOREIGN KEY (leader_id) REFERENCES leader(leader_code) ON DELETE CASCADE
);

-- =============================================================================
-- 7. COURSE REQUIREMENT TABLE (Cấu hình Yêu cầu điều kiện khóa học - Thực thể mới thêm)
-- =============================================================================
CREATE TABLE course_requirement (
                                    requirement_code VARCHAR(30) PRIMARY KEY,
                                    requirement_name VARCHAR(150) NOT NULL,
                                    requirement_type VARCHAR(30) NOT NULL CHECK (
                                        requirement_type IN (
                                                             'SMTL-DT', 'SMKN-DT', 'SMTL-I', 'SMKN-I',
                                                             'TSM-II', 'SMTL-II', 'SMKN-II', 'TSM-III-1',
                                                             'TSM-III-2', 'SMTL-III', 'SMKN-III',
                                                             'HLV-I', 'HLV-II', 'HLV-III'
                                            )
                                        ),
                                    required_current_level VARCHAR(50) NOT NULL CHECK (
                                        required_current_level IN ('YOUTH_LEADER', 'LEVEL_1', 'LEVEL_2', 'LEVEL_3', 'PROBATIONARY', 'TAP_SU')
                                        ),
                                    required_min_age INT NOT NULL CHECK (required_min_age > 0),
                                    required_years_active INT NOT NULL DEFAULT 0 CHECK (required_years_active >= 0),
                                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 8. TRAINING COURSE TABLE (Sa mạc Huấn Luyện)
-- =============================================================================
CREATE TABLE training_course (
                                 course_code VARCHAR(20) PRIMARY KEY,
                                 requirement_code VARCHAR(30) NOT NULL, -- Mối quan hệ 1 Yêu cầu -> Nhiều Khóa học
                                 course_name VARCHAR(150) NOT NULL,
                                 host_year INT NOT NULL,
                                 location VARCHAR(200) NOT NULL,
                                 start_date DATE NOT NULL,
                                 end_date DATE NOT NULL,
                                 course_type VARCHAR(50) NOT NULL,
                                 additional_notes TEXT,
                                 CONSTRAINT chk_course_dates CHECK (end_date >= start_date),
                                 CONSTRAINT fk_course_requirement FOREIGN KEY (requirement_code) REFERENCES course_requirement(requirement_code) ON DELETE RESTRICT
);

-- =============================================================================
-- 9. COURSE PARTICIPATION TABLE (Danh sách đăng ký & kết quả Sa mạc)
-- =============================================================================
CREATE TABLE course_participation (
                                      id BIGSERIAL PRIMARY KEY,
                                      leader_id VARCHAR(20) NOT NULL,
                                      course_code VARCHAR(20) NOT NULL,
                                      registration_date DATE NOT NULL DEFAULT CURRENT_DATE,
                                      status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')),
                                      total_score NUMERIC(4,2) CHECK (total_score >= 0 AND total_score <= 10),
                                      result VARCHAR(20) CHECK (result IN ('PASSED', 'FAILED', 'DEFERRED')), -- DAT, KHONG_DAT, BAO_LUU
                                      review TEXT, -- Nhận xét của ban huấn luyện
                                      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                      updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                      CONSTRAINT fk_participation_leader FOREIGN KEY (leader_id) REFERENCES leader(leader_code) ON DELETE CASCADE,
                                      CONSTRAINT fk_participation_course FOREIGN KEY (course_code) REFERENCES training_course(course_code) ON DELETE CASCADE,
                                      CONSTRAINT uq_leader_course UNIQUE (leader_id, course_code) -- Đảm bảo một người chỉ đăng ký 1 lần/khóa
);

-- =============================================================================
-- 10. CERTIFICATE TABLE (Chứng nhận hoàn thành Sa mạc)
-- =============================================================================
CREATE TABLE certificate (
                             certificate_code VARCHAR(30) PRIMARY KEY,
                             old_certificate_code VARCHAR(30), -- Lưu mã chứng nhận cũ nếu cấp đổi
                             certificate_name VARCHAR(150) NOT NULL,
                             leader_id VARCHAR(20) NOT NULL,
                             course_code VARCHAR(20),
                             result VARCHAR(50),
                             signed_by VARCHAR(100) NOT NULL, -- Cha tuyên úy hoặc Trưởng ban huấn luyện ký
                             issue_date DATE NOT NULL,
                             image_url VARCHAR(255), -- Đường dẫn lưu file scan chứng nhận (MinIO/S3 URL)
                             created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_certificate_leader FOREIGN KEY (leader_id) REFERENCES leader(leader_code) ON DELETE RESTRICT,
                             CONSTRAINT fk_certificate_course FOREIGN KEY (course_code) REFERENCES training_course(course_code) ON DELETE SET NULL
);

-- =============================================================================
-- 11. ROLE & PERMISSION SYSTEM TABLES (Hệ thống Phân Quyền)
-- =============================================================================
CREATE TABLE role (
                      role_code VARCHAR(50) PRIMARY KEY,
                      role_name VARCHAR(100) NOT NULL
);

CREATE TABLE permission (
                            permission_code VARCHAR(100) PRIMARY KEY,
                            permission_name VARCHAR(150) NOT NULL
);

CREATE TABLE permission_role (
                                 role_code VARCHAR(50) NOT NULL,
                                 permission_code VARCHAR(100) NOT NULL,
                                 PRIMARY KEY (role_code, permission_code),
                                 CONSTRAINT fk_pr_role_code FOREIGN KEY (role_code) REFERENCES role(role_code) ON DELETE CASCADE,
                                 CONSTRAINT fk_pr_permission_code FOREIGN KEY (permission_code) REFERENCES permission(permission_code) ON DELETE CASCADE
);

-- =============================================================================
-- 12. ACCOUNT TABLE (Tài khoản người dùng - Hỗ trợ bảo mật OAuth2)
-- =============================================================================
CREATE TABLE account (
                         username VARCHAR(50) PRIMARY KEY,
                         password VARCHAR(255), -- Cho phép NULL để phục vụ tài khoản thuần OAuth2 (Đăng nhập qua Google)
                         status BOOLEAN DEFAULT TRUE,
                         leader_id VARCHAR(20) UNIQUE, -- Khớp 1-1 với Hồ sơ nhân sự Huynh trưởng
                         role_code VARCHAR(50) NOT NULL,
                         auth_provider VARCHAR(20) DEFAULT 'LOCAL' CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'FACEBOOK')), -- Phương thức xác thực
                         provider_id VARCHAR(100) UNIQUE, -- Lưu Unique ID (sub claim) từ Google Auth
                         avatar_url VARCHAR(255), -- Ảnh đại diện đồng bộ từ tài khoản MXH
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         CONSTRAINT fk_account_leader FOREIGN KEY (leader_id) REFERENCES leader(leader_code) ON DELETE CASCADE,
                         CONSTRAINT fk_account_role FOREIGN KEY (role_code) REFERENCES role(role_code) ON DELETE RESTRICT
);

-- =============================================================================
-- 13. SYSTEM LOG TABLE (Nhật Ký Hệ Thống / Audit Trail)
-- =============================================================================
CREATE TABLE system_log (
                            id BIGSERIAL PRIMARY KEY,
                            username VARCHAR(50),
                            action VARCHAR(100) NOT NULL, -- e.g., 'CREATE_LEADER', 'APPROVE_CERTIFICATE'
                            affected_table VARCHAR(50),  -- Tên bảng chịu tác động
                            old_data TEXT,               -- Dữ liệu JSON cũ trước khi thay đổi
                            new_data TEXT,               -- Dữ liệu JSON mới sau khi thay đổi
                            ip_address VARCHAR(45),
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 14. OPTIMIZATION INDEXES (Tạo chỉ mục tối ưu hóa tốc độ tìm kiếm)
-- =============================================================================
CREATE INDEX idx_leader_parish ON leader(parish_code);
CREATE INDEX idx_leader_level ON leader(leader_level);
CREATE INDEX idx_participation_status ON course_participation(status);
CREATE INDEX idx_account_oauth ON account(auth_provider, provider_id);
-- ============================================================================
-- V116: Thành phần điểm theo từng khóa huấn luyện.
--       score_component_template trở thành thư viện mẫu theo loại khóa; mỗi khóa giữ bản sao
--       (điểm tối đa, hệ số, cờ tính tổng kết, thứ tự) trong training_course_score_component.
--       Nhờ vậy khóa mới có thể bỏ một thành phần mà không ảnh hưởng khóa cũ.
-- ============================================================================

CREATE TABLE IF NOT EXISTS training_course_score_component (
    id               VARCHAR(36) PRIMARY KEY,
    course_code      VARCHAR(255) NOT NULL,
    component_id     VARCHAR(36)  NOT NULL,
    max_score        NUMERIC(10, 2) NOT NULL,
    coefficient      NUMERIC(10, 2) NOT NULL,
    include_in_final BOOLEAN NOT NULL DEFAULT TRUE,
    display_order    INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(100),
    updated_at       TIMESTAMP WITH TIME ZONE,
    updated_by       VARCHAR(100),
    status           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_tcsc_course FOREIGN KEY (course_code) REFERENCES training_course (course_code) ON DELETE CASCADE,
    CONSTRAINT fk_tcsc_component FOREIGN KEY (component_id) REFERENCES score_component_template (id) ON DELETE RESTRICT,
    CONSTRAINT chk_tcsc_coefficient CHECK (coefficient > 0),
    CONSTRAINT uk_tcsc_course_component UNIQUE (course_code, component_id)
);

CREATE INDEX IF NOT EXISTS idx_tcsc_course_code ON training_course_score_component (course_code);
CREATE INDEX IF NOT EXISTS idx_tcsc_component_id ON training_course_score_component (component_id);

-- Backfill 1: mọi khóa nhận các thành phần đang hoạt động cùng loại khóa và cùng đơn vị sở hữu (giữ hành vi cũ).
INSERT INTO training_course_score_component (id, course_code, component_id, max_score, coefficient, include_in_final, display_order,
                                             created_at, created_by, status)
SELECT (substr(md5(tc.course_code || ':' || sc.id), 1, 8) || '-' || substr(md5(tc.course_code || ':' || sc.id), 9, 4) || '-' ||
        substr(md5(tc.course_code || ':' || sc.id), 13, 4) || '-' || substr(md5(tc.course_code || ':' || sc.id), 17, 4) || '-' ||
        substr(md5(tc.course_code || ':' || sc.id), 21, 12)),
       tc.course_code, sc.id, sc.max_score, sc.coefficient, sc.include_in_final, sc.display_order,
       CURRENT_TIMESTAMP, 'SYSTEM', TRUE
FROM training_course tc
         JOIN score_component_template sc
              ON sc.course_type_code = tc.course_type_code
                 AND sc.status = TRUE
                 AND ((tc.deanery_id IS NOT NULL AND sc.deanery_id = tc.deanery_id)
                     OR (tc.deanery_id IS NULL AND sc.diocese_id = tc.diocese_id))
ON CONFLICT (course_code, component_id) DO NOTHING;

-- Backfill 2: thành phần đã có điểm trong khóa nhưng đã bị tắt vẫn phải giữ trong khóa đó.
INSERT INTO training_course_score_component (id, course_code, component_id, max_score, coefficient, include_in_final, display_order,
                                             created_at, created_by, status)
SELECT DISTINCT ON (cp.course_id, s.component_id)
       (substr(md5(cp.course_id || ':' || s.component_id), 1, 8) || '-' || substr(md5(cp.course_id || ':' || s.component_id), 9, 4) || '-' ||
        substr(md5(cp.course_id || ':' || s.component_id), 13, 4) || '-' || substr(md5(cp.course_id || ':' || s.component_id), 17, 4) || '-' ||
        substr(md5(cp.course_id || ':' || s.component_id), 21, 12)),
       cp.course_id, sc.id, sc.max_score, sc.coefficient, sc.include_in_final, sc.display_order,
       CURRENT_TIMESTAMP, 'SYSTEM', TRUE
FROM course_participation_component_score s
         JOIN course_participation cp ON cp.id = s.participation_id
         JOIN score_component_template sc ON sc.id = s.component_id
ON CONFLICT (course_code, component_id) DO NOTHING;

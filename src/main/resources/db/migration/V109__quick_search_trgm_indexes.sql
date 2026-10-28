-- Tìm kiếm nhanh ở header: không phân biệt dấu/hoa thường và chịu gõ sai nhẹ bằng unaccent + pg_trgm.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- unaccent() chỉ STABLE nên không dùng trực tiếp trong index; bọc lại thành IMMUTABLE (từ điển cố định).
CREATE OR REPLACE FUNCTION immutable_unaccent(value TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
STRICT
AS $$
    SELECT lower(public.unaccent('public.unaccent'::regdictionary, replace(replace(value, 'đ', 'd'), 'Đ', 'd')))
$$;

CREATE INDEX IF NOT EXISTS idx_leader_search_name_trgm
    ON leader USING gin (immutable_unaccent(holy_name || ' ' || last_name || ' ' || first_name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_leader_search_email_trgm
    ON leader USING gin (immutable_unaccent(email) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_leader_search_phone_trgm
    ON leader USING gin (phone_number gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_diocese_search_name_trgm
    ON diocese USING gin (immutable_unaccent(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_deanery_search_name_trgm
    ON deanery USING gin (immutable_unaccent(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_parish_search_name_trgm
    ON parish USING gin (immutable_unaccent(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_training_course_search_name_trgm
    ON training_course USING gin (immutable_unaccent(course_name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_training_course_search_code_trgm
    ON training_course USING gin (immutable_unaccent(course_code) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_certificate_search_code_trgm
    ON certificate USING gin (immutable_unaccent(certificate_code) gin_trgm_ops);

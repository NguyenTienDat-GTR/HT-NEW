-- Map a legacy course only when its owning scope has exactly one active
-- formula. Ambiguous or missing ownership requires an explicit business decision.
WITH unique_scope_formula AS (
    SELECT unit_type, diocese_id, deanery_id, min(id) AS formula_id
    FROM score_formula_template
    WHERE status = TRUE
    GROUP BY unit_type, diocese_id, deanery_id
    HAVING count(*) = 1
)
UPDATE training_course course
SET score_formula_id = formula.formula_id
FROM unique_scope_formula formula
WHERE course.score_formula_id IS NULL
  AND ((course.deanery_id IS NOT NULL AND formula.unit_type = 'DEANERY' AND formula.deanery_id = course.deanery_id)
    OR (course.deanery_id IS NULL AND course.diocese_id IS NOT NULL AND formula.unit_type = 'DIOCESE' AND formula.diocese_id = course.diocese_id));

DO $$
DECLARE missing_courses TEXT;
BEGIN
    SELECT string_agg(course_code, ', ' ORDER BY course_code) INTO missing_courses
    FROM training_course WHERE score_formula_id IS NULL;
    IF missing_courses IS NOT NULL THEN
        RAISE EXCEPTION 'Missing approved score formula for legacy training courses: %', missing_courses;
    END IF;
END $$;

ALTER TABLE training_course DROP CONSTRAINT IF EXISTS chk_training_course_score_formula_required;
ALTER TABLE training_course ALTER COLUMN score_formula_id SET NOT NULL;

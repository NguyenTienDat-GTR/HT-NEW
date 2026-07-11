-- New or changed courses cannot be formula-less. Existing legacy rows remain
-- visible for explicit business-data remediation; assigning an arbitrary
-- formula would change scoring semantics.
ALTER TABLE training_course DROP CONSTRAINT IF EXISTS fk_training_course_score_formula_id;
ALTER TABLE training_course ADD CONSTRAINT fk_training_course_score_formula_id
    FOREIGN KEY (score_formula_id) REFERENCES score_formula_template(id) ON DELETE RESTRICT;
ALTER TABLE training_course ADD CONSTRAINT chk_training_course_score_formula_required
    CHECK (score_formula_id IS NOT NULL) NOT VALID;

DO $$
DECLARE duplicate_participations TEXT;
DECLARE duplicate_certificates TEXT;
BEGIN
    SELECT string_agg(leader_id::text || ':' || course_id, ', ' ORDER BY leader_id::text, course_id)
    INTO duplicate_participations
    FROM (SELECT leader_id, course_id FROM course_participation WHERE leader_id IS NOT NULL AND course_id IS NOT NULL GROUP BY leader_id, course_id HAVING count(*) > 1) duplicate_rows;
    IF duplicate_participations IS NOT NULL THEN
        RAISE EXCEPTION 'Resolve duplicate course participation pairs before migration: %', duplicate_participations;
    END IF;
    SELECT string_agg(leader_id::text || ':' || course_code, ', ' ORDER BY leader_id::text, course_code)
    INTO duplicate_certificates
    FROM (SELECT leader_id, course_code FROM certificate WHERE leader_id IS NOT NULL AND course_code IS NOT NULL GROUP BY leader_id, course_code HAVING count(*) > 1) duplicate_rows;
    IF duplicate_certificates IS NOT NULL THEN
        RAISE EXCEPTION 'Resolve duplicate certificate pairs before migration: %', duplicate_certificates;
    END IF;
END $$;

ALTER TABLE course_participation ADD CONSTRAINT uk_course_participation_leader_course UNIQUE (leader_id, course_id);
ALTER TABLE certificate ADD CONSTRAINT uk_certificate_leader_course UNIQUE (leader_id, course_code);

ALTER TABLE leader
    ADD COLUMN IF NOT EXISTS image_url TEXT;

UPDATE leader l
SET image_url = a.avatar_url
FROM account a
WHERE a.leader_id = l.id
  AND a.avatar_url IS NOT NULL
  AND l.image_url IS NULL;

ALTER TABLE account
    DROP COLUMN IF EXISTS avatar_url;

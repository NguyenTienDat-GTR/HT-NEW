-- Bỏ trường "Tên chứng nhận" (certificateName) khỏi bố cục các cấu hình chứng nhận đã lưu.
UPDATE certificate_config
SET field_layout = COALESCE((
    SELECT jsonb_agg(item ORDER BY ord)
    FROM jsonb_array_elements(field_layout) WITH ORDINALITY AS t(item, ord)
    WHERE item ->> 'fieldKey' IS DISTINCT FROM 'certificateName'
), '[]'::jsonb)
WHERE jsonb_typeof(field_layout) = 'array'
  AND field_layout @> '[{"fieldKey": "certificateName"}]'::jsonb;

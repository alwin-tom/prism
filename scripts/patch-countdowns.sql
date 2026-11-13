UPDATE event_page_documents d
SET document_json = replace(
      document_json::text,
      '"props": {"label": "", "target": ""}',
      '"props": {"label": "Until the day", "target": "2027-10-10T11:11:00"}'
    )::jsonb,
    updated_at = now()
FROM events e
WHERE e.id = d.event_id
  AND e.slug = 'ajayweds';

UPDATE event_page_documents d
SET document_json = replace(
      document_json::text,
      '"props": {"label": "", "target": ""}',
      '"props": {"label": "Until the day", "target": "2027-10-10T09:00:00"}'
    )::jsonb,
    updated_at = now()
FROM events e
WHERE e.id = d.event_id
  AND e.slug = 'test1';

-- Fix cream-on-cream countdown text so it is visible in the editor
UPDATE event_page_documents d
SET document_json = replace(
      document_json::text,
      '"id": "countdown-clock", "name": "Countdown clock", "type": "COUNTDOWN", "props": {"label": "Until the day", "target": "2027-10-10T11:11:00"}, "styles": {"color": "#f7f4ef"',
      '"id": "countdown-clock", "name": "Countdown clock", "type": "COUNTDOWN", "props": {"label": "Until the day", "target": "2027-10-10T11:11:00"}, "styles": {"color": "#1c1a17"'
    )::jsonb,
    updated_at = now()
FROM events e
WHERE e.id = d.event_id
  AND e.slug = 'ajayweds';

SELECT e.slug, c.elem -> 'props' AS props, c.elem -> 'styles' ->> 'color' AS color
FROM events e
JOIN event_page_documents d ON d.event_id = e.id
CROSS JOIN LATERAL (
  SELECT jsonb_array_elements(jsonb_path_query_array(d.document_json, '$.**')) AS elem
) c
WHERE c.elem ->> 'type' = 'COUNTDOWN'
  AND e.slug IN ('ajayweds', 'test1');

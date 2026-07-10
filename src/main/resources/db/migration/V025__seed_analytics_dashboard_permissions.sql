INSERT INTO permission_module (module_code, module_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES ('analytics', 'Analytics', 'Dashboard analytics', 90, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (module_code) DO UPDATE SET module_name=EXCLUDED.module_name, description=EXCLUDED.description, status=TRUE, updated_at=CURRENT_TIMESTAMP, updated_by='SYSTEM';

INSERT INTO permission_resource (resource_code, module_code, resource_name, description, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES ('dashboard', 'analytics', 'Dashboard', 'Scoped dashboard analytics', 10, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM')
ON CONFLICT (resource_code) DO UPDATE SET module_code=EXCLUDED.module_code, resource_name=EXCLUDED.resource_name, description=EXCLUDED.description, status=TRUE, updated_at=CURRENT_TIMESTAMP, updated_by='SYSTEM';

INSERT INTO permission (permission_code, permission_name, module_code, resource_code, action_code, scope_code, description, is_system, display_order, status, created_at, created_by, updated_at, updated_by)
VALUES
('analytics.dashboard.read.all','Read system overview dashboard','analytics','dashboard','read','all','Only four system-wide organization KPIs',TRUE,10,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
('analytics.dashboard.read.diocese','Read diocese dashboard','analytics','dashboard','read','diocese','Read dashboard inside own diocese',TRUE,20,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
('analytics.dashboard.read.deanery','Read deanery dashboard','analytics','dashboard','read','deanery','Read dashboard inside own deanery',TRUE,30,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM'),
('analytics.dashboard.read.parish','Read parish dashboard','analytics','dashboard','read','parish','Read dashboard inside own parish',TRUE,40,TRUE,CURRENT_TIMESTAMP,'SYSTEM',CURRENT_TIMESTAMP,'SYSTEM')
ON CONFLICT (permission_code) DO UPDATE SET permission_name=EXCLUDED.permission_name, module_code=EXCLUDED.module_code, resource_code=EXCLUDED.resource_code, action_code=EXCLUDED.action_code, scope_code=EXCLUDED.scope_code, description=EXCLUDED.description, status=TRUE, updated_at=CURRENT_TIMESTAMP, updated_by='SYSTEM';

INSERT INTO role_permission (id, role_code, permission_code, effect, assigned_by, assigned_at, status, created_at, created_by, updated_at, updated_by)
SELECT (substr(md5(r.role_code || ':' || p.permission_code),1,8)||'-'||substr(md5(r.role_code || ':' || p.permission_code),9,4)||'-'||substr(md5(r.role_code || ':' || p.permission_code),13,4)||'-'||substr(md5(r.role_code || ':' || p.permission_code),17,4)||'-'||substr(md5(r.role_code || ':' || p.permission_code),21,12))::uuid, r.role_code, p.permission_code, 'ALLOW', 'SYSTEM', CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'
FROM role r JOIN permission p ON (r.role_code='SUPER_ADMIN' AND p.permission_code='analytics.dashboard.read.all') OR (r.role_code='ADMIN_DIOCESE' AND p.permission_code='analytics.dashboard.read.diocese') OR (r.role_code='ADMIN_DEANERY' AND p.permission_code='analytics.dashboard.read.deanery') OR (r.role_code='ADMIN_PARISH' AND p.permission_code='analytics.dashboard.read.parish')
WHERE NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_code=r.role_code AND rp.permission_code=p.permission_code);

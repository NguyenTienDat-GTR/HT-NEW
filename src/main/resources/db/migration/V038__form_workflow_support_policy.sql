INSERT INTO api_permission_policy (
    id,
    http_method,
    path_pattern,
    handler_name,
    permission_code,
    scope_strategy,
    is_system,
    created_at,
    created_by,
    updated_at,
    updated_by,
    status
)
SELECT
    source.id::uuid,
    source.http_method,
    source.path_pattern,
    source.handler_name,
    source.permission_code,
    source.scope_strategy,
    TRUE,
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    TRUE
FROM (
    VALUES
        ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d438001', 'GET', '/api/analytics/work-queue', 'AnalyticsController.workQueue', 'analytics.dashboard.read.all', 'ALL'),
        ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d438002', 'GET', '/api/analytics/work-queue', 'AnalyticsController.workQueue', 'analytics.dashboard.read.diocese', 'DIOCESE'),
        ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d438003', 'GET', '/api/analytics/work-queue', 'AnalyticsController.workQueue', 'analytics.dashboard.read.deanery', 'DEANERY'),
        ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d438004', 'GET', '/api/analytics/work-queue', 'AnalyticsController.workQueue', 'analytics.dashboard.read.parish', 'PARISH'),
        ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d438005', 'GET', '/api/leaders/{id}/rank-history', 'LeaderController.rankHistory', 'organization.leader.read.all', 'ALL'),
        ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d438006', 'GET', '/api/leaders/{id}/rank-history', 'LeaderController.rankHistory', 'organization.leader.read.diocese', 'DIOCESE'),
        ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d438007', 'GET', '/api/leaders/{id}/rank-history', 'LeaderController.rankHistory', 'organization.leader.read.deanery', 'DEANERY'),
        ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d438008', 'GET', '/api/leaders/{id}/rank-history', 'LeaderController.rankHistory', 'organization.leader.read.parish', 'PARISH'),
        ('a5a0d4a7-5d6a-4fd9-99f2-2d3a2d438009', 'GET', '/api/system/permissions/taxonomy', 'PermissionController.taxonomy', 'system.permission.read.all', 'ALL')
) AS source(id, http_method, path_pattern, handler_name, permission_code, scope_strategy)
WHERE EXISTS (
    SELECT 1
    FROM permission p
    WHERE p.permission_code = source.permission_code
)
ON CONFLICT (http_method, path_pattern, permission_code) DO NOTHING;

INSERT INTO user_roles (user_id, role_code)
SELECT DISTINCT da.user_id, 'DEPARTMENT_ADMIN'
FROM department_admins da
WHERE NOT EXISTS (
    SELECT 1
    FROM user_roles ur
    WHERE ur.user_id = da.user_id
      AND ur.role_code = 'DEPARTMENT_ADMIN'
);

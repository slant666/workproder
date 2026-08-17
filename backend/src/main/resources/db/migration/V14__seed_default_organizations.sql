INSERT IGNORE INTO companies (name, enabled)
VALUES ('默认公司', TRUE);

INSERT IGNORE INTO departments (company_id, name, enabled)
SELECT id, '财务部', TRUE FROM companies WHERE name = '默认公司';

INSERT IGNORE INTO departments (company_id, name, enabled)
SELECT id, '人事部', TRUE FROM companies WHERE name = '默认公司';

INSERT IGNORE INTO departments (company_id, name, enabled)
SELECT id, '技术部', TRUE FROM companies WHERE name = '默认公司';

INSERT IGNORE INTO teams (company_id, department_id, name, enabled)
SELECT c.id, d.id, '财务一组', TRUE
FROM companies c
JOIN departments d ON d.company_id = c.id AND d.name = '财务部'
WHERE c.name = '默认公司';

INSERT IGNORE INTO teams (company_id, department_id, name, enabled)
SELECT c.id, d.id, '技术支持组', TRUE
FROM companies c
JOIN departments d ON d.company_id = c.id AND d.name = '技术部'
WHERE c.name = '默认公司';

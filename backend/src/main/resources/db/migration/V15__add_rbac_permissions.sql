CREATE TABLE roles (
    code VARCHAR(60) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE permissions (
    code VARCHAR(120) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(60) NOT NULL,
    description VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role_permissions (
    role_code VARCHAR(60) NOT NULL,
    permission_code VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_code, permission_code),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_code) REFERENCES roles(code),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code) REFERENCES permissions(code)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_code VARCHAR(60) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_code) REFERENCES roles(code)
);

INSERT INTO roles (code, name, description) VALUES
('USER', '普通用户', '创建和查看自己的工单'),
('CUSTOMER_SERVICE', '客服人员', '查看和处理客服工单'),
('DEPARTMENT_ADMIN', '部门管理员', '查看和管理本部门工单'),
('ADMIN', '系统管理员', '管理整个系统'),
('AUDITOR', '审计人员', '只读查看工单、统计和操作记录');

INSERT INTO permissions (code, name, category, description) VALUES
('ticket:create', '创建工单', 'ticket', '创建自己的工单'),
('ticket:view', '查看工单', 'ticket', '查看有数据权限的工单'),
('ticket:update', '修改工单', 'ticket', '修改待处理工单'),
('ticket:cancel', '取消工单', 'ticket', '取消自己创建的待处理工单'),
('ticket:comment', '评论工单', 'ticket', '评论可见工单'),
('ticket:attachment', '管理附件', 'ticket', '上传和下载可见工单附件'),
('ticket:assign', '分配工单', 'ticket', '为有权限的工单分配处理人'),
('ticket:accept', '接单', 'ticket', '接收有权限的待处理工单'),
('ticket:submit', '提交确认', 'ticket', '提交处理完成等待创建人确认'),
('ticket:return', '退回处理', 'ticket', '将待确认工单退回处理中'),
('ticket:confirm', '确认完成', 'ticket', '确认自己创建的工单完成'),
('ticket:log:view', '查看操作记录', 'ticket', '查看工单操作记录'),
('user:view', '查看用户', 'user', '查看用户列表'),
('user:update', '修改用户', 'user', '修改用户资料、角色或组织'),
('user:disable', '禁用用户', 'user', '启用或禁用用户'),
('role:manage', '管理角色权限', 'role', '维护角色和权限配置'),
('organization:manage', '管理组织', 'organization', '维护公司、部门和团队'),
('statistics:view', '查看统计', 'statistics', '查看工单统计数据');

INSERT INTO role_permissions (role_code, permission_code) VALUES
('USER', 'ticket:create'),
('USER', 'ticket:view'),
('USER', 'ticket:update'),
('USER', 'ticket:cancel'),
('USER', 'ticket:comment'),
('USER', 'ticket:attachment'),
('USER', 'ticket:log:view'),
('USER', 'ticket:confirm'),
('CUSTOMER_SERVICE', 'ticket:view'),
('CUSTOMER_SERVICE', 'ticket:comment'),
('CUSTOMER_SERVICE', 'ticket:attachment'),
('CUSTOMER_SERVICE', 'ticket:accept'),
('CUSTOMER_SERVICE', 'ticket:submit'),
('CUSTOMER_SERVICE', 'ticket:log:view'),
('DEPARTMENT_ADMIN', 'ticket:view'),
('DEPARTMENT_ADMIN', 'ticket:comment'),
('DEPARTMENT_ADMIN', 'ticket:attachment'),
('DEPARTMENT_ADMIN', 'ticket:assign'),
('DEPARTMENT_ADMIN', 'ticket:accept'),
('DEPARTMENT_ADMIN', 'ticket:submit'),
('DEPARTMENT_ADMIN', 'ticket:return'),
('DEPARTMENT_ADMIN', 'ticket:log:view'),
('DEPARTMENT_ADMIN', 'statistics:view'),
('ADMIN', 'ticket:create'),
('ADMIN', 'ticket:view'),
('ADMIN', 'ticket:update'),
('ADMIN', 'ticket:cancel'),
('ADMIN', 'ticket:comment'),
('ADMIN', 'ticket:attachment'),
('ADMIN', 'ticket:assign'),
('ADMIN', 'ticket:accept'),
('ADMIN', 'ticket:submit'),
('ADMIN', 'ticket:return'),
('ADMIN', 'ticket:confirm'),
('ADMIN', 'ticket:log:view'),
('ADMIN', 'user:view'),
('ADMIN', 'user:update'),
('ADMIN', 'user:disable'),
('ADMIN', 'role:manage'),
('ADMIN', 'organization:manage'),
('ADMIN', 'statistics:view'),
('AUDITOR', 'ticket:view'),
('AUDITOR', 'ticket:log:view'),
('AUDITOR', 'statistics:view');

INSERT INTO user_roles (user_id, role_code)
SELECT id, role FROM users
WHERE role IN ('USER', 'ADMIN');

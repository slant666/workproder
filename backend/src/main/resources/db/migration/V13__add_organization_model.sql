CREATE TABLE companies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE departments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_departments_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT uk_departments_company_name UNIQUE (company_id, name)
);

CREATE TABLE teams (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_teams_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_teams_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT uk_teams_department_name UNIQUE (department_id, name)
);

ALTER TABLE users
    ADD COLUMN company_id BIGINT NULL,
    ADD COLUMN department_id BIGINT NULL,
    ADD COLUMN team_id BIGINT NULL,
    ADD COLUMN org_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id),
    ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments(id),
    ADD CONSTRAINT fk_users_team FOREIGN KEY (team_id) REFERENCES teams(id);

ALTER TABLE work_orders
    ADD COLUMN company_id BIGINT NULL,
    ADD COLUMN department_id BIGINT NULL,
    ADD COLUMN team_id BIGINT NULL,
    ADD CONSTRAINT fk_work_orders_company FOREIGN KEY (company_id) REFERENCES companies(id),
    ADD CONSTRAINT fk_work_orders_department FOREIGN KEY (department_id) REFERENCES departments(id),
    ADD CONSTRAINT fk_work_orders_team FOREIGN KEY (team_id) REFERENCES teams(id);

CREATE TABLE department_admins (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_department_admins_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_department_admins_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT uk_department_admins_user_department UNIQUE (user_id, department_id)
);

CREATE INDEX idx_departments_company_enabled ON departments(company_id, enabled, id);
CREATE INDEX idx_teams_department_enabled ON teams(department_id, enabled, id);
CREATE INDEX idx_users_department_confirmed ON users(department_id, org_confirmed, enabled, id);
CREATE INDEX idx_users_team_id ON users(team_id);
CREATE INDEX idx_work_orders_department_created ON work_orders(department_id, created_at, id);
CREATE INDEX idx_work_orders_team_id ON work_orders(team_id);
CREATE INDEX idx_department_admins_department_user ON department_admins(department_id, user_id);

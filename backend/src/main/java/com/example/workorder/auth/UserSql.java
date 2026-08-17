package com.example.workorder.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

final class UserSql {

    static final String CURRENT_USER_SELECT = """
            SELECT u.id, u.username, u.nickname, u.role, u.enabled,
                   u.company_id, c.name AS company_name,
                   u.department_id, d.name AS department_name,
                   u.team_id, t.name AS team_name,
                   u.org_confirmed
            FROM users u
            LEFT JOIN companies c ON c.id = u.company_id
            LEFT JOIN departments d ON d.id = u.department_id
            LEFT JOIN teams t ON t.id = u.team_id
            """;

    static final String REGISTER_SELECT = CURRENT_USER_SELECT;

    private UserSql() {
    }

    static CurrentUser mapCurrentUser(ResultSet rs, int rowNum) throws SQLException {
        return new CurrentUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("role"),
                Set.of(rs.getString("role")),
                Set.of(),
                (Long) rs.getObject("company_id"),
                rs.getString("company_name"),
                (Long) rs.getObject("department_id"),
                rs.getString("department_name"),
                (Long) rs.getObject("team_id"),
                rs.getString("team_name"),
                rs.getBoolean("org_confirmed"));
    }

    static RegisterResponse mapRegister(ResultSet rs, int rowNum) throws SQLException {
        return new RegisterResponse(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("role"),
                (Long) rs.getObject("company_id"),
                rs.getString("company_name"),
                (Long) rs.getObject("department_id"),
                rs.getString("department_name"),
                (Long) rs.getObject("team_id"),
                rs.getString("team_name"),
                rs.getBoolean("org_confirmed"));
    }
}

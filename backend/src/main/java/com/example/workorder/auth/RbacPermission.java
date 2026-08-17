package com.example.workorder.auth;

public final class RbacPermission {

    public static final String TICKET_CREATE = "ticket:create";
    public static final String TICKET_VIEW = "ticket:view";
    public static final String TICKET_UPDATE = "ticket:update";
    public static final String TICKET_CANCEL = "ticket:cancel";
    public static final String TICKET_COMMENT = "ticket:comment";
    public static final String TICKET_ATTACHMENT = "ticket:attachment";
    public static final String TICKET_ASSIGN = "ticket:assign";
    public static final String TICKET_ACCEPT = "ticket:accept";
    public static final String TICKET_SUBMIT = "ticket:submit";
    public static final String TICKET_RETURN = "ticket:return";
    public static final String TICKET_CONFIRM = "ticket:confirm";
    public static final String TICKET_LOG_VIEW = "ticket:log:view";
    public static final String USER_VIEW = "user:view";
    public static final String USER_UPDATE = "user:update";
    public static final String USER_DISABLE = "user:disable";
    public static final String ORGANIZATION_MANAGE = "organization:manage";
    public static final String STATISTICS_VIEW = "statistics:view";

    private RbacPermission() {
    }
}

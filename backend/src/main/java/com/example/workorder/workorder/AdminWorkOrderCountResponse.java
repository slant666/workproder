package com.example.workorder.workorder;

public record AdminWorkOrderCountResponse(
        Long handlerId,
        String handlerUsername,
        String handlerNickname,
        long count) {
}
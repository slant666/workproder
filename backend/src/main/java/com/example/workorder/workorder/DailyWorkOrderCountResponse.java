package com.example.workorder.workorder;

import java.time.LocalDate;

public record DailyWorkOrderCountResponse(LocalDate date, long count) {
}
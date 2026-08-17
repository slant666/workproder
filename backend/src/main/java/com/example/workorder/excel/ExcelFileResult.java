package com.example.workorder.excel;

import java.nio.file.Path;

public record ExcelFileResult(Long jobId, Path path, String filename) {
}

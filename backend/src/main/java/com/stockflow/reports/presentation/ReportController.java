package com.stockflow.reports.presentation;

import com.stockflow.reports.application.ReportService;
import com.stockflow.reports.domain.ReportSummary;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    ReportSummary summary(
            @RequestParam(defaultValue = "TODAY") @Pattern(regexp = "TODAY|WEEK|MONTH|YEAR")
                    String range) {
        return service.summary(range);
    }
}

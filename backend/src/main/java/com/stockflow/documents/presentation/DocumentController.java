package com.stockflow.documents.presentation;

import com.stockflow.documents.application.DocumentService;
import com.stockflow.documents.domain.DocumentIssuer;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/sales/{saleId}")
    DocumentIssuer.Receipt receipt(@PathVariable UUID saleId) {
        return service.receipt(saleId);
    }
}

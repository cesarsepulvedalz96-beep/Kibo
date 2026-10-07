package com.stockflow.documents.domain;

import com.stockflow.sales.domain.*;
import java.util.List;

public interface DocumentIssuer {
    Receipt issue(
            String businessName, String taxId, String timezone, Sale sale, List<SaleItem> items);

    record Receipt(
            String documentType,
            boolean fiscalDocument,
            String businessName,
            String taxId,
            String timezone,
            Sale sale,
            List<SaleItem> items) {}
}

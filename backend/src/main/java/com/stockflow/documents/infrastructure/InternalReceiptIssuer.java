package com.stockflow.documents.infrastructure;

import com.stockflow.documents.domain.DocumentIssuer;
import com.stockflow.sales.domain.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InternalReceiptIssuer implements DocumentIssuer {
    public Receipt issue(
            String businessName, String taxId, String timezone, Sale sale, List<SaleItem> items) {
        return new Receipt(
                "INTERNAL_RECEIPT", false, businessName, taxId, timezone, sale, List.copyOf(items));
    }
}

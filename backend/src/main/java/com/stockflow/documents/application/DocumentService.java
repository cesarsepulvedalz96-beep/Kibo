package com.stockflow.documents.application;

import com.stockflow.auth.application.CurrentIdentity;
import com.stockflow.businesses.domain.BusinessRepository;
import com.stockflow.documents.domain.DocumentIssuer;
import com.stockflow.sales.application.SaleService;
import com.stockflow.settings.domain.SettingsRepository;
import com.stockflow.shared.domain.BusinessException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {
    private final DocumentIssuer issuer;
    private final SaleService sales;
    private final BusinessRepository businesses;
    private final SettingsRepository settings;
    private final CurrentIdentity current;

    public DocumentService(
            DocumentIssuer issuer,
            SaleService sales,
            BusinessRepository businesses,
            SettingsRepository settings,
            CurrentIdentity current) {
        this.issuer = issuer;
        this.sales = sales;
        this.businesses = businesses;
        this.settings = settings;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public DocumentIssuer.Receipt receipt(UUID saleId) {
        var config = settings.get(current.businessId());
        if (!config.generateReceipt()) throw new BusinessException("RECEIPTS_DISABLED", 409);
        var details = sales.details(saleId);
        var business =
                businesses.find(current.businessId()).orElseThrow(BusinessException::notFound);
        return issuer.issue(
                business.name(),
                business.taxId(),
                config.timezone(),
                details.sale(),
                details.items());
    }
}

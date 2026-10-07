package com.stockflow.documents.domain;

/** Future boundary for certified electronic documents. No fiscal document is issued by the MVP. */
public interface SiiDocumentAdapter {
    ElectronicDocument issue(DocumentIssuer.Receipt receipt);

    record ElectronicDocument(String folio, String status, String documentKey) {}
}

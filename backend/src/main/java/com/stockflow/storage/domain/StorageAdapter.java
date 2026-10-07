package com.stockflow.storage.domain;

import java.net.URI;

public interface StorageAdapter {
    void put(String key, byte[] bytes, String contentType);

    URI signedRead(String key);
}

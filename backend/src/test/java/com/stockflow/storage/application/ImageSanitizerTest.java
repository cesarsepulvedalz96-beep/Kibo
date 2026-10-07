package com.stockflow.storage.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageSanitizerTest {
    @Test
    void rejectsSvgAndArbitraryContentEvenWhenClaimedToBeAnImage() {
        assertThatThrownBy(
                        () ->
                                new ImageSanitizer()
                                        .sanitize(
                                                "<svg onload='alert(1)'/>"
                                                        .getBytes(StandardCharsets.UTF_8)))
                .hasMessage("INVALID_IMAGE");
    }
}

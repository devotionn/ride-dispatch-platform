package com.funccrypto.ridedispatch.driver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DriverQrIntegrationTest {

    @Test
    void rendersDriverOnlyLandingPathAsPngDataUrl() {
        String dataUrl = new QrCodeRenderer().dataUrl("/ride/d/QRD101");

        assertThat(dataUrl).startsWith("data:image/png;base64,");
        assertThat(dataUrl.length()).isGreaterThan(500);
    }
}

package com.funccrypto.ridedispatch.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class BrandLogoStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndLoadsPngWithControlledName() {
        BrandLogoStorage storage = new BrandLogoStorage(tempDir.toString());
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

        BrandLogoStorage.StoredLogo stored = storage.store(new MockMultipartFile(
                "file", "logo.png", "image/png", png));

        assertThat(stored.url()).startsWith("/api/v1/public/brand/logo/");
        assertThat(stored.filename()).endsWith(".png");
        assertThat(storage.load(stored.filename()).resource().exists()).isTrue();
    }

    @Test
    void rejectsSpoofedOrOversizedFiles() {
        BrandLogoStorage storage = new BrandLogoStorage(tempDir.toString());

        assertThatThrownBy(() -> storage.store(new MockMultipartFile(
                "file", "logo.png", "image/png", new byte[] {1, 2, 3})))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("有效");

        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> storage.store(new MockMultipartFile(
                "file", "logo.png", "image/png", oversized)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 MB");
    }
}

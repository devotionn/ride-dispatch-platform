package com.funccrypto.ridedispatch.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PlaceCatalogServiceIntegrationTest {

    @Autowired PlaceCatalogService service;
    @Autowired PlaceCatalogRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createsTextOnlyPlaceAndSearchesNameAddressAndAlias() {
        service.create(command("扬州东站", "江苏省扬州市广陵区烟花三月路", null, null, "东站,高铁站"));

        assertThat(service.search("扬州", 10)).hasSize(1);
        assertThat(service.search("烟花", 10)).hasSize(1);
        assertThat(service.search("高铁", 10)).hasSize(1);
    }

    @Test
    void excludesDisabledPlacesAndRequiresCompleteCoordinatePair() {
        PlaceCatalogEntity place = service.create(command("瘦西湖", "扬州市邗江区大虹桥路", new BigDecimal("32.401"), new BigDecimal("119.414"), null));
        service.setEnabled(place.getId(), false);

        assertThat(service.search("瘦西", 10)).isEmpty();
        assertThatThrownBy(() -> service.create(command("不完整", "测试地址", new BigDecimal("32.4"), null, null)))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getCode()).isEqualTo("PLACE_COORDINATES_INCOMPLETE"));
    }

    private PlaceCatalogService.Command command(
            String name, String address, BigDecimal latitude, BigDecimal longitude, String aliases) {
        return new PlaceCatalogService.Command(name, address, latitude, longitude, "扬州", "广陵", "交通", aliases);
    }
}

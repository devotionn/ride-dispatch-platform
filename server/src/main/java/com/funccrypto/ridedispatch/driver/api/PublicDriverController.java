package com.funccrypto.ridedispatch.driver.api;

import com.funccrypto.ridedispatch.driver.PublicDriverService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/drivers")
public class PublicDriverController {

    private final PublicDriverService service;

    public PublicDriverController(PublicDriverService service) {
        this.service = service;
    }

    @GetMapping("/{shortCode}")
    PublicDriverService.PublicDriverView get(@PathVariable String shortCode) {
        return service.getByQrShortCode(shortCode);
    }
}

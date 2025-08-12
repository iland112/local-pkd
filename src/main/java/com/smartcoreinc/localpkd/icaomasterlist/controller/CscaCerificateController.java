package com.smartcoreinc.localpkd.icaomasterlist.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.smartcoreinc.localpkd.icaomasterlist.entity.CscaCertificate;
import com.smartcoreinc.localpkd.icaomasterlist.service.CscaLdapSearchService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/csca")
public class CscaCerificateController {

    private final CscaLdapSearchService cscaLdapSearchService;

    public CscaCerificateController(CscaLdapSearchService cscaLdapSearchService) {
        this.cscaLdapSearchService = cscaLdapSearchService;
    }

    @GetMapping("/list")
    public String index(Model model) {
        List<CscaCertificate> certs = cscaLdapSearchService.findAll();
        log.debug("count: {}", certs.size());
        model.addAttribute("certs", certs);
        return "";
    }
}

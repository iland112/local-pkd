package com.smartcoreinc.localpkd.icaomasterlist.controller;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.smartcoreinc.localpkd.icaomasterlist.service.CscaMasterListParser;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/icao/csca")
public class CscaUploadController {
    private final CscaMasterListParser parser;
    
    private int count = 0;

    public CscaUploadController(CscaMasterListParser parser) {
        this.parser = parser;
    }

    @GetMapping
    public String uploadForm() {
        return "masterlist/upload";
    }

    /**
     * 수신 받은 ICAO Master list 파일(.ml) 을 분석
     * @param file
     * @param model
     * @throws Exception
     */
    @HxRequest
    @PostMapping("/upload")
    @ResponseBody
    public void handleUpload(@RequestParam("file") MultipartFile file, Model model) throws Exception {
        log.debug("Start file upload: {}, {} bytes", file.getOriginalFilename(), file.getSize());
        
        // TODO: 개발 완료 후 isAddLdap 파라미터 제거할 것
        List<X509Certificate> x509Certs = parser.parseMasterList(file.getBytes(), true);
        log.debug("result certs count: {}", x509Certs.size());
        
        Map<String, Integer> cscaCountByCountry = parser.getCscaCountByCountry();
        
        cscaCountByCountry.forEach((key, value) -> {
            count = count + value;
            log.debug("key: {}, value: {}, count: {}", key, value, count);
        });
        log.debug("CSCA 국가 수: {}, {}", cscaCountByCountry.size(), count);
    }

}

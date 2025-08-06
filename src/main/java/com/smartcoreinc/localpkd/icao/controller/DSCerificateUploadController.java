package com.smartcoreinc.localpkd.icao.controller;

import java.io.File;
import java.io.IOException;

import org.springframework.ldap.core.LdapAttributes;
import org.springframework.ldap.ldif.parser.LdifParser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/icao/dsc")
public class DSCerificateUploadController {

    @GetMapping
    public String index() {
        return "dsc/upload";
    }

    @HxRequest
    @PostMapping("/upload")
    @ResponseBody
    public void handleUpload(@RequestParam("file") MultipartFile file, Model model) throws Exception {
        log.debug("Start file upload: {}, {} bytes", file.getOriginalFilename(), file.getSize());
        
        
        LdifParser parser = new LdifParser(convertMultipartFileToFile(file));
        LdapAttributes attributes = parser.getRecord();
    }

    public File convertMultipartFileToFile(MultipartFile multipartFile) throws IOException {
        // Create a new File object using the original filename from the MultipartFile.
        // It's important to note that using the original filename directly might lead to issues
        // if multiple files with the same name are uploaded or if the filename contains invalid characters.
        // For production environments, consider generating a unique, safe filename.
        File convertedFile = new File(multipartFile.getOriginalFilename());

        // Transfer the contents of the MultipartFile to the newly created File object.
        multipartFile.transferTo(convertedFile);

        return convertedFile;
    }
}

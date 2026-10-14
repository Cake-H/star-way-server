package com.smartapply.ida.integration.resume;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 异步任务里替代请求期的 MultipartFile，字节常驻内存，不依赖 Tomcat 临时文件
 */
public class InMemoryMultipartFile implements MultipartFile {
    private final byte[] bytes;
    private final String filename;
    private final String contentType;

    public InMemoryMultipartFile(byte[] bytes, String filename, String contentType) {
        this.bytes = bytes;
        this.filename = filename;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return bytes;
    }

    @Override
    public ByteArrayInputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        Files.write(dest.toPath(), bytes);
    }
}


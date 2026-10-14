package com.smartapply.ida.integration.resume;

import com.smartapply.ida.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 从简历附件（PDF / Word docx）抽取纯文本，供大模型解析。
 * <p>
 * <b>限制：</b>
 * <ul>
 *   <li>扫描版 PDF（纯图片）无法抽字，需另行接入 OCR；此处会抛出业务异常提示。</li>
 *   <li>老版 Word {@code .doc}（二进制）未支持，请用户另存为 docx 或 PDF。</li>
 * </ul>
 */
@Component
public class ResumePlainTextExtractor {

    public static final long MAX_BYTES = 15 * 1024 * 1024;
    public static final String SIZE_EXCEEDED_MESSAGE = "文件过大，请上传不超过 15MB 的简历";

    /**
     * @return 非空则已 trim；若为空串说明可能为扫描件
     */
    public String extract(MultipartFile file) throws Exception {
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(SIZE_EXCEEDED_MESSAGE);
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".pdf")) {
            return extractPdf(file.getInputStream());
        }
        if (name.endsWith(".docx")) {
            return extractDocx(file.getInputStream());
        }
        throw new BusinessException("仅支持 .pdf 或 .docx 格式");
    }

    private String extractPdf(InputStream in) throws Exception {
        byte[] bytes = in.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                throw new BusinessException("未能从 PDF 提取文字，可能是扫描版 PDF，请使用可复制的 PDF 或 Word");
            }
            return text.trim();
        }
    }

    private String extractDocx(InputStream in) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (XWPFParagraph p : paragraphs) {
                sb.append(p.getText()).append('\n');
            }
            String text = sb.toString().trim();
            if (text.isBlank()) {
                throw new BusinessException("Word 文档为空或无法读取段落");
            }
            return text;
        }
    }
}

package com.scriptforge.service.impl;

import com.scriptforge.exception.BusinessException;
import com.scriptforge.model.dto.ExtractedNovel;
import com.scriptforge.service.NovelTextExtractor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** TXT/MD 文本提取器 —— 直接按 UTF-8 读取全文，章节结构交给 ChapterSplitter 兜底 */
@Component
public class PlainTextNovelExtractor implements NovelTextExtractor {

    @Override
    public boolean supports(String extension) {
        return "txt".equals(extension) || "md".equals(extension);
    }

    @Override
    public ExtractedNovel extract(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return new ExtractedNovel(content, null);
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }
    }
}

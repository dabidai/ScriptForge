package com.scriptforge.service;

import com.scriptforge.model.dto.ExtractedNovel;
import org.springframework.web.multipart.MultipartFile;

/**
 * 小说文本提取策略接口 —— 按文件扩展名路由，将上传文件解析为纯文本。
 * TXT/MD 直接读字节流；EPUB 等自带章节结构的格式由对应实现额外返回结构化章节。
 */
public interface NovelTextExtractor {

    /** 是否支持该文件扩展名（小写，不含点） */
    boolean supports(String extension);

    /**
     * 将上传文件解析为纯文本与章节信息。
     * @throws com.scriptforge.exception.BusinessException 文件损坏或格式不合法时
     */
    ExtractedNovel extract(MultipartFile file);
}

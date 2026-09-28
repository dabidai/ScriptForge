package com.scriptforge.model.dto;

import java.util.List;

/**
 * 文件提取结果 —— 文本提取器从上传文件中解析出的内容。
 *
 * @param text     全文纯文本（去除 HTML 标记后的正文，用于入库与展示）
 * @param chapters 结构化章节列表；文件本身不含章节结构时为 null（如 TXT/MD），交给 ChapterSplitter 分章
 */
public record ExtractedNovel(String text, List<ChapterDto> chapters) {}

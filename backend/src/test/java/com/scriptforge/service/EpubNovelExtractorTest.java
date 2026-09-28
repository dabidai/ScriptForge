package com.scriptforge.service;

import com.scriptforge.exception.BusinessException;
import com.scriptforge.model.dto.ExtractedNovel;
import com.scriptforge.service.impl.EpubNovelExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EpubNovelExtractorTest {

    @Autowired
    private EpubNovelExtractor extractor;

    @Test
    void shouldSupportOnlyEpub() {
        assertTrue(extractor.supports("epub"));
        assertFalse(extractor.supports("txt"));
        assertFalse(extractor.supports("md"));
    }

    @Test
    void shouldExtractChaptersInSpineOrder() {
        var file = new MockMultipartFile("file", "小说.epub", "application/epub+zip", new TestEpub()
                .chapter("c1", "chapter1.xhtml", "第一章 初入江湖", "少年背着剑走出山村。", "山路漫漫。")
                .chapter("c2", "chapter2.xhtml", "第二章 长安夜雨", "夜色下的长安城灯火通明。")
                .build());

        ExtractedNovel result = extractor.extract(file);

        assertEquals(2, result.chapters().size());
        assertEquals("第一章 初入江湖", result.chapters().get(0).title());
        assertTrue(result.chapters().get(0).content().contains("少年背着剑走出山村。"));
        assertTrue(result.chapters().get(0).content().contains("山路漫漫。"));
        assertEquals("第二章 长安夜雨", result.chapters().get(1).title());
        // 全文文本包含所有章节内容
        assertTrue(result.text().contains("少年背着剑走出山村。"));
        assertTrue(result.text().contains("长安城灯火通明。"));
    }

    @Test
    void shouldSkipTextlessPagesAndFallbackTitle() {
        var file = new MockMultipartFile("file", "小说.epub", "application/epub+zip", new TestEpub()
                .imageOnlyPage("cover", "cover.xhtml")
                .chapter("c1", "chapter1.xhtml", null, "没有标题标签的一章。")
                .build());

        ExtractedNovel result = extractor.extract(file);

        // 纯图片封面被跳过；无标题标签时用"第N章"兜底
        assertEquals(1, result.chapters().size());
        assertEquals("第1章", result.chapters().get(0).title());
        assertTrue(result.chapters().get(0).content().contains("没有标题标签的一章。"));
    }

    @Test
    void shouldRejectCorruptedEpub() {
        var file = new MockMultipartFile("file", "bad.epub", "application/epub+zip",
                "这不是一个有效的 zip 文件".getBytes());

        assertThrows(BusinessException.class, () -> extractor.extract(file));
    }
}

package com.scriptforge.service.impl;

import com.scriptforge.exception.BusinessException;
import com.scriptforge.model.dto.ChapterDto;
import com.scriptforge.model.dto.ExtractedNovel;
import com.scriptforge.service.NovelTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUB 文本提取器 —— EPUB 本质是 ZIP + XHTML：
 * 1. 读 META-INF/container.xml 定位 OPF 包描述文件
 * 2. 按 OPF spine 顺序遍历正文 XHTML（spine 即阅读顺序，天然的章节边界）
 * 3. 用 jsoup 剥离标签提取纯文本，每个 spine 文档视为一章
 */
@Slf4j
@Component
public class EpubNovelExtractor implements NovelTextExtractor {

    private static final String CONTAINER_ENTRY = "META-INF/container.xml";
    private static final String OPF_MEDIA_TYPE = "application/oebps-package+xml";

    /** 单条目解压大小上限，防止畸形 EPUB 撑爆内存 */
    private static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;

    /** 章节标题最大长度，与 RegexChapterSplitter 的标题上限保持一致 */
    private static final int MAX_TITLE_LENGTH = 50;

    @Override
    public boolean supports(String extension) {
        return "epub".equals(extension);
    }

    @Override
    public ExtractedNovel extract(MultipartFile file) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("scriptforge-", ".epub");
            file.transferTo(tempFile);
            return parseEpub(tempFile);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("EPUB 解析失败: {}", e.getMessage());
            throw new BusinessException("EPUB 文件解析失败，请确认文件未损坏");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private ExtractedNovel parseEpub(Path epubPath) throws IOException {
        try (ZipFile zip = new ZipFile(epubPath.toFile())) {
            String opfPath = locateOpfPath(zip);
            Document opf = parseZipEntry(zip, requireEntry(zip, opfPath), true);
            Map<String, String> manifest = readManifest(opf);
            String opfDir = opfDirOf(opfPath);

            List<ChapterDto> chapters = new ArrayList<>();
            for (Element itemref : opf.select("spine > itemref")) {
                // linear="no" 是脚注/附录等辅助内容，不算章节
                if ("no".equalsIgnoreCase(itemref.attr("linear"))) continue;
                String href = manifest.get(itemref.attr("idref"));
                if (href == null) continue;
                ZipEntry entry = zip.getEntry(resolvePath(opfDir, href));
                if (entry == null) continue;

                Document doc = parseZipEntry(zip, entry, false);
                String text = extractBodyText(doc.body());
                if (text.isBlank()) continue; // 封面/纯图片页直接跳过

                chapters.add(new ChapterDto(
                        chapters.size() + 1,
                        chapterTitle(doc, chapters.size()),
                        text,
                        text.length()
                ));
            }

            String fullText = chapters.stream().map(ChapterDto::content).collect(Collectors.joining("\n\n"));
            log.info("EPUB extracted: {} chapters, {} chars", chapters.size(), fullText.length());
            return new ExtractedNovel(fullText, chapters);
        }
    }

    /** 解析 container.xml，返回 OPF 包描述文件在 ZIP 内的路径 */
    private String locateOpfPath(ZipFile zip) throws IOException {
        Document container = parseZipEntry(zip, requireEntry(zip, CONTAINER_ENTRY), true);
        Element rootfile = container.selectFirst("rootfile[media-type=\"" + OPF_MEDIA_TYPE + "\"]");
        if (rootfile == null) rootfile = container.selectFirst("rootfile");
        if (rootfile == null) {
            throw new BusinessException("EPUB 文件解析失败，请确认文件未损坏");
        }
        return rootfile.attr("full-path");
    }

    /** manifest 中只保留 HTML 类正文资源（图片/样式等对文本提取无意义） */
    private Map<String, String> readManifest(Document opf) {
        Map<String, String> hrefs = new LinkedHashMap<>();
        for (Element item : opf.select("manifest > item")) {
            String id = item.attr("id");
            String href = item.attr("href");
            String mediaType = item.attr("media-type");
            if (id.isBlank() || href.isBlank()) continue;
            if (!mediaType.isBlank() && !mediaType.contains("html")) continue;
            hrefs.put(id, href);
        }
        return hrefs;
    }

    /**
     * 提取 XHTML 正文为带换行的纯文本。
     * 优先取段落级块元素（p/h1-h6/blockquote）保留换行；div 布局的 EPUB 取 body 顶层子元素文本，避免嵌套重复。
     */
    private String extractBodyText(Element body) {
        Elements blocks = body.select("p, h1, h2, h3, h4, h5, h6, blockquote");
        List<String> lines = new ArrayList<>();
        if (!blocks.isEmpty()) {
            for (Element el : blocks) {
                String t = el.text();
                if (!t.isBlank()) lines.add(t);
            }
        } else {
            for (Element child : body.children()) {
                String t = child.text();
                if (!t.isBlank()) lines.add(t);
            }
        }
        return String.join("\n", lines);
    }

    /** 章节标题取文档第一个标题标签，取不到用"第N章"兜底 */
    private String chapterTitle(Document doc, int ordinal) {
        Element heading = doc.body().selectFirst("h1, h2, h3, h4, h5, h6");
        String title = heading != null ? heading.text().trim() : "";
        if (title.isBlank()) {
            return "第" + (ordinal + 1) + "章";
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    private Document parseZipEntry(ZipFile zip, ZipEntry entry, boolean xml) throws IOException {
        checkEntrySize(entry);
        try (InputStream in = zip.getInputStream(entry)) {
            return xml
                    ? Jsoup.parse(in, null, "", Parser.xmlParser())
                    : Jsoup.parse(in, null, "");
        }
    }

    private void checkEntrySize(ZipEntry entry) {
        if (entry.getSize() > MAX_ENTRY_BYTES) {
            throw new BusinessException("EPUB 内单文件过大（>20MB）");
        }
    }

    private ZipEntry requireEntry(ZipFile zip, String path) {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            throw new BusinessException("EPUB 文件解析失败，请确认文件未损坏");
        }
        return entry;
    }

    /** href 相对 OPF 所在目录，需 URL 解码并归一化 ../ 与 ./ 段 */
    private String resolvePath(String opfDir, String href) {
        String path = href;
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        String base = path.startsWith("/") ? "" : opfDir;
        Deque<String> stack = new ArrayDeque<>();
        for (String seg : (base + path).split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) {
                if (!stack.isEmpty()) stack.pollLast();
            } else {
                stack.addLast(seg);
            }
        }
        return String.join("/", stack);
    }

    private String opfDirOf(String opfPath) {
        int slash = opfPath.lastIndexOf('/');
        return slash >= 0 ? opfPath.substring(0, slash + 1) : "";
    }
}

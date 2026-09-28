package com.scriptforge.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 测试专用 —— 在内存中构造最小可用的 EPUB 字节流（OEBPS 固定布局） */
final class TestEpub {

    private final Map<String, String> entries = new LinkedHashMap<>();
    private final StringBuilder manifest = new StringBuilder();
    private final StringBuilder spine = new StringBuilder();

    /** 添加一个正文 XHTML 文档，title 为 null 时不带标题标签 */
    TestEpub chapter(String id, String fileName, String title, String... paragraphs) {
        StringBuilder body = new StringBuilder();
        if (title != null) body.append("<h1>").append(title).append("</h1>\n");
        for (String p : paragraphs) body.append("<p>").append(p).append("</p>\n");
        entries.put("OEBPS/" + fileName, html(body.toString()));
        manifest.append("<item id=\"").append(id).append("\" href=\"").append(fileName)
                .append("\" media-type=\"application/xhtml+xml\"/>\n");
        spine.append("<itemref idref=\"").append(id).append("\"/>\n");
        return this;
    }

    /** 添加 spine 中无正文文本的页面（如纯图片封面） */
    TestEpub imageOnlyPage(String id, String fileName) {
        entries.put("OEBPS/" + fileName, html("<div><img src=\"pic.jpg\" alt=\"封面\"/></div>"));
        manifest.append("<item id=\"").append(id).append("\" href=\"").append(fileName)
                .append("\" media-type=\"application/xhtml+xml\"/>\n");
        spine.append("<itemref idref=\"").append(id).append("\"/>\n");
        return this;
    }

    byte[] build() {
        entries.putIfAbsent("mimetype", "application/epub+zip");
        entries.put("META-INF/container.xml", container());
        entries.put("OEBPS/content.opf", opf());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private String html(String body) {
        return "<html><head><meta charset=\"utf-8\"/></head><body>\n" + body + "</body></html>";
    }

    private String container() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>""";
    }

    private String opf() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                  <manifest>
                %s                  </manifest>
                  <spine>
                %s                  </spine>
                </package>""".formatted(manifest, spine);
    }
}

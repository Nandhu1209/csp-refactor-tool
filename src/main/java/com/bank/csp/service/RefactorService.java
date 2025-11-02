package com.bank.csp.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The "Brain" of the application.
 * This service handles all the Jsoup logic to refactor the HTML.
 */
@Service
public class RefactorService {

    // A list of all inline event attributes (onclick, onmouseover, etc.)
    private static final String[] EVENT_ATTRIBUTES = {
            "onclick", "onmousedown", "onmouseup", "onmouseover", "onmouseout",
            "onmousemove", "onkeydown", "onkeyup", "onkeypress", "onload", "onunload",
            "onfocus", "onblur", "onsubmit", "onreset", "onchange", "onselect"
    };

    /**
     * Main class to hold the 3 output files and the change log.
     */
    public static class RefactorResult {
        private final String html;
        private final String css;
        private final String js;
        private final List<String> changeLog;

        public RefactorResult(String html, String css, String js, List<String> changeLog) {
            this.html = html;
            this.css = css;
            this.js = js;
            this.changeLog = changeLog;
        }

        public String getHtml() { return html; }
        public String getCss() { return css; }
        public String getJs() { return js; }
        public List<String> getChangeLog() { return changeLog; }
    }

    /**
     * The main public method that performs the entire refactoring.
     */
    public RefactorResult refactor(String htmlContent, String originalFilename) {
        Document doc = Jsoup.parse(htmlContent);
        doc.outputSettings().prettyPrint(true); // Makes the final HTML readable

        StringBuilder cssBuilder = new StringBuilder();
        StringBuilder jsBuilder = new StringBuilder();
        List<String> changeLog = new ArrayList<>();
        AtomicInteger uniqueIdCounter = new AtomicInteger(1);
        Map<String, String> styleToClassCache = new HashMap<>();

        // 1. Process <style> tags
        extractStyleTags(doc, cssBuilder, changeLog);

        // 2. Process style="..." attributes
        extractStyleAttributes(doc, cssBuilder, changeLog, uniqueIdCounter, styleToClassCache);

        // 3. Process <script> tags
        extractScriptTags(doc, jsBuilder, changeLog);

        // 4. Process inline event attributes (e.g., onclick)
        extractEventAttributes(doc, jsBuilder, changeLog, uniqueIdCounter);

        // 5. Add links to the new CSS/JS files
        String baseFilename = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        String cssFilename = baseFilename + ".css";
        String jsFilename = baseFilename + ".js";

        if (cssBuilder.length() > 0) {
            Element head = doc.head();
            head.appendElement("link")
                    .attr("rel", "stylesheet")
                    .attr("href", cssFilename);
            changeLog.add("Added <link> to new file: " + cssFilename);
        }

        if (jsBuilder.length() > 0) {
            Element body = doc.body();
            body.appendElement("script")
                    .attr("src", jsFilename)
                    .attr("defer", true);
            changeLog.add("Added <script> tag for new file: " + jsFilename);
        }

        return new RefactorResult(
                doc.outerHtml(),
                cssBuilder.toString(),
                jsBuilder.toString(),
                changeLog
        );
    }

    private void extractStyleTags(Document doc, StringBuilder cssBuilder, List<String> changeLog) {
        Elements styleTags = doc.select("style");
        if (styleTags.isEmpty()) {
            changeLog.add("INFO: No <style> tags found.");
        }
        for (Element style : styleTags) {
            cssBuilder.append("/* --- Extracted from <style> tag --- */\n");
            cssBuilder.append(style.html()).append("\n\n");
            style.remove();
            changeLog.add("SUCCESS: Extracted <style> tag to CSS file.");
        }
    }

    private void extractStyleAttributes(Document doc, StringBuilder cssBuilder, List<String> changeLog, AtomicInteger idCounter, Map<String, String> cache) {
        Elements styledElements = doc.select("[style]");
        if (styledElements.isEmpty()) {
            changeLog.add("INFO: No inline [style] attributes found.");
        }
        for (Element el : styledElements) {
            String style = el.attr("style");
            if (style.trim().isEmpty()) continue;

            String newClassName;
            if (cache.containsKey(style)) {
                // Use existing class if styles are identical
                newClassName = cache.get(style);
            } else {
                // Create a new class
                newClassName = "csp-auto-class-" + idCounter.getAndIncrement();
                cache.put(style, newClassName);
                cssBuilder.append("/* --- Extracted from [style] attribute --- */\n");
                cssBuilder.append(".").append(newClassName).append(" {\n");
                cssBuilder.append("    ").append(style.replace(";", ";\n    ")).append("\n");
                cssBuilder.append("}\n\n");
            }

            el.removeAttr("style");
            el.addClass(newClassName);
            changeLog.add(String.format("SUCCESS: Replaced [style] on <%s> with new class .%s", el.tagName(), newClassName));
        }
    }

    private void extractScriptTags(Document doc, StringBuilder jsBuilder, List<String> changeLog) {
        Elements scriptTags = doc.select("script");
        for (Element script : scriptTags) {
            // Only extract inline scripts (those without a 'src' attribute)
            if (!script.hasAttr("src") && !script.html().trim().isEmpty()) {
                jsBuilder.append("// --- Extracted from inline <script> tag --- \n");
                jsBuilder.append(script.html()).append("\n\n");
                script.remove();
                changeLog.add("SUCCESS: Extracted inline <script> block to JS file.");
            } else if (script.hasAttr("src")) {
                changeLog.add("INFO: Kept external <script src=\"" + script.attr("src") + "\">.");
            }
        }
    }

    private void extractEventAttributes(Document doc, StringBuilder jsBuilder, List<String> changeLog, AtomicInteger idCounter) {
        jsBuilder.append("// --- Auto-generated Event Listeners --- \n");
        jsBuilder.append("document.addEventListener('DOMContentLoaded', () => {\n");

        boolean listenerAdded = false;
        for (String attr : EVENT_ATTRIBUTES) {
            Elements elements = doc.select("[" + attr + "]");
            for (Element el : elements) {
                String script = el.attr(attr);
                el.removeAttr(attr);

                // Ensure the element has a unique ID
                String id = el.id();
                if (id.isEmpty() || doc.select("#" + id).size() > 1) { // Check for empty or duplicate IDs
                    id = "csp-auto-id-" + idCounter.getAndIncrement();
                    el.attr("id", id);
                }

                // For 'onload' on body, attach to 'DOMContentLoaded'
                if (attr.equals("onload") && el.tagName().equals("body")) {
                    jsBuilder.append(String.format("    // Replaced body.onload\n    try {\n        (%s)();\n    } catch(e) { console.error('Error running legacy onload function: ', e); }\n", script));
                    changeLog.add("SUCCESS: Replaced [body.onload] with 'DOMContentLoaded' listener.");
                } else {
                    // For other events, add a standard event listener
                    String event = attr.substring(2); // "onclick" -> "click"
                    jsBuilder.append(String.format("    const el_%s = document.getElementById('%s');\n", id, id));
                    jsBuilder.append(String.format("    if (el_%s) {\n", id));
                    jsBuilder.append(String.format("        el_%s.addEventListener('%s', (event) => {\n", id, event));
                    jsBuilder.append(String.format("            try {\n                %s;\n            } catch(e) { console.error('Error in legacy %s handler: ', e); }\n", script, attr));
                    jsBuilder.append("        });\n");
                    jsBuilder.append("    }\n\n");
                    changeLog.add(String.format("SUCCESS: Replaced [%s] on <%s> with event listener.", attr, el.tagName()));
                }
                listenerAdded = true;
            }
        }

        if (!listenerAdded) {
            jsBuilder.append("    // No inline event listeners found to refactor.\n");
            changeLog.add("INFO: No inline event attributes (e.g., onclick) found.");
        }
        jsBuilder.append("});\n");
    }

    /**
     * Helper method to add a new file to the zip stream.
     */
    public void addFileToZip(ZipOutputStream zos, String filename, String content) throws IOException {
        if (content == null || content.isEmpty()) {
            return; // Don't add empty files
        }
        ZipEntry entry = new ZipEntry(filename);
        zos.putNextEntry(entry);
        zos.write(content.getBytes());
        zos.closeEntry();
    }
}


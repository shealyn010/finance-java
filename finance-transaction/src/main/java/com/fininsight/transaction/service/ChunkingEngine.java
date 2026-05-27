package com.fininsight.transaction.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * RAG 文档切片引擎 — 演示三种策略
 */
@Component
public class ChunkingEngine {

    public record Chunk(String text, String strategy, int id) {}

    /** 策略1: 固定长度切片（最粗暴） */
    public List<Chunk> fixedSize(String text, int size) {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0, id = 0; i < text.length(); i += size) {
            int end = Math.min(i + size, text.length());
            chunks.add(new Chunk(text.substring(i, end), "fixed-" + size, id++));
        }
        return chunks;
    }

    /** 策略2: 递归语义切片（按 Markdown 标题 > 段落 > 句子降级切） */
    public List<Chunk> recursiveSemantic(String text, int maxLen) {
        List<Chunk> chunks = new ArrayList<>();
        // 先按 ## 标题切
        String[] sections = text.split("(?=\n## )");
        int id = 0;
        for (String sec : sections) {
            if (sec.trim().isEmpty()) continue;
            if (sec.length() <= maxLen) {
                chunks.add(new Chunk(sec.trim(), "semantic-h" + maxLen, id++));
            } else {
                // 超标了，按段落切
                String[] paras = sec.split("\n\n");
                StringBuilder buf = new StringBuilder();
                for (String p : paras) {
                    if (buf.length() + p.length() > maxLen && !buf.isEmpty()) {
                        chunks.add(new Chunk(buf.toString().trim(), "semantic-p" + maxLen, id++));
                        buf = new StringBuilder();
                    }
                    buf.append(p).append("\n\n");
                }
                if (!buf.isEmpty()) {
                    chunks.add(new Chunk(buf.toString().trim(), "semantic-p" + maxLen, id++));
                }
            }
        }
        return chunks;
    }

    /** 策略3: 滑动窗口（带 overlap，保证边界不丢上下文） */
    public List<Chunk> slidingWindow(String text, int size, int overlap) {
        List<Chunk> chunks = new ArrayList<>();
        int step = size - overlap;
        int id = 0;
        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + size, text.length());
            chunks.add(new Chunk(text.substring(i, end), "sliding-w" + size + "o" + overlap, id++));
            if (end >= text.length()) break;
        }
        return chunks;
    }

    /** 对比三种策略的输出 */
    public Map<String, Object> compare(String text) {
        return Map.of(
            "fixed_200", summarize(fixedSize(text, 200)),
            "recursive_300", summarize(recursiveSemantic(text, 300)),
            "sliding_200_50", summarize(slidingWindow(text, 200, 50))
        );
    }

    private Map<String, Object> summarize(List<Chunk> chunks) {
        return Map.of(
            "chunkCount", chunks.size(),
            "avgLen", chunks.stream().mapToInt(c -> c.text().length()).average().orElse(0),
            "firstChunk", chunks.isEmpty() ? "" : chunks.get(0).text().substring(0, Math.min(100, chunks.get(0).text().length()))
        );
    }
}

package com.xlongwei.search;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.config.Config;
import com.networknt.server.ShutdownHookProvider;
import com.networknt.utility.StringUtils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;

import io.undertow.util.FileUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * @see https://gitee.com/Myzhang/luceneplus
 */
@Slf4j
public class LucenePlus implements ShutdownHookProvider {

    private static Map<String, Object> config = Config.getInstance().getJsonMapConfig("lucene");
    private static String index = (String) config.get("index");
    private static String indices = "indices.json";
    private static Map<String, IndexWriter> writers = new HashMap<>();
    private static Map<String, IndexSearcher> searchers = new HashMap<>();
    private static Map<String, List<LuceneField>> fields = new HashMap<>();

    public static IndexWriter getWriter(String name, String analyzer) throws Exception {
        IndexWriter writer = writers.get(name);
        if (writer == null) {
            synchronized (writers) {
                writer = writers.get(name);
                if (writer == null) {
                    FSDirectory directory = NIOFSDirectory.open(Paths.get(index, name));
                    writer = new IndexWriter(directory, new IndexWriterConfig(getAnalyzer(analyzer)));
                    writers.put(name, writer);
                }
            }
        }
        return writer;
    }

    public static IndexSearcher getSearcher(String name) throws Exception {
        IndexSearcher searcher = searchers.get(name);
        if (searcher == null) {
            synchronized (searchers) {
                searcher = searchers.get(name);
                if (searcher == null) {
                    FSDirectory directory = NIOFSDirectory.open(Paths.get(index, name));
                    DirectoryReader reader = DirectoryReader.open(directory);
                    searcher = new IndexSearcher(reader);
                    searchers.put(name, searcher);
                }
            }
        }
        return searcher;
    }

    public static Analyzer getAnalyzer(String analyzer) {
        return new StandardAnalyzer();
    }

    public static void close(String name) throws Exception {
        IndexWriter writer = writers.get(name);
        if (writer != null) {
            writer.close();
            writers.remove(name);
            // 关闭索引之后重新获取searcher
            IndexSearcher searcher = searchers.remove(name);
            if (searcher != null) {
                searcher.getIndexReader().close();
            }
        }
    }

    public static void drop(String name) throws Exception {
        close(name);
        Path path = Paths.get(index, name);
        FileUtils.deleteRecursive(path);
        fields(name, null);
    }

    /**
     * @param fields null 删除索引，non-null 初始化索引，多次提交时不处理
     */
    public static boolean fields(String name, List<LuceneField> fields) throws Exception {
        Path path = Paths.get(index, indices);
        String json = path.toFile().exists() ? Files.readString(path) : "";
        json = StringUtils.isNotBlank(json) ? json : "{}";
        LucenePlus.fields = Config.getInstance().getMapper().readValue(json,
                new TypeReference<Map<String, List<LuceneField>>>() {
                });
        if (fields == null) {
            List<LuceneField> remove = LucenePlus.fields.remove(name);
            if (remove != null) {
                Files.writeString(path, Config.getInstance().getMapper().writeValueAsString(LucenePlus.fields));
                return true;
            }
        } else {
            if (!LucenePlus.fields.containsKey(name)) {
                LucenePlus.fields.put(name, fields);
                Files.writeString(path, Config.getInstance().getMapper().writeValueAsString(LucenePlus.fields));
                return true;
            }
        }
        return false;
    }

    /**
     * 获取索引的字段列表，需要先打开索引
     * 
     * @param name
     * @return
     */
    public static List<LuceneField> fields(String name) {
        List<LuceneField> list = fields.get(name);
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public void onShutdown() {
        writers.entrySet().forEach(entry -> {
            try {
                entry.getValue().close();
                log.info("shutdown {} writer", entry.getKey());
            } catch (Exception e) {
                log.warn("fail to shutdown {} writer", entry.getKey());
            }
        });
        searchers.entrySet().forEach(entry -> {
            try {
                entry.getValue().getIndexReader().close();
                log.info("shutdown {} searcher", entry.getKey());
            } catch (Exception e) {
                log.warn("fail to shutdown {} searcher", entry.getKey());
            }
        });
        log.info("lucene shutdown");
    }
}

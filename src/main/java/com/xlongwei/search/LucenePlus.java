package com.xlongwei.search;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.networknt.server.ShutdownHookProvider;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSLockFactory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.NoLockFactory;

import io.undertow.util.FileUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * @see https://gitee.com/Myzhang/luceneplus
 */
@Slf4j
@SuppressWarnings({ "rawtypes" })
public class LucenePlus implements ShutdownHookProvider {

    private static Map<String, IndexWriter> writers = new HashMap<>();
    private static Map<String, IndexSearcher> searchers = new HashMap<>();

    public static IndexWriter getWriter(String name) throws Exception {
        IndexWriter writer = writers.get(name);
        if (writer == null) {
            synchronized (writers) {
                writer = writers.get(name);
                if (writer == null) {
                    Directory directory = getDirectory(name);
                    writer = new IndexWriter(directory, new IndexWriterConfig(getAnalyzer(name)));
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
                    LuceneIndex index = LuceneIndex.index(name);
                    boolean realtime = index != null ? index.isRealtime() : false;
                    DirectoryReader reader = realtime ? DirectoryReader.open(getWriter(name))
                            : DirectoryReader.open(getDirectory(name));
                    searcher = new IndexSearcher(reader);
                    searchers.put(name, searcher);
                }
            }
        }
        return searcher;
    }

    public static Analyzer getAnalyzer(String name) {
        return new StandardAnalyzer();
    }

    public static Directory getDirectory(String name) throws Exception {
        Path path = LuceneIndex.path(name);
        LuceneIndex index = LuceneIndex.index(name);
        LockFactory lockFactory = NoLockFactory.INSTANCE;
        if (index != null && index.getDirectory() != null) {
            if ("fs".equals(index.getLockFactory())) {
                lockFactory = FSLockFactory.getDefault();
            }
            switch (index.getDirectory()) {
                case "nio":
                    return NIOFSDirectory.open(path, lockFactory);
                case "mmap":
                    return MMapDirectory.open(path, lockFactory);
            }
        }
        String systemName = System.getProperty("os.name").toLowerCase();
        return systemName.contains("win") ? MMapDirectory.open(path, lockFactory)
                : NIOFSDirectory.open(path, lockFactory);
    }

    public static boolean close(String name) throws Exception {
        IndexWriter writer = writers.get(name);
        if (writer != null) {
            // 近实时搜索不必关闭
            if (LuceneIndex.realtime(name)) {
                return true;
            }
            writer.close();
            writers.remove(name);
            // 关闭索引之后重新获取searcher
            closeSearcher(name);
        }
        return writer != null;
    }

    // 关闭searcher，不关闭近实时索引
    public static boolean closeSearcher(String name) throws Exception {
        IndexSearcher searcher = searchers.remove(name);
        if (searcher != null) {
            if (!LuceneIndex.realtime(name)) {
                searcher.getIndexReader().close();
            }
            return true;
        }
        return false;
    }

    public static boolean drop(String name) throws Exception {
        boolean index = LuceneIndex.index(name, null);
        if (index) {
            IndexSearcher searcher = searchers.remove(name);
            if (searcher != null && !LuceneIndex.realtime(name)) {
                searcher.getIndexReader().close();
            }
            IndexWriter writer = writers.remove(name);
            if (writer != null) {
                writer.close();
            }
            Path path = LuceneIndex.path(name);
            FileUtils.deleteRecursive(path);
        }
        return index;
    }

    /** {add:[{name:value}]} */
    public static boolean docs(String name, Map<String, Object> map) throws Exception {
        if (map == null || map.isEmpty()) {
            return false;
        }
        List<LuceneField> fields = LuceneIndex.fields(name);
        if (fields == null || fields.isEmpty()) {
            return false;
        }
        List<?> list = (List) map.get("add");
        if (list != null && list.size() > 0) {
            List<Document> docs = new ArrayList<>(list.size());
            try {
                for (Object obj : list) {
                    Map row = (Map) obj;
                    List<Field> docFields = LuceneField.docFields(row, fields);
                    if (docFields.size() > 0) {
                        Document doc = new Document();
                        docFields.forEach(doc::add);
                        docs.add(doc);
                    } else {
                        return false;
                    }
                }
                IndexWriter writer = getWriter(name);
                writer.addDocuments(docs);
                writer.flush();
                writer.commit();
            } catch (Exception e) {
                return false;
            }
            closeSearcher(name);
            return true;
        }
        return false;
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

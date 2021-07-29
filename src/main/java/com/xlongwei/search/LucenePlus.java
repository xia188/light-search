package com.xlongwei.search;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.networknt.server.ShutdownHookProvider;
import com.networknt.utility.StringUtils;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
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
@SuppressWarnings({ "rawtypes", "unchecked" })
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
        boolean commit = false;
        IndexWriter writer = null;
        try {
            Object object = map.get("delete");
            if (object != null) {
                List<Query> deletes = new ArrayList<>();
                if (object instanceof Map) {
                    deletes.add(termQuery(fields, (Map) object));
                } else if (object instanceof List) {
                    List list = (List) object;
                    for (Object obj : list) {
                        deletes.add(termQuery(fields, (Map) obj));
                    }
                } else {
                    return false;
                }
                writer = getWriter(name);
                for (Query deleteQuery : deletes) {
                    writer.deleteDocuments(deleteQuery);
                }
                commit = true;
            }
            List<?> list = (List) map.get("add");
            if (list != null && list.size() > 0) {
                List<Document> docs = new ArrayList<>(list.size());
                for (Object obj : list) {
                    Map row = (Map) obj;
                    List<Field> docFields = LuceneField.docFields(row, fields);
                    if (docFields.size() > 0) {
                        Document doc = new Document();
                        docFields.forEach(doc::add);
                        docs.add(doc);
                    } else {
                        throw new IllegalArgumentException("add row can't be empty");
                    }
                }
                writer = writer != null ? writer : getWriter(name);
                writer.addDocuments(docs);
                commit = true;
            }
            return commit;
        } catch (Exception e) {
            if (writer != null) {
                writer.rollback();
                writer = null;
            }
            return false;
        } finally {
            if (commit && writer != null) {
                writer.flush();
                writer.commit();
                closeSearcher(name);
            }
        }
    }

    public static Query termQuery(String name, Map<String, Object> map) throws Exception {
        List<LuceneField> fields = LuceneIndex.fields(name);
        if (fields == null || fields.isEmpty() || map == null || map.isEmpty()) {
            return new MatchNoDocsQuery();
        }
        // text类型预先分词
        Map<String, Object> copy = null;
        for (Entry<String, Object> entry : map.entrySet()) {
            for (LuceneField field : fields) {
                if (!entry.getKey().equals(field.getName())) {
                    continue;
                }
                if ("text".equals(field.getType())) {
                    String value = entry.getValue() == null ? StringUtils.EMPTY
                            : StringUtils.trimToEmpty(entry.getValue().toString());
                    if (StringUtils.isNotBlank(value)) {
                        List<String> list = analyze(name, value);
                        // entry.setValue(list);
                        if (copy == null) {
                            copy = new HashMap<>(map);
                        }
                        copy.put(entry.getKey(), list);
                    }
                }
                break;
            }
        }
        return termQuery(fields, copy != null ? copy : map);
    }

    public static List<String> analyze(String name, String text) throws Exception {
        List<String> list = new LinkedList<>();
        if (StringUtils.isNotBlank(text)) {
            Analyzer analyzer = getAnalyzer(name);
            TokenStream tokenStream = analyzer.tokenStream("contents", new StringReader(text));
            CharTermAttribute term = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                list.add(term.toString());
            }
            tokenStream.end();
            tokenStream.close();
        }
        return list;
    }

    public static Query termQuery(List<LuceneField> fields, Map<String, Object> map) {
        if (fields == null || fields.isEmpty() || map == null || map.isEmpty()) {
            return new MatchNoDocsQuery();
        }
        List<Query> queries = new LinkedList<>();
        for (LuceneField field : fields) {
            Object value = map.get(field.getName());
            if (value != null) {
                if ("text".equals(field.getType()) && value instanceof List) {
                    List list = (List) value;
                    for (Object item : list) {
                        queries.add(termQuery(field, item.toString()));
                    }
                } else {
                    queries.add(termQuery(field, value.toString()));
                }
            }
        }
        if (queries.size() > 1) {
            Builder builder = new BooleanQuery.Builder();
            queries.stream().forEach(query -> builder.add(query, Occur.MUST));
            queries = Arrays.asList(builder.build());
        }
        return queries.isEmpty() ? new MatchNoDocsQuery() : queries.get(0);
    }

    public static Query termQuery(LuceneField field, String value) {
        int pos = value.indexOf(',');
        switch (field.getType()) {
            case "string":
                if (pos == -1) {
                    return new TermQuery(new Term(field.getName(), value));
                } else {
                    return TermRangeQuery.newStringRange(field.getName(), value.substring(1, pos),
                            value.substring(pos + 1, value.length() - 1), '[' == value.charAt(0),
                            ']' == value.charAt(value.length() - 1));
                }
            case "text":
                return new TermQuery(new Term(field.getName(), value));
            case "int":
                if (pos == -1) {
                    return IntPoint.newExactQuery(field.getName(), Integer.parseInt(value));
                } else {
                    return IntPoint.newRangeQuery(field.getName(), Integer.parseInt(value.substring(1, pos)),
                            Integer.parseInt(value.substring(pos + 1, value.length() - 1)));
                }
            case "long":
                if (pos == -1) {
                    return LongPoint.newExactQuery(field.getName(), Long.parseLong(value));
                } else {
                    return LongPoint.newRangeQuery(field.getName(), Long.parseLong(value.substring(1, pos)),
                            Long.parseLong(value.substring(pos + 1, value.length() - 1)));
                }
            case "float":
                if (pos == -1) {
                    return FloatPoint.newExactQuery(field.getName(), Float.parseFloat(value));
                } else {
                    return FloatPoint.newRangeQuery(field.getName(), Float.parseFloat(value.substring(1, pos)),
                            Float.parseFloat(value.substring(pos + 1, value.length() - 1)));
                }
            case "double":
                if (pos == -1) {
                    return DoublePoint.newExactQuery(field.getName(), Double.parseDouble(value));
                } else {
                    return DoublePoint.newRangeQuery(field.getName(), Double.parseDouble(value.substring(1, pos)),
                            Double.parseDouble(value.substring(pos + 1, value.length() - 1)));
                }
            case "date":
                if (pos == -1) {
                    return LongPoint.newExactQuery(field.getName(), HandlerUtil.parseDate(value, null).getTime());
                } else {
                    return LongPoint.newRangeQuery(field.getName(),
                            HandlerUtil.parseDate(value.substring(1, pos), null).getTime(),
                            HandlerUtil.parseDate(value.substring(pos + 1, value.length() - 1), null).getTime());
                }
        }
        throw new IllegalArgumentException("termQuery unsupport field type " + field.getName());
    }

    public static boolean hasDoc(TopDocs topDocs) {
        return topDocs != null && topDocs.scoreDocs != null && topDocs.scoreDocs.length > 0;
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

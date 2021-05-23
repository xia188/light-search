package com.xlongwei.search;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.config.Config;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.BytesRef;

import io.undertow.server.HttpServerExchange;
import lombok.extern.slf4j.Slf4j;

/**
 * 行政区划搜索
 */
@Slf4j
public class DistrictHandler extends SearchHandler {

    private static IndexSearcher indexSearcher = null;
    private static DirectoryReader directoryReader = null;
    private static ObjectMapper objectMapper = Config.getInstance().getMapper();

    public void search(HttpServerExchange exchange) throws Exception {
        Deque<String> deque = exchange.getQueryParameters().get("name");
        String name = deque == null || deque.isEmpty() ? null : deque.getFirst();
        Map<String, List<String>> search = search(name);
        exchange.getResponseSender().send(objectMapper.writeValueAsString(search));
    }

    public static Map<String, List<String>> search(String name) throws Exception {
        if (name != null && !name.isBlank()) {
            if (indexSearcher == null) {
                synchronized (DistrictHandler.class) {
                    if (indexSearcher == null) {// double check
                        Map<String, Object> config = Config.getInstance().getJsonMapConfig("lucene");
                        String index = (String) config.get("index"), district = "district";
                        FSDirectory fsDirectory = NIOFSDirectory.open(Path.of(index, district));
                        directoryReader = DirectoryReader.open(fsDirectory);
                        indexSearcher = new IndexSearcher(directoryReader);
                    }
                }
            }
            Builder builder = new BooleanQuery.Builder();
            name.chars().mapToObj(c -> new BytesRef(String.valueOf((char) c)))
                    .forEach(word -> builder.add(new TermQuery(new Term("name", word)), Occur.SHOULD));
            Query query = builder.build();
            TopDocs search = indexSearcher.search(query, 20);
            if (search.scoreDocs != null && search.scoreDocs.length > 0) {
                List<String> codes = new ArrayList<>(search.scoreDocs.length);
                for (ScoreDoc scoreDoc : search.scoreDocs) {
                    Document doc = indexSearcher.doc(scoreDoc.doc);
                    String code = doc.get("code");
                    codes.add(code);
                    log.trace("doc={} score={} code={}", scoreDoc.doc, scoreDoc.score, code);
                }
                return Collections.singletonMap("codes", codes);
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public void close() throws Exception {
        if (directoryReader != null) {
            directoryReader.close();
        }
    }

}

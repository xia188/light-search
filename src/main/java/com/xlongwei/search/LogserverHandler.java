package com.xlongwei.search;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.networknt.utility.StringUtils;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;

import io.undertow.server.HttpServerExchange;

public class LogserverHandler extends SearchHandler {

    private static String name = SearchHandler.name(LogserverHandler.class);

    public void pages(HttpServerExchange exchange) throws Exception {
        String logs = HandlerUtil.getParam(exchange, "logs");
        String search = HandlerUtil.getParam(exchange, "search");
        String day = StringUtils.isBlank(logs) ? null
                : (logs.contains("-") ? logs.substring(logs.lastIndexOf(".") + 1) : LocalDate.now().toString());
        int pageSize = (int) HandlerUtil.parseLong(HandlerUtil.getParam(exchange, "pageSize"), 1000L);
        List<Integer[]> pages = pages(day, search, pageSize);
        HandlerUtil.setResp(exchange, Collections.singletonMap("pages", pages));
    }

    public void delete(HttpServerExchange exchange) throws Exception {
        String day = HandlerUtil.getParam(exchange, "day");
        if (StringUtils.isNotBlank(day)) {
            IndexWriter writer = LucenePlus.getWriter(name);
            // 删除day之前的索引，因为logserver已经tgz压缩了日志文件，搜索也没用了
            writer.deleteDocuments(new TermRangeQuery("day", null, new BytesRef(day), false, false));
            // writer.deleteDocuments(new Term("day", day));
            writer.flush();
            writer.commit();
            LucenePlus.closeSearcher(name);
            HandlerUtil.setResp(exchange, Collections.singletonMap("delete", true));
        } else {
            HandlerUtil.setResp(exchange, Collections.singletonMap("delete", false));
        }
    }

    public static List<Integer[]> pages(String day, String search, int pageSize) throws Exception {
        if (StringUtils.isBlank(day) && StringUtils.isBlank(search)) {
            return Collections.emptyList();
        }
        Builder builder = new BooleanQuery.Builder();
        if (StringUtils.isNotBlank(day)) {
            builder.add(new TermQuery(new Term("day", day)), Occur.MUST);
        }
        if (StringUtils.isNotBlank(search)) {
            builder.add(LucenePlus.termQuery(name, Collections.singletonMap("line", search)), Occur.MUST);

        }
        BooleanQuery query = builder.build();
        IndexSearcher searcher = LucenePlus.getSearcher(name);
        TopDocs topDocs = searcher.search(query, 100000);// 1000*100
        if (topDocs.scoreDocs != null && topDocs.scoreDocs.length > 0) {
            List<Integer[]> pages = new LinkedList<>();
            int currentPage = 0;
            Integer[] arr = null;
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                int line = Integer.parseInt(doc.get("number"));
                int calcPage = (line - 1) / pageSize + 1;
                if (calcPage > currentPage) {
                    arr = new Integer[] { calcPage, 1 };
                    pages.add(arr);
                    currentPage = calcPage;
                } else {
                    arr[1] += 1;
                }
            }
            return pages;
        }
        return Collections.emptyList();
    }
}

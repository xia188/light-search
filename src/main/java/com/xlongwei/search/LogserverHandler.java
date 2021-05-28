package com.xlongwei.search;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import com.networknt.utility.StringUtils;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;

import io.undertow.server.HttpServerExchange;

public class LogserverHandler extends SearchHandler {

    public void lines(HttpServerExchange exchange) throws Exception {
        String logs = HandlerUtil.getParam(exchange, "logs");
        String search = HandlerUtil.getParam(exchange, "search");
        String day = StringUtils.isBlank(logs) ? null
                : (logs.contains("-") ? logs.substring(logs.lastIndexOf(".") + 1) : LocalDate.now().toString());
        List<String> lines = lines(day, search);
        HandlerUtil.setResp(exchange, Collections.singletonMap("lines", lines));
    }

    public void delete(HttpServerExchange exchange) throws Exception {
        String day = HandlerUtil.getParam(exchange, "day");
        if (StringUtils.isNotBlank(day)) {
            IndexWriter writer = LucenePlus.getWriter("logserver", null);
            // 删除day之前的索引，因为logserver已经tgz压缩了日志文件，搜索也没用了
            writer.deleteDocuments(new TermRangeQuery("day", null, new BytesRef(day), false, false));
            // writer.deleteDocuments(new Term("day", day));
            writer.flush();
            writer.commit();
            LucenePlus.closeSearcher("logserver");
            HandlerUtil.setResp(exchange, Collections.singletonMap("delete", true));
        } else {
            HandlerUtil.setResp(exchange, Collections.singletonMap("delete", false));
        }
    }

    public static List<String> lines(String day, String search) throws Exception {
        if (StringUtils.isBlank(day) && StringUtils.isBlank(search)) {
            return Collections.emptyList();
        }
        Builder builder = new BooleanQuery.Builder();
        if (StringUtils.isNotBlank(day)) {
            builder.add(new TermQuery(new Term("day", day)), Occur.MUST);
        }
        if (StringUtils.isNotBlank(search)) {
            StringTokenizer tokenizer = new StringTokenizer(search.toLowerCase());
            while (tokenizer.hasMoreTokens()) {
                String nextToken = tokenizer.nextToken();
                builder.add(new RegexpQuery(new Term("line", nextToken + "*")), Occur.MUST);
            }
        }
        BooleanQuery query = builder.build();
        IndexSearcher searcher = LucenePlus.getSearcher("logserver");
        TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
        if (topDocs.scoreDocs != null && topDocs.scoreDocs.length > 0) {
            List<String> lines = new LinkedList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                lines.add(doc.get("number"));
            }
            return lines;
        }
        return Collections.emptyList();
    }
}

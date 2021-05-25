package com.xlongwei.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.networknt.utility.StringUtils;
import com.networknt.utility.Util;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;

import io.undertow.server.HttpServerExchange;
import lombok.extern.slf4j.Slf4j;

/**
 * 行政区划搜索
 */
@Slf4j
public class DistrictHandler extends SearchHandler {

    public void search(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        String length = HandlerUtil.getParam(exchange, "length");
        Map<String, List<String>> search = searchNameAndLength(name, Util.parseInteger(length));
        HandlerUtil.setResp(exchange, search);
    }

    public static Map<String, List<String>> searchName(String name) throws Exception {
        return searchNameAndLength(name, 0);
    }

    public static Map<String, List<String>> searchNameAndLength(String name, int length) throws Exception {
        if (StringUtils.isNotBlank(name)) {
            Builder builder = new BooleanQuery.Builder();
            name.chars().mapToObj(c -> new BytesRef(String.valueOf((char) c)))
                    .forEach(word -> builder.add(new TermQuery(new Term("name", word)), Occur.SHOULD));
            if (length == 2 || length == 4 || length == 6 || length == 9 || length == 12) {
                builder.add(new RegexpQuery(new Term("code", ".{" + length + "}")), Occur.MUST);
            }
            Query query = builder.build();
            IndexSearcher indexSearcher = LucenePlus.getSearcher("district");
            TopDocs search = indexSearcher.search(query, 10);
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

}

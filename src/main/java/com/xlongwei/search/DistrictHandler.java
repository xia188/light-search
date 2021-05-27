package com.xlongwei.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.networknt.utility.StringUtils;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
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
        String ancestor = HandlerUtil.getParam(exchange, "ancestor");
        int length = (int) HandlerUtil.parseLong(HandlerUtil.getParam(exchange, "length"), 0L);
        int n = (int) HandlerUtil.parseLong(HandlerUtil.getParam(exchange, "n"), 10L);
        Map<String, List<String>> search = search(name, ancestor, length, n > 0 ? n : 10);
        HandlerUtil.setResp(exchange, search);
    }

    public static Map<String, List<String>> search(String name, String ancestor, int length, int n) throws Exception {
        if (StringUtils.isNotBlank(name)) {
            Builder builder = new BooleanQuery.Builder();
            // 至少包含一个字，否则不相关的结果也会出来
            builder.add(new RegexpQuery(new Term("name", "[" + name + "]+")), Occur.MUST);
            // 包含的字越多越靠前，否则结果排序不准确
            name.chars().mapToObj(c -> new BytesRef(String.valueOf((char) c)))
                    .forEach(word -> builder.add(new TermQuery(new Term("name", word)), Occur.SHOULD));
            if (length == 2 || length == 4 || length == 6 || length == 9 || length == 12) {
                builder.add(new RegexpQuery(new Term("code", ".{" + length + "}")), Occur.MUST);
            }
            // code也支持查询条件，因此不能使用store类型
            if (StringUtils.isNotBlank(ancestor)) {
                builder.add(new RegexpQuery(new Term("code", ancestor + ".*")), Occur.MUST);
            }
            Query query = builder.build();
            IndexSearcher indexSearcher = LucenePlus.getSearcher("district");
            TopDocs search = indexSearcher.search(query, n);
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

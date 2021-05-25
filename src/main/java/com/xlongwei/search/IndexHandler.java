package com.xlongwei.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.networknt.utility.StringUtils;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;

import io.undertow.server.HttpServerExchange;

/**
 * 索引管理类
 */
public class IndexHandler extends SearchHandler {

    /** {name,analyzer,fields:[{field,type,store,sort}]} */
    @SuppressWarnings({ "rawtypes" })
    public void open(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name)) {
            return;
        }
        List<?> list = (List<?>) HandlerUtil.fromJson(HandlerUtil.getBodyString(exchange)).get("fields");
        List<LuceneField> fields = new ArrayList<>();
        if (list != null && list.size() > 0) {
            for (Object obj : list) {
                Map map = (Map) obj;
                LuceneField field = new LuceneField();
                field.setName(Objects.toString(map.get("field"), StringUtils.EMPTY).toLowerCase());
                field.setType(Objects.toString(map.get("type"), StringUtils.EMPTY).toLowerCase());
                field.setSort(Objects.toString(map.get("sort"), StringUtils.EMPTY).toLowerCase());
                if (field.checkValid()) {
                    String store = Objects.toString(map.get("store"), StringUtils.EMPTY);
                    // string默认存储，适合主键，其他类型默认不存储，或手动指定store:yes
                    field.setStore(StringUtils.isNotBlank(store) ? "yes".equalsIgnoreCase(store)
                            : "string".equals(field.getType()));
                    fields.add(field);
                } else {
                    // 索引定义好之后不能变更，因此校验不通过时拒绝创建索引，从而有机会调整请求参数
                    HandlerUtil.setResp(exchange, Collections.singletonMap("invalid", map));
                    return;
                }
            }
        }
        LucenePlus.getWriter(name, HandlerUtil.getParam(exchange, "analyzer"));
        // fields不是null，会触发读取indices.json索引字段定义
        LucenePlus.fields(name, fields);
        fields = LucenePlus.fields(name);
        HandlerUtil.setResp(exchange, Collections.singletonMap("fields", fields));
    }

    /** {name,add:[{name:value}]} */
    public void docs(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name)) {
            HandlerUtil.setResp(exchange, Collections.singletonMap("docs", false));
        } else {
            Map<String, Object> map = HandlerUtil.fromJson(HandlerUtil.getBodyString(exchange));
            boolean docs = LucenePlus.docs(name, map);
            HandlerUtil.setResp(exchange, Collections.singletonMap("docs", docs));
        }
    }

    /** {name,query} */
    public void search(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        // 实现简单的QueryParser功能 name:四川邻水
        String query = HandlerUtil.getParam(exchange, "query");
        if (StringUtils.isBlank(name) || StringUtils.isBlank(query) || !query.contains(":")) {
            HandlerUtil.setResp(exchange, Collections.singletonMap("search", Collections.emptyList()));
        } else {
            String[] split = query.split("[:]");
            Builder builder = new BooleanQuery.Builder();
            split[1].chars().mapToObj(c -> new BytesRef(String.valueOf((char) c)))
                    .forEach(word -> builder.add(new TermQuery(new Term(split[0], word)), Occur.SHOULD));
            IndexSearcher indexSearcher = LucenePlus.getSearcher(name);
            TopDocs search = indexSearcher.search(builder.build(), 10);
            List<Map<String, Object>> list = new ArrayList<>();
            if (search.scoreDocs != null && search.scoreDocs.length > 0) {
                List<LuceneField> fields = LucenePlus.fields(name);
                for (ScoreDoc scoreDoc : search.scoreDocs) {
                    Document doc = indexSearcher.doc(scoreDoc.doc);
                    Map<String, Object> item = new HashMap<>();
                    for (LuceneField field : fields) {
                        item.put(field.getName(), doc.get(field.getName()));
                    }
                    list.add(item);
                }
            }
            HandlerUtil.setResp(exchange, Collections.singletonMap("search", list));
        }
    }

    public void close(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name)) {
            HandlerUtil.setResp(exchange, Collections.singletonMap("close", false));
        } else {
            boolean close = LucenePlus.close(name);
            HandlerUtil.setResp(exchange, Collections.singletonMap("close", close));
        }
    }

    public void drop(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name)) {
            HandlerUtil.setResp(exchange, Collections.singletonMap("drop", false));
        } else {
            boolean drop = LucenePlus.drop(name);
            HandlerUtil.setResp(exchange, Collections.singletonMap("drop", drop));
        }
    }

}

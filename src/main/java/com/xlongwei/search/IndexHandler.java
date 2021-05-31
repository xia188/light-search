package com.xlongwei.search;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.networknt.utility.StringUtils;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

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
        LuceneIndex index = LuceneIndex.index(name);
        if (index == null) {
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
                                : "string,store".contains(field.getType()));
                        fields.add(field);
                    } else {
                        // 索引定义好之后不能变更，因此校验不通过时拒绝创建索引，从而有机会调整请求参数
                        HandlerUtil.setResp(exchange, Collections.singletonMap("invalid", map));
                        return;
                    }
                }
            }
            if (fields.size() > 0) {
                index = new LuceneIndex();
                index.setAnalyzer(HandlerUtil.getParam(exchange, "analyzer"));
                index.setDirectory(HandlerUtil.getParam(exchange, "directory"));
                index.setLockFactory(HandlerUtil.getParam(exchange, "lockFactory"));
                index.setRealtime(HandlerUtil.parseBoolean(HandlerUtil.getParam(exchange, "realtime"), false));
                index.setFields(fields);
                LuceneIndex.index(name, index);
                index = LuceneIndex.index(name);
            }
        }

        if (index != null && index.getFields() != null && index.getFields().size() > 0) {
            LucenePlus.getWriter(name);
        }
        HandlerUtil.setResp(exchange, Collections.singletonMap(name, index));
    }

    /** {name,commit,add:[{name:value}]} */
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
        if (StringUtils.isBlank(name) || !LuceneIndex.exists(name)) {
            return;
        }
        Map<String, Object> map = HandlerUtil.fromJson(HandlerUtil.getBodyString(exchange));
        if (map == null || map.isEmpty()) {
            return;
        }
        int offset = (int) HandlerUtil.parseLong(HandlerUtil.getParam(exchange, "offset"), 0);
        int limit = (int) HandlerUtil.parseLong(HandlerUtil.getParam(exchange, "limit"), 10L);
        Query query = LucenePlus.termQuery(name, map);
        IndexSearcher searcher = LucenePlus.getSearcher(name);
        TopDocs search = searcher.search(query, offset + limit);
        List<Map<String, Object>> list = new ArrayList<>();
        if (search.scoreDocs != null && search.scoreDocs.length > offset) {
            List<LuceneField> fields = LuceneIndex.fields(name);
            for (int i = offset; i < search.scoreDocs.length; i++) {
                ScoreDoc scoreDoc = search.scoreDocs[i];
                Document doc = searcher.doc(scoreDoc.doc);
                Map<String, Object> item = new HashMap<>();
                for (LuceneField field : fields) {
                    item.put(field.getName(), field.resolve(doc));
                }
                list.add(item);
            }
        }
        HandlerUtil.setResp(exchange, Collections.singletonMap("search", list));
    }

    public void close(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name) || !LuceneIndex.exists(name)) {
            HandlerUtil.setResp(exchange, Collections.singletonMap("close", false));
        } else {
            boolean close = LucenePlus.close(name);
            HandlerUtil.setResp(exchange, Collections.singletonMap("close", close));
        }
    }

    public void drop(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name) || !LuceneIndex.exists(name)) {
            HandlerUtil.setResp(exchange, Collections.singletonMap("drop", false));
        } else {
            boolean drop = LucenePlus.drop(name);
            HandlerUtil.setResp(exchange, Collections.singletonMap("drop", drop));
        }
    }

    public void indices(HttpServerExchange exchange) throws Exception {
        HandlerUtil.setResp(exchange, LuceneIndex.indices());
    }

    public void stats(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name) || !LuceneIndex.exists(name)) {
            return;
        }
        File file = LuceneIndex.path(name).toFile();
        if (file.exists()) {
            Map<String, Object> map = new HashMap<>();
            long size = LuceneIndex.size(file), k = 1024, m = k * k;
            map.put("size", size > m ? (size / m + "M") : size / k + "K");
            IndexSearcher searcher = LucenePlus.getSearcher(name);
            map.put("total", searcher.count(new MatchAllDocsQuery()));
            map.put(name, LuceneIndex.index(name));
            HandlerUtil.setResp(exchange, map);
        }
    }
}

package com.xlongwei.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.networknt.utility.StringUtils;

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
        LucenePlus.getWriter(name, HandlerUtil.getParam(exchange, "analyzer"));
        List<?> list = (List<?>) HandlerUtil.fromJson(HandlerUtil.getBodyString(exchange)).get("fields");
        List<LuceneField> fields = new ArrayList<>();
        if (list != null && list.size() > 0) {
            for (Object obj : list) {
                if (obj instanceof Map) {
                    Map map = (Map) obj;
                    LuceneField field = new LuceneField();
                    field.setName(Objects.toString(map.get("field"), "").toLowerCase());
                    field.setType(Objects.toString(map.get("type"), "").toLowerCase());
                    field.setSort(Objects.toString(map.get("sort"), "").toLowerCase());
                    if (field.checkValid()) {
                        String store = Objects.toString(map.get("store"), "");
                        // string默认存储，适合主键，其他类型默认不存储，或手动指定store:yes
                        field.setStore(StringUtils.isNotBlank(store) ? "yes".endsWith(store)
                                : "string".equals(field.getType()));
                        fields.add(field);
                    }
                }
            }
        }
        // fields不是null，会触发读取indices.json索引字段定义
        LucenePlus.fields(name, fields);
        fields = LucenePlus.fields(name);
        HandlerUtil.setResp(exchange, Collections.singletonMap("fields", fields));
    }

    /** {name,add:[{name:value}]} */
    public void docs(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name)) {
            return;
        }

    }

    /** {name,query} */
    public void search(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name)) {
            return;
        }
        String query = HandlerUtil.getParam(exchange, "query");
        // 实现简单的QueryParser功能
    }

    public void close(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name)) {
            return;
        }
        LucenePlus.close(name);
    }

    public void drop(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name)) {
            return;
        }
        LucenePlus.drop(name);
    }

}

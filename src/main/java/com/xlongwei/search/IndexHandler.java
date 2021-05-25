package com.xlongwei.search;

import com.networknt.utility.StringUtils;

import io.undertow.server.HttpServerExchange;

/**
 * 索引管理类
 */
public class IndexHandler extends SearchHandler {

    /** {name,analyzer} */
    public void open(HttpServerExchange exchange) throws Exception {
        String name = HandlerUtil.getParam(exchange, "name");
        if (StringUtils.isBlank(name)) {
            return;
        }
        LucenePlus.getWriter(name, HandlerUtil.getParam(exchange, "analyzer"));
    }

    /** {name,fields:[{field,type,store}],add:[{name:value}]} */
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

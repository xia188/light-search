package com.xlongwei.search;

import java.lang.reflect.Modifier;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.networknt.handler.LightHttpHandler;
import com.networknt.utility.StringUtils;

import io.undertow.server.HttpServerExchange;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索服务入口类
 */
@Slf4j
public class ServiceHandler implements LightHttpHandler {

    public ServiceHandler() {
        String clazzName = getClass().getName();
        String packageName = clazzName.substring(0, clazzName.lastIndexOf('.'));
        List<Class<?>> clazzList = HandlerUtil.scanClass(null, packageName, false);
        for (Class<?> clazz : clazzList) {
            boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
            boolean isSearch = SearchHandler.class.isAssignableFrom(clazz);
            if (!isAbstract && isSearch) {
                try {
                    log.info("{} => {}", SearchHandler.name(clazz), clazz.getName());
                    clazz.getConstructor().newInstance();
                } catch (Exception e) {
                    log.warn("fail to init {}", clazz);
                }
            }
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        long start = exchange.getRequestStartTime() > 0 ? exchange.getRequestStartTime() : System.nanoTime();
        Map<String, Deque<String>> queryParameters = exchange.getQueryParameters();
        String service = queryParameters.remove("*").getFirst();
        log.info("{} {}", exchange.getRequestMethod(), exchange.getRequestURI());
        int dot = service.indexOf('.');
        String[] split = StringUtils.split(dot > 0 ? service.substring(0, dot) : service, "/");
        if (split.length > 0) {
            String name = split[0];
            SearchHandler handler = SearchHandler.handler(name);
            if (handler != null) {
                String path = split.length > 1 ? split[1] : StringUtils.EMPTY;
                SearchHandler.path(exchange, path);
                HandlerUtil.parseBody(exchange);
                handler.handleRequest(exchange);
            }
        }
        String response = HandlerUtil.sendResp(exchange);
        if(StringUtils.isNotBlank(response)) {
            log.info("res({}): {}", (System.nanoTime() - start) / 1000, response);
        }
    }

}

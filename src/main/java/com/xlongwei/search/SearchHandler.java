package com.xlongwei.search;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.networknt.handler.LightHttpHandler;
import com.networknt.utility.StringUtils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索服务父类
 */
@Slf4j
public abstract class SearchHandler implements LightHttpHandler {

    private static final AttachmentKey<String> PATH = AttachmentKey.create(String.class);
    private static Map<String, SearchHandler> handlers = new HashMap<>();
    private Map<String, Method> methods = new HashMap<>();

    public SearchHandler() {
        Method[] declaredMethods = getClass().getDeclaredMethods();
        String name = name();
        handlers.put(name, this);
        for (Method method : declaredMethods) {
            boolean isPublic = Modifier.isPublic(method.getModifiers());
            boolean hasExchange = method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == HttpServerExchange.class;
            boolean isVoid = method.getReturnType().equals(Void.TYPE);
            if (isPublic && hasExchange && isVoid) {
                methods.put(method.getName(), method);
                log.info("{}/{}", name, method.getName());
            }
        }
    }

    /**
     * DistrictHandler => district
     */
    public String name() {
        return name(getClass());
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String path = exchange.getAttachment(PATH);
        Method method = methods.get(StringUtils.isBlank(path) ? name() : path);
        if (method != null) {
            try {
                method.invoke(this, exchange);
                return;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (e instanceof InvocationTargetException) {
                    InvocationTargetException ex = (InvocationTargetException) e;
                    Throwable t = ex.getTargetException();
                    if (t != null) {
                        msg = t.getMessage();
                    }
                }
                log.warn("service handle failed, path: {}, ex: {}", path, msg);
                HandlerUtil.setResp(exchange, Collections.singletonMap("error", msg));
            }
        }
    }

    public static void path(HttpServerExchange exchange, String path) {
        exchange.putAttachment(PATH, path);
    }

    public static String path(HttpServerExchange exchange) {
        return exchange.getAttachment(PATH);
    }

    public static SearchHandler handler(String name) {
        return handlers.get(name);
    }

    public static String name(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        int handler = simpleName.indexOf("Handler");
        String name = handler > 0 ? simpleName.substring(0, handler) : simpleName;
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }
}

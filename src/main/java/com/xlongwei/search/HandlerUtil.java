package com.xlongwei.search;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.networknt.config.Config;
import com.networknt.utility.NioUtils;
import com.networknt.utility.StringUtils;
import com.networknt.utility.Util;

import org.apache.commons.codec.CharEncoding;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormData.FormValue;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.FormParserFactory.Builder;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具类
 */
@Slf4j
public class HandlerUtil {
    private static final AttachmentKey<Map<String, Object>> BODY = AttachmentKey.create(Map.class);
    private static final String BODYSTRING = "BODYSTRING";
    private static final AttachmentKey<Object> RESP = AttachmentKey.create(Object.class);
    private static String[] patterns = new String[] { "yyyy-MM-dd HH:mm:ss", "yyyyMMddHHmmss", "yyyy-MM-dd", "yyyyMMdd",
            "yyyy/MM/dd", "yyyy.MM.dd", "yyyy-MM-ddTHH:mm:ss" };

    /**
     * 解析body为Map<String, Object> <br>
     * Object可能是String、List<String>、FileItem、List<FileItem>
     * 
     * @param exchange
     */
    public static void parseBody(HttpServerExchange exchange) {
        Map<String, Object> body = new HashMap<>(4);
        Map<String, Deque<String>> params = exchange.getQueryParameters();
        for (Entry<String, Deque<String>> entry : params.entrySet()) {
            String param = entry.getKey();
            Deque<String> deque = entry.getValue();
            if (deque.size() > 1) {
                body.put(param, new ArrayList<>(deque));
            } else {
                body.put(param, deque.getFirst());
            }
        }
        String contentType = Optional.ofNullable(exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE))
                .orElse(StringUtils.EMPTY).toLowerCase();
        try {
            exchange.startBlocking();// 参考BodyHandler
            boolean isForm = StringUtils.isNotBlank(contentType) && (contentType.startsWith("multipart/form-data")
                    || contentType.startsWith("application/x-www-form-urlencoded"));
            boolean isText = !isForm && (StringUtils.isBlank(contentType) || contentType.contains("json")
                    || contentType.contains("text") || contentType.contains("xml"));
            if (isForm) {
                Builder builder = FormParserFactory.builder();
                builder.setDefaultCharset(CharEncoding.UTF_8);
                FormParserFactory formParserFactory = builder.build();
                // MultiPartParserDefinition#93，exchange.addExchangeCompleteListener，在请求结束时关闭parser并删除临时文件
                FormDataParser parser = formParserFactory.createParser(exchange);
                if (parser != null) {
                    FormData formData = parser.parseBlocking();
                    for (String name : formData) {
                        Deque<FormValue> deque = formData.get(name);
                        if (deque.size() > 1) {
                            List<Object> list = new ArrayList<>();
                            for (FormValue formValue : deque) {
                                list.add(formValue.isFileItem() ? formValue : formValue.getValue());
                            }
                            body.put(name, list);
                        } else {
                            FormValue formValue = deque.getFirst();
                            body.put(name, formValue.isFileItem() ? formValue : formValue.getValue());
                        }
                    }
                } else {
                    InputStream inputStream = exchange.getInputStream();
                    body.put(BODYSTRING, NioUtils.toString(inputStream));
                    inputStream.close();
                }
            } else if (isText) {
                InputStream inputStream = exchange.getInputStream();
                body.put(BODYSTRING, NioUtils.toString(inputStream));
                inputStream.close();
            } else {
                log.info("not suppoert Content-Type: {}", contentType);
            }
            String string = (String) body.get(BODYSTRING);
            if (string != null && string.length() > 0) {
                if (string.contains("{")) {
                    Map<String, Object> bodyMap = fromJson(string);
                    if (bodyMap != null && bodyMap.size() > 0) {
                        body.putAll(bodyMap);
                    }
                } else if (string.contains("=")) {
                    Arrays.stream(string.split("[&]")).map(kv -> kv.split("[=]")).collect(Collectors.toList()).stream()
                            .forEach(kv -> body.put(Util.urlDecode(kv[0]), Util.urlDecode(kv[1])));
                }
            }
        } catch (Exception e) {
            log.info("fail to parse body: {}", e.getMessage());
        }
        if (!body.isEmpty()) {
            log.debug("body: {}", body);
            exchange.putAttachment(BODY, body);
        }
    }

    /** 获取请求参数 */
    public static String getParam(HttpServerExchange exchange, String name) {
        return getObject(exchange, name, String.class);
    }

    /** FormValue包含fileName+FileItem */
    public static FormValue getFile(HttpServerExchange exchange, String name) {
        return getObject(exchange, name, FormValue.class);
    }

    /** 支持参数或正文 */
    public static String getParamOrBody(HttpServerExchange exchange, String name) {
        String param = getObject(exchange, name, String.class);
        if (StringUtils.isBlank(param)) {
            param = getObject(exchange, BODYSTRING, String.class);
        }
        return param;
    }

    /** 获取正文字符串 */
    public static String getBodyString(HttpServerExchange exchange) {
        return getObject(exchange, BODYSTRING, String.class);
    }

    /** 获取请求参数名集合 */
    public static Set<String> getParamNames(HttpServerExchange exchange) {
        Map<String, Object> body = exchange.getAttachment(BODY);
        return body != null ? body.keySet() : Collections.emptySet();
    }

    /**
     * @param obj
     * @param clazz 支持String、FormValue、List
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T getObject(HttpServerExchange exchange, String name, Class<T> clazz) {
        Map<String, Object> body = exchange.getAttachment(BODY);
        if (body != null) {
            Object obj = body.get(name);
            if (obj != null) {
                Class<? extends Object> clz = obj.getClass();
                if (clazz == clz || clazz.isAssignableFrom(clz)) {
                    return (T) obj;
                } else if (List.class.isAssignableFrom(clz)) {
                    obj = ((List) obj).get(0);
                    clz = obj.getClass();
                    if (clazz == clz || clazz.isAssignableFrom(clz)) {
                        return (T) obj;
                    }
                } else if (String.class == clazz) {
                    return (T) obj.toString();
                }
            }
        }
        return null;
    }

    /** 获取日期参数 */
    public static Date parseDate(String string, Date defDate) {
        if (StringUtils.isNotBlank(string)) {
            int length = string.length();
            // yyyyMd=6 yyyy-MM-dd HH:mm:ss=19
            if (6 <= length && length <= 19) {
                for (String pattern : patterns) {
                    try {
                        return new SimpleDateFormat(pattern).parse(string);
                    } catch (Exception e) {

                    }
                }
            }
        }
        return defDate;
    }

    /** 获取整数参数 */
    public static long parseLong(String string, long defLong) {
        if (StringUtils.isNotBlank(string)) {
            try {
                return Long.parseLong(string);
            } catch (Exception e) {

            }
        }
        return defLong;
    }

    /** 获取浮点参数 */
    public static double parseDouble(String string, double defDouble) {
        if (StringUtils.isNotBlank(string)) {
            try {
                return Double.parseDouble(string);
            } catch (Exception e) {

            }
        }
        return defDouble;
    }

    /** 仅支持map，其他类型需手动响应 */
    public static void setResp(HttpServerExchange exchange, Map<String, ?> map) {
        exchange.putAttachment(HandlerUtil.RESP, map);
    }

    /** 仅支持json，其他类型需手动响应 */
    public static String sendResp(HttpServerExchange exchange) {
        if (exchange.isComplete()) {
            return null;
        }
        Object resp = exchange.removeAttachment(HandlerUtil.RESP);
        String response = toJson(resp);
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
        exchange.setStatusCode(200);
        exchange.getResponseSender().send(response);
        return response;
    }

    @SuppressWarnings({ "unchecked" })
    public static Map<String, Object> fromJson(String json) {
        try {
            if (StringUtils.isNotBlank(json)) {
                return Config.getInstance().getMapper().readValue(json, Map.class);
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
        }
        return Collections.emptyMap();
    }

    public static String toJson(Object obj) {
        try {
            if (obj != null) {
                return Config.getInstance().getMapper().writeValueAsString(obj);
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
        }
        return StringUtils.EMPTY;
    }
}

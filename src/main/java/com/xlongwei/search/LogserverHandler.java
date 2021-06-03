package com.xlongwei.search;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.networknt.utility.StringUtils;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.undertow.server.HttpServerExchange;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogserverHandler extends SearchHandler {

    private static String name = SearchHandler.name(LogserverHandler.class);
    private static String tokenKey = "logserver.token", token = System.getProperty(tokenKey, System.getenv(tokenKey));

    public void pages(HttpServerExchange exchange) throws Exception {
        String logs = HandlerUtil.getParam(exchange, "logs");
        String search = HandlerUtil.getParam(exchange, "search");
        String day = StringUtils.isBlank(logs) ? null
                : (logs.contains("-") ? logs.substring(logs.lastIndexOf(".") + 1) : LocalDate.now().toString());
        int pageSize = (int) HandlerUtil.parseLong(HandlerUtil.getParam(exchange, "pageSize"), 1000L);
        List<Integer[]> pages = pages(day, search, pageSize);
        HandlerUtil.setResp(exchange, Collections.singletonMap("pages", pages));
    }

    public void delete(HttpServerExchange exchange) throws Exception {
        String day = HandlerUtil.getParam(exchange, "day");
        if (StringUtils.isNotBlank(day)) {
            IndexWriter writer = LucenePlus.getWriter(name);
            // 删除day之前的索引，因为logserver已经tgz压缩了日志文件，搜索也没用了
            writer.deleteDocuments(new TermRangeQuery("day", null, new BytesRef(day), false, false));
            // writer.deleteDocuments(new Term("day", day));
            writer.flush();
            writer.commit();
            LucenePlus.closeSearcher(name);
            HandlerUtil.setResp(exchange, Collections.singletonMap("delete", true));
        } else {
            HandlerUtil.setResp(exchange, Collections.singletonMap("delete", false));
        }
    }

    public void log(HttpServerExchange exchange) throws Exception {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        String loggerName = HandlerUtil.getParam(exchange, "logger");
        List<ch.qos.logback.classic.Logger> loggers = null;
        if (StringUtils.isNotBlank(loggerName)) {
            ch.qos.logback.classic.Logger logger = lc.getLogger(loggerName);
            if (logger != null) {
                loggers = Arrays.asList(logger);
                String levelName = HandlerUtil.getParam(exchange, "level");
                if (StringUtils.isNotBlank(levelName)) {
                    if (StringUtils.isBlank(token) || token.equals(HandlerUtil.getParam(exchange, "token"))) {
                        Level level = Level.toLevel(levelName, null);
                        log.warn("change logger:{} level from:{} to:{}", logger.getName(), logger.getLevel(), level);
                        logger.setLevel(level);
                    }
                }
            }
        }
        if (loggers == null) {
            loggers = lc.getLoggerList();
        }
        log.info("check logger level, loggers:{}", loggers.size());
        List<Map<String, String>> list = loggers.stream().sorted((a, b) -> a.getName().compareTo(b.getName()))
                .map(logger -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("logger", logger.getName());
                    map.put("level", Objects.toString(logger.getLevel(), ""));
                    return map;
                }).collect(Collectors.toList());
        Map<String, Object> map = new HashMap<>();
        map.put("loggers", list);
        HandlerUtil.setResp(exchange, map);
    }

    public static List<Integer[]> pages(String day, String search, int pageSize) throws Exception {
        if (StringUtils.isBlank(day) && StringUtils.isBlank(search)) {
            return Collections.emptyList();
        }
        Builder builder = new BooleanQuery.Builder();
        if (StringUtils.isNotBlank(day)) {
            builder.add(new TermQuery(new Term("day", day)), Occur.MUST);
        }
        if (StringUtils.isNotBlank(search)) {
            builder.add(LucenePlus.termQuery(name, Collections.singletonMap("line", search)), Occur.MUST);

        }
        BooleanQuery query = builder.build();
        IndexSearcher searcher = LucenePlus.getSearcher(name);
        TopDocs topDocs = searcher.search(query, 100000);// 1000*100
        if (topDocs.scoreDocs != null && topDocs.scoreDocs.length > 0) {
            Map<Integer, Integer> pages = new HashMap<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                int line = Integer.parseInt(doc.get("number"));
                Integer calcPage = (line - 1) / pageSize + 1;
                Integer pageCount = pages.get(calcPage);
                pages.put(calcPage, pageCount == null ? 1 : pageCount + 1);
            }
            return pages.entrySet().stream().map(entry -> new Integer[] { entry.getKey(), entry.getValue() })
                    .sorted((arr1, arr2) -> {
                        return arr1[0].compareTo(arr2[0]);
                    }).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

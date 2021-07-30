package com.xlongwei.search;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.networknt.utility.StringUtils;

import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
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

    public void list(HttpServerExchange exchange) throws Exception {
        String search = HandlerUtil.getParam(exchange, "search");
        List<String> list = new LinkedList<>();
        IndexSearcher searcher = LucenePlus.getSearcher(name);
        LocalDate day = LocalDate.now();
        while (true) {
            Builder builder = new BooleanQuery.Builder();
            builder.add(new TermQuery(new Term("day", day.toString())), Occur.MUST);
            TopDocs topDocs = searcher.search(builder.build(), 1);
            if (!LucenePlus.hasDoc(topDocs)) {
                break;
            }
            builder.add(LucenePlus.termQuery(name, Collections.singletonMap("line", search)), Occur.MUST);
            topDocs = searcher.search(builder.build(), 1);
            if (LucenePlus.hasDoc(topDocs)) {
                list.add(day.toString());
            }
            day = day.minusDays(1);
        }
        ;
        HandlerUtil.setResp(exchange, Collections.singletonMap("list", list));
    }

    public void pages(HttpServerExchange exchange) throws Exception {
        String logs = HandlerUtil.getParam(exchange, "logs");
        String search = HandlerUtil.getParam(exchange, "search");
        String day = (StringUtils.isNotBlank(logs) && logs.contains("-") ? logs.substring(logs.lastIndexOf(".") + 1)
                : LocalDate.now().toString());
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
                if (StringUtils.isNotBlank(levelName) && !HandlerUtil.badToken(exchange)) {
                    Level level = Level.toLevel(levelName, null);
                    log.warn("change logger:{} level from:{} to:{}", logger.getName(), logger.getLevel(), level);
                    logger.setLevel(level);
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
        if (StringUtils.isBlank(day) || StringUtils.isBlank(search)) {
            return Collections.emptyList();
        }
        List<Integer[]> list = new LinkedList<>();
        IndexSearcher searcher = LucenePlus.getSearcher(name);
        int page = 0;
        while (true) {
            Builder builder = new BooleanQuery.Builder();
            builder.add(new TermQuery(new Term("day", day)), Occur.MUST);
            builder.add(IntPoint.newRangeQuery("number", page * pageSize, (page + 1) * pageSize - 1), Occur.MUST);
            TopDocs topDocs = searcher.search(builder.build(), 1);
            if (!LucenePlus.hasDoc(topDocs)) {
                break;
            }
            builder.add(LucenePlus.termQuery(name, Collections.singletonMap("line", search)), Occur.MUST);
            topDocs = searcher.search(builder.build(), 10);
            if (LucenePlus.hasDoc(topDocs)) {
                list.add(new Integer[] { page + 1, topDocs.scoreDocs.length });
            }
            page = page + 1;
        }
        return list;
    }
}

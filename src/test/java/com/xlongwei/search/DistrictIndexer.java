package com.xlongwei.search;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.networknt.client.Http2Client;
import com.networknt.cluster.Cluster;
import com.networknt.config.Config;
import com.networknt.service.SingletonServiceFactory;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.junit.jupiter.api.Test;
import org.xnio.OptionMap;

import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DistrictIndexer {

    private static Cluster cluster = (Cluster) SingletonServiceFactory.getBean(Cluster.class);
    private static Http2Client client = Http2Client.getInstance();
    private static ClientConnection connection = null;

    @Test
    public void createIndex() throws Exception {
        Map<String, Object> config = Config.getInstance().getJsonMapConfig("lucene");
        String index = (String) config.get("index"), district = "district";
        FSDirectory fsDirectory = NIOFSDirectory.open(Path.of(index, district));
        IndexWriter indexWriter = new IndexWriter(fsDirectory, new IndexWriterConfig());
        AtomicInteger counter = new AtomicInteger(0);
        Consumer<Map<String, String>> add = (map) -> {
            int count = counter.incrementAndGet();
            Document doc = new Document();
            doc.add(new StringField("code", map.get("code"), Store.YES));
            doc.add(new TextField("name", map.get("code"), Store.NO));
            try {
                indexWriter.addDocument(doc);
                if (count % 10000 == 0) {
                    indexWriter.flush();
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                System.exit(0);
            }
        };
        long start = System.currentTimeMillis();
        consume("Province", add);
        log.info("Province done counter={}", counter.get());
        consume("City", add);
        log.info("City done counter={}", counter.get());
        consume("County", add);
        log.info("County done counter={}", counter.get());
        consume("Town", add);
        log.info("Town done counter={}", counter.get());
        consume("Village", add);
        log.info("Village done counter={}", counter.get());
        indexWriter.flush();
        indexWriter.commit();
        indexWriter.close();
        fsDirectory.close();
        log.info("millis={}", System.currentTimeMillis() - start);
        log.info("success createIndex");// counter=677391
    }

    @Test
    public void province() throws Exception {
        consume("Province", (map) -> {
            log.info("code={} name={}", map.get("code"), map.get("name"));
        });
    }

    @Test
    public void search() throws Exception {
        String name = "四川广安";
        Map<String, List<String>> search = DistrictHandler.search(name);
        log.info("search={}", search);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void consume(String table, Consumer<Map<String, String>> consumer) throws Exception {
        Map<String, Object> page = new HashMap<>();
        // 对于district只需code,name字段
        page.put(table, Collections.singletonMap("@column", "code,name"));
        // pageSize最大值1000，超过此值light4j会拒绝响应数据
        int pageNo = 0, pageSize = 1000;
        page.put("count", pageSize);
        page.put("page", pageNo);
        boolean nextPage = false;
        do {
            nextPage = false;
            Map<String, Object> request = Collections.singletonMap(table + "[]", page);
            String response = post(HandlerUtil.toJson(request));
            Map<String, Object> parseJson = response == null ? Collections.emptyMap() : HandlerUtil.fromJson(response);
            Object object = parseJson.get(table + "[]");
            if (object != null && object instanceof List) {
                List list = (List) object;
                list.forEach(consumer);
                if (list.size() >= pageSize) {
                    nextPage = true;
                    pageNo += 1;
                    page.put("page", pageNo);
                }
            }
        } while (nextPage);
        log.info("last page={}", pageNo);
    }

    public static String post(String body) throws Exception {
        String serviceToUrl = cluster.serviceToUrl("http", "com.xlongwei.light4j", null, null);
        log.info("serviceToUrl={} body={}", serviceToUrl, body);
        if (connection == null || !connection.isOpen()) {
            connection = client
                    .connect(new URI(serviceToUrl), Http2Client.WORKER, null, Http2Client.BUFFER_POOL, OptionMap.EMPTY)
                    .get();
        }
        ClientRequest clientRequest = new ClientRequest().setMethod(Methods.POST).setPath("/service/apijson/get");
        clientRequest.getRequestHeaders().put(Headers.HOST, "localhost");
        clientRequest.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
        AtomicReference<ClientResponse> responseReference = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        connection.sendRequest(clientRequest, client.createClientCallback(responseReference, latch, body));
        latch.await(10000, TimeUnit.MILLISECONDS);
        ClientResponse clientResponse = responseReference.get();
        if (clientResponse != null && clientResponse.getResponseCode() == 200) {
            return clientResponse.getAttachment(Http2Client.RESPONSE_BODY);
        } else {
            return "{}";
        }
    }
}

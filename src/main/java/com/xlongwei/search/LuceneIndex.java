package com.xlongwei.search;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.config.Config;
import com.networknt.utility.StringUtils;

import io.undertow.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class LuceneIndex {

    private String analyzer;
    private String directory;// nio mmap
    private String lockFactory;// no fs
    private boolean realtime;// no yes
    private List<LuceneField> fields;

    private static final String CONFIG_NAME = "lucene";
    private static Map<String, LuceneIndex> indices = new HashMap<>();

    static {
        // load indices.json to indices
        load();
    }

    /** 获取name索引配置 */
    public static LuceneIndex index(String name) {
        return indices.get(name);
    }

    /** 创建或删除name索引配置 */
    public static boolean index(String name, LuceneIndex index) {
        if (index == null) {
            index = indices.remove(name);
            if (index != null) {
                return save();
            }
        } else {
            if (!indices.containsKey(name)) {
                indices.put(name, index);
                return save();
            }
        }
        return false;
    }

    /** 获取name字段列表 */
    public static List<LuceneField> fields(String name) {
        LuceneIndex index = index(name);
        return index != null && index.getFields() != null ? index.getFields() : Collections.emptyList();
    }

    /** 获取所有索引配置 */
    public static Map<String, LuceneIndex> indices() {
        return indices;
    }

    /** 获取name是否近实时 */
    public static boolean realtime(String name) {
        LuceneIndex index = index(name);
        return index != null ? index.isRealtime() : false;
    }

    /** 判断name索引是否存在 */
    public static boolean exists(String name) {
        return indices.containsKey(name);
    }

    /** 获取lucene配置 */
    public static Map<String, Object> config() {
        return Config.getInstance().getJsonMapConfig(CONFIG_NAME);
    }

    /** 获取name索引路径 */
    public static Path path(String name) {
        String index = (String) config().get("index");
        return Paths.get(index, name);
    }

    /** 获取file大小 */
    public static long size(File file) {
        if (file.isFile()) {
            return file.length();
        }
        long size = 0;
        for (File f : file.listFiles()) {
            if (f.isFile()) {
                size += f.length();
            } else {
                size += size(f);
            }
        }
        return size;
    }

    // load indices=Map<String, LuceneIndex> from indices.json
    private static void load() {
        File file = indicesFile();
        try {
            String json = null;
            if (file.exists()) {
                json = FileUtils.readFile(new FileInputStream(file));
            }
            json = StringUtils.isNotBlank(json) ? json : "{}";
            try {
                indices = Config.getInstance().getMapper().readValue(json,
                        new TypeReference<Map<String, LuceneIndex>>() {
                        });
            } catch (Exception e) {
                // 兼容0.1版本配置
                Map<String, List<LuceneField>> fields = Config.getInstance().getMapper().readValue(json,
                        new TypeReference<Map<String, List<LuceneField>>>() {
                        });
                if (fields != null && fields.size() > 0) {
                    fields.entrySet().forEach(entry -> {
                        LuceneIndex index = new LuceneIndex();
                        index.setFields(entry.getValue());
                        indices.put(entry.getKey(), index);
                    });
                    save();
                }
            }
        } catch (Exception e) {
            log.warn("fail to load indices", e);
        }
    }

    // save indices=Map<String, LuceneIndex> to indices.json
    private static boolean save() {
        File file = indicesFile();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            String json = Config.getInstance().getMapper().writeValueAsString(indices);
            writer.write(json);
            return true;
        } catch (Exception e) {
        }
        return false;
    }

    private static File indicesFile() {
        Map<String, Object> config = config();
        String index = (String) config.get("index");
        String indices = (String) config.get("indices");
        return new File(index, indices);
    }
}

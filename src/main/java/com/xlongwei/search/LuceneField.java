package com.xlongwei.search;

import java.util.Arrays;

import com.networknt.utility.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LuceneField {
    private String name, type, sort;
    private boolean store;

    public static String[] types = "string,text,int,long,float,double,date,binary".split("[,]");
    public static String[] sorts = "sorted,sortedset,numeric,sortednumeric".split("[,]");

    static {
        // sort for binarySearch
        Arrays.sort(types);
        Arrays.sort(sorts);
    }

    // 命名isValid会导致json多个valid字段
    public boolean checkValid() {
        return StringUtils.isNotBlank(name) && StringUtils.isNotBlank(type) && Arrays.binarySearch(types, type) >= 0
                && (StringUtils.isBlank(sort) || Arrays.binarySearch(sorts, sort) >= 0);
    }
}

package com.xlongwei.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.networknt.utility.StringUtils;

import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.document.BinaryPoint;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

import lombok.Data;

@Data
@SuppressWarnings({ "rawtypes" })
public class LuceneField {
    private String name, type, sort;
    private boolean store;

    private static String[] types = "string,text,store,int,long,float,double,date,binary".split("[,]");
    private static String[] sorts = "sorted,sortedset,numeric,sortednumeric".split("[,]");

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

    public Object resolve(Document doc) {
        String[] values = null;
        switch (type) {
            case "string":
            case "text":
            case "store":
            case "date":// store时原样存储
                values = doc.getValues(name);
                return values.length > 1 ? values
                        : (values.length == 1 ? StringUtils.trimToEmpty(values[0]) : StringUtils.EMPTY);
            case "int":
            case "long":
                values = doc.getValues(name);
                return values.length > 1 ? Arrays.stream(values).mapToLong(Long::parseLong).toArray()
                        : (values.length == 1 ? HandlerUtil.parseLong(values[0], 0L) : StringUtils.EMPTY);
            case "float":
            case "double":
                values = doc.getValues(name);
                return values.length > 1 ? Arrays.stream(values).mapToDouble(Double::parseDouble).toArray()
                        : (values.length == 1 ? HandlerUtil.parseDouble(values[0], 0.0) : StringUtils.EMPTY);
            case "binary":
                BytesRef[] binaryValues = doc.getBinaryValues(name);
                return binaryValues.length > 1
                        ? Arrays.stream(binaryValues).map(bv -> Base64.encodeBase64String(bv.bytes)).toArray()
                        : (binaryValues.length == 1 ? Base64.encodeBase64String(binaryValues[0].bytes)
                                : StringUtils.EMPTY);
        }
        return StringUtils.EMPTY;
    }

    public static List<Field> docFields(Map row, List<LuceneField> fields) {
        List<Field> list = new ArrayList<>();
        for (LuceneField field : fields) {
            String name = field.getName();
            Object value = row.get(name);
            List listValues = value instanceof List ? (List) value : null;
            String stringValue = listValues != null ? null : Objects.toString(value, StringUtils.EMPTY);
            switch (field.getType()) {
                case "string":
                    stringFields(list, stringValue, listValues, field);
                    break;
                case "text":
                    textFields(list, stringValue, listValues, field);
                    break;
                case "store":
                    storeFields(list, stringValue, listValues, field);
                    break;
                case "int":
                    intFields(list, stringValue, listValues, field);
                    break;
                case "long":
                    longFields(list, stringValue, listValues, field);
                    break;
                case "date":// 按long处理
                    dateFields(list, stringValue, listValues, field);
                    break;
                case "float":
                    floatFields(list, stringValue, listValues, field);
                    break;
                case "double":
                    doubleFields(list, stringValue, listValues, field);
                    break;
                case "binary":
                    binaryFields(list, stringValue, listValues, field);
                    break;
            }
        }
        return list;
    }

    private static void binaryFields(List<Field> list, String stringValue, List listValues, LuceneField field) {
        if (listValues != null) {
            if (listValues.size() > 0) {
                byte[][] arr = new byte[listValues.size()][];
                for (int i = 0; i < arr.length; i++) {
                    String string = Objects.toString(listValues.get(i), StringUtils.EMPTY);
                    arr[i] = Base64.decodeBase64(string);
                    storeField(list, string, field);
                }
                list.add(new BinaryPoint(field.getName(), arr));
            }
        } else {
            if (StringUtils.isNotBlank(stringValue)) {
                list.add(new BinaryPoint(field.getName(), Base64.decodeBase64(stringValue)));
                storeField(list, stringValue, field);
            }
        }
    }

    private static void doubleFields(List<Field> list, String stringValue, List listValues, LuceneField field) {
        if (listValues != null) {
            if (listValues.size() > 0) {
                double[] arr = new double[listValues.size()];
                for (int i = 0; i < arr.length; i++) {
                    String string = Objects.toString(listValues.get(i), StringUtils.EMPTY);
                    arr[i] = Double.parseDouble(string);
                    sortField(list, string, field);
                    storeField(list, string, field);
                }
                list.add(new DoublePoint(field.getName(), arr));
            }
        } else {
            if (StringUtils.isNotBlank(stringValue)) {
                list.add(new DoublePoint(field.getName(), Double.parseDouble(stringValue)));
                sortField(list, stringValue, field);
                storeField(list, stringValue, field);
            }
        }
    }

    private static void floatFields(List<Field> list, String stringValue, List listValues, LuceneField field) {
        if (listValues != null) {
            if (listValues.size() > 0) {
                float[] arr = new float[listValues.size()];
                for (int i = 0; i < arr.length; i++) {
                    String string = Objects.toString(listValues.get(i), StringUtils.EMPTY);
                    arr[i] = Float.parseFloat(string);
                    sortField(list, string, field);
                    storeField(list, string, field);
                }
                list.add(new FloatPoint(field.getName(), arr));
            }
        } else {
            if (StringUtils.isNotBlank(stringValue)) {
                list.add(new FloatPoint(field.getName(), Float.parseFloat(stringValue)));
                sortField(list, stringValue, field);
                storeField(list, stringValue, field);
            }
        }
    }

    private static void dateFields(List<Field> list, String stringValue, List listValues, LuceneField field) {
        if (listValues != null) {
            if (listValues.size() > 0) {
                long[] arr = new long[listValues.size()];
                for (int i = 0; i < arr.length; i++) {
                    String string = Objects.toString(listValues.get(i), StringUtils.EMPTY);
                    arr[i] = HandlerUtil.parseDate(string, null).getTime();
                    sortField(list, String.valueOf(arr[i]), field);
                    storeField(list, string, field);
                }
                list.add(new LongPoint(field.getName(), arr));
            }
        } else {
            if (StringUtils.isNotBlank(stringValue)) {
                long longValue = HandlerUtil.parseDate(stringValue, null).getTime();
                list.add(new LongPoint(field.getName(), longValue));
                sortField(list, String.valueOf(longValue), field);
                storeField(list, stringValue, field);
            }
        }
    }

    private static void longFields(List<Field> list, String stringValue, List listValues, LuceneField field) {
        if (listValues != null) {
            if (listValues.size() > 0) {
                long[] arr = new long[listValues.size()];
                for (int i = 0; i < arr.length; i++) {
                    String string = Objects.toString(listValues.get(i), StringUtils.EMPTY);
                    arr[i] = Long.parseLong(string);
                    sortField(list, string, field);
                    storeField(list, string, field);
                }
                list.add(new LongPoint(field.getName(), arr));
            }
        } else {
            if (StringUtils.isNotBlank(stringValue)) {
                list.add(new LongPoint(field.getName(), Long.parseLong(stringValue)));
                sortField(list, stringValue, field);
                storeField(list, stringValue, field);
            }
        }
    }

    private static void intFields(List<Field> list, String stringValue, List listValues, LuceneField field) {
        if (listValues != null) {
            if (listValues.size() > 0) {
                int[] arr = new int[listValues.size()];
                for (int i = 0; i < arr.length; i++) {
                    String string = Objects.toString(listValues.get(i), StringUtils.EMPTY);
                    arr[i] = Integer.parseInt(string);
                    sortField(list, string, field);
                    storeField(list, string, field);
                }
                list.add(new IntPoint(field.getName(), arr));
            }
        } else {
            if (StringUtils.isNotBlank(stringValue)) {
                list.add(new IntPoint(field.getName(), Integer.parseInt(stringValue)));
                sortField(list, stringValue, field);
                storeField(list, stringValue, field);
            }
        }
    }

    private static void storeFields(List<Field> list, String stringValue, List listValues, LuceneField field) {
        if (listValues != null) {
            for (Object obj : listValues) {
                String string = Objects.toString(obj, StringUtils.EMPTY);
                if (field.isStore()) {
                    list.add(new StoredField(field.getName(), string));
                }
                sortField(list, string, field);
            }
        } else {
            if (field.isStore()) {
                list.add(new StoredField(field.getName(), stringValue));
            }
            sortField(list, stringValue, field);
        }
    }

    private static void textFields(List<Field> list, String stringValue, List listValues, LuceneField field) {
        if (listValues != null) {
            for (Object obj : listValues) {
                String string = Objects.toString(obj, StringUtils.EMPTY);
                list.add(new TextField(field.getName(), string, field.isStore() ? Store.YES : Store.NO));
                sortField(list, string, field);
            }
        } else {
            list.add(new TextField(field.getName(), stringValue, field.isStore() ? Store.YES : Store.NO));
            sortField(list, stringValue, field);
        }
    }

    private static void stringFields(List<Field> list, String stringValue, List listValues, LuceneField field) {
        if (listValues != null) {
            for (Object obj : listValues) {
                String string = Objects.toString(obj, StringUtils.EMPTY);
                list.add(new StringField(field.getName(), string, field.isStore() ? Store.YES : Store.NO));
                sortField(list, string, field);
            }
        } else {
            list.add(new StringField(field.getName(), stringValue, field.isStore() ? Store.YES : Store.NO));
            sortField(list, stringValue, field);
        }
    }

    private static void sortField(List<Field> list, String string, LuceneField field) {
        if (StringUtils.isBlank(string) || StringUtils.isBlank(field.getSort())) {
            return;
        }
        switch (field.getSort()) {
            case "sorted":
                list.add(new SortedDocValuesField(field.getName(), new BytesRef(string)));
                break;
            case "sortedset":
                list.add(new SortedSetDocValuesField(field.getName(), new BytesRef(string)));
                break;
            case "numeric":
                list.add(new NumericDocValuesField(field.getName(),
                        "float".equals(field.getType()) ? NumericUtils.floatToSortableInt(Float.parseFloat(string))
                                : ("double".equals(field.getType())
                                        ? NumericUtils.doubleToSortableLong(Double.parseDouble(string))
                                        : Long.parseLong(string))));
                break;
            case "sortednumeric":
                list.add(new SortedNumericDocValuesField(field.getName(),
                        "float".equals(field.getType()) ? NumericUtils.floatToSortableInt(Float.parseFloat(string))
                                : ("double".equals(field.getType())
                                        ? NumericUtils.doubleToSortableLong(Double.parseDouble(string))
                                        : Long.parseLong(string))));
                break;
        }
    }

    // 对于int,long,float,double,date需要存储时添加额外的StoreField
    private static void storeField(List<Field> list, String string, LuceneField field) {
        if (field.isStore()) {
            if ("binary".equals(field.getType())) {
                list.add(new StoredField(field.getName(), Base64.decodeBase64(string)));
            } else {
                list.add(new StoredField(field.getName(), string));
            }
        }
    }
}

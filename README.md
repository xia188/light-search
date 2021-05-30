# light-search

#### 介绍
基于light-4j和lucene的搜索服务

#### 软件架构
总体原则是搜索与数据解耦，light-search支持搜索和创建索引，light4j提供具体数据。前期实现了DistrictIndexer从light4j拉数据来建索引，后期计划实现light4j向DistrictHandler推数据创建索引，见[DistrictUtilTest](https://gitee.com/xlongwei/light4j/blob/master/src/test/java/com/xlongwei/light4j/DistrictUtilTest.java)。目前只是用了StandardAnalyzer，后期会考虑Ansj分词。


#### 安装教程

1.  sh start.sh jar
2.  sh start.sh jars
3.  sh start.sh start

#### district索引

1.  数据条数：677391
2.  索引大小：26M（压缩后9.8M），索引参数：code:string,name:text
3.  支持查询：name逐字匹配，code长度匹配（限定类型），code前缀匹配（限定地域）

### logserver索引

1.  配合[logserver](https://gitee.com/xlongwei/logserver)使用，改善搜索效果，但:=-等特殊符号处理不理想
2.  索引参数：day:text,number:store,line:text
3.  支持查询：line前缀匹配，day索引清理，number返回行号

#### 索引规则

1.  IndexHandler处理/service/index/*请求，校验参数，响应报文
2.  LucenePlus管理索引：open打开索引，docs推送文档，close关闭索引，search搜索，drop删除索引
3.  LuceneField处理字段：POSt /service/index/open {name:索引,fields:[{field:名称,type:类型,store:存储,sort:排序}]}}

```
#indices.json
analyzer:std，分词
directory:nio mmap，目录
lockFactory:no fs，锁
realtime:no yes，实时
#field,type:必填，sort,store可选
type:string,text,store,int,long,float,double,date,binary
sort:sorted,sortedset,numeric,sortednumeric
store:yes,no
#string text store支持可变多值，int long date等其他类型维度不可变，可以无值，但有值时必须内容合法
string:建索引，不分词，默认存储，适合主键
text:建索引，有分词，默认不存储，适合搜索字段
store:仅存储，无索引，不分词，默认存储。code不用来搜索，因此store类型就够了
int,long,float,double,date:数值型，支持范围搜索，默认不存储，需手动指定存储和排序
binary:字节内容，org.apache.commons.codec.binary.Base64#decodeBase64解码字符串，传值时使用encodeBase64即可
```




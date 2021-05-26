# light-search

#### Description
this is a search service base on light-4j and lucene.

#### Software Architecture
Software architecture description

#### Installation

1.  sh start.sh jar
2.  sh start.sh jars
3.  sh start.sh start

#### district index

1.  data rows: 677391
2.  data size: 26M, index: code:string,name:text
3.  xxxx

#### index
1.  IndexHandler: /service/index/*
2.  LucenePlus: open docs close search drop
3.  LuceneField: POSt /service/index/open {name,fields:[{field,type,store,sort}]}}

```
#field,type:required，sort,store:optional
type:string,text,store,int,long,float,double,date,binary
sort:sorted,sortedset,numeric,sortednumeric
store:yes,no

string:Store.YES Indexed
text:Analyzed Indexed, Store.NO
store:Store.YES
int,long,float,double,date:Numric values, Store.NO
binary:byte[] org.apache.commons.codec.binary.Base64#decodeBase64 encodeBase64
```


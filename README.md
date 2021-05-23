# light-search

#### 介绍
基于light-4j和lucene的搜索服务

#### 软件架构
总体原则是搜索与数据解耦，light-search支持搜索和创建索引，light4j提供具体数据。前期实现了DistrictIndexer从light4j拉数据来建索引，后期计划实现light4j向DistrictHandler推数据创建索引。目前只是用了StandardAnalyzer，后期会考虑Ansj分词。


#### 安装教程

1.  sh start.sh jar
2.  sh start.sh jars
3.  sh start.sh start

#### district索引

1.  数据条数：677391
2.  索引大小：26M（压缩后9.8M）

#### 参与贡献

1.  Fork 本仓库
2.  新建 Feat_xxx 分支
3.  提交代码
4.  新建 Pull Request




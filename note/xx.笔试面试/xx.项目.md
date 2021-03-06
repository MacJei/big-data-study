# 集群规模

- 12台物理机
- 128G内存，8T机械，2T固态，20核40线程，志强E5，DELL



# 爱标客离线数仓项目



## 数据量计算

爱标客每日数据量：10w日活，300（条），2k（数据量） = 3000w*2k = 60G数据

每天平均日志速度： 3000w/24/3600=340条/s

低谷每s：40条

高峰每s(2-20倍)：3400条/s

每条数据2k，平均每s数据量：6.8M



## 框架结构

- 日志部分：web-flume-kafka-flume-hdfs-hive-mysql
- 业务数据部分：mysql-sqoop-hdfs-hive-mysql



## 介绍

- 使用sparkCore进行数据清洗，对敏感数据脱敏，对不符合的字段进行过滤

- 数仓主要是做用户行为数仓和标注业务数仓，主要分为2个部分
- 对于用户行为
  - 数据源是日志，从flume收集发送给kafka，然后在通过kafka发送给flume，通过flume传输给hdfs，有些服务，比如业务线，数据量不大，就直接通过封装的api发送给kafka了
- 对于标注业务数仓
  - 通过sqoop每日导入前一天的数据
- 所有数据处理完成后通过sqoop导出到mysql。由于sqoop的底层是MR任务，同时是只有Map阶段，没有Reduce阶段，就会有事务一致性的问题，如果使用默认设置，可能会造成脏数据
  - 通过设置一个参数是stage-table的方式，先将数据导入到一个mysql临时表，然后都导入成功后，开启一个mysql事务，将临时表的数据导入到目标表中，注意目标表和临时表的schema要一致
    - 如果失败了，就会清空临时表数据，防止脏数据的产生
  - sqoop还有个坑是从mysql导入到hive表的时候，对于null，双方表现不一样，mysql中，是null，在hive中就是\N，需要使用--input-null-string执行null的字符，导出的时候使用--null-string
- kafka主题分类
  - 日志：启动主题，用户行为，故障日志等
  - 业务：任务主题，勋章主题，消息通知等



## 架构

- 主要划分4层，ods，dwd，dws，ads层

- ods层存储着原始数据

- dwd层依据ods层的数据进行解析，清除脏数据

  - 使用spark做ETL数据清洗，敏感信息处理，电话，身份证脱敏，掩码，加密，截取等
  - ==导入表使用parquet格式 以及snappy压缩==

- dws对dwd层数据做轻度聚合，主要是一些宽表数据

- ads是聚合后的数据，数据量很小，一般是聚合的指标数据，可以导入到mysql中

- 数仓是基于星型模型的，基于维度表建模

- 把表分为四类，不同类型的表使用不同的==同步策略==

  - 维度表：业务的状态，编号解释，码表，全量更新

    - 任务状态表：完成，操作中，开始，提交，保存等
    - 一级分类：音频，视频，图片，文本
    - 二级分类：文本-政法，教育，评测，音频-方言，英语，日语
    - 对维度表进行降维处理，以事实表为中心的星型模型，在dws层形成宽表时降维

  - 实体表，每日增量更新，使用日期作为分区字段

    - 任务表：任务id，任务名称，任务类型，创建时间，渠道，任务状态，操作时间，批次id等（每日增量）
    - 用户表：用户id，用户名称，电话，email，标注任务类型，地区，积分等（mysql导入，每日全量，用户量级不大）
    - 团队表：团队id，团队名称，团队类型，创建时间，发布任务数等
    - 勋章表：勋章id，勋章通过率，勋章名称，勋章类型，任务id，创建时间等

  - 事务型事实表，每日增量更新，使用日期作为分区字段

    - 用户行为表：uid，操作时间，批次id，任务id，操作类型，ip（通过ip分析地区）等
    - 积分表：uid，积分，创建时间，任务id等（积分可以提现）

    - 任务明细表：标注id，用户id，创建时间，提交状态，结果详情，标注类型，任务类型等

  - 周期型事实表，使用拉链表，只记录更新的部分，使用开始时间和结束时间分区，从mysql中使用sqoop导入

    - 任务进度表：标注id，用户id，创建时间，更新时间，任务状态（保存，提交，开始，进行中，完成，打回等），结果信息，任务类型等
    - 操作日志，uid，操作时间，操作类型等

- dws层使用uid将任务表，任务进度表，任务明细表，形成宽表，用户行为表形成宽表-
- 总结
  - 实体表不大的，可以做每日全量
  - 对于维度表，不大，可以做每日全量，对于某些维度，不会变化的，保存一份，手动更新
  - 事务型事实表，如用户行为表，操作日志等，每日数据量比较大，需要历史数据的，依据时间做每日新增，利用分区表，每日做分区存储
  - 周期型事务表，如任务进度表，有周期性变化的，需要反映不同时间点的状态的，需要做拉链表，记录每条记录的生命周期，对开始时间和结束时间做分区



## 指标

- 日活，周活，月活统计
  - 每日的依据key进行聚合，求mid的总数
- 用户新增统计，日新增，周新增，月新增
  - 日新增举例：用每日用户行为表与用户表left join，用户表id列为null的表示新增）
- 用户留存率
  - 计算7日留存：7号新增用户明细 join 8号活跃用户明细可以得到 8日留存用户数 / 7号新增用户人数，依次类推7号和9号，7号和10号等留存率
- 沉默用户数
  - 注册之后超过7日未登录
  - 对每日活跃用户使用group by uid 得到该uid的登录时间的合集，对该合集过滤，找到记录的时间只有一个，且与当前时间是超过7天的用户明细，再次基础上统计个数
- 本周回流的用户数
  - 本周活跃-上周新增-上周活跃
  - 只在本周活跃，上周不活跃，上周不新增的用户
  - 活跃用户 left join 上周活跃 t1 left join on 上周新增 用户  t2，得到t1,t2的id都是null的用户
- 统计流失用户
  - 7日未登录的用户
  - 对活跃用户宽表 group by uid 得到多个登录时间，having max(dt) 超过当前7日的留下
- 最近连续三周活跃用户
  - 使用周活跃用户表-一个uid一周只有一个记录，查询三周的uid详情，对uid进行分组 留下count(1)=3的，再统计uid个数）
- 最近7天连续3天活跃
  - 对最近7天每日活跃用户对uid，dt,以及日期进行窗口函数rank,使用日期-rank排名，如果是连续登陆，则差值是相同的，然后对uid和差值进行分组计算uid的个数，>3 表示连续登陆，本质上是2个等差数列相减

- 任务总数统计

  - 细分为标注，检查，质检总数，以及各自的正确率，打回率
  - group by dt

- 当日，当周，当月待支付数额、已支付数额

- 完成任务用户占比

  - 完成任务用户数/日活数

- 用户勋章通过率

  - 当日完成勋章用户/当日新增用户

- 用户完成任务==漏斗分析==

  - 当日访问人数/当日参加任务人数/完成任务人数/完成检查任务人数/完成质检任务人数

- 任务重复放弃率

  - 任务被放弃，重复领取
  - 任务领取总数、单次放弃次数，多次放弃次数

  - 从任务明细宽表（含任务id，任务执行情况，是否被放弃，任务类型）子查询放弃次数，对子查询结果次数sum(if(count>2),1,0) 多次放弃 

- 任务重复放弃前十统计

- 关于即席查询：
  presto：原理预计算，shuffle不落盘，在内存中计算
  impala，Druid
  kylin，Cube，预计算



## 用户画像

- 走的时候在做，参与了部分
- 说一下大概思路
- 用户画像我的理解就是打标签，给每个用户打上不同的标签，然后存储成一个宽表，查询的时候使用sparkSQL，或者MR进行查询特定标签的人群，可以做一些定制化的运营
  - 比如，某个用户他标签是音频翻译这个标签，同时这个标签的值是他的平均正确率，那么在有紧急翻译的任务时，运营就可以通过后台的运营系统给他发送消息，提前通知他任务信息
- 首先设计一个表标签窄表（uid，日期类型，数值类型，字符串类型，分区字段—tag，表示标签名称）
  - 标签窄表3个类型
    - 用户属性标签表，性别，年龄，工作，学历，注册时间等等，从mysql导入
    - 用户行为标签表，是否近7日内活跃，音频正确率，文本正确率，在线时长等标签，编写SparkSQL进行统计
      - 7日活跃，7日内上线就算活跃
    - 外部表，其他平台导入的表，或是人工标注的标签（曾经刷单等）
- 每日凌晨更新窄表，再合并成宽表（因为用户量级不高，是全量更新的）

```sql
drop table if exists dm.user_tag;
create table if not exists dm.user_tag as
select
	uid,
	max(case when tag_id="age" then int_tag_vale end) as  age,
	max(case when tag_id="gender" then str_tag_value end) as gender,
	max(case when tag_id="register_date" then date_tag_value end) as register_date,
	max(case when tag_id="seven_days_active" then int_tag_value end) as seven_days_active
from dm.user_tag_narrow group by uid;
```

- 后期优化
  - 考虑到宽表存储在HDFS上，查询是时候也会比较慢，可以存在ES中，使用ES查询时聚合



# 爱标客实时计算项目



## 架构

- web端的flume收集数据发送给kafka集群
- canal从数据库更新数据给kafka集群
- sparkStreaming接收
  - 一些指标简单处理结果给es或redis
  - 一些指标结果给mysql
- web服务从es读取数据进行分析汇总



## 指标

- 每日日活实时统计
- 日活分时趋势统计
- 日活昨日对比统计
  - 使用redis的set集合做uid的去重，将登录的用户信息存储在ES中
  - 通过ES查询出每日日活，分时日活（存储在ES中有日期，小时，分钟，时间戳，用于不同维度的查询）
- 每日完成任务量实时统计
- 完成任务量分时趋势统计
  - 直接通过sparkStreaming 保存到ES中
- 每日领取任务量实时统计
- 当日任务金额统计
- 对完成的任务进行用户分析（根据区域，性别，年龄等）
- 异常刷单用户实时运营
  - 10分钟内超过3次提交，一般任务都是超过5分钟的，算作刷单，将用户uid记录在mysql异常刷单表中，如果用户已在，则不进行保存，后期给运营人员处理
  - 使用sparkStreaming的窗口函数进行计算，10分钟窗口，5分钟滑动，1分钟单位时间
  - 实现kafka的精准一次性消费

- 实时统计当日提现金额数
  - 使用kafka精准一次性消费，使用了checkpoint，用于状态恢复
  - 通过实时统计保存在mysql中



## 技术点

- sparkStreaming精准一次性消费
- 如何做到spark-streaming和kafka之间做到精确一次性消费
  - 首先kafka要是direct模式，用于消息的拉取控制
    - direct模式的好处
      - 并行度的问题，rdd算子的分区数和kafka的分区数是一致的
      - 高效，如果使用receiver模式实现数据零丢失，需要使用WAL（预写日志），影响吞吐量
      - 可以实现精确一次消费
  - 其次，要看使用的算子中有没有shuffle的算子，如果有则需要在foreachRDD中实现事务，而不能在foreachPartition实现事务，否则会产生序列化的问题



# 三个具体指标较难，对运营-营销非常有价值，帮助他们做了什么事，怎么做的



## 异常刷单用户实时运营

- 针对刷单问题，需要进行风险控制，找出刷单疑似名单给运营
- 技术难点在于kafka的精确一次消费，做了技术调研，重点在于消费消息操作和提交kafka偏移量在一个事务中
- 业务上，针对单位滑动窗口时间内多次异常提交进行名单汇
- 技术上，需要kafka的精确消费，否则会有重复消费的可能，造成数据不精准



## 最近7天连续3日活跃用户

- 对日活表首先定义查询的时间范围7天，然后对uid进行分组查询，对登录时间进行rank over 开窗函数操作
- 得到rank值，通过时间-rank值得到标识位列，基于此进行group by  计算 count的值是>=3的算作3日连续活跃的用户

- 将用户输出给运营，通过系统推荐任务



## 任务重复放弃多次明细

- 难度不大，但是很有意义，毕竟是刚做的大数据

- 某一个任务被重复放弃多次，说明该任务制定的有问题
- 统计一个任务被放弃2次，放弃3次的
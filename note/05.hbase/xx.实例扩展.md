# 谷粒微博



## 需求

- 微博内容的浏览，数据库表设计

- 用户社交体现：关注用户，取关用户

- 拉取关注的人的微博内容



## 分析



### 关系表

- 和mysql中的关系表不同的是，这里将关系以列族的方式存储
- 好处是避免数据量过大时进行分区
- 添加fans列族，可以反向查询

<img src="img/37.png" alt="1" style="zoom:67%;" /> 



### 收件箱

- 存储一定的版本的微博数据，A可以查询到C的最新的N条微博数据
- 等于实现了mysql中的limit

![](img/38.png) 



![谷粒微博表设计](img/39.png) 

- 关系表中，uid作为列族，里面的值无所谓，只要有列存在，说明关注和fans



## 实现



### 创建命名空间以及表名的定义

```java
// 创建命名空间名称
private static final String NAMESPACE = "Weibo";
// 微博内容表，表名
private static final byte[] TABLE_CONTENT = Bytes.toBytes("Weibo:content");
// 用户关系表
private static final byte[] TABLE_RELATIONS = Bytes.toBytes("Weibo:relations");
// 微博收件箱表
private static final byte[] TABLE_RECEIVE_CONTENT = Bytes.toBytes("Weibo:receive_content");

public void initNamespace(){
    try {
        // 创建之前已经判断
        BaseApi.createNamespace(
            NAMESPACE,
            ImmutableMap.of("creator","stt",
                            "create_time",System.currentTimeMillis()+"")
        );
    } catch (IOException e) {
        throw new RuntimeException(e);
    }finally {
        try {
            BaseApi.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```



### 创建微博内容表

| **方法名**   | creatTableeContent |
| ------------ | ------------------ |
| Table Name   | weibo:content      |
| RowKey       | 用户ID_时间戳      |
| ColumnFamily | info               |
| ColumnLabel  | 标题,内容,图片     |
| Version      | 1个版本            |

```java
public void createTableContent(){
    try {
        BaseApi.createTable(TABLE_CONTENT,1,1,"info");
    } catch (IOException e) {
        throw new RuntimeException(e);
    }finally {
        try {
            BaseApi.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```



### 创建用户关系表

| **方法名**   | createTableRelations   |
| ------------ | ---------------------- |
| Table Name   | weibo:relations        |
| RowKey       | 用户ID                 |
| ColumnFamily | attends、fans          |
| ColumnLabel  | 关注用户ID，粉丝用户ID |
| ColumnValue  | 用户ID                 |
| Version      | 1个版本                |

```java
public void createTableRelations(){
    try {
        BaseApi.createTable(TABLE_RELATIONS,1,1,"attends","fans");
    } catch (IOException e) {
        e.printStackTrace();
    }finally {
        try {
            BaseApi.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```



### 创建用户微博内容接收邮件表

| **方法名**   | createTableReceiveContentEmails |
| ------------ | ------------------------------- |
| Table Name   | weibo:receive_content_email     |
| RowKey       | 用户ID                          |
| ColumnFamily | info                            |
| ColumnLabel  | 用户ID                          |
| ColumnValue  | 取微博内容的RowKey              |
| Version      | 1000                            |

```java
public void createTableReceiveContent(){
    try {
        BaseApi.createTable(TABLE_RECEIVE_CONTENT,1000,1000,"info");
    } catch (IOException e) {
        e.printStackTrace();
    }finally {
        try {
            BaseApi.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```



### 发布微博内容

- 微博内容表中添加1条数据

- 微博收件箱表对所有粉丝用户添加数据

```java
public void publishMessage(String uid,String content){
    try {
        // 增加A微博的数据
        String rowKey = uid+"_"+System.currentTimeMillis();
        BaseApi.addOrUpdateData(TABLE_CONTENT,rowKey,"info","content",content);

        // 获取A的所有fans数据
        List<String> fanIds = getFanIds(uid);

        // 向A的所有fans的收件箱增加数据,增加的是内容的rowKey
        if(!CollectionUtils.isEmpty(fanIds)){
            putContent(fanIds,uid,rowKey);
        }
    } catch (IOException e) {
        e.printStackTrace();
    }finally {
        try {
            BaseApi.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

public List<String> getFanIds(String userId){
    List<String> ids = new ArrayList<>();
    try {
        Table table = getConnection().getTable(TableName.valueOf(TABLE_RELATIONS));
        Get get = new Get(Bytes.toBytes(userId))
            .addFamily(Bytes.toBytes("fans"));
        Result result = table.get(get);
        for (Cell cell : result.rawCells()) {
            ids.add(Bytes.toString(CellUtil.cloneQualifier(cell)));
        }
        table.close();
    } catch (IOException e) {
        e.printStackTrace();
    }
    return ids;
}
public void putContent(List<String> fanIds,String uid, String uid_ts){
    try {
        Table table = getConnection().getTable(TableName.valueOf(TABLE_RECEIVE_CONTENT));
        List<Put> puts = new ArrayList<>();
        for (String fanId : fanIds) {
            Put put = new Put(Bytes.toBytes(fanId))
              .addColumn(Bytes.toBytes("info"),Bytes.toBytes(uid),Bytes.toBytes(uid_ts));
            puts.add(put);
        }
        table.put(puts);
        table.close();
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```



### 添加关注用户

- 在微博用户关系表中，对当前主动操作的用户添加新关注的好友

- 在微博用户关系表中，对被关注的用户添加新的粉丝

- 微博收件箱表中添加所关注的用户发布的微博

```java
public void attendUser(String uid,String attends){
    try {
        // 对当前主动操作的用户添加新的关注的好友
        BaseApi.addOrUpdateData(TABLE_RELATIONS,uid,"attends",attends,"1");
        // 微博用户关系表中，对被关注的用户添加粉丝（当前操作的用户）
        BaseApi.addOrUpdateData(TABLE_RELATIONS,attends,"fans",uid,"1");
        // 查询 attends发送的数据
        List<String> weiboRowkey = scanWeibo(attends);

        if(!CollectionUtils.isEmpty(weiboRowkey)){
            // 当前操作用户的微博收件箱添加所关注的用户发布的微博rowkey
            Table table = getConnection().getTable(TableName.valueOf(TABLE_RECEIVE_CONTENT));
            List<Put> puts = new ArrayList<>();
            for (String val : weiboRowkey) {
                // 注意相同的family，相同的column，赋值多个value，需要指定不同的timestamp
                Long ts = Long.MAX_VALUE - Long.valueOf(val.split("_")[1]);
                Put put = new Put(Bytes.toBytes(uid))
                    .addColumn(Bytes.toBytes("info"),Bytes.toBytes(attends),ts,Bytes.toBytes(val));
                puts.add(put);
            }
            if(!CollectionUtils.isEmpty(puts)){
                // 增加数据
                table.put(puts);
            }
            // 关闭表格
            table.close();
        }

    } catch (IOException e) {
        e.printStackTrace();
    }finally {
        try {
            BaseApi.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/**
	 * 查询最近的数据
	 * @param attends
	 * @return
	 */
private List<String> scanWeibo(String attends) {
    // 获取5条
    List<String> content_id = new ArrayList<>(5);
    try {
        Table table = getConnection().getTable(TableName.valueOf(TABLE_CONTENT));
        Scan scan = new Scan();
        // attends_ < xxxx < attends_|
        // | 在ascii中，第二大符号
        scan.setStartRow(Bytes.toBytes(attends+"_"));
        scan.setStopRow(Bytes.toBytes(attends+"_|"));
        ResultScanner scanner = table.getScanner(scan);
        for (Result result : scanner) {
            if(content_id.size() >= 5){
                break;
            }
            for (Cell cell : result.rawCells()) {
                // 获取内容的rowKey
                content_id.add(Bytes.toString(CellUtil.cloneRow(cell)));
            }
        }
        scanner.close();
        table.close();
    } catch (IOException e) {
        e.printStackTrace();
    }
    return content_id;
}
```

- 查看收取的内容

```bash
hbase(main):020:0> get 'Weibo:receive_content','1002',{COLUMN=>'info:1001',VERSIONS=>5}
scan 'Weibo:receive_content',{RAW=>true,VERSIONS=>5}
```



### 获取关注的人的微博内容

- 从微博收件箱中获取所关注的用户的微博RowKey 

- 根据获取的RowKey，得到微博内容

```java
package com.stt.demo.hbase.Ch03_guli_weibo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Message {
	private String uid;
	private String content;
	private String timestamp;
}
```

```java
// 获取关注人的微博内容
public List<Message> getAttendsContent(String uid) {
    List<Message> re = new ArrayList<>();
    try {
        //获取收件箱中uid关注的信息
        List<String> contentRowKeys = BaseApi.getRowDataList(TABLE_RECEIVE_CONTENT,uid,5);

        // 通过内容的rowKey查询微博内容表获取相关结果
        Table table = getConnection().getTable(TableName.valueOf(TABLE_CONTENT));
        List<Get> gets = new ArrayList<>();
        contentRowKeys.forEach(k->gets.add(new Get(Bytes.toBytes(k))));
        Result[] results = table.get(gets);
        for (Result result : results) {
            if(!result.isEmpty()){
                for (Cell cell : result.rawCells()) {
                    // 结果封装
                    String rowKey = Bytes.toString(CellUtil.cloneRow(cell));
                    re.add(Message.builder()
                           .uid(rowKey.substring(0,rowKey.indexOf("_")))
                           .timestamp(rowKey.substring(rowKey.indexOf("_")+1))
                           .content(Bytes.toString(CellUtil.cloneValue(cell)))
                           .build());
                }
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    }finally {
        try {
            BaseApi.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    return re;
}
```



### 取关用户

- 在微博用户关系表中，对当前主动操作的用户移除取关的好友(attends)

- 在微博用户关系表中，对被取关的用户移除粉丝

- 微博收件箱中删除取关的用户发布的微博

```java
/**
	 * 取消关注
	 */
public void removeAttends(String uid,String ... attends){
    try {
        // 删除uid的关注信息
        BaseApi.deleteData(TABLE_RELATIONS,uid,"attends",attends);

        // 删除attends的fans中的uid信息
        Table table = getConnection().getTable(TableName.valueOf(TABLE_RELATIONS));
        List<Delete> deletes = Lists.newArrayList();
        for (String attend : attends) {
            deletes.add(new Delete(Bytes.toBytes(attend)).addColumn(Bytes.toBytes("fans"),Bytes.toBytes(uid)));
        }
        table.delete(deletes);
        table.close();

        // 从收件箱中删除相应的微博记录
        BaseApi.deleteData(TABLE_RECEIVE_CONTENT,uid,"info",attends);

    } catch (IOException e) {
        e.printStackTrace();
    }finally {
        try {
            BaseApi.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

- 注意type类型是DeleteColumn，添加了一个条记录
  - 不会马上删除，在生成具体的文件的时候删除
  - 注意timestamp大小，这里由于rowkey的设计加上LongMax的减法，导致timestamp过大，而新加入的记录timestamp过小，从而导致删除失败

```bash
hbase(main):005:0> scan 'Weibo:receive_content',{RAW=>true,VERSIONS=>5}
ROW                                  COLUMN+CELL                                        
 1002                                column=info:1001, timestamp=9223370467435889608, type=Delete                                           
 1002                                column=info:1001, timestamp=9223370467435889608, value=1001_1569418886199  
 1002                                column=info:1001, timestamp=9223370467435888100, value=1001_1569418887707                              
 1002                                column=info:1001, timestamp=9223370467435888092, value=1001_1569418887715                              
 1002                                column=info:1001, timestamp=1569467026605, type=DeleteColumn   
```



### 测试

```java
package com.stt.demo.hbase.Ch03_guli_weibo;

import java.util.List;

public class WeiboMain {

	public static void main(String[] args) {
		WeiboComponent com = new WeiboComponent();

//		com.initNamespace();
//		com.createTableContent();
//		com.createTableRelations();
//		com.createTableReceiveContent();

		// 1001 发微博

//		com.publishMessage("1001","hello1");
//		com.publishMessage("1001","hello2");
//		com.publishMessage("1001","hello3");

		// A 关注 B

//		com.attendUser("1002","1001");

		// A 查看 B 的微博

//		String uid = "1002";
//		List<Message> msg = com.getAttendsContent(uid);
//		for (Message message : msg) {
//			System.out.println(message);
//		}

		// A 取消关注 B

//		com.removeAttends("1002","1001");
	}
}
```

### 工具类

```java
package com.stt.demo.hbase.Ch01_api;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.CollectionUtils;
import scala.Int;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BaseApi {

	// 获取配置对象
	public static Configuration config = HBaseConfiguration.create();
	public static ThreadLocal<Admin> adminThreadLocal = new ThreadLocal<>();
	public static ThreadLocal<Connection> connThreadLocal = new ThreadLocal<>();

	static {
		// 若在resources中没有hbase-site.xml配置,则需要添加如下
//		config.set("hbase.zookeeper.quorum","hadoop102");
//		config.set("hbase.zookeeper.property.clientPort","2181");
	}

	public static void main(String[] args) throws Exception {
		String tableName = "student";
//		boolean isExists = isTableExists("student");
//		boolean isExists2 = isTableExists("test:user");
//		System.out.println(isExists);
//		System.out.println(isExists2);

//		createTable("test:createUser","info");

//		dropTable("test:createUser");

//		createNamespace("myNamespace");

//		addOrUpdateData("test:user","1004","info","name","stt");
//		addOrUpdateData("test:user","1004","info","age","11");

//		deleteData("test:user","1004","info","name");
//		deleteData("test:user","1004","info","age");
//		deleteRowData("test:user","1003");

//		getAllData("test:user");
//		getRowData("test:user","1002");
//		getRowQualifier("test:user","1002","info","name");

		addObject(Student.builder().rowKey("1005").address("addr2").age("11").name("ceshi").build());


		close();
	}

	public static Connection getConnection() throws IOException {
		Connection connection = connThreadLocal.get();
		if(connection == null){
			// 建立连接
			connection = ConnectionFactory.createConnection(config);
			connThreadLocal.set(connection);
		}
		return connection;
	}

	public static Admin getAdmin() throws IOException {
		Admin admin = adminThreadLocal.get();
		if (admin == null){
			// 建立连接,过期的
			// HBaseAdmin admin = new HBaseAdmin(config);
			// 获取admin对象
			admin = getConnection().getAdmin();
			adminThreadLocal.set(admin);
		}
		return admin;
	}

	public static void close() throws IOException {
		Admin admin = adminThreadLocal.get();
		if(admin != null){
			admin.close();
			adminThreadLocal.remove();
			connThreadLocal.remove();
		}
	}

	// 通过异常判断命名空间是否存在
	public static boolean isNamespaceExists(String namespaceName){
		try{
			getAdmin().getNamespaceDescriptor(namespaceName);
			return true;
		} catch (NamespaceNotFoundException e) {
			return false;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void createNamespace(String namespaceStr,Map<String,String> confg) throws IOException {
		if(isNamespaceExists(namespaceStr)){
			System.out.println("命名空间已存在");
			return;
		}
		NamespaceDescriptor build = NamespaceDescriptor
				.create(namespaceStr)
				.build();
		if(confg != null && confg.size() != 0){
			confg.forEach((k,v) -> build.setConfiguration(k,v));
		}
		getAdmin().createNamespace(build);
	}

	public static void createNamespace(String namespaceStr) throws IOException {
		createNamespace(namespaceStr,null);
	}

	public static boolean isTableExists(String tableName) throws IOException {
		Admin admin = getAdmin();
		return admin.tableExists(TableName.valueOf(tableName));
	}

	public static void createTable(String tableName,String columnFamily,Integer maxVersions,Integer minVersions,String ... columnFamilies) throws IOException {

		if(isTableExists(tableName)){
			System.out.println("表已经存在");
			return;
		}

		Admin admin = getAdmin();
		// 获取表格描述器
		HTableDescriptor hTableDescriptor = new HTableDescriptor(TableName.valueOf(tableName));

		if(!StringUtils.isBlank(columnFamily)){
			// 列族描述器
			HColumnDescriptor family = new HColumnDescriptor(columnFamily);
			//设置块缓存
			family.setBlockCacheEnabled(true);
			//设置块缓存大小
			family.setBlocksize(2097152);
			//设置压缩方式
			//family.setCompressionType(Algorithm.SNAPPY);
			//设置版本确界
			if(maxVersions != null){
				family.setMaxVersions(maxVersions);
			}
			if(minVersions != null){
				family.setMinVersions(minVersions);
			}
			// 添加列族
			hTableDescriptor.addFamily(family);
		}

		if(columnFamilies != null){
			for (String column : columnFamilies) {
				HColumnDescriptor f = new HColumnDescriptor(column)
						.setBlockCacheEnabled(true)
						.setBlocksize(2097152);

				//f.setCompressionType(Algorithm.SNAPPY);

				if(maxVersions != null){
					f.setMaxVersions(maxVersions);
				}
				if(minVersions != null){
					f.setMinVersions(minVersions);
				}
				hTableDescriptor.addFamily(f);
			}
		}
		admin.createTable(hTableDescriptor);
	}

	public static void createTable(String tableName,String ... columnFamilies) throws IOException {
		createTable(tableName,null,null,null,columnFamilies);
	}

	public static void createTable(String tableName,Integer maxVersions,Integer minVersions,String ... columnFamilies) throws IOException {
		createTable(tableName,null,maxVersions,minVersions,columnFamilies);
	}

	public static void dropTable(String tableName) throws IOException {
		if(isTableExists(tableName)){
			Admin admin = getAdmin();
			// 删除表注意要先改状态
			admin.disableTable(TableName.valueOf(tableName));
			admin.deleteTable(TableName.valueOf(tableName));
		}
	}

	public static void addOrUpdateData(String tableName,String rowKey,String family,String column,String ... value) throws IOException {

		Table table = getConnection().getTable(TableName.valueOf(tableName));
		List<Put> puts = new ArrayList<>();

		Long time = System.currentTimeMillis();
		for (String val : value) {
			// 注意相同的family，相同的column，赋值多个value，需要指定不同的timestamp
			Put put = new Put(Bytes.toBytes(rowKey))
					.addColumn(Bytes.toBytes(family),Bytes.toBytes(column),time++,Bytes.toBytes(val));
			puts.add(put);
		}
		if(!CollectionUtils.isEmpty(puts)){
			// 增加数据
			table.put(puts);
		}
		// 关闭表格
		table.close();
	}

	public static void deleteData(String tableName,String rowKey,String family,String ... columns) throws IOException {

		Table table = getConnection().getTable(TableName.valueOf(tableName));
		Delete val = new Delete(Bytes.toBytes(rowKey));

		if(!StringUtils.isBlank(family)){
			if(columns == null || columns.length == 0){
				val.addFamily(Bytes.toBytes(family));
			}else{
				for (String column : columns) {
					// 注意，使用addColumns和addColumn的区别，前者删除所有版本，后者删除最新的
					val.addColumns(Bytes.toBytes(family), Bytes.toBytes(column));
				}
			}
		}

		table.delete(val);
		table.close();
	}

	public static void deleteRowData(String tableName,String rowKey) throws IOException {
		deleteData(tableName,rowKey,null,null);
	}

	public static void deleteMultiRowData(String tableName,String ... rowKeys) throws IOException {
		Table table = getConnection().getTable(TableName.valueOf(tableName));
		List<Delete> val = new ArrayList<>();
		for (String rowKey : rowKeys) {
			val.add(new Delete(Bytes.toBytes(rowKey)));
		}
		table.delete(val);
		table.close();
	}

	public static void getAllData(String tableName) throws IOException {
		Table table = getConnection().getTable(TableName.valueOf(tableName));
		// 全表扫描
		Scan scan = new Scan();
		// 获取结果
		ResultScanner scanner = table.getScanner(scan);

		// 每次读取一批数据，不会一次全部读取
//		scanner.next();

		for (Result result : scanner) {
			Cell[] cells = result.rawCells();
			for (Cell cell : cells) {
				String family = Bytes.toString(CellUtil.cloneFamily(cell));
				String column = Bytes.toString(CellUtil.cloneQualifier(cell));
				String rowKey = Bytes.toString(CellUtil.cloneRow(cell));
				String val = Bytes.toString(CellUtil.cloneValue(cell));
				System.out.println(family+":"+column+":"+rowKey+":"+val);
			}
		}
		scanner.close();
		table.close();
	}

	public static void getRowData(String tableName,String rowKey) throws IOException {
		Table table = getConnection().getTable(TableName.valueOf(tableName));
		Get get = new Get(Bytes.toBytes(rowKey));
//		get.setMaxVersions(); 设置显示所有版本
//		get.setTimeStamp(); 设置显示指定的时间戳版本
		Result result = table.get(get);
		Cell[] cells = result.rawCells();
		for (Cell cell : cells) {
			String family = Bytes.toString(CellUtil.cloneFamily(cell));
			String column = Bytes.toString(CellUtil.cloneQualifier(cell));
			String val = Bytes.toString(CellUtil.cloneValue(cell));
			System.out.println(family+":"+column+":"+rowKey+":"+val);
		}
		table.close();
	}

	public static List<String> getRowDataList(String tableName, String rowKey, Integer versions) throws IOException {
		Table table = getConnection().getTable(TableName.valueOf(tableName));
		Get get = new Get(Bytes.toBytes(rowKey)).setMaxVersions(versions);
//		get.setTimeStamp(); 设置显示指定的时间戳版本
		Result result = table.get(get);
		Cell[] cells = result.rawCells();
		List<String> re = new ArrayList<>();
		for (Cell cell : cells) {
			re.add(Bytes.toString(CellUtil.cloneValue(cell)));
		}
		table.close();
		return re;
	}


	public static void getRowQualifier(String tableName,String rowKey,String family,String qualifier) throws IOException {
		Table table = getConnection().getTable(TableName.valueOf(tableName));

		Get get = new Get(Bytes.toBytes(rowKey));
		get.addColumn(Bytes.toBytes(family),Bytes.toBytes(qualifier));

		Result result = table.get(get);
		Cell[] cells = result.rawCells();
		for (Cell cell : cells) {
			String column = Bytes.toString(CellUtil.cloneQualifier(cell));
			String val = Bytes.toString(CellUtil.cloneValue(cell));
			System.out.println(family+":"+column+":"+rowKey+":"+val);
		}
		table.close();
	}

	public static void addObject(Object object) throws IOException, IllegalAccessException {
		Class clazz = object.getClass();
		HBaseTable hBaseTableAnn = (HBaseTable)clazz.getAnnotation(HBaseTable.class);

		if(hBaseTableAnn!=null){
			Table table = getConnection().getTable(TableName.valueOf(hBaseTableAnn.namespace()+":"+hBaseTableAnn.value()));
			String rowKey = "";
			Field[] fields = clazz.getDeclaredFields();

			for (Field field : fields) {
				HRowKey rowKeyAnn = field.getAnnotation(HRowKey.class);
				if(rowKeyAnn != null){
					field.setAccessible(true);
					rowKey = (String) field.get(object);
					break;
				}
			}

			if(StringUtils.isBlank(rowKey)){
				throw new RuntimeException("rowkey is empty");
			}

			List<Put> vals = new ArrayList<>();
			for (Field field : fields) {
				HBaseColumn columnAnn = field.getAnnotation(HBaseColumn.class);
				if(columnAnn != null){
					field.setAccessible(true);
					String value = (String) field.get(object);
					String family = columnAnn.family();
					String column = columnAnn.column();
					if(StringUtils.isBlank(column)){
						column = field.getName();
					}
					Put val = new Put(Bytes.toBytes(rowKey))
							.addColumn(Bytes.toBytes(family),Bytes.toBytes(column),Bytes.toBytes(value));
					vals.add(val);
				}
			}

			if(!CollectionUtils.isEmpty(vals)){
				// 增加数据
				table.put(vals);
			}
			// 关闭表格
			table.close();
		}
	}
}
```




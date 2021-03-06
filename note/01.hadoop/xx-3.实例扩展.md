# 多Job串联

- 倒排索引案例

  

需求

- 大量文档，网页，需要建立搜索索引，依照关键字查找有哪些文件包含

示例文件：

- a.txt

```text
atguigu pingping
atguigu ss
atguigu ss
```

- b.txt

```text
atguigu pingping
atguigu pingping
pingping ss
```

- c.txt

```text
atguigu ss
atguigu pingping
```

期望输出文件

```text
atguigu	c.txt-->2	b.txt-->2	a.txt-->3	
pingping	c.txt-->1	b.txt-->3	a.txt-->1	
ss	c.txt-->1	b.txt-->1	a.txt-->2
```



## 分析

![1](img/04.mr24.png)





## 实现

- 这里的实现与分析有些不同，但是原理相似

### 第一次job

#### mapper

```java
package com.stt.demo.mr.Ch16_MultiJob;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.IOException;

public class OneIndexMapper extends Mapper<LongWritable,Text,Text,IntWritable>{

	private String fileName;
	private IntWritable v = new IntWritable(1);
	private Text k = new Text();

	@Override
	protected void setup(Context context) throws IOException, InterruptedException {
		fileName = ((FileSplit) context.getInputSplit()).getPath().getName();
	}

	@Override
	protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
//		atguigu pingping
//		转换为 atguigu**fileName.txt-->1
		String[] fields = value.toString().split("\\s+");
		for(String field : fields){
			k.set(field+"**"+fileName);
			context.write(k,v);
		}
	}
}
```

#### reducer

```java
package com.stt.demo.mr.Ch16_MultiJob;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class OneIndexReducer extends Reducer<Text,IntWritable,Text,NullWritable>{

	@Override
	protected void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
		int sum = 0;
		for(IntWritable val : values){
			sum += val.get();
		}
		key.set(key.toString()+"-->"+sum);
		context.write(key,NullWritable.get());
	}
}
```

#### driver

```java
package com.stt.demo.mr.Ch16_MultiJob;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import static com.stt.demo.mr.Constant.*;

public class OneIndexDriver {

	public static void main(String[] args) throws Exception {

		args = new String[]{INPUT+"ch16/input", OUTPUT+"ch16/output"};

		Job job = Job.getInstance(new Configuration());

		job.setJarByClass(OneIndexDriver.class);

		job.setMapperClass(OneIndexMapper.class);
		job.setReducerClass(OneIndexReducer.class);

		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(IntWritable.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(NullWritable.class);

		FileInputFormat.setInputPaths(job,new Path(args[0]));
		FileOutputFormat.setOutputPath(job,new Path(args[1]));

		boolean result = job.waitForCompletion(true);
		System.exit(result ? 0 : 1);
	}
}
```



### 第二次job

#### mapper

```java
package com.stt.demo.mr.Ch16_MultiJob;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class TwoIndexMapper extends Mapper<LongWritable,Text,Text,Text> {

	private Text k = new Text();
	private Text v = new Text();

	@Override
	protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
		//atguigu**a.txt-->3
		String[] fields = value.toString().split("\\*+");
		k.set(fields[0]);
		v.set(fields[1]);
		context.write(k,v);
	}
}
```

#### reducer

```java
package com.stt.demo.mr.Ch16_MultiJob;

import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class TwoIndexReducer extends Reducer<Text,Text,Text,NullWritable> {

	@Override
	protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

		StringBuilder sb = new StringBuilder(key.toString()).append("\t");
		for(Text val : values){
			sb.append(val.toString()).append("\t");
		}
		key.set(sb.toString());
		context.write(key,NullWritable.get());
	}
}
```

#### driver

```java
package com.stt.demo.mr.Ch16_MultiJob;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import static com.stt.demo.mr.Constant.*;

public class TwoIndexDriver {

	public static void main(String[] args) throws Exception {

		args = new String[]{INPUT+"ch16/output/part-r-00000", OUTPUT+"ch16/output2"};

		Job job = Job.getInstance(new Configuration());
		job.setJarByClass(TwoIndexDriver.class);
		job.setMapperClass(TwoIndexMapper.class);
		job.setReducerClass(TwoIndexReducer.class);

		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(NullWritable.class);

		FileInputFormat.setInputPaths(job,new Path(args[0]));
		FileOutputFormat.setOutputPath(job,new Path(args[1]));

		boolean result = job.waitForCompletion(true);
		System.exit(result ? 0 : 1);
	}
}
```



# TopN案例



## 需求

- 输入数据

```text
13470253144	180	180	360
13509468723	7335	110349	117684
13560439638	918	4938	5856
13568436656	3597	25635	29232
13590439668	1116	954	2070
13630577991	6960	690	7650
13682846555	1938	2910	4848
13729199489	240	0	240
13736230513	2481	24681	27162
13768778790	120	120	240
13846544121	264	0	264
13956435636	132	1512	1644
13966251146	240	0	240
13975057813	11058	48243	59301
13992314666	3008	3720	6728
15043685818	3659	3538	7197
15910133277	3156	2936	6092
15959002129	1938	180	2118
18271575951	1527	2106	3633
18390173782	9531	2412	11943
84188413	4116	1432	5548
```

- 按照最后一个值排序的前10输出

```text
13509468723	7335	110349	117684
13975057813	11058	48243	59301
13568436656	3597	25635	29232
13736230513	2481	24681	27162
18390173782	9531	2412	11943
13630577991	6960	690	7650
15043685818	3659	3538	7197
13992314666	3008	3720	6728
15910133277	3156	2936	6092
13560439638	918	4938	5856
```



## 分析

- 在Mapper中对最后一个值进行排序，使用TreeMap进行排序，TreeMap大小设定为10，超过的部分从底部去除
- 在Reducer中也对最后一个数值进行排序，同样使用TreeMap
- 2个流程的map，和reduce结束后，在cleanup阶段进行数据的输出



## 实现



### bean

```java
package com.stt.demo.mr.Ch17_TopN;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.hadoop.io.WritableComparable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

/**
 * 用于统计流量的bean
 */
@Data
@AllArgsConstructor
// 反序列化时，需要反射调用空参构造函数
@NoArgsConstructor
public class FlowBean implements WritableComparable<FlowBean>{

	private long upFlow;
	private long downFlow;
	private long sumFlow;

	public FlowBean(long upFlow, long downFlow){
		this.upFlow = upFlow;
		this.downFlow = downFlow;
		this.sumFlow = upFlow + downFlow;
	}

	// 写序列化方法
	@Override
	public void write(DataOutput out) throws IOException {
		out.writeLong(upFlow);
		out.writeLong(downFlow);
		out.writeLong(sumFlow);
	}

	// 反序列化方法
	@Override
	public void readFields(DataInput in) throws IOException {
		// 反序列化方法必须要和序列化方法的执行顺序保持一致
		this.upFlow = in.readLong();
		this.downFlow = in.readLong();
		this.sumFlow = in.readLong();
	}

	public String toString(){
		return upFlow+"\t"+downFlow+"\t"+sumFlow;
	}

	@Override
	public int compareTo(FlowBean o) {
		// 按照流量总大小倒叙排列
		if(Objects.equals(sumFlow,o.getSumFlow())) return 0;
		return sumFlow > o.getSumFlow() ? -1 : 1;
	}
}
```



### mapper

```java
package com.stt.demo.mr.Ch17_TopN;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class TopNMapper extends Mapper<LongWritable,Text,Text,FlowBean> {

	FlowBean v;
	Text k;
	// 使用treeMap对FlowBean进行排序过滤
	TreeMap<FlowBean,Text> treeMap = new TreeMap<>();

	@Override
	protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
		String[] fields = value.toString().split("\\s+");
		v = new FlowBean(Long.parseLong(fields[1]),Long.parseLong(fields[2]),Long.parseLong(fields[3]));
		k = new Text(fields[0]);
		treeMap.put(v,k);
		if(treeMap.size() > 10){
			treeMap.pollLastEntry();
		}
	}

	@Override
	protected void cleanup(Context context) 
        throws IOException, InterruptedException {
		for(Map.Entry<FlowBean,Text> entry : treeMap.entrySet()){
			context.write(entry.getValue(),entry.getKey());
		}
		treeMap.clear();
	}
}
```



### reducer

```java
package com.stt.demo.mr.Ch17_TopN;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.TreeMap;

public class TopNReducer extends Reducer<Text,FlowBean,Text,FlowBean> {

	TreeMap<FlowBean,Text> treeMap = new TreeMap<>();

	@Override
	protected void reduce(Text key, Iterable<FlowBean> values, Context context) throws IOException, InterruptedException {
		// 对分区数据进行汇总过滤
		for(FlowBean val : values){
			try {
				FlowBean flowBean = new FlowBean();
				// 这里需要注意，val始终是同一个引用
				BeanUtils.copyProperties(flowBean,val);
				treeMap.put(flowBean,key);
				if(treeMap.size() > 10){
					treeMap.pollLastEntry();
				}
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void cleanup(Context context) 
        throws IOException, InterruptedException {
		for(Map.Entry<FlowBean,Text> entry : treeMap.entrySet()){
			context.write(entry.getValue(),entry.getKey());
		}
		// 释放资源
		treeMap.clear();
	}
}
```



### driver

```java
package com.stt.demo.mr.Ch17_TopN;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import static com.stt.demo.mr.Constant.*;

public class TopNDriver {

	public static void main(String[] args) throws Exception {

		args = new String[]{INPUT+"ch17/input.txt", OUTPUT+"ch17/output"};

		Job job = Job.getInstance(new Configuration());

		job.setJarByClass(TopNDriver.class);

		job.setMapperClass(TopNMapper.class);
		job.setReducerClass(TopNReducer.class);

		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(FlowBean.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(FlowBean.class);

		FileInputFormat.setInputPaths(job,new Path(args[0]));
		FileOutputFormat.setOutputPath(job,new Path(args[1]));

		boolean result = job.waitForCompletion(true);
		System.exit(result ? 0 : 1);
	}
}
```



# 查找博客共同被关注



## 需求

- 以下是博客的关注列表数据，冒号前是一个用户，冒号后是该用户关注的用户

- 数据中的关注是==单向==的

- 求出哪些人两两之间有共同被关注，及他俩的共同被关注都有谁

```text
A:B,C,D,F,E,O
B:A,C,E,K
C:F,A,D,I
D:A,E,F,L
E:B,C,D,M,L
F:A,B,C,D,E,O,M
G:A,C,D,E,F
H:A,C,D,E,O
I:A,O
J:B,O
K:A,C,D
L:D,E,F
M:E,F,G
O:A,H,I,J
```



## 分析

- 得到好友的关系，得到某个人被好友的集合
- 第一次处理的集合如下
  - 如显示出A是被I,K,C,B,G,F,H,O,D,都作为好友的

```text
A	I,K,C,B,G,F,H,O,D,
B	A,F,J,E,
C	A,E,B,H,F,G,K,
D	G,C,K,A,L,F,E,H,
E	G,M,L,H,A,F,B,D,
F	L,M,D,C,G,A,
G	M,
H	O,
I	O,C,
J	O,
K	B,
L	D,E,
M	E,F,
O	A,H,I,J,F,
```

- 基于第一次的基础上，产生新的关系，从每一行的value中取出被好友集合的组合
  - 如 `I-K` 和 `K-I` 表示 K 与 I 之间好友有A
  - 对value的 `被好友集合` 进行排序，两两组合，如得到`A-B` 作为key，每一行的第一个元素作为value
  - 在reduce端进行分区汇总，最后得到关系数据

```text
A-B	E C 
A-C	D F 
A-D	E F 
A-E	D B C 
A-F	O B C D E 
A-G	F E C D 
A-H	E C D O 
A-I	O 
A-J	O B 
A-K	D C 
A-L	F E D 
A-M	E F 
B-C	A 
B-D	A E 
B-E	C 
B-F	E A C 
B-G	C E A 
B-H	A E C 
B-I	A 
B-K	C A 
B-L	E 
B-M	E 
B-O	A 
C-D	A F 
C-E	D 
C-F	D A 
C-G	D F A 
C-H	D A 
C-I	A 
C-K	A D 
C-L	D F 
C-M	F 
C-O	I A 
D-E	L 
D-F	A E 
D-G	E A F 
D-H	A E 
D-I	A 
D-K	A 
D-L	E F 
D-M	F E 
D-O	A 
E-F	D M C B 
E-G	C D 
E-H	C D 
E-J	B 
E-K	C D 
E-L	D 
F-G	D C A E 
F-H	A D O E C 
F-I	O A 
F-J	B O 
F-K	D C A 
F-L	E D 
F-M	E 
F-O	A 
G-H	D C E A 
G-I	A 
G-K	D A C 
G-L	D F E 
G-M	E F 
G-O	A 
H-I	O A 
H-J	O 
H-K	A C D 
H-L	D E 
H-M	E 
H-O	A 
I-J	O 
I-K	A 
I-O	A 
K-L	D 
K-O	A 
L-M	E F 
```



## 实现

### 1mapper

```java
package com.stt.demo.mr.Ch18_CommonFriends;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class OneShareFriendMapper extends Mapper<LongWritable,Text,Text,Text> {

	Text k = new Text();
	Text v = new Text();

	@Override
	protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
		//A:B,C,D,F,E,O
		// A 的单向好友是B，那么B的所有被单向好友有哪些
		// 理解成B的被哪些人关注
		String[] fields = value.toString().split(":");
		String person = fields[0];
		v.set(person);

		String[] friends = fields[1].split(",");
		for (String friend : friends) {
			k.set(friend);
			context.write(k,v);
		}
	}
}
```



### 1reducer

```java
package com.stt.demo.mr.Ch18_CommonFriends;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class OneShareFriendReducer extends Reducer<Text,Text,Text,Text> {

	@Override
	protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
		// key 是 好友对象
		// values 是 被好友对象
		// 此处的输出要求是 key 好友对象，value，被好友对象的集合，用,号隔开
		StringBuilder sb = new StringBuilder();
		values.forEach(
            val -> sb.append(val.toString()).append(",")
        );
		context.write(key,new Text(sb.toString()));
	}
}
```



### 1driver

```java
package com.stt.demo.mr.Ch18_CommonFriends;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import static com.stt.demo.mr.Constant.INPUT;
import static com.stt.demo.mr.Constant.OUTPUT;

public class OneShareFriendDriver {

	public static void main(String[] args) throws Exception {

		args = new String[]{INPUT+"ch18/input.txt", OUTPUT+"ch18/output"};

		Job job = Job.getInstance(new Configuration());

		job.setJarByClass(OneShareFriendDriver.class);

		job.setMapperClass(OneShareFriendMapper.class);
		job.setReducerClass(OneShareFriendReducer.class);

		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		FileInputFormat.setInputPaths(job,new Path(args[0]));
		FileOutputFormat.setOutputPath(job,new Path(args[1]));

		boolean result = job.waitForCompletion(true);
		System.exit(result ? 0 : 1);
	}
}
```



### 2mapper

```java
package com.stt.demo.mr.Ch18_CommonFriends;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;
import java.util.Arrays;

public class TwoShareFriendMapper extends Mapper<LongWritable,Text,Text,Text>{

	Text k = new Text();
	Text v = new Text();

	@Override
	protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

//		A	I,K,C,B,G,F,H,O,D,
//		转换为 key I-K,C-K value A
		String[] fields = value.toString().split("\\s+");
		String friend = fields[0];
		v.set(friend);

		String[] person = fields[1].split(",");
//	    对 value中的人进行排序 因为 I-K 和 K-I 都表示 I和K之间的共同好友的key
		Arrays.sort(person);
		int len = person.length;
		for(int i=0;i<len-1;i++) {
			for(int j=i+1;j<len;j++){
				k.set(person[i]+"-"+person[j]);
				context.write(k,v);
			}
		}
	}
}
```



### 2reducer

```java
package com.stt.demo.mr.Ch18_CommonFriends;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class TwoShareFriendReducer extends Reducer<Text,Text,Text,Text> {

	@Override
	protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
		StringBuilder sb = new StringBuilder();
		values.forEach(text -> sb.append(text.toString()).append(" "));
		context.write(key,new Text(sb.toString()));
	}
}
```



### 2driver

```java
package com.stt.demo.mr.Ch18_CommonFriends;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import static com.stt.demo.mr.Constant.INPUT;
import static com.stt.demo.mr.Constant.OUTPUT;

public class TwoShareFriendDriver {

	public static void main(String[] args) throws Exception {

		args = new String[]{INPUT+"ch18/output/part-r-00000", OUTPUT+"ch18/output2"};

		Job job = Job.getInstance(new Configuration());

		job.setJarByClass(TwoShareFriendDriver.class);

		job.setMapperClass(TwoShareFriendMapper.class);
		job.setReducerClass(TwoShareFriendReducer.class);

		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		FileInputFormat.setInputPaths(job,new Path(args[0]));
		FileOutputFormat.setOutputPath(job,new Path(args[1]));

		boolean result = job.waitForCompletion(true);
		System.exit(result ? 0 : 1);
	}
}
```


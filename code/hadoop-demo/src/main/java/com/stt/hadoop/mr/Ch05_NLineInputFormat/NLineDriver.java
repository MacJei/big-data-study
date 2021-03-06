package com.stt.hadoop.mr.Ch05_NLineInputFormat;


import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;

import static com.stt.hadoop.Constant.PATH;

public class NLineDriver {

	public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {
		args = new String[]{PATH+"ch05/input.txt", PATH+"ch05/output"};

		Configuration config = new Configuration();
		Job job = Job.getInstance(config);

		// 设置3行一个切片
		NLineInputFormat.setNumLinesPerSplit(job,3);
		job.setInputFormatClass(NLineInputFormat.class);

		job.setJarByClass(NLineDriver.class);
		job.setMapperClass(NLineMapper.class);
		job.setReducerClass(NLineReducer.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(LongWritable.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(LongWritable.class);

		FileInputFormat.setInputPaths(job,new Path(args[0]));
		FileOutputFormat.setOutputPath(job,new Path(args[1]));

		job.waitForCompletion(true);
	}
}

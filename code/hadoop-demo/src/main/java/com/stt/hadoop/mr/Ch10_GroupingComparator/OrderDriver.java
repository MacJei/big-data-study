package com.stt.hadoop.mr.Ch10_GroupingComparator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;

import static com.stt.hadoop.Constant.PATH;

public class OrderDriver {

	public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {

		args = new String[]{PATH+"ch10/input.txt", PATH+"ch10/output"};

		Job job = Job.getInstance(new Configuration());

		job.setJarByClass(OrderDriver.class);

		job.setMapperClass(OrderMapper.class);
		job.setGroupingComparatorClass(OrderGroupingComparator.class);
		job.setReducerClass(OrderReducer.class);

		job.setMapOutputKeyClass(OrderBean.class);
		job.setMapOutputValueClass(NullWritable.class);
		job.setOutputKeyClass(OrderBean.class);
		job.setOutputValueClass(NullWritable.class);


		FileInputFormat.setInputPaths(job,new Path(args[0]));
		FileOutputFormat.setOutputPath(job,new Path(args[1]));
		boolean result = job.waitForCompletion(true);
		System.exit(result ? 0 : 1);
	}
}

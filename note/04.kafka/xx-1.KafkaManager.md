- 侧重对集群的管理，特别是topic的管理

1.上传压缩包kafka-manager-1.3.3.15.zip到集群

```bash
unzip kafka-manager-1.3.3.15.zip -d /opt/module/
```

2.解压到/opt/module

3.修改配置文件conf/application.conf
kafka-manager.zkhosts="kafka-manager-zookeeper:2181"
修改为：
kafka-manager.zkhosts="hadoop102:2181,hadoop103:2181,hadoop104:2181"

4.启动kafka-manager

- 启动之前需要修改权限

```bash
chmod 777 kafka-manager

bin/kafka-manager
```

5.登录hadoop102:9000页面查看详细信息



<img src="img/28.png" alt="1" style="zoom:50%;" /> 





操作系统：CentOS7

1：首先建个/kafka-manger文件夹。
2: 安装sbt: yum install sbt
3：https://github.com/yahoo/kafka-manager/releases 下载1.3.3.15.tar.gz包, 放入/kafka-manger文件夹

3：命令行：cd /kafka-manger, 执行解压命令： tar -xzvf kafka-manager-1.3.3.15.tar.gz， 解压后生成/kafka-manger/kafka-manager-1.3.3.15文件夹

4: 命令行：cd kafka-manager-1.3.3.15, 编译: ./sbt clean dist。 需要很长时间。 编译成功后，会在target/universal下生成一个kafka-manager-1.3.3.15.zip包

5: 编译过程中如果报javac错误，安装javac: yum install java-devel, 然后重新执行:./sbt clean dist

6：解压编译成功的kafka-manager-1.3.3.15.zip,找到conf/application.conf文件中第一个kafka-manager.zkhosts变量，把值修改成"localhost:2181"，localhost你可以写成其它ip地址。 

7：cd /kafka-manger/kafka-manager-1.3.3.15/bin, 运行KafkaManager: ./kafka-manager

8: 如果运行时出现root权限不足，可以使用chmod 777 /kafka-manger/kafka-manager-1.3.3.15/bin/kafka-manager为文件分配权限，然后执行就可以了
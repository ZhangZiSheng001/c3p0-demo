# Table of Contents

* [简介](#简介)
* [使用例子-入门](#使用例子-入门)
  * [需求](#需求)
  * [工程环境](#工程环境)
  * [主要步骤](#主要步骤)
  * [创建项目](#创建项目)
  * [引入依赖](#引入依赖)
  * [编写c3p0.properties](#编写c3p0properties)
  * [获取连接池和获取连接](#获取连接池和获取连接)
  * [编写测试类](#编写测试类)
* [使用例子-通过`JNDI`获取数据源](#使用例子-通过jndi获取数据源)
  * [需求](#需求-1)
  * [引入依赖](#引入依赖-1)
  * [编写context.xml](#编写contextxml)
  * [编写web.xml](#编写webxml)
  * [编写jsp](#编写jsp)
  * [测试结果](#测试结果)
* [配置文件详解](#配置文件详解)
  * [数据库连接参数](#数据库连接参数)
  * [连接池数据基本参数](#连接池数据基本参数)
  * [连接存活参数](#连接存活参数)
  * [连接检查参数](#连接检查参数)
  * [缓存语句](#缓存语句)
  * [失败重试参数](#失败重试参数)
  * [事务相关参数](#事务相关参数)
  * [其他](#其他)
* [源码分析](#源码分析)
  * [创建数据源对象](#创建数据源对象)
    * [获得this的identityToken，并注册到C3P0Registry](#获得this的identitytoken并注册到c3p0registry)
    * [添加监听配置参数改变的Listenner](#添加监听配置参数改变的listenner)
    * [创建DriverManagerDataSource](#创建drivermanagerdatasource)
    * [创建WrapperConnectionPoolDataSource](#创建wrapperconnectionpooldatasource)
  * [创建连接池对象](#创建连接池对象)
    * [创建BasicResourcePool对象](#创建basicresourcepool对象)
  * [创建连接对象](#创建连接对象)
    * [C3P0PooledConnectionPool.checkoutPooledConnection()](#c3p0pooledconnectionpoolcheckoutpooledconnection)
    * [C3P0PooledConnectionPool.checkoutAndMarkConnectionInUse()](#c3p0pooledconnectionpoolcheckoutandmarkconnectioninuse)
    * [BasicResourcePool.checkoutResource(long)](#basicresourcepoolcheckoutresourcelong)
    * [BasicResourcePool.prelimCheckoutResource(long)](#basicresourcepoolprelimcheckoutresourcelong)
    * [BasicResourcePool._recheckResizePool()](#basicresourcepool_recheckresizepool)
    * [BasicResourcePool.expandPool(int)](#basicresourcepoolexpandpoolint)
    * [PooledConnectionResourcePoolManager.acquireResource()](#pooledconnectionresourcepoolmanageracquireresource)
    * [WrapperConnectionPoolDataSource.getPooledConnection(String, String, ConnectionCustomizer, String)](#wrapperconnectionpooldatasourcegetpooledconnectionstring-string-connectioncustomizer-string)
* [参考资料](#参考资料)




# 简介

`c3p0`是用于创建和管理连接，利用“池”的方式复用连接减少资源开销，和其他数据源一样，也具有连接数控制、连接可靠性测试、连接泄露控制、缓存语句等功能。目前，`hibernate`自带的连接池就是`c3p0`。

本文将包含以下内容(因为篇幅较长，可根据需要选择阅读)：

1. `c3p0`的使用方法（入门案例、`JDNI`使用）
2. `c3p0`的配置参数详解
3. `c3p0`主要源码分析

# 使用例子-入门

## 需求

使用`C3P0`连接池获取连接对象，对用户数据进行简单的增删改查（`sql`脚本项目中已提供）。

## 工程环境

`JDK`：1.8.0_201

`maven`：3.6.1

`IDE`：eclipse 4.12

`mysql-connector-java`：8.0.15

`mysql`：5.7 .28 

`C3P0`：0.9.5.3

## 主要步骤

1. 编写`c3p0.properties`，设置数据库连接参数和连接池基本参数等

2. `new`一个`ComboPooledDataSource`对象，它会自动加载`c3p0.properties`

3. 通过`ComboPooledDataSource`对象获得`Connection`对象

4. 使用`Connection`对象对用户表进行增删改查

## 创建项目

项目类型Maven Project，打包方式war（其实jar也可以，之所以使用war是为了测试`JNDI`）。

## 引入依赖

这里引入日志包，主要为了看看连接池的创建过程，不引入不会有影响的。
```xml
		<dependency>
			<groupId>junit</groupId>
			<artifactId>junit</artifactId>
			<version>4.12</version>
			<scope>test</scope>
		</dependency>
		<!-- c3p0 -->
		<dependency>
			<groupId>com.mchange</groupId>
			<artifactId>c3p0</artifactId>
			<version>0.9.5.3</version>
		</dependency>
		<!-- mysql驱动 -->
		<dependency>
			<groupId>mysql</groupId>
			<artifactId>mysql-connector-java</artifactId>
			<version>8.0.15</version>
		</dependency>
		<!-- log -->
		<dependency>
			<groupId>log4j</groupId>
			<artifactId>log4j</artifactId>
			<version>1.2.17</version>
		</dependency>
		<dependency>
			<groupId>commons-logging</groupId>
			<artifactId>commons-logging</artifactId>
			<version>1.2</version>
		</dependency>
```

## 编写c3p0.properties

`c3p0`支持使用`.xml`、`.properties`等文件来配置参数。本文用的是`c3p0.properties`作为配置文件，相比`.xml`文件我觉得会直观一些。

配置文件路径在`resources`目录下，因为是入门例子，这里仅给出数据库连接参数和连接池基本参数，后面源码会对所有配置参数进行详细说明。另外，数据库`sql`脚本也在该目录下。

注意：文件名必须是`c3p0.properties`，否则不会自动加载（如果是`.xml`，文件名为`c3p0-config.xml`）。

```properties
# c3p0只是会将该驱动实例注册到DriverManager，不能保证最终用的是该实例，除非设置了forceUseNamedDriverClass
c3p0.driverClass=com.mysql.cj.jdbc.Driver
c3p0.forceUseNamedDriverClass=true
c3p0.jdbcUrl=jdbc:mysql://localhost:3306/github_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8&useSSL=true
# 获取连接时使用的默认用户名
c3p0.user=root
# 获取连接时使用的默认用户密码
c3p0.password=root

####### Basic Pool Configuration ########
# 当没有空闲连接可用时，批量创建连接的个数
# 默认3
c3p0.acquireIncrement=3
# 初始化连接个数
# 默认3
c3p0.initialPoolSize=3
# 最大连接个数
# 默认15
c3p0.maxPoolSize=15
# 最小连接个数
# 默认3
c3p0.minPoolSize=3  
```

## 获取连接池和获取连接

项目中编写了`JDBCUtil`来初始化连接池、获取连接、管理事务和释放资源等，具体参见项目源码。

路径：`cn.zzs.c3p0`

```java
		// 配置文件名为c3p0.properties，会自动加载。
		DataSource dataSource = new ComboPooledDataSource();
		// 获取连接
		Connection conn = dataSource.getConnection();
```

除了使用`ComboPooledDataSource`，`c3p0`还提供了静态工厂类`DataSources`，这个类可以创建未池化的数据源对象，也可以将未池化的数据源池化，当然，这种方式也会去自动加载配置文件。

```java
		// 获取未池化数据源对象
        DataSource ds_unpooled = DataSources.unpooledDataSource();
        // 将未池化数据源对象进行池化
		DataSource ds_pooled = DataSources.pooledDataSource(ds_unpooled);
        // 获取连接
		Connection connection = ds_pooled.getConnection();
```

## 编写测试类

这里以保存用户为例，路径在test目录下的`cn.zzs.c3p0`。

```java
	@Test
	public void save() {
		// 创建sql
		String sql = "insert into demo_user values(null,?,?,?,?,?)";
		Connection connection = null;
		PreparedStatement statement = null;
		try {
			// 获得连接
			connection = JDBCUtil.getConnection();
			// 开启事务设置非自动提交
			JDBCUtil.startTrasaction();
			// 获得Statement对象
			statement = connection.prepareStatement(sql);
			// 设置参数
			statement.setString(1, "zzf003");
			statement.setInt(2, 18);
			statement.setDate(3, new Date(System.currentTimeMillis()));
			statement.setDate(4, new Date(System.currentTimeMillis()));
			statement.setBoolean(5, false);
			// 执行
			statement.executeUpdate();
			// 提交事务
			JDBCUtil.commit();
		} catch(Exception e) {
			JDBCUtil.rollback();
			log.error("保存用户失败", e);
		} finally {
			// 释放资源
			JDBCUtil.release(connection, statement, null);
		}
	}
```
# 使用例子-通过`JNDI`获取数据源

## 需求

本文测试使用`JNDI`获取`ComboPooledDataSource`和`JndiRefConnectionPoolDataSource`对象，选择使用`tomcat 9.0.21`作容器。

如果之前没有接触过`JNDI`，并不会影响下面例子的理解，其实可以理解为像`spring`的`bean`配置和获取。

## 引入依赖

本文在入门例子的基础上增加以下依赖，因为是`web`项目，所以打包方式为`war`：  
```xml
		<dependency>
			<groupId>javax.servlet</groupId>
			<artifactId>jstl</artifactId>
			<version>1.2</version>
			<scope>provided</scope>
		</dependency>
		<dependency>
			<groupId>javax.servlet</groupId>
			<artifactId>javax.servlet-api</artifactId>
			<version>3.1.0</version>
			<scope>provided</scope>
		</dependency>
		<dependency>
			<groupId>javax.servlet.jsp</groupId>
			<artifactId>javax.servlet.jsp-api</artifactId>
			<version>2.2.1</version>
			<scope>provided</scope>
		</dependency>
```

## 编写context.xml

在`webapp`文件下创建目录`META-INF`，并创建`context.xml`文件。这里面的每个`resource`节点都是我们配置的对象，类似于`spring`的`bean`节点。其中`jdbc/pooledDS`可以看成是这个`bean`的`id`。

注意，这里获取的数据源对象是单例的，如果希望多例，可以设置`singleton="false"`。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Context>
	<Resource auth="Container"
	          description="DB Connection"
	          driverClass="com.mysql.cj.jdbc.Driver"
	          maxPoolSize="4"
	          minPoolSize="2"
	          acquireIncrement="1"
	          name="jdbc/pooledDS"
	          user="root"
	          password="root"
	          factory="org.apache.naming.factory.BeanFactory"
	          type="com.mchange.v2.c3p0.ComboPooledDataSource"
	          jdbcUrl="jdbc:mysql://localhost:3306/github_demo?useUnicode=true&amp;characterEncoding=utf8&amp;serverTimezone=GMT%2B8&amp;useSSL=true" />
</Context>
```

## 编写web.xml

在`web-app`节点下配置资源引用，每个`resource-env-ref`指向了我们配置好的对象。

```xml
	<resource-ref>
		<res-ref-name>jdbc/pooledDS</res-ref-name>
		<res-type>javax.sql.DataSource</res-type>
		<res-auth>Container</res-auth>
	</resource-ref>
```

## 编写jsp

因为需要在`web`环境中使用，如果直接建类写个`main`方法测试，会一直报错的，目前没找到好的办法。这里就简单地使用`jsp`来测试吧。

`c3p0`提供了`JndiRefConnectionPoolDataSource`来支持`JNDI`（方式一），当然，我们也可以采用常规方式获取`JNDI`的数据源（方式二）。因为我设置的数据源时单例的，所以，两种方式获得的是同一个数据源对象，只是方式一会将该对象再次包装。

```jsp
<body>
    <%
    String jndiName = "java:comp/env/jdbc/pooledDS";
    // 方式一
    JndiRefConnectionPoolDataSource jndiDs = new JndiRefConnectionPoolDataSource();
    jndiDs.setJndiName(jndiName);
    System.err.println("方式一获得的数据源identityToken：" + jndiDs.getIdentityToken());
    Connection con2 = jndiDs.getPooledConnection().getConnection();
    // do something
    System.err.println("方式一获得的连接：" + con2);
    
    // 方式二
    InitialContext ic = new InitialContext();
    // 获取JNDI上的ComboPooledDataSource
    DataSource ds = (DataSource) ic.lookup(jndiName);
    System.err.println("方式二获得的数据源identityToken：" + ((ComboPooledDataSource)ds).getIdentityToken());
    Connection con = ds.getConnection();
    // do something
    System.err.println("方式二获得的连接：" + con);
    
    // 释放资源
    if (ds instanceof PooledDataSource){
      PooledDataSource pds = (PooledDataSource) ds;
      // 先看看当前连接池的状态
	  System.err.println("num_connections: "      + pds.getNumConnectionsDefaultUser());
	  System.err.println("num_busy_connections: " + pds.getNumBusyConnectionsDefaultUser());
	  System.err.println("num_idle_connections: " + pds.getNumIdleConnectionsDefaultUser());
      pds.close();
    }else{
      System.err.println("Not a c3p0 PooledDataSource!");
    }
    %>
</body>
```
## 测试结果

打包项目在`tomcat9`上运行，访问  http://localhost:8080/C3P0-demo/testJNDI.jsp ，控制台打印如下内容：

```
方式一获得的数据源identityToken：1hge1hra7cdbnef1fooh9k|3c1e541
方式一获得的连接：com.mchange.v2.c3p0.impl.NewProxyConnection@2baa7911
方式二获得的数据源identityToken：1hge1hra7cdbnef1fooh9k|9c60446
方式二获得的连接：com.mchange.v2.c3p0.impl.NewProxyConnection@e712a7c
num_connections: 3
num_busy_connections: 2
num_idle_connections: 1
```
此时正在使用的连接对象有2个，即两种方式各持有1个，即印证了两种方式获得的是同一数据源。

# 配置文件详解

这部分内容是参考官网的，对应当前所用的`0.9.5.3 `版本([官网地址](https://www.mchange.com/projects/c3p0/#contents))。

## 数据库连接参数

注意，这里在`url`后面拼接了多个参数用于避免乱码、时区报错问题。  补充下，如果不想加入时区的参数，可以在`mysql`命令窗口执行如下命令：`set global time_zone='+8:00'`。

还有，如果是`xml`文件，记得将`&`改成`&amp;`。

```properties
# c3p0只是会将该驱动实例注册到DriverManager，不能保证最终用的是该实例，除非设置了forceUseNamedDriverClass
c3p0.driverClass=com.mysql.cj.jdbc.Driver
c3p0.forceUseNamedDriverClass=true

c3p0.jdbcUrl=jdbc:mysql://localhost:3306/github_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8&useSSL=true

# 获取连接时使用的默认用户名
c3p0.user=root
# 获取连接时使用的默认用户密码
c3p0.password=root
```

## 连接池数据基本参数

这几个参数都比较常用，具体设置多少需根据项目调整。

```properties
####### Basic Pool Configuration ########
# 当没有空闲连接可用时，批量创建连接的个数
# 默认3
c3p0.acquireIncrement=3

# 初始化连接个数
# 默认3
c3p0.initialPoolSize=3

# 最大连接个数
# 默认15
c3p0.maxPoolSize=15

# 最小连接个数
# 默认3
c3p0.minPoolSize=3
```

## 连接存活参数

为了避免连接泄露无法回收的问题，建议设置`maxConnectionAge`和`unreturnedConnectionTimeout`。

```properties
# 最大空闲时间。超过将被释放
# 默认0，即不限制。单位秒
c3p0.maxIdleTime=0

# 最大存活时间。超过将被释放
# 默认0，即不限制。单位秒
c3p0.maxConnectionAge=1800

# 过量连接最大空闲时间。
# 默认0，即不限制。单位秒
c3p0.maxIdleTimeExcessConnections=0

# 检出连接未归还的最大时间。
# 默认0。即不限制。单位秒
c3p0.unreturnedConnectionTimeout=0
```

## 连接检查参数

针对连接失效和连接泄露的问题，建议开启空闲连接测试（异步），而不建议开启检出测试（从性能考虑）。另外，通过设置`preferredTestQuery`或`automaticTestTable`可以加快测试速度。

```properties
# c3p0创建的用于测试连接的空表的表名。如果设置了，preferredTestQuery将失效。
# 默认null
#c3p0.automaticTestTable=test_table

# 自定义测试连接的sql。如果没有设置，c3p0会去调用isValid方法进行校验（c3p0版本0.9.5及以上）
# null
c3p0.preferredTestQuery=select 1 from dual

# ConnectionTester实现类，用于定义如何测试连接
# com.mchange.v2.c3p0.impl.DefaultConnectionTester
c3p0.connectionTesterClassName=com.mchange.v2.c3p0.impl.DefaultConnectionTester

# 空闲连接测试周期
# 默认0，即不检验。单位秒
c3p0.idleConnectionTestPeriod=300

# 连接检入时测试（异步）。
# 默认false
c3p0.testConnectionOnCheckin=false

# 连接检出时测试。
# 默认false。建议不要设置为true。
c3p0.testConnectionOnCheckout=false
```

## 缓存语句

针对大部分数据库而言，开启缓存语句可以有效提高性能。

```properties
# 所有连接PreparedStatement的最大总数量。是JDBC定义的标准参数，c3p0建议使用自带的maxStatementsPerConnection
# 默认0。即不限制
c3p0.maxStatements=0

# 单个连接PreparedStatement的最大数量。
# 默认0。即不限制
c3p0.maxStatementsPerConnection=0

# 延后清理PreparedStatement的线程数。可设置为1。
# 默认0。即不限制
c3p0.statementCacheNumDeferredCloseThreads=0
```

## 失败重试参数

根据项目实际情况设置。

```properties
# 失败重试时间。
# 默认30。如果非正数，则将一直阻塞地去获取连接。单位毫秒。
c3p0.acquireRetryAttempts=30

# 失败重试周期。
# 默认1000。单位毫秒
c3p0.acquireRetryDelay=1000

# 当获取连接失败，是否标志数据源已损坏，不再重试。
# 默认false。
c3p0.breakAfterAcquireFailure=false
```

## 事务相关参数

建议保留默认就行。

```properties
# 连接检入时是否自动提交事务。
# 默认false。但c3p0会自动回滚
c3p0.autoCommitOnClose=false

# 连接检入时是否强制c3p0不去提交或回滚事务，以及修改autoCommit
# 默认false。强烈建议不要设置为true。
c3p0.forceIgnoreUnresolvedTransactions=false
```

## 其他

```properties
# 连接检出时是否记录堆栈信息。用于在unreturnedConnectionTimeout超时时打印。
# 默认false。
c3p0.debugUnreturnedConnectionStackTraces=false

# 在获取、检出、检入和销毁时，对连接对象进行操作的类。
# 默认null。通过继承com.mchange.v2.c3p0.AbstractConnectionCustomizer来定义。
#c3p0.connectionCustomizerClassName

# 池耗尽时，获取连接最大等待时间。
# 默认0。即无限阻塞。单位毫秒
c3p0.checkoutTimeout=0

# JNDI数据源的加载URL
# 默认null
#c3p0.factoryClassLocation

# 是否同步方式检入连接
# 默认false
c3p0.forceSynchronousCheckins=false

# c3p0的helper线程最大任务时间
# 默认0。即不限制。单位秒
c3p0.maxAdministrativeTaskTime=0

# c3p0的helper线程数量
# 默认3
c3p0.numHelperThreads=3

# 类加载器来源
# 默认caller
#c3p0.contextClassLoaderSource

# 是否使用c3p0的AccessControlContext
c3p0.privilegeSpawnedThreads=false
```

# 源码分析

`c3p0`的源码真的非常难啃，没有注释也就算了，代码的格式也是非常奇葩。正因为这个原因，我刚开始接触`c3p0`时，就没敢深究它的源码。现在硬着头皮再次来翻看它的源码，还是花了我不少时间。

因为`c3p0`的部分方法调用过程比较复杂，所以，这次源码分析重点关注**类与类的关系和一些重要功能的实现**，不像以往还可以一步步地探索。 

另外，`c3p0`大量使用了**监听器和多线程**，因为是`JDK`自带的功能，所以本文不会深究其原理。感兴趣的同学，可以补充学习下，毕竟实际项目中也会使用到的。

## 创建数据源对象

我们使用`c3p0`时，一般会以`ComboPooledDataSource`这个类为入口，那么就从这个类展开吧。首先，看下`ComboPooledDataSource`的`UML`图。

![ComboPooledDataSource的类继承图](https://github.com/ZhangZiSheng001/c3p0-demo/tree/master/img/ComboPooledDataSource.png)

下面重点说下几个类的作用：

| 类名                              | 描述                                                         |
| --------------------------------- | ------------------------------------------------------------ |
| `DataSource`                      | 用于创建原生的`Connection`                                   |
| `ConnectionPoolDataSource`        | 用于创建`PooledConnection`                                   |
| `PooledDataSource`                | 用于支持对`c3p0`连接池中连接数量和状态等的监控               |
| `IdentityTokenized`               | 用于支持注册功能。每个`DataSource`实例都有一个`identityToken`，用于在`C3P0Registry`中注册 |
| `PoolBackedDataSourceBase`        | 实现了`IdentityTokenized`接口，还持有`PropertyChangeSupport`和`VetoableChangeSupport`对象，并提供了添加和移除监听器的方法 |
| `AbstractPoolBackedDataSource`    | 实现了`PooledDataSource`和`DataSource`                       |
| `AbstractComboPooledDataSource`   | 提供了数据源参数配置的`setter/getter`方法                    |
| `DriverManagerDataSource`         | `DataSource`实现类，用于创建原生的`Connection`               |
| `WrapperConnectionPoolDataSource` | `ConnectionPoolDataSource`实现类，用于创建`PooledConnection` |
| `C3P0PooledConnectionPoolManager` | 连接池管理器，非常重要。用于创建连接池，并持有连接池的Map（根据账号密码匹配连接池）。 |

当我们`new`一个`ComboPooledDataSource`对象时，主要做了几件事：

1. 获得`this`的`identityToken`，并注册到`C3P0Registry `
2. 添加监听配置参数改变的`Listenner `
3. 创建`DriverManagerDataSource`和`WrapperConnectionPoolDataSource`对象

当然，在此之前有某个静态代码块加载类配置文件，具体加载过程后续有空再做补充。

### 获得this的identityToken，并注册到C3P0Registry

在`c3p0`里，每个数据源都有一个唯一的身份标志`identityToken`，用于在`C3P0Registry`中注册。下面看看具体`identityToken`的获取，调用的是`C3P0ImplUtils`的`allocateIdentityToken`方法。

`System.identityHashCode(o)`是本地方法，即使我们不重写`hashCode`，同一个对象获得的`hashCode`唯一且不变，甚至程序重启也是一样。这个方法还是挺神奇的，感兴趣的同学可以研究下具体原理。

```java
	public static String allocateIdentityToken(Object o) {
		if(o == null)
			return null;
		else {
			// 获取对象的identityHashCode，并转为16进制
			String shortIdToken = Integer.toString(System.identityHashCode(o), 16);
			String out;
			long count;
			StringBuffer sb = new StringBuffer(128);
			sb.append(VMID_PFX);
			// 判断是否拼接当前对象被查看过的次数
			if(ID_TOKEN_COUNTER != null && ((count = ID_TOKEN_COUNTER.encounter(shortIdToken)) > 0)) {
				sb.append(shortIdToken);
				sb.append('#');
				sb.append(count);
			} else
				sb.append(shortIdToken);
			out = sb.toString().intern();
			return out;
		}
	}
```
接下来，再来看下注册过程，调用的是`C3P0Registry`的`incorporate`方法。

```java
	// 存放identityToken=PooledDataSource的键值对
	private static Map tokensToTokenized = new DoubleWeakHashMap();
	// 存放未关闭的PooledDataSource
    private static HashSet unclosedPooledDataSources = new HashSet();
	private static void incorporate(IdentityTokenized idt) {
		tokensToTokenized.put(idt.getIdentityToken(), idt);
		if(idt instanceof PooledDataSource) {
			unclosedPooledDataSources.add(idt);
			mc.attemptManagePooledDataSource((PooledDataSource)idt);
		}
	}
```
注册的过程还是比较简单易懂，但是有个比较奇怪的地方，一般这种所谓的注册，都会提供某个方法，让我们可以在程序的任何位置通过唯一标识去查找数据源对象。然而，即使我们知道了某个数据源的`identityToken`，还是获取不到对应的数据源，因为`C3P0Registry`并没有提供相关的方法给我们。

后来发现，我们不能也不应该通过`identityToken`来查找数据源，而是应该通过`dataSourceName`来查找才对，这不，`C3P0Registry`就提供了这样的方法。所以，如果我们想在程序的任何位置都能获取到数据源对象，应该再创建数据源时就设置好它的`dataSourceName`。

```java
	public synchronized static PooledDataSource pooledDataSourceByName(String dataSourceName) {
		for(Iterator ii = unclosedPooledDataSources.iterator(); ii.hasNext();) {
			PooledDataSource pds = (PooledDataSource)ii.next();
			if(pds.getDataSourceName().equals(dataSourceName))
				return pds;
		}
		return null;
	}
```

### 添加监听配置参数改变的Listenner

接下来是到监听器的内容了。监听器的支持是`jdk`自带的，主要涉及到`PropertyChangeSupport`和`VetoableChangeSupport`两个类，至于具体的实现机理不在本文讨论范围内，感兴趣的同学可以补充学习下。

创建`ComboPooledDataSource`时，总共添加了三个监听器。

| 监听器                    | 描述                                                         |
| ------------------------- | ------------------------------------------------------------ |
| `PropertyChangeListener`1 | 当`connectionPoolDataSource`, `numHelperThreads`, `identityToken`改变后，重置`C3P0PooledConnectionPoolManager` |
| `VetoableChangeListener`  | 当`connectionPoolDataSource`改变前，校验新设置的对象是否是`WrapperConnectionPoolDataSource`对象，以及该对象中的`DataSource`是否`DriverManagerDataSource`对象，如果不是，会抛出异常 |
| `PropertyChangeListener`2 | 当`connectionPoolDataSource`改变后，修改this持有的`DriverManagerDataSource`和`WrapperConnectionPoolDataSource`对象 |

我们可以看到，在`PoolBackedDataSourceBase对`象中，持有了`PropertyChangeSupport`和`VetoableChangeSupport`对象，用于支持监听器的功能。

```java
public class PoolBackedDataSourceBase extends IdentityTokenResolvable implements Referenceable, Serializable
{
	protected PropertyChangeSupport pcs = new PropertyChangeSupport( this );
	protected VetoableChangeSupport vcs = new VetoableChangeSupport( this );
}
```
通过以上过程，`c3p0`可以在参数改变前进行校验，在参数改变后重置某些对象。

### 创建DriverManagerDataSource

`ComboPooledDataSource`在实例化父类`AbstractComboPooledDataSource`时会去创建`DriverManagerDataSource`和`WrapperConnectionPoolDataSource`对象，这两个对象都是用于创建连接对象，后者依赖前者。

```java
	public AbstractComboPooledDataSource(boolean autoregister) {
		super(autoregister);
		// 创建DriverManagerDataSource和WrapperConnectionPoolDataSource对象
		dmds = new DriverManagerDataSource();
		wcpds = new WrapperConnectionPoolDataSource();
		// 将DriverManagerDataSource设置给WrapperConnectionPoolDataSource
		wcpds.setNestedDataSource(dmds);
		
		// 初始化属性connectionPoolDataSource
		this.setConnectionPoolDataSource(wcpds);
		// 注册监听器
		setUpPropertyEvents();
	}
```

前面已经讲过，`DriverManagerDataSource`可以用来获取原生的连接对象，所以它的功能有点类似于`JDBC`的`DriverManager`。

![DriverManagerDataSource的UML图](https://github.com/ZhangZiSheng001/c3p0-demo/tree/master/img/DriverManagerDataSource.png)

创建`DriverManagerDataSource`实例主要做了三件事，如下：

```java
	public DriverManagerDataSource(boolean autoregister) {
		// 1. 获得this的identityToken，并注册到C3P0Registry  
		super(autoregister);
		// 2. 添加监听配置参数改变的Listenner(当driverClass属性更改时触发事件) 
		setUpPropertyListeners();
		// 3. 读取配置文件，初始化默认的user和password
		String user = C3P0Config.initializeStringPropertyVar("user", null);
		String password = C3P0Config.initializeStringPropertyVar("password", null);
		if(user != null)
			this.setUser(user);
		if(password != null)
			this.setPassword(password);
	}
```
### 创建WrapperConnectionPoolDataSource

下面再看看`WrapperConnectionPoolDataSource`，它可以用来获取`PooledConnection`。

![WrapperConnectionPoolDataSource的UML图](https://github.com/ZhangZiSheng001/c3p0-demo/tree/master/img/WrapperConnectionPoolDataSource.png)

创建`WrapperConnectionPoolDataSource`，主要做了以下三件件事：

```java
	public WrapperConnectionPoolDataSource(boolean autoregister) {
		// 1. 获得this的identityToken，并注册到C3P0Registry
		super(autoregister);
		// 2. 添加监听配置参数改变的Listenner(当connectionTesterClassName属性更改时实例化ConnectionTester，当userOverridesAsString更改时重新解析字符串) 
		setUpPropertyListeners();
		// 3. 解析userOverridesAsString
		this.userOverrides = C3P0ImplUtils.parseUserOverridesAsString(this.getUserOverridesAsString());
	}
```
以上基本将`ComboPooledDataSource`的内容讲完，下面介绍连接池的创建。

## 创建连接池对象

当我们创建完数据源时，连接池并没有创建，也就是说只有我们调用`getConnection`时才会触发创建连接池。因为`AbstractPoolBackedDataSource`实现了`DataSource`，所以我们可以在这个类看到`getConnection`的具体实现，如下。

```java
    public Connection getConnection() throws SQLException{
        PooledConnection pc = getPoolManager().getPool().checkoutPooledConnection();
        return pc.getConnection();
    }
```
这个方法中`getPoolManager()`得到的就是我们前面提到过的`C3P0PooledConnectionPoolManager`，而`getPool()`得到的是`C3P0PooledConnectionPool`。

我们先来看看这两个类(注意，图中的类展示的只是部分的属性和方法)：

![C3P0PooledConnectionPoolManager和C3P0PooledConnectionPool的UML图](https://github.com/ZhangZiSheng001/c3p0-demo/tree/master/img/C3P0PooledConnectionPoolManager.png)

下面介绍下这几个类：

| 类名                                  | 描述                                                         |
| ------------------------------------- | ------------------------------------------------------------ |
| `C3P0PooledConnectionPoolManager`     | 连接池管理器。主要用于获取/创建连接池，它持有`DbAuth`-`C3P0PooledConnectionPool`键值对的`Map` |
| `C3P0PooledConnectionPool`            | 连接池。主要用于检入和检出连接对象，实际调用的是其持有的`BasicResourcePool`对象 |
| `BasicResourcePool`                   | 资源池。主要用于检入和检出连接对象                           |
| `PooledConnectionResourcePoolManager` | 资源管理器。主要用于创建新的连接对象，以及检入、检出或空闲时进行连接测试 |

创建连接池的过程可以概括为四个步骤：

1. 创建`C3P0PooledConnectionPoolManager`对象，开启另一个线程来初始化`timer`、`taskRunner`、`deferredStatementDestroyer`、`rpfact`和`authsToPools`等属性

2. 创建默认账号密码对应的`C3P0PooledConnectionPool`对象，并创建`PooledConnectionResourcePoolManager`对象 

4. 创建`BasicResourcePool`对象，创建`initialPoolSize`对应的初始连接，开启检查连接是否过期、以及检查空闲连接有效性的定时任务

这里主要分析下第四步。

### 创建BasicResourcePool对象

在这个方法里除了初始化许多属性之外，还会去创建`initialPoolSize`对应的初始连接，开启检查连接是否过期、以及检查空闲连接有效性的定时任务。

```java
	public BasicResourcePool(Manager mgr, int start, int min, int max, int inc, int num_acq_attempts, int acq_attempt_delay, long check_idle_resources_delay, long max_resource_age, long max_idle_time, long excess_max_idle_time, long destroy_unreturned_resc_time, long expiration_enforcement_delay, boolean break_on_acquisition_failure, boolean debug_store_checkout_exceptions, boolean force_synchronous_checkins, AsynchronousRunner taskRunner, RunnableQueue asyncEventQueue,
			Timer cullAndIdleRefurbishTimer, BasicResourcePoolFactory factory) throws ResourcePoolException {
		// ·······
		this.taskRunner = taskRunner;
		this.asyncEventQueue = asyncEventQueue;
		this.cullAndIdleRefurbishTimer = cullAndIdleRefurbishTimer;
		this.factory = factory;
        // 开启监听器支持
        if (asyncEventQueue != null)
        	this.rpes = new ResourcePoolEventSupport(this);
        else
        	this.rpes = null;
		// 确保初始连接数量，这里会去调用recheckResizePool()方法，后面还会讲到的
		ensureStartResources();
		// 如果设置maxIdleTime、maxConnectionAge、maxIdleTimeExcessConnections和unreturnedConnectionTimeout，会开启定时任务检查连接是否过期
		if(mustEnforceExpiration()) {
			this.cullTask = new CullTask();
			cullAndIdleRefurbishTimer.schedule(cullTask, minExpirationTime(), this.expiration_enforcement_delay);
		}
        // 如果设置idleConnectionTestPeriod，会开启定时任务检查空闲连接有效性
		if(check_idle_resources_delay > 0) {
			this.idleRefurbishTask = new CheckIdleResourcesTask();
			cullAndIdleRefurbishTimer.schedule(idleRefurbishTask, check_idle_resources_delay, check_idle_resources_delay);
		}
		// ·······
	}
```
看过`c3p0`源码就会发现，`c3p0`的开发真的非常喜欢监听器和多线程，正是因为这样，才导致它的源码阅读起来会比较吃力。为了方便理解，这里再补充解释下`BasicResourcePool`的几个属性：

| 属性                                  | 描述                                                         |
| ------------------------------------- | ------------------------------------------------------------ |
| `BasicResourcePoolFactory`  `factory` | 资源池工厂。用于创建`BasicResourcePool`                      |
| `AsynchronousRunner ` `taskRunner`    | 异步线程。用于执行资源池中连接的创建、销毁                   |
| `RunnableQueue`  `asyncEventQueue`    | 异步队列。用于存放连接检出时向`ResourcePoolEventSupport`报告的事件 |
| `ResourcePoolEventSupport` `rpes`     | 用于支持监听器                                               |
| `Timer` `cullAndIdleRefurbishTimer`   | 定时任务线程。用于执行检查连接是否过期、以及检查空闲连接有效性的任务 |
| `TimerTask` `cullTask`                | 执行检查连接是否过期的任务                                   |
| `TimerTask` `idleRefurbishTask`       | 检查空闲连接有效性的任务                                     |
| `HashSet` `acquireWaiters`            | 存放等待获取连接的客户端                                     |
| `HashSet` `otherWaiters`              | 当客户端试图检出某个连接，而该连接刚好被检查空闲连接有效性的线程占用，此时客户端就会被加入`otherWaiters` |
| `HashMap`  `managed`                  | 存放当前池中所有的连接对象                                   |
| `LinkedList` `unused`                 | 存放当前池中所有的空闲连接对象                               |
| `HashSet`  `excluded`                 | 存放当前池中已失效但还没检出或使用的连接对象                 |
| `Set` `idleCheckResources`            | 存放当前检查空闲连接有效性的线程占用的连接对象               |

以上，基本讲完获取连接池的部分，接下来介绍连接的创建。

##  创建连接对象

我总结下获取连接的过程，为以下几步：

1. 从`BasicResourcePool`的空闲连接中获取，如果没有，会尝试去创建新的连接，当然，创建的过程也是异步的

2. 开启缓存语句支持

3. 判断连接是否正在被空闲资源检测线程使用，如果是，重新获取连接

4. 校验连接是否过期

5. 检出测试

6. 判断连接原来的Statement是不是已经清除完，如果没有，重新获取连接

7. 设置监听器后将连接返回给客户端

下面还是从头到尾分析该过程的源码吧。

### C3P0PooledConnectionPool.checkoutPooledConnection()

现在回到`AbstractPoolBackedDataSource`的`getConnection`方法，获取连接对象时会去调用`C3P0PooledConnectionPool`的`checkoutPooledConnection()`。

```java
	// 返回的是NewProxyConnection对象
	public Connection getConnection() throws SQLException{
        PooledConnection pc = getPoolManager().getPool().checkoutPooledConnection();
        return pc.getConnection();
    }	
	// 返回的是NewPooledConnection对象
	public PooledConnection checkoutPooledConnection() throws SQLException {
        // 从连接池检出连接对象
		PooledConnection pc = (PooledConnection)this.checkoutAndMarkConnectionInUse();
        // 添加监听器，当连接close时会触发checkin事件
		pc.addConnectionEventListener(cl);
		return pc;
	}
```
之前我一直有个疑问，`PooledConnection`对象并不持有连接池对象，那么当客户端调用`close()`时，连接不就不能还给连接池了吗？看到这里总算明白了，`c3p0`使用的是监听器的方式，当客户端调用`close()`方法时会触发监听器把连接`checkin`到连接池中。

### C3P0PooledConnectionPool.checkoutAndMarkConnectionInUse()

通过这个方法可以看到，从连接池检出连接的过程不断循环，除非我们设置了`checkoutTimeout`，超时会抛出异常，又或者检出过程抛出了其他异常。

另外，因为`c3p0`在`checkin`连接时清除`Statement`采用的是异步方式，所以，当我们尝试再次检出该连接，有可能`Statement`还没清除完，这个时候我们不得不将连接还回去，再尝试重新获取连接。

```java
	private Object checkoutAndMarkConnectionInUse() throws TimeoutException, CannotAcquireResourceException, ResourcePoolException, InterruptedException {
		Object out = null;
		boolean success = false;
        // 注意，这里会自旋直到成功获得连接对象，除非抛出超时等异常
		while(!success) {
			try {
                // 从BasicResourcePool中检出连接对象
				out = rp.checkoutResource(checkoutTimeout);
				if(out instanceof AbstractC3P0PooledConnection) {
					// 检查该连接下的Statement是不是已经清除完，如果没有，还得重新获取连接
					AbstractC3P0PooledConnection acpc = (AbstractC3P0PooledConnection)out;
					Connection physicalConnection = acpc.getPhysicalConnection();
					success = tryMarkPhysicalConnectionInUse(physicalConnection);
				} else
					success = true; // we don't pool statements from non-c3p0 PooledConnections
			} finally {
				try {
                    // 如果检出了连接对象，但出现异常或者连接下的Statement还没清除完，那么就需要重新检入连接
					if(!success && out != null)
						rp.checkinResource(out);
				} catch(Exception e) {
					logger.log(MLevel.WARNING, "Failed to check in a Connection that was unusable due to pending Statement closes.", e);
				}
			}
		}
		return out;
	}
```

### BasicResourcePool.checkoutResource(long)

下面这个方法会采用递归方式不断尝试检出连接，只有设置了`checkoutTimeout`，或者抛出其他异常，才能从该方法中出来。

如果我们设置了`testConnectionOnCheckout`，则进行连接检出测试，如果不合格，就必须销毁这个连接对象，并尝试重新检出。

```java
	public Object checkoutResource(long timeout) throws TimeoutException, ResourcePoolException, InterruptedException {
		try {
			Object resc = prelimCheckoutResource(timeout);

			// 如果设置了testConnectionOnCheckout，会进行连接检出测试，会去调用PooledConnectionResourcePoolManager的refurbishResourceOnCheckout方法
			boolean refurb = attemptRefurbishResourceOnCheckout(resc);

			synchronized(this) {
				// 连接测试不通过
				if(!refurb) {
					// 清除该连接对象
					removeResource(resc);
					// 确保连接池最小容量，会去调用recheckResizePool()方法，后面还会讲到的
					ensureMinResources();
					resc = null;
				} else {
                    // 在asyncEventQueue队列中加入当前连接检出时向ResourcePoolEventSupport报告的事件
					asyncFireResourceCheckedOut(resc, managed.size(), unused.size(), excluded.size());
					PunchCard card = (PunchCard)managed.get(resc);
                    // 该连接对象被删除了？？
					if(card == null) // the resource has been removed!
					{
						if(Debug.DEBUG && logger.isLoggable(MLevel.FINER))
							logger.finer("Resource " + resc + " was removed from the pool while it was being checked out " + " or refurbished for checkout. Will try to find a replacement resource.");
						resc = null;
					} else {
						card.checkout_time = System.currentTimeMillis();
					}
				}
			}
			// 如果检出失败，还会继续检出，除非抛出超时等异常
			if(resc == null)
				return checkoutResource(timeout);
			else
				return resc;
		} catch(StackOverflowError e) {
			throw new NoGoodResourcesException("After checking so many resources we blew the stack, no resources tested acceptable for checkout. " + "See logger com.mchange.v2.resourcepool.BasicResourcePool output at FINER/DEBUG for information on individual failures.", e);
		}
	}
```

### BasicResourcePool.prelimCheckoutResource(long)

这个方法也是采用递归的方式不断地尝试获取空闲连接，只有设置了`checkoutTimeout`，或者抛出其他异常，才能从该方法中出来。

如果我们开启了空闲连接检测，当我们获取到某个空闲连接时，如果它正在进行空闲连接检测，那么我们不得不等待，并尝试重新获取。

还有，如果我们设置了`maxConnectionAge`，还必须校验当前获取的连接是不是已经过期，过期的话也得重新获取。

```java
	private synchronized Object prelimCheckoutResource(long timeout) throws TimeoutException, ResourcePoolException, InterruptedException {
		try {
            // 检验当前连接池是否已经关闭或失效
			ensureNotBroken();
            
			int available = unused.size();
            // 如果当前没有空闲连接
			if(available == 0) {
				int msz = managed.size();
                // 如果当前连接数量小于maxPoolSize，则可以创建新连接
				if(msz < max) {
					// 计算想要的目标连接数=池中总连接数+等待获取连接的客户端数量+当前客户端
					int desired_target = msz + acquireWaiters.size() + 1;

					if(logger.isLoggable(MLevel.FINER))
						logger.log(MLevel.FINER, "acquire test -- pool size: " + msz + "; target_pool_size: " + target_pool_size + "; desired target? " + desired_target);
					// 如果想要的目标连接数不小于原目标连接数，才会去尝试创建新连接
					if(desired_target >= target_pool_size) {
						// inc是我们一开始设置的acquireIncrement
						desired_target = Math.max(desired_target, target_pool_size + inc);
						// 确保我们的目标数量不大于maxPoolSize，不小于minPoolSize
						target_pool_size = Math.max(Math.min(max, desired_target), min);
						// 这里就会去调整池中的连接数量
						_recheckResizePool();
					}
				} else {
					if(logger.isLoggable(MLevel.FINER))
						logger.log(MLevel.FINER, "acquire test -- pool is already maxed out. [managed: " + msz + "; max: " + max + "]");
				}
				// 等待可用连接，如果设置checkoutTimeout可能会抛出超时异常
				awaitAvailable(timeout); // throws timeout exception
			}
			// 从空闲连接中获取
			Object resc = unused.get(0);

			// 如果获取到的连接正在被空闲资源检测线程使用
			if(idleCheckResources.contains(resc)) {
				if(Debug.DEBUG && logger.isLoggable(MLevel.FINER))
					logger.log(MLevel.FINER, "Resource we want to check out is in idleCheck! (waiting until idle-check completes.) [" + this + "]");

				// 需要再次等待后重新获取连接对象
				Thread t = Thread.currentThread();
				try {
					otherWaiters.add(t);
					this.wait(timeout);
					ensureNotBroken();
				} finally {
					otherWaiters.remove(t);
				}
				return prelimCheckoutResource(timeout);
            // 如果当前连接过期，需要从池中删除，并尝试重新获取连接
			} else if(shouldExpire(resc)) {
				if(Debug.DEBUG && logger.isLoggable(MLevel.FINER))
					logger.log(MLevel.FINER, "Resource we want to check out has expired already. Trying again.");

				removeResource(resc);
				ensureMinResources();
				return prelimCheckoutResource(timeout);
            // 将连接对象从空闲队列中移出
			} else {
				unused.remove(0);
				return resc;
			}
		} catch(ResourceClosedException e) // one of our async threads died
			// ·······
		}
	}
```

### BasicResourcePool._recheckResizePool()

从上个方法可知，当前没有空闲连接可用，且连接池中的连接还未达到`maxPoolSize`时，就可以尝试创建新的连接。在这个方法中，会计算需要增加的连接数。

```java
	private void _recheckResizePool() {
		assert Thread.holdsLock(this);
		
		if(!broken) {
			int msz = managed.size();

			int shrink_count;
			int expand_count;
			// 从池中清除指定数量的连接
			if((shrink_count = msz - pending_removes - target_pool_size) > 0)
				shrinkPool(shrink_count);
            // 从池中增加指定数量的连接
			else if((expand_count = target_pool_size - (msz + pending_acquires)) > 0)
				expandPool(expand_count);
		}
	}
```

### BasicResourcePool.expandPool(int)

在这个方法中，会采用异步的方式来创建新的连接对象。`c3p0`挺奇怪的，动不动就异步？

```java
	private void expandPool(int count) {
		assert Thread.holdsLock(this);

		// 这里是采用异步方式获取连接对象的，具体有两个不同人物类型，我暂时不知道区别
		if(USE_SCATTERED_ACQUIRE_TASK) {
			for(int i = 0; i < count; ++i)
				taskRunner.postRunnable(new ScatteredAcquireTask());
		} else {
			for(int i = 0; i < count; ++i)
				taskRunner.postRunnable(new AcquireTask());
		}
	}
```
`ScatteredAcquireTask`和`AcquireTask`都是`BasicResourcePool`的内部类，在它们的`run`方法中最终会去调用`PooledConnectionResourcePoolManager`的`acquireResource`方法。

### PooledConnectionResourcePoolManager.acquireResource()

在创建数据源对象时有提到`WrapperConnectionPoolDataSource`这个类，它可以用来创建`PooledConnection`。这个方法中就是调用`WrapperConnectionPoolDataSource`对象来获取`PooledConnection`对象（实现类`NewPooledConnection`）。

```java
	public Object acquireResource() throws Exception {
		PooledConnection out;
        // 一般我们不回去设置connectionCustomizerClassName，所以直接看connectionCustomizer为空的情况
		if(connectionCustomizer == null) {
            // 会去调用WrapperConnectionPoolDataSource的getPooledConnection方法
			out = (auth.equals(C3P0ImplUtils.NULL_AUTH) ? cpds.getPooledConnection() : cpds.getPooledConnection(auth.getUser(), auth.getPassword()));
		} else {
			// ·····
		}
		
        // 如果开启了缓存语句
        if(scache != null) {
            if(c3p0PooledConnections)
                ((AbstractC3P0PooledConnection)out).initStatementCache(scache);
            else {
                logger.warning("StatementPooling not " + "implemented for external (non-c3p0) " + "ConnectionPoolDataSources.");
            }
        }
		// ······
        return out;
	}
```

### WrapperConnectionPoolDataSource.getPooledConnection(String, String, ConnectionCustomizer, String)

这个方法会先获取物理连接，然后将物理连接包装成`NewPooledConnection`。

```java
	protected PooledConnection getPooledConnection(String user, String password, ConnectionCustomizer cc, String pdsIdt) throws SQLException {
        // 这里获得的就是我们前面提到的DriverManagerDataSource
		DataSource nds = getNestedDataSource();
		Connection conn = null;
        // 使用DriverManagerDataSource获得原生的Connection
		conn = nds.getConnection(user, password);
        // 一般我们不会去设置usesTraditionalReflectiveProxies，所以只看false的情况
		if(this.isUsesTraditionalReflectiveProxies(user)) {
			return new C3P0PooledConnection(conn, 
					connectionTester, 
					this.isAutoCommitOnClose(user), 
					this.isForceIgnoreUnresolvedTransactions(user), 
					cc, 
					pdsIdt);
		} else {
            // NewPooledConnection就是原生连接的一个包装类而已，没什么特别的
			return new NewPooledConnection(conn, 
					connectionTester, 
					this.isAutoCommitOnClose(user), 
					this.isForceIgnoreUnresolvedTransactions(user), 
					this.getPreferredTestQuery(user), 
					cc, 
					pdsIdt);
		}
	}
```
以上，基本讲完获取连接对象的过程，`c3p0`的源码分析也基本完成，后续有空再做补充。

# 参考资料

c3p0 - JDBC3 Connection and Statement Pooling  by Steve Waldman 



>本文为原创文章，转载请附上原文出处链接：https://github.com/ZhangZiSheng001/c3p0-demo

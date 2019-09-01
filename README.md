
# C3P0

## 简介  
c3p0用于创建和管理连接。我们可以通过ComboPooledDataSource这个类来设置参数和获取连接，类似于DBCP的BaseDataSource。  
c3p0的源码比较复杂，具体的实现逻辑还没看明白。后续再补充

## 使用例子
### 需求
使用C3P0连接池获取连接对象，对用户数据进行增删改查。

### 工程环境
JDK：1.8.0_201  
maven：3.6.1  
IDE：Spring Tool Suites4 for Eclipse  
mysql驱动：8.0.15
mysql：5.7 

### 主要步骤
C3P0对外交互主要是一个`ComboPooledDataSource`，用于设置连接池参数和获取连接对象。
1. 只要我们的配置文件名为`c3p0-config.xml`，直接 `new ComboPooledDataSource()`就可以获得数据源对象了。
2. 通过数据源对象的`getConnection()`方法获得连接。

### 创建表
```sql
CREATE DATABASE `demo`CHARACTER SET utf8 COLLATE utf8_bin;
User `demo`;
CREATE TABLE `user` (
  `id` tinyint(3) unsigned NOT NULL AUTO_INCREMENT COMMENT '用户id',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '用户名',
  `age` int(10) unsigned DEFAULT NULL COMMENT '用户年龄',
  `gmt_create` datetime DEFAULT NULL COMMENT '记录创建时间',
  `gmt_modified` datetime DEFAULT NULL COMMENT '记录最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
```

### 创建项目
项目类型Maven Project，打包方式jar

### 引入依赖
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
	<version>0.9.5.2</version>
</dependency>
<!-- mysql驱动 -->
<dependency>
	<groupId>mysql</groupId>
	<artifactId>mysql-connector-java</artifactId>
	<version>8.0.15</version>
</dependency>
<!-- 日志 -->
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

### 编写`c3p0-config.xml`
路径：resources目录下
注意：文件名必须是`c3p0-config.xml`
```xml
#数据库配置
<?xml version="1.0" encoding="UTF-8"?>
<c3p0-config>
	<default-config>
	    <property name="driverClass">com.mysql.cj.jdbc.Driver</property>
	    <property name="jdbcUrl">jdbc:mysql://localhost:3306/demo?useUnicode=true&amp;characterEncoding=utf8&amp;serverTimezone=GMT%2B8&amp;useSSL=true</property>
	    <property name="user">root</property>
	    <property name="password">root</property>
	</default-config>
</c3p0-config>    
```

### 编写JDBCUtil用于获得连接对象
这里设置工具类的目的是避免多个线程使用同一个连接对象，并提供了释放资源的方法（注意，考虑到重用性，这里并不会关闭连接）。  
路径：`cn.zzs.c3p0`
```java
/**
 * @ClassName: JDBCUtil
 * @Description: 用于获取数据库连接对象的工具类
 * @author: zzs
 * @date: 2019年8月31日 下午9:05:08
 */
public class JDBCUtil {
	private static DataSource dataSource;
	private static ThreadLocal<Connection> tl = new ThreadLocal<>();
	private static Object obj = new Object();
	
	static {
		init();
	}
	/**
	 * 
	 * @Title: getConnection
	 * @Description: 获取数据库连接对象的方法，线程安全
	 * @author: zzs
	 * @date: 2019年8月31日 下午9:22:29
	 * @return: Connection
	 */
	public static Connection getConnection(){
		//从当前线程中获取连接对象
		Connection connection = tl.get();
		//判断为空的话，创建连接并绑定到当前线程
		if(connection == null) {
			synchronized (obj) {
				if(tl.get() == null) {
					connection = createConnection();
					tl.set(connection);
				}
			}
		}
		return connection;
	}
	/**
	 * 
	 * @Title: release
	 * @Description: 释放资源
	 * @author: zzs
	 * @date: 2019年8月31日 下午9:39:24
	 * @param conn
	 * @param statement
	 * @return: void
	 */
	public static void release(Connection conn,Statement statement,ResultSet resultSet) {
		if(resultSet!=null) {
			try {
				resultSet.close();
			} catch (SQLException e) {
				System.err.println("关闭ResultSet对象异常");
				e.printStackTrace();
			}
		}
		if(statement != null) {
			try {
				statement.close();
			} catch (SQLException e) {
				System.err.println("关闭Statement对象异常");
				e.printStackTrace();
			}
		}
		//注意：这里不关闭连接
		if(conn!=null) {
			try {
				//如果连接失效的话，从当前线程的绑定中删除
				if(!conn.isValid(3)) {
					tl.remove();
				}
			} catch (SQLException e) {
				System.err.println("校验连接有效性");
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * 
	 * @Title: createConnection
	 * @Description: 创建数据库连接
	 * @author: zzs
	 * @date: 2019年8月31日 下午9:27:03
	 * @return: Connection
	 */
	private static Connection createConnection(){ 
		Connection conn = null;
		//获得连接
		try {
			conn = dataSource.getConnection();
		} catch (SQLException e) {
			System.err.println("从数据源获取连接失败");
			e.printStackTrace();
		}
		return conn;
	}
	
	/**
	 * @Title: init
	 * @Description: 根据指定配置文件创建数据源对象
	 * @author: zzs
	 * @date: 2019年9月1日 上午10:53:05
	 * @return: void
	 */
	private static void init() {
		//配置文件名为c3p0-config.xml，构造不需要传入参数。
		dataSource = new ComboPooledDataSource();
	}
}
```

### 编写测试类
路径：test目录下的`cn.zzs.c3p0`

#### 添加用户
注意：这里引入了事务
```java
/**
 * 测试添加用户
 * @throws SQLException 
 */
@Test
public void saveUser() throws Exception {
	//创建sql
	String sql = "insert into user values(null,?,?,?,?)";
	//获得连接
	Connection connection = JDBCUtil.getConnection();
	PreparedStatement statement = null;
	try {
		//设置非自动提交
		connection.setAutoCommit(false);
		//获得Statement对象
		statement = connection.prepareStatement(sql);
		//设置参数
		statement.setString(1, "zzs001");
		statement.setInt(2, 18);
		statement.setDate(3, new Date(System.currentTimeMillis()));
		statement.setDate(4, new Date(System.currentTimeMillis()));
		//执行
		statement.executeUpdate();
		//提交事务
		connection.commit();
	} catch (Exception e) {
		System.out.println("异常导致操作回滚");
		connection.rollback();
		e.printStackTrace();
	} finally {
		//释放资源
		JDBCUtil.release(connection, statement,null);
	}
}
```
#### 更新用户
```java
/**
 * 测试更新用户
 */
@Test
public void updateUser() throws Exception {
	//创建sql
	String sql = "update user set age = ?,gmt_modified = ? where name = ?";
	//获得连接
	Connection connection = JDBCUtil.getConnection();
	PreparedStatement statement = null;
	try {
		//设置非自动提交
		connection.setAutoCommit(false);
		//获得Statement对象
		statement = connection.prepareStatement(sql);
		//设置参数
		statement.setInt(1, 19);
		statement.setDate(2, new Date(System.currentTimeMillis()));
		statement.setString(3, "zzs001");
		//执行
		statement.executeUpdate();
		//提交事务
		connection.commit();
	} catch (Exception e) {
		System.out.println("异常导致操作回滚");
		connection.rollback();
		e.printStackTrace();
	} finally {
		//释放资源
		JDBCUtil.release(connection, statement,null);
	}
}
```
#### 查询用户
```java
/**
 * 测试查找用户
 */
@Test
public void findUser() throws Exception {
	//创建sql
	String sql = "select * from user where name = ?";
	//获得连接
	Connection connection = JDBCUtil.getConnection();
	PreparedStatement statement = null;
	ResultSet resultSet = null;
	try {
		//获得Statement对象
		statement = connection.prepareStatement(sql);
		//设置参数
		statement.setString(1, "zzs001");
		//执行
		resultSet = statement.executeQuery();
		//遍历结果集
		while (resultSet.next()) {
			String name = resultSet.getString(2);
			int age = resultSet.getInt(3);
			System.out.println("用户名：" + name + ",年龄：" + age);
		}
	} finally {
		//释放资源
		JDBCUtil.release(connection, statement,resultSet);
	}
}
```
#### 删除用户
```java
/**
 * 测试删除用户
 */
@Test
public void deleteUser() throws Exception {
	//创建sql
	String sql = "delete from user where name = ?";
	//获得连接
	Connection connection = JDBCUtil.getConnection();
	PreparedStatement statement = null;
	try {
		//设置非自动提交
		connection.setAutoCommit(false);
		//获得Statement对象
		statement = connection.prepareStatement(sql);
		//设置参数
		statement.setString(1, "zzs001");
		//执行
		statement.executeUpdate();
		//提交事务
		connection.commit();
	} catch (Exception e) {
		System.out.println("异常导致操作回滚");
		connection.rollback();
		e.printStackTrace();
	} finally {
		//释放资源
		JDBCUtil.release(connection, statement,null);
	}
}
```

### c3p0配置文件详解
具体参考源码中的c3p0-config.xml，里面已经罗列出来了。

> 学习使我快乐！！

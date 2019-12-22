<%@page import="com.mchange.v2.c3p0.ComboPooledDataSource"%>
<%@page import="javax.sql.PooledConnection"%>
<%@page import="com.mchange.v2.c3p0.JndiRefConnectionPoolDataSource"%>
<%@page import="java.sql.Connection"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@page import="com.mchange.v2.c3p0.PooledDataSource"%>
<%@page import="javax.sql.DataSource"%>
<%@page import="javax.naming.InitialContext"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
</head>
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
</html>
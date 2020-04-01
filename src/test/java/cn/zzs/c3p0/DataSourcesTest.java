package cn.zzs.c3p0;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import com.mchange.v2.c3p0.DataSources;
import com.mchange.v2.c3p0.PooledDataSource;

/**
 * <p>测试DataSources获取数据源并操作数据库</p>
 * @author: zzs
 * @date: 2019年12月17日 上午9:22:03
 */
public class DataSourcesTest {

    private static final Log log = LogFactory.getLog(DataSourcesTest.class);

    @Test
    public void test01() throws Exception {
        DataSource ds_unpooled = DataSources.unpooledDataSource();
        DataSource ds_pooled = DataSources.pooledDataSource(ds_unpooled);
        Connection connection = ds_pooled.getConnection();
        findAll(connection);
        queryDSStatus(ds_pooled);
        DataSources.destroy(ds_pooled);
    }

    private void queryDSStatus(DataSource ds) throws SQLException {
        if(ds instanceof PooledDataSource) {
            PooledDataSource pds = (PooledDataSource)ds;
            System.err.println("num_connections: " + pds.getNumConnectionsDefaultUser());
            System.err.println("num_busy_connections: " + pds.getNumBusyConnectionsDefaultUser());
            System.err.println("num_idle_connections: " + pds.getNumIdleConnectionsDefaultUser());
            System.err.println();
        } else
            System.err.println("Not a c3p0 PooledDataSource!");
    }

    /**
     * 测试查找用户
     */
    private void findAll(Connection connection) {
        // 创建sql
        String sql = "select * from demo_user where deleted = false";
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            // 获得Statement对象
            statement = connection.prepareStatement(sql);
            // 执行
            resultSet = statement.executeQuery();
            // 遍历结果集
            while(resultSet.next()) {
                String name = resultSet.getString(2);
                int age = resultSet.getInt(3);
                System.out.println("用户名：" + name + ",年龄：" + age);
            }
        } catch(SQLException e) {
            log.error("查询用户异常", e);
        } finally {
            // 释放资源
            JDBCUtil.release(connection, statement, resultSet);
        }
    }
}

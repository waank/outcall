package org.qianye.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * 数据源配置类
 * 支持动态切换 MySQL 和 H2 内存数据库
 */
@Configuration
@MapperScan(basePackages = "org.qianye.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class DataSourceConfig {

    @Autowired
    private Environment env;

    /**
     * MySQL 数据源配置
     */
    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create()
                .driverClassName(env.getProperty("spring.datasource.mysql.driver-class-name"))
                .url(env.getProperty("spring.datasource.mysql.url"))
                .username(env.getProperty("spring.datasource.mysql.username"))
                .password(env.getProperty("spring.datasource.mysql.password"))
                .build();
    }

    /**
     * H2 内存数据库数据源配置
     */
    @Bean(name = "h2DataSource")
    public DataSource h2DataSource() {
        return DataSourceBuilder.create()
                .driverClassName(env.getProperty("spring.datasource.h2.driver-class-name"))
                .url(env.getProperty("spring.datasource.h2.url"))
                .username(env.getProperty("spring.datasource.h2.username"))
                .password(env.getProperty("spring.datasource.h2.password"))
                .build();
    }

    /**
     * 主数据源 - 根据配置决定使用哪个数据源
     */
    @Bean(name = "dataSource")
    @Primary
    public DataSource dataSource() {
        String dbType = env.getProperty("app.database.type", "mysql");
        System.out.println("当前数据库类型配置: " + dbType);
        
        if ("h2".equals(dbType)) {
            System.out.println("使用 H2 内存数据库");
            return h2DataSource();
        } else {
            System.out.println("使用 MySQL 数据库");
            return mysqlDataSource();
        }
    }

    /**
     * SqlSessionFactory 配置
     */
    @Bean(name = "sqlSessionFactory")
    @Primary
    public SqlSessionFactory sqlSessionFactory(@Qualifier("dataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
        return bean.getObject();
    }

    /**
     * 事务管理器
     */
    @Bean(name = "transactionManager")
    @Primary
    public DataSourceTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * SqlSessionTemplate
     */
    @Bean(name = "sqlSessionTemplate")
    @Primary
    public SqlSessionTemplate sqlSessionTemplate(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}

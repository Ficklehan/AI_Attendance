package com.attendance.config;

import com.attendance.mybatis.SlowSqlInterceptor;
import com.attendance.web.ApiTimingFilter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PerformanceObservabilityConfig {

    @Bean
    public SlowSqlInterceptor slowSqlInterceptor(
            @Value("${attendance.performance.slow-sql-ms:500}") long thresholdMs) {
        return new SlowSqlInterceptor(thresholdMs);
    }

    /** 将慢 SQL 拦截器注册进 MyBatis（仅声明 Bean 不会自动挂载）。 */
    @Bean
    public org.springframework.beans.factory.config.BeanPostProcessor slowSqlInterceptorRegistrar(
            SlowSqlInterceptor slowSqlInterceptor) {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof SqlSessionFactory) {
                    SqlSessionFactory factory = (SqlSessionFactory) bean;
                    org.apache.ibatis.session.Configuration cfg = factory.getConfiguration();
                    boolean exists = false;
                    for (org.apache.ibatis.plugin.Interceptor i : cfg.getInterceptors()) {
                        if (i instanceof SlowSqlInterceptor) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        cfg.addInterceptor(slowSqlInterceptor);
                    }
                }
                return bean;
            }
        };
    }

    @Bean
    public FilterRegistrationBean<ApiTimingFilter> apiTimingFilter(
            @Value("${attendance.performance.slow-api-ms:800}") long thresholdMs) {
        FilterRegistrationBean<ApiTimingFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new ApiTimingFilter(thresholdMs));
        bean.addUrlPatterns("/*");
        bean.setOrder(50);
        return bean;
    }
}

package org.sharedhealth.datasense.launch;


import org.apache.log4j.Logger;
import org.quartz.spi.JobFactory;
import org.sharedhealth.datasense.export.dhis.reports.DHISDynamicReport;
import org.sharedhealth.datasense.scheduler.AutowiringSpringBeanJobFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.ServletContextInitializer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Integer.valueOf;
import static java.lang.System.getenv;

@Configuration
@Import({DatabaseConfig.class, ScheduleConfig.class, ApplicationConfig.class, SecurityConfig.class})
@ComponentScan(basePackages = {"org.sharedhealth.datasense.config",
        "org.sharedhealth.datasense.processor",
        "org.sharedhealth.datasense.feeds",
        "org.sharedhealth.datasense.repository",
        "org.sharedhealth.datasense.client",
        "org.sharedhealth.datasense.handler",
        "org.sharedhealth.datasense.export.dhis",
        "org.sharedhealth.datasense.security",
        "org.sharedhealth.datasense.util",
        "org.sharedhealth.datasense.scheduler.jobs",
        "org.sharedhealth.datasense.service",
        "org.sharedhealth.datasense.controller",
        "org.sharedhealth.datasense.aqs"
})
public class Main {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private DataSourceTransactionManager txmanager;

    @Autowired
    private DHISDynamicReport dhisDynamicReport;

    @Autowired
    private ApplicationContext applicationContext;

    Logger log = Logger.getLogger(Main.class);

    @Bean
    public EmbeddedServletContainerFactory getFactory() {
        Map<String, String> env = getenv();
        TomcatEmbeddedServletContainerFactory factory = new TomcatEmbeddedServletContainerFactory();
        factory.addInitializers(new ServletContextInitializer() {
            @Override
            public void onStartup(ServletContext servletContext) throws ServletException {
                ServletRegistration.Dynamic shr = servletContext.addServlet("datasense", DispatcherServlet.class);
                shr.addMapping("/");
                shr.setInitParameter("contextClass", "org.springframework.web.context.support" +
                        ".AnnotationConfigWebApplicationContext");
                shr.setInitParameter("contextConfigLocation", "org.sharedhealth.datasense.launch.WebMvcConfig");
            }
        });
        String bdshr_port = env.get("DATASENSE_PORT");
        factory.setPort(valueOf(bdshr_port));
        return factory;
    }

    @Bean
    public SchedulerFactoryBean scheduler() {
        SchedulerFactoryBean quartzScheduler = new SchedulerFactoryBean();
        quartzScheduler.setDataSource(dataSource);
        quartzScheduler.setTransactionManager(txmanager);
        //quartzScheduler.setOverwriteExistingJobs(true);
        quartzScheduler.setSchedulerName("datasense-scheduler");

        quartzScheduler.setJobFactory(jobFactory());
        quartzScheduler.setConfigLocation(new ClassPathResource("db/quartz.properties"));

//        schedulerFactoryBean.setApplicationContextSchedulerContextKey("applicationContext");
        quartzScheduler.setSchedulerContextAsMap(schedulerContextMap());
        quartzScheduler.setWaitForJobsToCompleteOnShutdown(false);
        try {
            quartzScheduler.afterPropertiesSet();
        } catch (Exception e) {
            String message = "Cannot start scheduler :";
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
        return quartzScheduler;
    }


    @Bean
    public JobFactory jobFactory() {
        //SpringBeanJobFactory springBeanJobFactory = new SpringBeanJobFactory();
        // custom job factory of spring with DI support for @Autowired!
        AutowiringSpringBeanJobFactory springBeanJobFactory = new AutowiringSpringBeanJobFactory();
        springBeanJobFactory.setApplicationContext(applicationContext);
        return springBeanJobFactory;
    }

    @Bean
    public Map<String, Object> schedulerContextMap() {
        HashMap<String, Object> ctx = new HashMap<>();
        ctx.put("dhisDynamicReport", dhisDynamicReport);
        return ctx;
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Main.class, args);
    }
}

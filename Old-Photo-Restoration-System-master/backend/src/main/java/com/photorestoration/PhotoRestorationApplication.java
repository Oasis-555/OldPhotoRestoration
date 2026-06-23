package com.photorestoration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 老照片修复系统主应用程序
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
@EnableCaching
@MapperScan("com.photorestoration.repository")
public class PhotoRestorationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhotoRestorationApplication.class, args);
    }
}

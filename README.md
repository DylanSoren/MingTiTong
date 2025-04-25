# 命题通 智能命题答题平台

### 项目介绍：

基于SpringBoot+Redis+DeepSeek+RxJava+SSE的AI命题、AI评测平台。用户可基于AI快速生成题目并制作应用，经管理
员审核后，可在线答题并基于多种评分算法或AI得到反馈总结。

### 主要工作：
* 库表设计：根据业务设计用户、应用、题目、评分结果、用户答题表。其中给题目表添加aPP_id索引以提升检索性能。
* 评分模块：基于策略模式实现了多种用户回答评分算法（如统计得分、AI评分等），全局执行器会扫描策略类上的自定义注解
并选取策略，相较于ifelse提高了系统的可扩展性。
* 基于DeepSeek封装了通用AI服务，并通过配置类自动读取key配置初始化AI客户端Bean，便于全局使用。
* 由于AI生成题目较慢，选用DeepSeek的流式API并通过RxJava+SSE实时推送单道题目给前端，提高用户体验。
* 由于相同答案的AI评分应该相同，使用Caffeine本地缓存答案Hash对应的AI评分结果，提高评分性能的同时大幅节约成本；
并通过Redisson分布式锁解决缓存击穿问题。

### 主流框架 & 特性

- Spring Boot 2.7.x
- Spring MVC
- MyBatis + MyBatis-Plus 数据访问（开启分页）
- Spring Boot 调试工具和项目处理器
- Spring AOP 切面编程

### 数据存储

- MySQL 数据库
- Redis 内存数据库

### 工具类

- Hutool 工具库
- Lombok 注解

### 业务特性

- 业务代码生成器（支持自动生成 Service、Controller、数据模型代码）
- Spring Session Redis 分布式登录
- 全局请求响应拦截器（记录日志）
- 全局异常处理器
- 自定义错误码
- 封装通用响应类
- Swagger + Knife4j 接口文档
- 自定义权限注解 + 全局校验


### 单元测试

- JUnit5 单元测试
- 示例单元测试类
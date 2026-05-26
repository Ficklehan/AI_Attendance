# AI考勤智能助手 - 技术栈需求规范

## 文档信息

- **项目名称**：AI考勤智能助手（Attendance Assistant）
- **目标技术栈**：JDK 1.8 + Spring Boot 2.7.x + MyBatis 3.5.x + MySQL 8.0
- **前端技术栈**：Vue 3 + Vite + Element Plus
- **文档版本**：v1.0
- **创建日期**：2026-05-20
- **状态**：需求规范

---

## 一、后端技术规范

### 1.1 开发环境要求

#### 1.1.1 JDK版本要求

```
JDK版本：JDK 1.8 (Java 8)
最低版本：1.8.0_31
推荐版本：1.8.0_311 或更高
下载地址：https://adoptium.net/temurin/releases/?version=8
```

#### 1.1.2 Maven配置要求

```xml
<!-- pom.xml 关键配置 -->
<properties>
    <java.version>1.8</java.version>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <spring-boot.version>2.7.18</spring-boot.version>
    <mybatis.version>3.5.16</mybatis.version>
    <mybatis-spring-boot.version>2.2.2</mybatis-spring-boot.version>
</properties>
```

#### 1.1.3 必需依赖清单

| 依赖 | 版本 | 说明 | 用途 |
|-----|------|-----|------|
| spring-boot-starter-web | 2.7.18 | Spring Boot Web | RESTful API |
| spring-boot-starter-security | 2.7.18 | Spring Security | 认证授权 |
| spring-boot-starter-validation | 2.7.18 | Hibernate Validator | 参数校验 |
| mybatis-spring-boot-starter | 2.2.2 | MyBatis集成 | ORM框架 |
| mysql-connector-java | 8.0.33 | MySQL驱动 | 数据库连接 |
| druid-spring-boot-starter | 1.2.20 | Druid连接池 | 连接池管理 |
| jjwt | 0.11.5 | JWT库 | Token生成验证 |
| feishu-sdk-java | 1.0.0+ | 飞书SDK | 飞书集成 |
| okhttp | 4.12.0 | HTTP客户端 | AI接口调用 |
| lombok | 1.18.30 | 代码生成 | 简化代码 |
| spring-boot-starter-test | 2.7.18 | 测试框架 | 单元测试 |

### 1.2 Spring Boot配置规范

#### 1.2.1 application.yml结构

```yaml
# 多环境配置结构
# application.yml (公共配置)
spring:
  profiles:
    active: @spring.profiles.active@
  application:
    name: attendance-assistant

# application-dev.yml (开发环境)
spring:
  profiles: dev
  datasource:
    url: jdbc:mysql://localhost:3306/attendance_assistant?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: 12345678
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
  servlet:
    multipart:
      enabled: true
      max-file-size: 10MB
      max-request-size: 20MB

server:
  port: 3000
  servlet:
    context-path: /api

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.attendance.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

jwt:
  secret: attendance-jwt-secret-key-2024
  expiration: 604800000  # 7天

upload:
  path: ./uploads
  max-size: 10485760  # 10MB

feishu:
  app-id: ${FEISHU_APP_ID}
  app-secret: ${FEISHU_APP_SECRET}
  encryption-key: ${FEISHU_ENCRYPT_KEY}
  verification-token: ${FEISHU_VERIFICATION_TOKEN}

mimo:
  api-key: ${MIMO_API_KEY}
  api-url: https://api.mimo.com/v2/vision
```

#### 1.2.2 配置类规范

```java
// 1. 配置属性绑定类
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private Long expiration;
}

// 2. 配置启用
@EnableConfigurationProperties(JwtProperties.class)
@SpringBootApplication
public class AttendanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AttendanceApplication.class, args);
    }
}
```

### 1.3 MyBatis开发规范

#### 1.3.1 Mapper接口规范

```java
// Mapper接口命名规范
@Mapper
public interface TaskMapper {
    
    // 查询方法命名规范
    // select/get + 实体名 + By + 条件
    Task selectTaskByTaskId(@Param("taskId") String taskId);
    
    List<Task> selectTaskList(TaskQuery query);
    
    // 插入方法命名规范
    // insert + 实体名
    int insertTask(Task task);
    
    // 更新方法命名规范
    // update + 实体名
    int updateTask(Task task);
    
    // 删除方法命名规范
    // delete + 实体名 + By + 条件
    int deleteTaskByTaskId(@Param("taskId") String taskId);
    
    // 统计方法命名规范
    // count + 条件
    Long countTaskByUserId(@Param("userId") String userId);
}
```

#### 1.3.2 XML映射文件规范

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" 
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.attendance.mapper.TaskMapper">
    
    <resultMap id="BaseResultMap" type="com.attendance.entity.Task">
        <id column="task_id" property="taskId"/>
        <result column="user_id" property="userId"/>
        <result column="file_key" property="fileKey"/>
        <result column="status" property="status"/>
        <result column="raw_data" property="rawData"/>
        <result column="confirmed_data" property="confirmedData"/>
        <result column="ai_raw_output" property="aiRawOutput"/>
        <result column="processed_by" property="processedBy"/>
        <result column="created_at" property="createdAt"/>
        <result column="updated_at" property="updatedAt"/>
    </resultMap>
    
    <sql id="Base_Column_List">
        task_id, user_id, file_key, status, raw_data, confirmed_data,
        ai_raw_output, processed_by, created_at, updated_at
    </sql>
    
    <select id="selectTaskList" resultMap="BaseResultMap">
        SELECT <include refid="Base_Column_List"/>
        FROM tasks
        <where>
            <if test="userId != null and userId != ''">
                AND user_id = #{userId}
            </if>
            <if test="status != null and status != ''">
                AND status = #{status}
            </if>
            <if test="startDate != null">
                AND created_at &gt;= #{startDate}
            </if>
            <if test="endDate != null">
                AND created_at &lt;= #{endDate}
            </if>
        </where>
        ORDER BY created_at DESC
        LIMIT #{offset}, #{limit}
    </select>
    
</mapper>
```

#### 1.3.3 动态SQL规范

```java
// 使用Scripting动态SQL
@Select("<script>" +
        "SELECT * FROM tasks WHERE 1=1" +
        "<if test='status != null'> AND status = #{status}</if>" +
        "<if test='keyword != null'> AND (task_id LIKE CONCAT('%',#{keyword},'%') OR file_key LIKE CONCAT('%',#{keyword},'%'))</if>" +
        "</script>")
List<Task> searchTasks(TaskSearchDTO dto);
```

### 1.4 RESTful API设计规范

#### 1.4.1 统一响应格式

```java
// 统一响应类
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    
    private int code;
    private String message;
    private T data;
    private long timestamp;
    
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null, System.currentTimeMillis());
    }
    
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data, System.currentTimeMillis());
    }
    
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data, System.currentTimeMillis());
    }
    
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null, System.currentTimeMillis());
    }
    
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }
}

// 分页响应
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long current;
    private long size;
    private long pages;
}
```

#### 1.4.2 请求参数校验

```java
// 使用Hibernate Validator进行参数校验
@Data
public class LoginRequest {
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50个字符之间")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度不能少于6位")
    private String password;
    
    @Pattern(regexp = "^[a-zA-Z0-9_+=\\-\\.]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$", 
             message = "邮箱格式不正确")
    private String email;
}

// 控制器中使用
@PostMapping("/login")
public Result<String> login(@Valid @RequestBody LoginRequest request) {
    // 业务逻辑
}
```

#### 1.4.3 分页查询规范

```java
// 分页请求DTO
@Data
public class PageRequest {
    @Min(value = 1, message = "页码最小为1")
    private Long current = 1L;
    
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Long size = 20L;
    
    public Long getOffset() {
        return (current - 1) * size;
    }
}

// 分页查询示例
@GetMapping("/tasks")
public Result<PageResult<Task>> getTaskList(TaskQuery query) {
    query.setOffset((query.getCurrent() - 1) * query.getSize());
    List<Task> records = taskMapper.selectTaskList(query);
    Long total = taskMapper.countTaskList(query);
    
    PageResult<Task> pageResult = new PageResult<>();
    pageResult.setRecords(records);
    pageResult.setTotal(total);
    pageResult.setCurrent(query.getCurrent());
    pageResult.setSize(query.getSize());
    pageResult.setPages((total + query.getSize() - 1) / query.getSize());
    
    return Result.success(pageResult);
}
```

### 1.5 异常处理规范

#### 1.5.1 业务异常定义

```java
// 业务异常类
@Data
public class BusinessException extends RuntimeException {
    private int code;
    
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
    
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}

// 异常码定义
public class ErrorCode {
    public static final int USER_NOT_FOUND = 1001;
    public static final int USER_ALREADY_EXISTS = 1002;
    public static final int PASSWORD_ERROR = 1003;
    public static final int TOKEN_INVALID = 1004;
    public static final int TOKEN_EXPIRED = 1005;
    public static final int PERMISSION_DENIED = 1006;
    
    public static final int TASK_NOT_FOUND = 2001;
    public static final int TASK_STATUS_ERROR = 2002;
    
    public static final int FILE_UPLOAD_ERROR = 3001;
    public static final int FILE_TYPE_NOT_ALLOWED = 3002;
    public static final int FILE_SIZE_EXCEEDED = 3003;
    
    public static final int AI_PARSE_ERROR = 4001;
    public static final int FEISHU_API_ERROR = 4002;
}
```

#### 1.5.2 全局异常处理器

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        log.error("参数校验异常: {}", message);
        return Result.error(400, message);
    }
    
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统异常，请稍后重试");
    }
}
```

### 1.6 日志规范

#### 1.6.1 日志级别使用规范

```java
@Slf4j
@Service
public class TaskService {
    
    public void createTask(Task task) {
        log.info("创建任务: taskId={}, userId={}", task.getTaskId(), task.getUserId());
        try {
            // 业务逻辑
            log.info("任务创建成功: taskId={}", task.getTaskId());
        } catch (Exception e) {
            log.error("任务创建失败: taskId={}, error={}", task.getTaskId(), e.getMessage());
            throw e;
        }
    }
    
    public void processTask(String taskId) {
        log.debug("开始处理任务: taskId={}", taskId);
        // 详细处理日志
        log.debug("任务处理完成: taskId={}, duration={}ms", taskId, duration);
    }
}
```

#### 1.6.2 日志配置

```xml
<!-- logback-spring.xml -->
<configuration>
    <property name="LOG_PATH" value="./logs"/>
    <property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"/>
    
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>
    
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/application.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>
    
    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/error.log</file>
        <filter class="ch.qos.logback.classic.filter.LevelFilter">
            <level>ERROR</level>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/error.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
        <appender-ref ref="ERROR_FILE"/>
    </root>
    
    <logger name="com.attendance" level="DEBUG"/>
    <logger name="org.springframework" level="INFO"/>
    <logger name="org.mybatis" level="DEBUG"/>
</configuration>
```

### 1.7 事务管理规范

#### 1.7.1 事务传播行为

```java
@Service
@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
public class TaskService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTask(Task task) {
        taskMapper.insertTask(task);
    }
    
    public void confirmTask(String taskId, List<Record> records) {
        // 1. 查询任务
        Task task = taskMapper.selectTaskByTaskId(taskId);
        
        // 2. 验证任务状态
        if (!"processed".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_ERROR, "任务状态不允许确认");
        }
        
        // 3. 更新任务（在同一事务中）
        task.setStatus("confirmed");
        task.setConfirmedData(JSON.toJSONString(records));
        taskMapper.updateTask(task);
        
        // 4. 记录审计日志（在同一事务中）
        auditLogService.log("TASK_CONFIRMED", "task", taskId, userId);
    }
}
```

### 1.8 安全配置规范

#### 1.8.1 CORS跨域配置

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

#### 1.8.2 JWT认证配置

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors().and()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(auth -> auth
                .antMatchers("/api/auth/**", "/webhook/**").permitAll()
                .antMatchers("/api/service/status").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}

// JWT认证过滤器
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                String userId = claims.getSubject();
                String username = claims.get("username", String.class);
                
                UserDetails userDetails = new User(userId, username);
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.error("JWT验证失败: {}", e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
```

---

## 二、前端技术规范

### 2.1 开发环境要求

#### 2.1.1 Node.js版本要求

```
Node.js版本：18.x 或 20.x LTS
最低版本：18.0.0
推荐版本：20.11.1 LTS
下载地址：https://nodejs.org/
```

#### 2.1.2 包管理器要求

```
推荐使用：pnpm (高性能) 或 npm (官方)
pnpm版本：8.x+
npm版本：10.x+
```

#### 2.1.3 必需依赖清单

| 依赖 | 版本 | 说明 | 用途 |
|-----|------|-----|------|
| vue | 3.4.x | Vue核心 | 框架 |
| vue-router | 4.2.x | Vue路由 | 路由管理 |
| pinia | 2.1.x | 状态管理 | 状态管理 |
| axios | 1.6.x | HTTP客户端 | API请求 |
| element-plus | 2.5.x | UI组件库 | UI组件 |
| @element-plus/icons-vue | 2.3.x | 图标库 | 图标 |
| sass | 1.69.x | CSS预处理器 | 样式 |
| @vueuse/core | 10.7.x | Vue工具函数 | 工具函数 |

### 2.2 项目配置规范

#### 2.2.1 vite.config.js配置

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '~': path.resolve(__dirname, 'src'),
    },
  },
  
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:3000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
        },
      },
    },
  },
  
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@import "@/styles/variables.scss";`,
      },
    },
  },
})
```

#### 2.2.2 环境变量配置

```bash
# .env.development
VITE_APP_TITLE=AI考勤智能助手
VITE_API_BASE_URL=/api
VITE_APP_ENV=development

# .env.production
VITE_APP_TITLE=AI考勤智能助手
VITE_API_BASE_URL=https://api.example.com
VITE_APP_ENV=production
```

### 2.3 Vue 3开发规范

#### 2.3.1 组件命名规范

```vue
<!-- 组件命名：PascalCase 或 kebab-case -->
<!-- 组件文件名：PascalCase.vue -->
<template>
  <div class="task-edit">
    <!-- 模板中使用 kebab-case -->
    <el-button type="primary" @click="handle-submit">
      提交
    </el-button>
  </div>
</template>

<script setup>
// 组件名：PascalCase
import TaskTable from './TaskTable.vue'
import ImageUploader from './ImageUploader.vue'

// 使用 defineProps 定义 props
const props = defineProps({
  taskId: {
    type: String,
    required: true,
  },
})

// 使用 defineEmits 定义事件
const emit = defineEmits(['success', 'cancel'])

// 组件逻辑
const handleSubmit = () => {
  emit('success', data)
}
</script>

<style lang="scss" scoped>
.task-edit {
  padding: 20px;
}
</style>
```

#### 2.3.2 组合式函数（Composables）规范

```javascript
// composables/useAuth.js
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getToken, setToken, removeToken } from '@/utils/auth'

export function useAuth() {
  const router = useRouter()
  const token = ref(getToken() || '')
  const userInfo = ref(null)
  
  const isAuthenticated = computed(() => !!token.value)
  
  const login = async (credentials) => {
    try {
      const response = await authApi.login(credentials)
      token.value = response.data.token
      setToken(response.data.token)
      userInfo.value = response.data.userInfo
      return response
    } catch (error) {
      throw error
    }
  }
  
  const logout = () => {
    token.value = ''
    userInfo.value = null
    removeToken()
    router.push('/login')
  }
  
  return {
    token,
    userInfo,
    isAuthenticated,
    login,
    logout,
  }
}
```

#### 2.3.3 Pinia Store规范

```javascript
// stores/auth.js
import { defineStore } from 'pinia'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { getUserInfo, login as loginApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken() || '',
    userInfo: null,
    roles: [],
  }),
  
  getters: {
    isAuthenticated: (state) => !!state.token,
    username: (state) => state.userInfo?.username || '',
    isAdmin: (state) => state.roles.includes('admin'),
  },
  
  actions: {
    async login(credentials) {
      const response = await loginApi(credentials)
      this.token = response.data.token
      this.userInfo = response.data.userInfo
      this.roles = response.data.roles || []
      setToken(this.token)
      return response
    },
    
    async fetchUserInfo() {
      try {
        const response = await getUserInfo()
        this.userInfo = response.data
        this.roles = response.data.roles || []
      } catch (error) {
        this.logout()
        throw error
      }
    },
    
    logout() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      removeToken()
    },
  },
})
```

### 2.4 API封装规范

#### 2.4.1 Axios封装

```javascript
// utils/request.js
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers['Authorization'] = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    const res = response.data
    
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      
      if (res.code === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        router.push('/login')
      }
      
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    
    return res
  },
  (error) => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default service
```

#### 2.4.2 API模块化封装

```javascript
// api/auth.js
import request from './index'

export const login = (data) => {
  return request({
    url: '/auth/login',
    method: 'post',
    data,
  })
}

export const register = (data) => {
  return request({
    url: '/auth/register',
    method: 'post',
    data,
  })
}

export const getUserInfo = () => {
  return request({
    url: '/auth/profile',
    method: 'get',
  })
}

export const changePassword = (data) => {
  return request({
    url: '/auth/change-password',
    method: 'post',
    data,
  })
}

// api/task.js
export const getTaskList = (params) => {
  return request({
    url: '/tasks',
    method: 'get',
    params,
  })
}

export const getTaskDetail = (taskId) => {
  return request({
    url: `/tasks/${taskId}`,
    method: 'get',
  })
}

export const confirmTask = (taskId, data) => {
  return request({
    url: `/tasks/${taskId}/confirm`,
    method: 'post',
    data,
  })
}
```

### 2.5 路由规范

#### 2.5.1 路由守卫

```javascript
// router/index.js
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/components/Layout.vue'),
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home/Home.vue'),
      },
      {
        path: 'tasks',
        name: 'TaskList',
        component: () => import('@/views/task/TaskList.vue'),
      },
      {
        path: 'tasks/:taskId',
        name: 'TaskEdit',
        component: () => import('@/views/task/TaskEdit.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  
  if (requiresAuth && !authStore.isAuthenticated) {
    next('/login')
  } else if (to.path === '/login' && authStore.isAuthenticated) {
    next('/')
  } else {
    next()
  }
})

export default router
```

### 2.6 组件开发规范

#### 2.6.1 通用组件规范

```vue
<!-- components/common/StatusBadge.vue -->
<template>
  <el-tag :type="tagType" :effect="effect">
    <slot>{{ text }}</slot>
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: String,
    required: true,
  },
  effect: {
    type: String,
    default: 'light',
  },
})

const tagType = computed(() => {
  const typeMap = {
    processing: 'warning',
    processed: 'primary',
    confirmed: 'success',
    failed: 'danger',
  }
  return typeMap[props.status] || 'info'
})

const text = computed(() => {
  const textMap = {
    processing: '处理中',
    processed: '已处理',
    confirmed: '已确认',
    failed: '失败',
  }
  return textMap[props.status] || props.status
})
</script>
```

#### 2.6.2 业务组件规范

```vue
<!-- components/business/TaskTable.vue -->
<template>
  <el-table :data="data" border stripe @selection-change="handleSelectionChange">
    <el-table-column type="selection" width="55" />
    <el-table-column prop="taskId" label="任务ID" width="150" />
    <el-table-column prop="status" label="状态" width="100">
      <template #default="{ row }">
        <StatusBadge :status="row.status" />
      </template>
    </el-table-column>
    <el-table-column prop="createdAt" label="创建时间" width="180" />
    <el-table-column label="操作" fixed="right" width="200">
      <template #default="{ row }">
        <el-button type="primary" link @click="handleView(row)">
          查看
        </el-button>
        <el-button type="danger" link @click="handleDelete(row)">
          删除
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import StatusBadge from '@/components/common/StatusBadge.vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['view', 'delete', 'selection-change'])

const handleView = (row) => {
  emit('view', row)
}

const handleDelete = (row) => {
  emit('delete', row)
}

const handleSelectionChange = (selection) => {
  emit('selection-change', selection)
}
</script>
```

### 2.7 样式规范

#### 2.7.1 SCSS变量定义

```scss
// styles/variables.scss

// 主色
$primary-color: #409eff;
$success-color: #67c23a;
$warning-color: #e6a23c;
$danger-color: #f56c6c;
$info-color: #909399;

// 文字色
$text-primary: #303133;
$text-regular: #606266;
$text-secondary: #909399;
$text-placeholder: #c0c4cc;

// 边框色
$border-color: #dcdfe6;
$border-light: #e4e7ed;
$border-lighter: #ebeef5;
$border-extra-light: #f2f6fc;

// 背景色
$bg-color: #ffffff;
$bg-page: #f5f7fa;
$bg-overlay: rgba(0, 0, 0, 0.7);

// 字体
$font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 
              'Microsoft YaHei', Arial, sans-serif;
$font-size-xs: 12px;
$font-size-sm: 13px;
$font-size-base: 14px;
$font-size-lg: 16px;
$font-size-xl: 18px;

// 间距
$spacing-xs: 4px;
$spacing-sm: 8px;
$spacing-base: 12px;
$spacing-md: 16px;
$spacing-lg: 24px;
$spacing-xl: 32px;

// 圆角
$border-radius-sm: 2px;
$border-radius-base: 4px;
$border-radius-lg: 8px;

// 阴影
$shadow-sm: 0 2px 4px rgba(0, 0, 0, 0.12);
$shadow-base: 0 2px 12px rgba(0, 0, 0, 0.15);
$shadow-lg: 0 4px 24px rgba(0, 0, 0, 0.2);
```

#### 2.7.2 全局样式

```scss
// styles/global.scss
@import './variables.scss';

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  width: 100%;
  height: 100%;
  font-family: $font-family;
  font-size: $font-size-base;
  color: $text-primary;
  background-color: $bg-page;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  height: 100%;
}

// 通用工具类
.text-primary {
  color: $primary-color;
}

.text-success {
  color: $success-color;
}

.text-danger {
  color: $danger-color;
}

.text-center {
  text-align: center;
}

.flex {
  display: flex;
}

.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
}

.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

// 卡片样式
.card {
  background: $bg-color;
  border-radius: $border-radius-base;
  box-shadow: $shadow-sm;
  padding: $spacing-lg;
}

// 页面标题
.page-title {
  font-size: $font-size-xl;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-lg;
}
```

### 2.8 SSE流式处理规范

#### 2.8.1 SSE组合式函数

```javascript
// composables/useSSE.js
import { ref, onUnmounted } from 'vue'

export function useSSE(url, options = {}) {
  const { onMessage, onError, onComplete } = options
  
  const data = ref(null)
  const loading = ref(false)
  const error = ref(null)
  let eventSource = null
  
  const connect = (params = {}) => {
    loading.value = true
    error.value = null
    
    const authStore = useAuthStore()
    const queryString = new URLSearchParams(params).toString()
    const fullUrl = `${import.meta.env.VITE_API_BASE_URL}${url}?${queryString}`
    
    eventSource = new EventSource(fullUrl, {
      headers: {
        'Authorization': `Bearer ${authStore.token}`,
      },
    })
    
    eventSource.onopen = () => {
      loading.value = true
    }
    
    eventSource.onmessage = (event) => {
      try {
        const result = JSON.parse(event.data)
        data.value = result
        
        if (onMessage) {
          onMessage(result)
        }
      } catch (e) {
        console.error('SSE数据解析失败:', e)
      }
    }
    
    eventSource.addEventListener('record', (event) => {
      const record = JSON.parse(event.data)
      if (onMessage) {
        onMessage({ type: 'record', data: record })
      }
    })
    
    eventSource.addEventListener('complete', (event) => {
      const result = JSON.parse(event.data)
      loading.value = false
      if (onComplete) {
        onComplete(result)
      }
    })
    
    eventSource.onerror = (e) => {
      loading.value = false
      error.value = e
      if (onError) {
        onError(e)
      }
      close()
    }
  }
  
  const close = () => {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    loading.value = false
  }
  
  onUnmounted(() => {
    close()
  })
  
  return {
    data,
    loading,
    error,
    connect,
    close,
  }
}
```

#### 2.8.2 图片上传组件使用示例

```vue
<script setup>
import { ref } from 'vue'
import { useSSE } from '@/composables/useSSE'

const records = ref([])
const uploading = ref(false)

const { connect, close } = useSSE('/local/upload-stream', {
  onMessage: (result) => {
    if (result.type === 'record') {
      records.value.push(result.data)
    }
  },
  onComplete: () => {
    uploading.value = false
    ElMessage.success('上传成功')
  },
  onError: () => {
    uploading.value = false
    ElMessage.error('上传失败')
  },
})

const handleUpload = async (file) => {
  uploading.value = true
  records.value = []
  
  const formData = new FormData()
  formData.append('image', file.raw)
  
  connect({ taskId: taskId.value })
  
  // 使用fetch上传，因为SSE需要在请求时保持连接
  await fetch('/api/local/upload-stream', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
    body: formData,
  })
}
</script>
```

---

## 三、数据库规范

### 3.1 表设计规范

#### 3.1.1 命名规范

```sql
-- 表名：snake_case，小写，多个单词用下划线分隔
-- 正确：tasks, user_sessions, plugin_config
-- 错误：Tasks, UserSessions, pluginConfig

-- 字段名：snake_case，小写
-- 正确：user_id, created_at, is_deleted
-- 错误：userId, createdAt, isDeleted

-- 索引名：idx_表名_字段名
-- 正确：idx_tasks_user_id, idx_tasks_status
-- 错误：idx_user_id, index1
```

#### 3.1.2 字段设计规范

```sql
-- 1. 主键使用 VARCHAR(64)，不使用自增主键
-- 2. 状态字段使用 ENUM 类型
-- 3. 时间字段使用 DATETIME 类型
-- 4. JSON字段使用 JSON 类型
-- 5. 敏感字段加密存储
-- 6. 必填字段 NOT NULL

CREATE TABLE tasks (
    task_id VARCHAR(64) PRIMARY KEY COMMENT '任务ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    status ENUM('processing', 'processed', 'confirmed', 'failed') NOT NULL DEFAULT 'processing',
    raw_data JSON NULL COMMENT '原始数据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';
```

### 3.2 索引规范

```sql
-- 1. 主键自动有索引
-- 2. WHERE条件字段添加索引
-- 3. ORDER BY字段添加索引
-- 4. 联合索引注意字段顺序（等值查询字段在前）

-- 联合索引示例
CREATE INDEX idx_user_status_created ON tasks(user_id, status, created_at);

-- 前缀索引（字符串长度超过20的字段）
CREATE INDEX idx_file_key ON tasks(file_key(64));
```

### 3.3 SQL编写规范

```java
// 1. 使用参数化查询，防止SQL注入
@Select("SELECT * FROM users WHERE username = #{username} AND status = #{status}")
User selectUser(@Param("username") String username, @Param("status") String status);

// 2. 避免 SELECT *，明确指定字段
@Select("SELECT user_id, username, email, role FROM users WHERE user_id = #{userId}")

// 3. 大数据量分页
@Select("SELECT * FROM logs ORDER BY created_at DESC LIMIT #{offset}, #{size}")

// 4. 批量操作使用 BatchExecutor
int batchInsert(@Param("list") List<Task> tasks);

// 5. 事务控制合理使用
@Transactional(rollbackFor = Exception.class)
public void batchProcess(List<String> taskIds) {
    // 批量操作
}
```

---

## 四、测试规范

### 4.1 后端测试规范

#### 4.1.1 单元测试

```java
// TaskServiceTest.java
@SpringBootTest
@AutoConfigureMockMvc
class TaskServiceTest {
    
    @Autowired
    private TaskService taskService;
    
    @Autowired
    private TaskMapper taskMapper;
    
    @Test
    void testCreateTask() {
        Task task = new Task();
        task.setTaskId("TEST001");
        task.setUserId("user123");
        task.setFileKey("test.jpg");
        
        int result = taskService.createTask(task);
        
        assertEquals(1, result);
        
        Task saved = taskMapper.selectTaskByTaskId("TEST001");
        assertNotNull(saved);
        assertEquals("user123", saved.getUserId());
    }
    
    @Test
    void testConfirmTask_WhenStatusIsProcessed() {
        Task task = new Task();
        task.setTaskId("TEST002");
        task.setStatus("processed");
        taskMapper.insertTask(task);
        
        List<Record> records = new ArrayList<>();
        records.add(new Record("1", "张三", "2024-01-01", "08:00", "18:00"));
        
        taskService.confirmTask("TEST002", records);
        
        Task updated = taskMapper.selectTaskByTaskId("TEST002");
        assertEquals("confirmed", updated.getStatus());
        assertNotNull(updated.getConfirmedData());
    }
    
    @Test
    void testConfirmTask_WhenStatusIsNotProcessed() {
        Task task = new Task();
        task.setTaskId("TEST003");
        task.setStatus("processing");
        taskMapper.insertTask(task);
        
        assertThrows(BusinessException.class, () -> {
            taskService.confirmTask("TEST003", new ArrayList<>());
        });
    }
}
```

### 4.2 前端测试规范

#### 4.2.1 组件测试

```javascript
// TaskTable.spec.js
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TaskTable from '@/components/business/TaskTable.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'

describe('TaskTable', () => {
  it('renders task list correctly', () => {
    const tasks = [
      { taskId: 'T001', status: 'processing', createdAt: '2024-01-01' },
      { taskId: 'T002', status: 'confirmed', createdAt: '2024-01-02' },
    ]
    
    const wrapper = mount(TaskTable, {
      props: { data: tasks },
    })
    
    expect(wrapper.findAll('tr').length).toBe(tasks.length + 1) // +1 for header
  })
  
  it('emits view event when view button clicked', async () => {
    const task = { taskId: 'T001', status: 'processing' }
    const wrapper = mount(TaskTable, {
      props: { data: [task] },
    })
    
    await wrapper.find('.el-button--primary').trigger('click')
    
    expect(wrapper.emitted('view')).toBeTruthy()
    expect(wrapper.emitted('view')[0][0]).toEqual(task)
  })
})
```

---

## 五、Git协作规范

### 5.1 分支命名规范

```
main:              # 主分支，保持稳定
develop:           # 开发分支
feature/T-xxx:     # 功能分支 (T-xxx为任务编号)
fix/T-xxx:         # 修复分支
release/v1.0.0:    # 发布分支
hotfix/xxx:        # 热修复分支
```

### 5.2 Commit规范

```
格式: <type>(<scope>): <subject>

# type类型
feat:     新功能
fix:      修复bug
docs:     文档修改
style:    代码格式（不影响代码运行的变动）
refactor: 重构
perf:     性能优化
test:     测试相关
chore:    构建过程或辅助工具的变动

# 示例
feat(task): 添加任务列表查询功能
fix(auth): 修复Token过期后无法自动刷新问题
docs(readme): 更新README文档
refactor(task): 重构任务服务类
```

### 5.3 Pull Request规范

```
标题: [T-xxx] 功能描述

内容:
## 任务链接
https://...

## 变更内容
- 描述1
- 描述2

## 测试情况
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 手动测试通过

## 影响范围
描述本次变更影响的功能模块

## 其他说明
其他需要reviewer注意的事项
```

---

## 六、部署规范

### 6.1 后端部署规范

#### 6.1.1 打包配置

```xml
<!-- pom.xml -->
<build>
    <finalName>${project.artifactId}</finalName>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <excludes>
                    <exclude>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                    </exclude>
                </excludes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### 6.1.2 启动脚本

```bash
#!/bin/bash
# start.sh

APP_NAME=attendance-assistant
APP_JAR=target/attendance-assistant.jar
LOG_FILE=logs/app.log
PID_FILE=app.pid

# 创建日志目录
mkdir -p logs

# 启动应用
nohup java -jar $APP_JAR \
  --spring.profiles.active=prod \
  -Xms512m -Xmx1024m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Djava.security.egd=file:/dev/./urandom \
  > $LOG_FILE 2>&1 &

echo $! > $PID_FILE
echo "Application started, PID: $(cat $PID_FILE)"
```

### 6.2 前端部署规范

#### 6.2.1 构建配置

```bash
# .env.production
VITE_API_BASE_URL=https://api.example.com
VITE_APP_ENV=production

# 构建命令
npm run build

# 产物目录
# dist/
#   ├── index.html
#   ├── assets/
#   └── favicon.ico
```

#### 6.2.2 Nginx配置

```nginx
server {
    listen 80;
    server_name attendance.example.com;
    
    root /var/www/attendance/dist;
    index index.html;
    
    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # 静态资源缓存
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    
    # API代理
    location /api/ {
        proxy_pass http://localhost:3000/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

---

## 七、运维监控规范

### 7.1 日志监控

```properties
# application-prod.yml
spring:
  audit:
    application-name: attendance-assistant
  boot:
    admin:
      context-path: /admin
      client:
        url: http://localhost:8080
        instance:
          prefer-ip: true
```

### 7.2 健康检查

```java
// 自定义健康检查
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            return Health.up()
                    .withDetail("database", "MySQL")
                    .withDetail("status", "Connected")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
```

---

**文档维护记录**

| 版本 | 日期 | 修改人 | 修改内容 |
|-----|------|-------|---------|
| v1.0 | 2026-05-20 | - | 初始版本 |

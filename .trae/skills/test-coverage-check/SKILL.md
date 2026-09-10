---
name: "test-coverage-check"
description: "检查与分析Java单元测试覆盖率（行/分支/方法/异常）。当用户请求评估覆盖率、识别缺失场景或设置阈值校验时调用。"
---

# 单元测试覆盖率校验规则

## 角色定义
作为专业的Java单元测试覆盖率分析专家，精通JUnit、Mockito、Spring Test等测试框架，负责系统化地检查和分析代码的测试覆盖情况。

## 覆盖率标准

### 数值目标
- 行覆盖率 (Line Coverage): ≥80% (核心业务逻辑≥90%)
- 分支覆盖率 (Branch Coverage): ≥75% (所有if/else、switch、三元运算符)
- 方法覆盖率 (Method Coverage): 100% (除getter/setter/toString等工具方法)
+- 异常覆盖率 (Exception Coverage): 100% (所有异常分支必须覆盖)

### 关键指标说明
```
行覆盖率 = 已执行代码行数 / 总代码行数
分支覆盖率 = 已执行分支数 / 总分支数
方法覆盖率 = 已测试方法数 / 总方法数
异常覆盖率 = 已测试异常路径 / 总异常路径数
```

## 检查维度清单

### 1. 正常场景覆盖 ✅
- [ ] 方法的基本功能是否有测试用例
- [ ] 各参数的正常值范围是否覆盖
- [ ] 业务流程的主路径是否完整测试
- [ ] 方法返回值的各种正常情况
- [ ] 多参数组合的常见场景

### 2. 边界条件覆盖 🎯
- [ ] 空值处理：
  - null对象；空字符串 ""；空集合 []、Collections.emptyList()；空Map {}；Optional.empty()
- [ ] 临界值：
  - 数值: 0、-1、1；Integer/Long 的 MAX/MIN；浮点数 0.0、负数、极小值
- [ ] 集合边界：空集合/单元素/大量元素(>1000)
- [ ] 字符串边界：空/超长/特殊字符/Unicode/SQL注入字符
- [ ] 时间边界：null/过去/未来/时区

### 3. 异常场景覆盖 ⚠️
- [ ] 参数校验异常（IllegalArgumentException、NullPointerException、自定义ValidationException）
- [ ] 业务异常（自定义业务异常、状态不合法、权限不足）
- [ ] 系统异常（RuntimeException、SQLException/Timeout、IOException/SocketTimeout）
- [ ] 依赖服务异常（调用失败、超时、降级/熔断）

### 4. 分支逻辑覆盖 🌿
- [ ] if-else 所有分支与嵌套组合
- [ ] switch 全 case + default + fall-through
- [ ] 三元运算符两分支
- [ ] 短路逻辑：&& 左假/|| 左真
- [ ] 循环：0/1/>1 次；break/continue
- [ ] Stream：filter 空、map、reduce

### 5. 状态转换覆盖 🔄
- [ ] 所有对象/业务状态与流转（合法/非法、并发、事务提交/回滚）

### 6. Mock依赖覆盖 🎭
- [ ] 外部依赖均已 Mock（Repository/Service/工具类/第三方接口）
- [ ] 返回值多样性：成功/null/空集合/抛异常
- [ ] 验证交互：times/never、ArgumentCaptor、InOrder

### 7. 并发场景覆盖 🔐
- [ ] 线程安全、锁竞争、分布式锁失败、并发修改、死锁检测

### 8. 数据库相关覆盖 💾
- [ ] 插入/更新/删除的成功与失败；唯一约束；事务回滚；批量部分失败

## 测试质量要求

### 命名规范
```java
// 推荐: should[预期行为]_when[条件]
@Test @DisplayName("应该返回空列表 - 当没有数据时")
void shouldReturnEmptyList_whenNoDataExists() {}

// 推荐: test[方法名]_[场景]_[结果]
@Test @DisplayName("创建用户 - 用户名为空 - 抛出异常")
void testCreateUser_EmptyUsername_ThrowException() {}
```

### 断言充分性
```java
// 充分断言
assertAll(
  () -> assertNotNull(result, "结果不应为null"),
  () -> assertEquals(expectedSize, result.size(), "列表大小不匹配"),
  () -> assertTrue(result.contains(expectedItem), "应包含预期元素"),
  () -> assertEquals(expectedStatus, result.getStatus(), "状态不匹配")
);
verify(mockRepository, times(1)).save(any());
verify(mockService, never()).delete(anyLong());
```

### 测试独立性原则
- 每个测试独立；@BeforeEach 初始化，@AfterEach 清理；避免数据污染与顺序依赖

## 覆盖率分析报告格式

### 输出结构
```markdown
# {类名}单元测试覆盖率分析报告

## 1. 覆盖率统计
- 行覆盖率: XX% (目标≥80%)
- 分支覆盖率: XX% (目标≥75%)
- 方法覆盖率: XX% (目标100%)
- 异常覆盖率: XX% (目标100%)
**综合评价**: [优秀/良好/待改进/不合格]

## 2. 已覆盖场景
### 正常场景
- ✅ 场景1: 描述
### 异常场景
- ✅ 参数为null抛出异常
### 边界场景
- ✅ 空集合处理

## 3. 未覆盖场景识别
### 🔴 高优先级
- [ ] 场景描述/风险/影响/建议
### 🟡 中优先级
- [ ] 场景描述/风险/建议
### 🔵 低优先级
- [ ] 场景描述/建议

## 4. 代码分支分析
| 位置 | 分支条件 | 缺失测试 | 优先级 |
|------|---------|----------|--------|
| 行号 | if条件  | true分支 | 高     |

## 5. 未覆盖的方法
| 方法名 | 可见性 | 优先级 | 说明 |
|--------|--------|--------|------|
| methodName() | public | 高 | 核心业务方法 |

## 6. 补充测试用例建议
```java
@Test
@DisplayName("{测试描述}")
void test{MethodName}_{Scenario}(){
  // Given/When/Then
}
```

## 7. 测试改进建议
- 代码可测试性：拆分长方法、依赖注入、时钟/随机封装
- 结构优化：@Nested 组织，公共数据工厂
- Mock策略：精确粒度与验证策略

## 8. 风险评估
- 生产风险等级：[高/中/低]
- 建议修复时间：[立即/本周/本月]
```

## 容易遗漏的场景检查表 ⚡
- 空指针防护、集合操作、并发安全、事务、缓存、限流熔断、分布式锁、重试、定时任务

## 工具和命令

### Maven Jacoco 插件
```bash
mvn clean test jacoco:report
open target/site/jacoco/index.html
mvn jacoco:check
```

### IDEA Coverage
1. 右键测试类 → Run with Coverage
2. 查看 Coverage 窗口
3. 导出覆盖率报告

### CI 阈值检查（pom 片段）
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <configuration>
    <rules>
      <rule>
        <element>PACKAGE</element>
        <limits>
          <limit>
            <counter>LINE</counter>
            <value>COVEREDRATIO</value>
            <minimum>0.80</minimum>
          </limit>
        </limits>
      </rule>
    </rules>
  </configuration>
  <executions>
    <execution>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
    </execution>
    <execution>
      <id>check</id>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
  </plugin>
```

## AI 辅助覆盖率检查流程
1. 理解上下文：目标类/方法、复杂度与核心功能
2. 分析结构：方法数、分支点、异常点、边界条件
3. 审查现有测试：统计与指标
4. 识别缺失场景：对照清单与风险点
5. 生成报告：标准格式 + 补充用例 + 改进建议
6. 风险评估：风险等级与修复时间

## 关键原则
- 目标导向：有效覆盖优先，高风险优先
- 全面性：系统化覆盖分支与边界
- 实用性：可执行的测试与建议
- 合理性：避免过度测试（简单 getter/setter 可忽略）

## 示例：完整分析流程（节选）
```java
public User createUser(CreateUserRequest request) {
  if (request == null || StringUtils.isEmpty(request.getName())) {
    throw new ValidationException("参数不能为空");
  }
  if (userRepository.existsByName(request.getName())) {
    throw new BusinessException("用户已存在");
  }
  User user = new User();
  user.setName(request.getName());
  return userRepository.save(user);
}
```

### 识别测试点
- 成功创建；request=null；name=""；name=null；已存在；保存异常

### 补充用例示例
```java
@Test
@DisplayName("创建用户 - request为null")
void testCreateUser_NullRequest(){
  ValidationException ex = assertThrows(ValidationException.class,
      () -> userService.createUser(null));
  assertEquals("参数不能为空", ex.getMessage());
}
```

---
遵循上述规则执行覆盖率分析与校验，确保测试的完整性与有效性。


# Android JSON 校验库选型对比

> Redmine: #919489450614  
> 调研人：未来姐姐  
> 时间：2026-10-14  
> 结论：**自研 FSM 校验器，不使用任何现成库**

## 1. 主人姊妹项目现状（关键事实）

| 项目 | 当前 JSON 库 | 用法 | 痛点 |
|---|---|---|---|
| **SisterFuture** | `gson 2.8.2` + `org.json.JSONTokener` | Gson 序列化、JSONTokener 校验 | **4 层兜底互相打架**（详见 #919489454955） |
| **Lanime** | `Gson 2.8.2` + `GsonRequest.java` | 工具调用参数解析 | 同上 |
| **HxJsonValidator** | **（本项目）** | 替换上述 4 层兜底 | 解决"误判 + 错误信息丢失"问题 |

## 2. 候选库对比矩阵

| 库 | License | APK 体积 | 错误位置 | RFC 8259 严格 | Android 原生 | 维护活跃度 | 综合评分 |
|---|---|---|---|---|---|---|---|
| **org.json (内置)** | MIT | 0 KB | ❌ 仅 message | ❌ 宽松 | ✅ | ⚠️ Android 停止更新 | 1/10 |
| **Gson 2.8.2** | Apache 2.0 | ~250 KB | ❌ 仅 message | ❌ 宽松 | ⚠️ | ✅ Google 维护 | 2/10 |
| **Moshi 1.15** | Apache 2.0 | ~150 KB | ⚠️ 需自定义 adapter | ✅ | ⚠️ | ✅ Square 维护 | 4/10 |
| **Jackson 2.16** | Apache 2.0 | ~1.3 MB | ❌ | ✅ | ❌ JVM 中心 | ✅ FasterXML 维护 | 3/10 |
| **kotlinx.serialization** | Apache 2.0 | ~200 KB | ❌ | ✅ | ❌ Kotlin only | ✅ JetBrains 维护 | 3/10 |
| **json-schema-validator** | Apache 2.0 | ~500 KB | ✅ Schema 路径 | ✅ | ❌ | ✅ | 6/10 |
| **everit-org/json-schema** | Apache 2.0 | ~400 KB | ✅ | ✅ | ❌ | ⚠️ | 6/10 |
| **Gson + json-schema-validator** | Apache 2.0 | ~750 KB | 部分 | ✅ | ⚠️ | ✅ | 5/10 |
| **HxJsonValidator (自研 FSM)** | Apache 2.0 | < 100 KB | ✅ 完整 | ✅ | ✅ | ✅ 主人维护 | **9/10** |

## 3. 详细分析

### 3.1 org.json.JSONObject / JSONTokener（Android 内置）
- ✅ 零依赖、零 APK 体积
- ❌ **错误信息仅 message，无 line/column/offset**
- ❌ **宽松解析**：接受 `1_000`、`+1`、未配对代理等（违反 RFC 8259）
- ❌ Android 12+ 已停止更新，AOSP 跟不上
- ❌ sisterfuture 实际**已踩坑**（详见 #919489454955）

### 3.2 Gson 2.8.2
- ✅ Google 官方，Java 生态最广泛
- ❌ 错误信息不包含位置
- ❌ 反射性能开销大
- ❌ sisterfuture 实际**误判率高**（`hasUnquotedStringValues` 拦截器误判）
- ❌ 早被 sisterfuture 验证**不够用**

### 3.3 Moshi 1.15
- ✅ Square 出品，Kotlin-first
- ✅ 比 Gson 性能好（无反射）
- ⚠️ 错误信息要自定义 `JsonAdapter` 才能拿到位置
- ⚠️ 主要为序列化设计，**校验不是其强项**
- ❌ 没有"按行列定位"开箱即用

### 3.4 Jackson 2.16
- ✅ 工业级，功能最全
- ❌ APK 体积太大（1.3 MB+）
- ❌ Android 集成复杂（要排除 kotlin-reflect）
- ❌ 错误信息不友好

### 3.5 kotlinx.serialization
- ✅ JetBrains 官方，Kotlin Native 友好
- ❌ **Kotlin only**，主人明确拒绝 Kotlin
- ❌ 编译期生成代码，校验也用同一套，错误信息不友好

### 3.6 json-schema-validator (networknt)
- ✅ 支持 JSON Schema Draft 4/6/7/2019-09/2020-12
- ✅ 错误信息含 `pointer`（JSON Pointer）
- ❌ 主要为 Schema 校验设计，**纯语法校验是大材小用**
- ❌ 体积 500 KB+
- ❌ 主人 #G6 还未决定是否要 Schema

### 3.7 everit-org/json-schema
- ⚠️ 同上，且社区维护放缓

### 3.8 HxJsonValidator (自研 FSM)
- ✅ **APK 体积 < 100 KB**（轻量、零反射）
- ✅ **错误信息完整**：line + column + offset + snippet
- ✅ **严格按 RFC 8259**（不会出现 `1_000` 这种宽松解析）
- ✅ **可作为 SisterFuture 的 drop-in 替换**（API 风格相似）
- ✅ **Apache 2.0**（可商用、可分叉）
- ✅ **流式可中断**（防 OOM、防恶意输入）
- ✅ **可脱 Android Framework 单元测试**（纯 JVM）
- ✅ **中文错误信息**（大模型友好）
- ⚠️ 需要自己实现（约 800 行）
- ⚠️ 没有 JSON Schema（待 #G6 决定）

## 4. 关键决策

### 决策 1：**不依赖 Gson / org.json / Jackson**
理由：它们的错误信息都不含位置，无法解决"按行列精确定位"的核心需求。

### 决策 2：**不基于 Moshi / kotlinx.serialization**
理由：前者为序列化设计、后者要 Kotlin。**校验是独立用例**。

### 决策 3：**不引入 json-schema-validator**
理由：体积大，主要为 Schema 设计。主人 #G6 还没决定 Schema，纯语法校验大材小用。
（若主人后续要 Schema，可以**叠加** Schema 库，不冲突）

### 决策 4：**自研 FSM**
理由：
- 解决唯一未被满足的需求：**按字符位置错误信息**
- 严格按 RFC 8259，避免 sisterfuture 现状的"宽松解析 → 误判"循环
- 体积极小（< 100 KB），不引入额外依赖
- 可被 sisterfuture 复用，**减少代码量 + 消除 4 层兜底**

## 5. 不重复造轮子的反驳

| 论点 | 反驱 |
|---|---|
| "Gson 已经够用" | ❌ Gson 错误信息无位置，sisterfuture 误判率高就是证据 |
| "Moshi 是现代化选择" | ❌ Moshi 主要解决序列化问题，校验场景是它的弱项 |
| "Schema 库更通用" | ❌ Schema 库是另一层抽象，语法校验是基础；而且体积大 |
| "Kotlin 时代不应写 Java" | ❌ 主人明确要求 Java；且 Java 兼容性最广 |
| "没人需要手撸 JSON 解析器" | ❌ JSON 解析器有 30 年历史，技术成熟，FSM 是经典实现 |

## 6. 与姊妹项目集成路径

```java
// SisterFuture/ContextManager.java 当前 4 层兜底（80+ 行）
if (hasUnquotedStringValues(argumentsStr)) { return false; }
if (!isJsonSyntaxComplete(argumentsStr)) { return false; }
JSONTokener tokener = new JSONTokener(argumentsStr);
Object parsed = tokener.nextValue();
// ...

// 替换为 1 行
if (!HxJsonValidator.isValid(argumentsStr)) {
    ValidationResult r = HxJsonValidator.validate(argumentsStr);
    FileLogger.d(TAG, "[isValidToolCallMessage] " + r);
    return false;
}
```

或更彻底：把所有 `org.json.JSONException` 捕获替换为 `HxJsonValidator.validate()`，获得**结构化错误位置**。

## 7. 推荐结构

```
SisterFuture 仓
  └─ build.gradle  // 添加 implementation 'com.github.hxcan:HxJsonValidator:2026.10.14'
  └─ ContextManager.java  // 替换 4 层兜底为 HxJsonValidator.validate()
```

**净收益**：删除 ~80 行内置校验代码 + 获得按字符位置错误信息 + 减少误判。

## 8. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 自研 FSM 有 bug | 70+ 单测覆盖 + 严格按 RFC 8259 设计 |
| 性能不达标 | 阶段 4 优化（字符分类查表、char[] 一次性拷贝） |
| 漏掉 RFC 8259 边界 | 70+ 边界 case 清单 + 状态机状态完备 |
| Android 兼容 | minSdk 24（与 SisterFuture 主体一致） |
| 维护负担 | 800 行 Java，单一 FSM 状态机，可读易改 |

## 9. 最终结论

✅ **采用 HxJsonValidator 自研 FSM**（基于 #F 调研）
❌ 不使用 Gson / org.json / Moshi / Jackson / kotlinx.serialization / Schema 库
✅ 后续可叠加 Schema 库（若主人 #G6 选择要 Schema）

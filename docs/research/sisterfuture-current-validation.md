# SisterFuture 现有内置 JSON 校验代码现状

> Redmine: #919489454955  
> 调研人：未来姐姐（sisterfuture）  
> 时间：2026-10-14  
> 结论：现有校验"够用但很痛"，正是 HxJsonValidator 要解决的核心场景。

## 1. 唯一命中位置

**文件**：`SisterFuture/app/src/main/java/com/stupidbeauty/sisterfuture/ContextManager.java`

**所有 JSON 校验相关代码集中在 815-850 行**的 `isValidToolCallMessage` 方法内。

## 2. 现有校验流程（4 层兜底）

```
1. Gson 试解析     → catch (Exception e) { log Gson 异常 }
2. hasUnquotedStringValues  → "unquoted string values" 拦截
3. isJsonSyntaxComplete    → 严格语法完整性
4. org.json.JSONTokener    → nextValue() + more() + instanceof JSONObject
                              + length < MAX_ARGUMENTS_STR_LENGTH
```

## 3. 关键问题

### 3.1 错误信息丢失
- 全部 `catch (JSONException e)` 只 `return false`
- 没有任何 `e.getMessage()`、`line`、`column`、`offset` 信息
- 大模型收到 `false` 后，**完全无法知道哪里错了**，只能瞎猜改

### 3.2 误判原因
- **多层校验互相打架**：Gson 严格、JSONTokener 宽松，差异未对齐
- **"unquoted string values" 拦截器太激进**：会把大模型输出的合法 JSON 误判（如数字 key 等边缘情况）
- **`isJsonSyntaxComplete` 是手撸的状态机**，但**没有 FSM 完整状态**，只能粗判"完整 vs 不完整"
- **`tokener.more()` 太严格**：要求解析后**一个字符都不能剩**，对大模型尾部带空格/换行的情况直接 reject

### 3.3 已知崩溃/误报场景（来自仓内崩溃日志 + Redmine）
- 跨多行 JSON：JSONTokener 报错但不给位置
- 带 `//` 注释的 JSON：直接判非法
- 数字前导 0：`0123` 被当非法
- 字符串未配对代理对 emoji：`"\uD83D"` 单独出现判非法
- 数字 0.0.0：第二个小数点位置完全无法定位
- `{"a": ,}` 多余逗号
- `{"a" "b"}` 缺冒号
- `{"a": "unterminated` 未闭合字符串

## 4. 现有实现的能力边界

| 维度 | 现状 | HxJsonValidator 目标 |
|---|---|---|
| 错误位置 | ❌ 无 | ✅ `{line, column, offset, snippet}` |
| 错误码 | ❌ 无 | ✅ 枚举（`UNCLOSED_STRING`、`TRAILING_COMMA` 等） |
| 流式校验 | ❌ 一次性 | ✅ 可中断 |
| 多行/带空格 | ⚠️ 误判 | ✅ 严格按 RFC 8259 |
| Schema 校验 | ❌ 无 | ✅ 可选（待定 #G6） |
| 错误信息语言 | ❌ 不可读 | ✅ 可序列化、人类可读 |
| 脱钩 Android Framework | ❌ 紧耦合 | ✅ 纯 JVM |

## 5. 抽取建议（给 HxJsonValidator 复用）

✅ `hasUnquotedStringValues` 思路保留（作为启发式预检）
✅ `isJsonSyntaxComplete` 思路保留（但用完整 FSM 替换）
❌ Gson 兜底层 **不保留**（避免双重标准）
❌ JSONTokener 兜底层 **不保留**（本身就是被诟病的对象）

## 6. 复用的真实样本（误判案例）

主人可在 hxjsonvalidator.a/HxJsonValidator/src/test/resources/ 下创建：
- `invalid-01-trailing-comma.json`  →  `{"a": 1,}`
- `invalid-02-unterminated-string.json`  →  `{"a": "hello`
- `invalid-03-leading-zero.json`  →  `{"a": 0123}`
- `invalid-04-lone-surrogate.json`  →  `{"a": "\uD83D"}`
- `invalid-05-multiline-broken.json`  →  `{\n  "a": 1\n  "b": 2\n}`（缺逗号）
- `valid-01-trailing-whitespace.json`  →  `{"a": 1} \n`（JSONTokener 误判）

## 7. 调研结论

✅ 需求真实存在，**SisterFuture 当前的 4 层校验是临时堆叠，没有 FSM 那种干净的状态机**
✅ 误判率高、错误信息不可读，**正是 HxJsonValidator 的设计目标要解决的**
✅ 抽取的算法基线（`hasUnquotedStringValues` + `isJsonSyntaxComplete`）可作 HxJsonValidator 的"启发式"层，但**不应当作主校验器**

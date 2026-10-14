# L JSON Output Format（for LLM Tool Calling）

> HxJsonValidator 配套文档，供大模型参考  
> 任务：#919489450991 G7 = 主人答"是"

## 概述

当 SisterFuture 要求你以工具调用（tool call）形式输出参数时，参数必须是**合法 JSON**。

本规范基于 RFC 8259 严格标准，配套 HxJsonValidator FSM 校验器。

## 硬性规则

1. **顶层必须是一个 JSON 对象**（以 `{` 开头，`}` 结尾），不是数组、不是裸值
2. **所有 key 必须用双引号**（`"name"`，不是 `name`）
3. **所有 string value 必须用双引号**（`"alice"`，不是 `'alice'`）
4. **数字不能有前导 0**（`42` ✅，`0123` ❌）
5. **不要有尾随逗号**（`{"a": 1,}` ❌）
6. **不要注释**（JSON 不支持 `//` 或 `/* */`）
7. **不要单引号**（JSON 只认双引号）
8. **字符串必须正确转义**：换行 `\n`、制表符 `\t`、引号 `\"`、反斜杠 `\\`

## 错误重试

当你收到 `HxJsonValidator` 错误时，**重试前**请根据错误信息修正：

```
错误码: ERR_OBJECT_AFTER_VALUE  // 多余逗号
位置: line 1, col 18
片段: "a": 1,}
       ^
说明: 对象 value 后不应有逗号（除非后面还有 key-value 对）

→ 修正: {"a": 1}
```

### 常见错误码速查

| 错误码 | 含义 | 常见原因 |
|---|---|---|
| `ERR_UNTERMINATED_STRING` | 字符串未关闭 | 缺右双引号 |
| `ERR_STRING_ESCAPE` | 转义字符非法 | 用了 `\'` `\.` 等 JS 风格转义 |
| `ERR_NUMBER` | 数字语法错 | 前导 0 / 多余小数点 / 指数后无数 |
| `ERR_OBJECT_AFTER_KEY` | 缺冒号 | `"a" 1` 而不是 `"a": 1` |
| `ERR_OBJECT_AFTER_VALUE` | 多余逗号 | `{"a": 1,}` |
| `ERR_TRAILING_DATA` | 顶层 value 后还有内容 | 多个对象连写 `{} {}` |

## 示例

### ✅ 正确
```json
{"name": "alice", "age": 30, "tags": ["a", "b"]}
```

### ❌ 错误示例
```json
{name: "alice", "age": 30}              // key 必须用双引号
{"name": 'alice', "age": 30}            // string 必须用双引号
{"name": "alice", "age": 030}           // 数字不能前导 0
{"name": "alice", "age": 30,,}          // 多余逗号
{"name": "alice" "age": 30}            // 缺逗号
{"name": "alice",}                      // 尾随逗号
```

## 工具调用格式

SisterFuture 期望的参数格式：

```json
{
  "tool_name": "具体工具名",
  "arguments": {
    // 工具特定参数
  }
}
```

例如 web_request 工具：

```json
{
  "tool_name": "web_request",
  "arguments": {
    "url": "https://example.com",
    "method": "GET"
  }
}
```

## 如果你出错

1. 收到错误后**重写整个 JSON**（不要增量修改）
2. 仔细读 `line` 和 `col`，定位到字符级
3. 修正后**重新发**，不要在原回复里改

## 例子：完整错误重试流程

**主人提问**："调用 web_request 抓取 example.com"

**你的第一轮回复**：
```json
{
  "tool_name": "web_request",
  "arguments": {
    "url": "https://example.com,
    "method: "GET"
  }
}
```

**SisterFuture 报错**（HxJsonValidator 检测）：
```
[ERROR] HxJsonValidator 校验失败
code: ERR_STRING_CONTROL
line: 3, col: 33
snippet:     "url": "https://example.com,
                                          ^
message: 字符串中包含未转义的换行或控制字符
```

**你的第二轮回复**（修正后）：
```json
{
  "tool_name": "web_request",
  "arguments": {
    "url": "https://example.com",
    "method": "GET"
  }
}
```

**SisterFuture 接收并执行**。

## 参考

- RFC 8259：https://datatracker.ietf.org/doc/html/rfc8259
- HxJsonValidator 校验器：`https://github.com/hxcan/HxJsonValidator`
- 边界 case 清单：项目内 `docs/research/fsm-边界case.md`

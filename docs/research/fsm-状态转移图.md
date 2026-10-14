# JSON 校验 FSM 状态转移图

> Redmine: #919489457328  
> 调研人：未来姐姐  
> 目的：把"按字符流解析 JSON"这件事用一个干净的状态机表达

## 状态清单

```
S0  START              解析前
S1  EXPECT_VALUE       期待一个 value（object/array/string/number/true/false/null）
S2  IN_OBJECT          在 object 内部（刚进入 {）
S3  EXPECT_KEY         期待 string key（object 内，{ 之后或 , 之后）
S4  AFTER_KEY          刚读完 key，见过 :，期待 value
S5  AFTER_VALUE_O      object 内的 value 结束，见过 } 或 , 之一
S6  IN_ARRAY           在 array 内部（刚进入 [）
S7  AFTER_VALUE_A      array 内的 value 结束，见过 ] 或 , 之一
S8  IN_STRING          在 string 内部（刚见过 "）
S9  IN_STRING_ESC      在 string 内部，见过 \
S10 IN_STRING_U        在 string 内部，见过 \u 之后
S11 IN_STRING_U1       在 string 内部，\u 之后已读 1 位 hex
S12 IN_STRING_U2       在 string 内部，\u 之后已读 2 位 hex
S13 IN_STRING_U3       在 string 内部，\u 之后已读 3 位 hex
S20 IN_NUMBER          在 number 内部
S21 IN_NUMBER_FRAC     number 见到 . 进入小数部分
S22 IN_NUMBER_EXP      number 见到 e/E 进入指数部分
S23 IN_NUMBER_EXP_SIG  number 指数部分见到 +/- 
S30 IN_TRUE            literal "true" 已读 t
S31 IN_FALS            literal "false" 已读 f / fa / fal
S32 IN_FALS_E          literal "false" 已读 fals
S33 IN_NULL            literal "null" 已读 n / nu / nul
S40 DONE               顶层 value 解析完成
S99 ERROR              错误状态（终态）
```

## 核心转移表（简化版）

```
S0  --[ws]-->           S0
S0  --[{]-->             S2
S0  --[[]-->             S6
S0  --["]-->             S8
S0  --[0-9-]-->          S20
S0  --[t]-->             S30
S0  --[f]-->             S31
S0  --[n]-->             S33
S0  --[其它]-->          S99 ERR_EXPECT_VALUE

S2  --[ws]-->            S2
S2  --["]-->             S3   (read key)
S2  --[}]-->             S5   (empty object, S5 knows)
S2  --[其它]-->          S99 ERR_OBJECT_KEY_OR_CLOSE

S3  --["]-->             S4   (read key, saw :)
S3  --[其它]-->          S99 ERR_OBJECT_KEY

S4  --[ws]-->            S4
S4  --[:,]-->            S1   (expect value of this key)
S4  --[其它]-->          S99 ERR_OBJECT_AFTER_KEY

S5  --[,]-->             S3   (next key)
S5  --[}]-->             S40  (close object)
S5  --[EOF]-->           S40  (stream end after object)
S5  --[其它]-->          S99 ERR_OBJECT_AFTER_VALUE

S6  --[ws]-->            S6
S6  --[]]-->             S7   (empty array)
S6  --[value start]-->   S1   (recurse, then S7)
S6  --[其它]-->          S99 ERR_ARRAY_VALUE_OR_CLOSE

S7  --[,]-->             S1   (next value)
S7  --[]]-->             S40  (close array)
S7  --[EOF]-->           S40
S7  --[其它]-->          S99 ERR_ARRAY_AFTER_VALUE

S8  --["]-->             S1   (close string, go back to where)
S8  --[\\]-->            S9   (escape)
S8  --[\u]-->            S10  (unicode escape)
S8  --[control char]-->  S99 ERR_STRING_CONTROL
S8  --[EOF]-->           S99 ERR_UNTERMINATED_STRING
S8  --[其它]-->          S8   (any other char is part of string)

S9  --["\\/bfnrt]-->     S8
S9  --[u]-->             S10
S9  --[其它]-->          S99 ERR_STRING_ESCAPE

S10 --[0-9a-fA-F]-->     S11
S10 --[其它]-->          S99 ERR_STRING_UNICODE_ESCAPE

S11 --[0-9a-fA-F]-->     S12
S11 --[其它]-->          S99 ERR_STRING_UNICODE_ESCAPE
S12 --[0-9a-fA-F]-->     S13
S12 --[其它]-->          S99 ERR_STRING_UNICODE_ESCAPE
S13 --[0-9a-fA-F]-->     S8   (one \u done, back to string)
S13 --[其它]-->          S99 ERR_STRING_UNICODE_ESCAPE

S20 --[0-9]-->           S20
S20 --[.]-->             S21
S20 --[eE]-->            S22
S20 --[ws,]}],EOF]-->    pop to container
S20 --[其它]-->          S99 ERR_NUMBER

S21 --[0-9]-->           S21
S21 --[eE]-->            S22
S21 --[ws,]}],EOF]-->    pop
S21 --[.]-->             S99 ERR_NUMBER_DOUBLE_DOT
S21 --[其它]-->          S99 ERR_NUMBER

S22 --[0-9]-->           S22
S22 --[+-]-->            S23
S22 --[ws,]}],EOF]-->    pop
S22 --[其它]-->          S99 ERR_NUMBER_EXP_DIGIT

S23 --[0-9]-->           S22
S23 --[其它]-->          S99 ERR_NUMBER_EXP_DIGIT

S30 --[r]-->             S30
S30 --[u]-->             S30
S30 --[e]-->             S40  (true done)
S30 --[其它]-->          S99 ERR_TRUE_LITERAL

S31 --[a]-->             S31
S31 --[l]-->             S32
S31 --[s]-->             S31  (false starts with 'f', 'a' after f → fal**, stay)
S31 --[e]-->             S40  (false done)
S31 --[其它]-->          S99 ERR_FALSE_LITERAL

S32 --[其它]-->          S99 ERR_FALSE_LITERAL  (must be 'false' not 'falsx')

S33 --[u]-->             S33
S33 --[l]-->             S33
S33 --[其它]-->          S99 ERR_NULL_LITERAL

S40 --[EOF]-->           S40  (DONE)
S40 --[其它]-->          S99 ERR_TRAILING_DATA
```

## 关键设计取舍

| 决策 | 选 | 不选 | 理由 |
|---|---|---|---|
| 入口状态 | S1 EXPECT_VALUE | 复合状态 | 简单、递归调用复用 |
| 对象 key 状态 | S3 EXPECT_KEY | 与 value 共享 S1 | key 必为 string，单独更清晰 |
| 转义字符子状态 | S9/S10-S13 | 单一状态 | 控制流清晰，错误码精确 |
| 数字子状态 | S20/S21/S22/S23 | 单一正则 | 支持 RFC 8259 所有合法数字 |
| 栈管理 | 显式 push/pop | 递归函数 | 错误信息位置准确（不丢失上下文） |
| 错误 | S99 ERROR + 错误码 | 抛异常 | 大模型可读、可结构化、可本地化 |

## 错误码（枚举）

```java
public enum JsonErrorCode {
    ERR_EXPECT_VALUE,             // S0/S1
    ERR_OBJECT_KEY_OR_CLOSE,      // S2
    ERR_OBJECT_KEY,               // S3
    ERR_OBJECT_AFTER_KEY,         // S4
    ERR_OBJECT_AFTER_VALUE,       // S5
    ERR_ARRAY_VALUE_OR_CLOSE,     // S6
    ERR_ARRAY_AFTER_VALUE,        // S7
    ERR_STRING_CONTROL,           // S8
    ERR_UNTERMINATED_STRING,      // S8 EOF
    ERR_STRING_ESCAPE,            // S9
    ERR_STRING_UNICODE_ESCAPE,    // S10-S13
    ERR_NUMBER,                   // S20/S21
    ERR_NUMBER_DOUBLE_DOT,        // S21
    ERR_NUMBER_EXP_DIGIT,         // S22/S23
    ERR_TRUE_LITERAL,             // S30
    ERR_FALSE_LITERAL,            // S31/S32
    ERR_NULL_LITERAL,             // S33
    ERR_TRAILING_DATA,            // S40
    ERR_NESTED_TOO_DEEP,          // stack overflow guard
    ERR_INPUT_TOO_LARGE,          // size limit
}
```

## 性能与边界

- **栈深度上限**：1000（防恶意 `[[[[...`）
- **输入大小上限**：10 MB（防 OOM）
- **流式接口**：`FeedBuffer.feed(char[])` / `endOfInput()` —— 调用方可以分块
- **错误信息位置**：`line: 1-indexed`、`column: 1-indexed`、`offset: 0-indexed`、`snippet: 当前行 ± 1 行`

# JSON 校验边界 case 清单

> Redmine: #919489457328  
> 调研人：未来姐姐  
> 目的：列出 30+ 边界 case，作为 HxJsonValidator 单测的种子

每条 case 包含：输入片段、期望错误码、备注。

## 数字边界 (S20-S23)

| # | 输入 | 期望 | 备注 |
|---|---|---|---|
| 1 | `0` | ✅ 合法 | 单个 0 |
| 2 | `-0` | ✅ 合法 | 负零 |
| 3 | `0123` | ❌ ERR_NUMBER | 前导 0（RFC 8259 严格） |
| 4 | `0.0` | ✅ 合法 | |
| 5 | `0.` | ❌ ERR_NUMBER | 小数点后必须有数 |
| 6 | `.5` | ❌ ERR_NUMBER | 整数部分不能省 |
| 7 | `0e1` | ✅ 合法 | |
| 8 | `0E+1` | ✅ 合法 | |
| 9 | `0e-1` | ✅ 合法 | |
| 10 | `0e` | ❌ ERR_NUMBER_EXP_DIGIT | 指数后必须有数 |
| 11 | `0e+` | ❌ ERR_NUMBER_EXP_DIGIT | |
| 12 | `1.` | ❌ ERR_NUMBER | |
| 13 | `1.0.` | ❌ ERR_NUMBER_DOUBLE_DOT | 第二个小数点 |
| 14 | `+1` | ❌ ERR_EXPECT_VALUE | RFC 8259 不允许 `+` 正号 |
| 15 | `1_000` | ❌ ERR_NUMBER | 不允许下划线分隔 |
| 16 | `0x1` | ❌ ERR_NUMBER | 不允许 hex |
| 17 | `1e9999` | ✅ 合法 | 不检查范围（语法层） |
| 18 | `NaN` | ❌ ERR_NUMBER | |
| 19 | `Infinity` | ❌ ERR_NUMBER | |
| 20 | `-1.5e+10` | ✅ 合法 | |

## 字符串边界 (S8-S13)

| # | 输入 | 期望 | 备注 |
|---|---|---|---|
| 21 | `""` | ✅ 合法 | 空串 |
| 22 | `"abc"` | ✅ 合法 | |
| 23 | `"` | ❌ ERR_UNTERMINATED_STRING | 字符串未关闭 |
| 24 | `"\n"` | ❌ ERR_STRING_CONTROL | 裸换行符 |
| 25 | `"\u0000"` | ❌ ERR_STRING_CONTROL | U+0000 不能直接出现（可 `\"\u0000\"`） |
| 26 | `"\/"` | ✅ 合法 | `/` 转义合法 |
| 27 | `"\x"` | ❌ ERR_STRING_ESCAPE | |
| 28 | `"\u"` | ❌ ERR_STRING_UNICODE_ESCAPE | |
| 29 | `"\uD83D"` | ❌ ERR_STRING_UNICODE_ESCAPE | 单独高代理 |
| 30 | `"\uD83D\uDE00"` | ✅ 合法 | 高低代理对（emoji 😀） |
| 31 | `"\uDC00\uD83D"` | ❌ ERR_STRING_UNICODE_ESCAPE | 低代理必须后跟高代理 |
| 32 | `"\"\\"` | ✅ 合法 | 反斜杠转义 |
| 33 | `"\b\f\n\r\t"` | ✅ 合法 | 5 个标准转义 |
| 34 | `"中文"` | ✅ 合法 | UTF-8 直接字符 |

## 对象边界 (S2-S5)

| # | 输入 | 期望 | 备注 |
|---|---|---|---|
| 35 | `{}` | ✅ 合法 | 空对象 |
| 36 | `{"a":1}` | ✅ 合法 | |
| 37 | `{"a":1,}` | ❌ ERR_OBJECT_AFTER_VALUE | 多余逗号 |
| 38 | `{"a":1,"a":2}` | ✅ 合法（JSON 允许重复 key） | 语义上由消费方处理 |
| 39 | `{"a":1,}` | ❌ ERR_OBJECT_AFTER_VALUE | |
| 40 | `{"a":}` | ❌ ERR_OBJECT_AFTER_KEY | value 缺失 |
| 41 | `{"a" 1}` | ❌ ERR_OBJECT_AFTER_KEY | 缺冒号 |
| 42 | `{"a":1 "b":2}` | ❌ ERR_OBJECT_AFTER_VALUE | 缺逗号 |
| 43 | `{` | ❌ ERR_OBJECT_KEY_OR_CLOSE | 未关闭 |
| 44 | `{,}` | ❌ ERR_OBJECT_KEY_OR_CLOSE |  |
| 45 | `{1:1}` | ❌ ERR_OBJECT_KEY_OR_CLOSE | key 必为 string |
| 46 | `{"a":1,"a":}` | ❌ ERR_OBJECT_AFTER_KEY |  |

## 数组边界 (S6-S7)

| # | 输入 | 期望 | 备注 |
|---|---|---|---|
| 47 | `[]` | ✅ 合法 | 空数组 |
| 48 | `[1,2,3]` | ✅ 合法 | |
| 49 | `[1,2,]` | ❌ ERR_ARRAY_AFTER_VALUE | 多余逗号 |
| 50 | `[1,]` | ❌ ERR_ARRAY_AFTER_VALUE | |
| 51 | `[1,,2]` | ❌ ERR_ARRAY_VALUE_OR_CLOSE | 连续逗号 |
| 52 | `[` | ❌ ERR_ARRAY_VALUE_OR_CLOSE | 未关闭 |
| 53 | `[[[[]]]]` | ✅ 合法（深度 4） |  |
| 54 | 深度 1001 | ❌ ERR_NESTED_TOO_DEEP | 防恶意 |

## 顶层 value 边界

| # | 输入 | 期望 | 备注 |
|---|---|---|---|
| 55 | `` (空) | ❌ ERR_EXPECT_VALUE | |
| 56 | ` ` | ❌ ERR_EXPECT_VALUE | 只有空白 |
| 57 | `null` | ✅ 合法 | |
| 58 | `true` | ✅ 合法 | |
| 59 | `True` | ❌ ERR_EXPECT_VALUE | 大小写敏感 |
| 60 | `tru` | ❌ ERR_TRUE_LITERAL | 字面值不完整 |
| 61 | `1 2` | ❌ ERR_TRAILING_DATA | 多余内容 |
| 62 | `{"a":1}{"b":2}` | ❌ ERR_TRAILING_DATA | 顶层多对象 |
| 63 | `01` | ❌ ERR_NUMBER | |

## UTF-8 / BOM 边界

| # | 输入 | 期望 | 备注 |
|---|---|---|---|
| 64 | `\uFEFF{}` | ❌ ERR_EXPECT_VALUE | BOM 视为非空白 |
| 65 | 嵌入 `\u0000` 字符 | 报错 | 0x00 不是合法 UTF-8 起始 |

## 注释（RFC 8259 严格不允许）

| # | 输入 | 期望 | 备注 |
|---|---|---|---|
| 66 | `{"a":1 /* comment */}` | ❌ ERR_EXPECT_VALUE | JSON 不支持注释 |
| 67 | `{"a":1 // line\n}` | ❌ ERR_OBJECT_AFTER_VALUE | |

## 多行

| # | 输入 | 期望 | 备注 |
|---|---|---|---|
| 68 | `{\n  "a": 1,\n  "b": 2\n}` | ✅ 合法 | 标准 pretty-print |
| 69 | `{\r\n  "a": 1\r\n}` | ✅ 合法 | CRLF |
| 70 | `{\n"a":1\n}` | ✅ 合法 | 各种换行 |

## 总结

- **70+ case 覆盖** RFC 8259 + 实际误判场景
- 测试时按 case 编号命名：`src/test/resources/case-01-zero.json`、`case-03-leading-zero.json`...
- 每个 case 配 1 个 expected.json：`{"valid": true, "code": null}` 或 `{"valid": false, "code": "ERR_NUMBER", "line": 1, "col": 3}`

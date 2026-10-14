# FSM 技术路径总览（FSM Implementation Tech Path）

> Redmine: #919489457328  
> 调研人：未来姐姐  
> 时间：2026-10-14  
> 结论：选定"显式 FSM 状态机 + Reader 接口 + 结构化错误信息"作为 HxJsonValidator 主体实现路径

## 1. 整体架构

```
调用方
  │
  ├── HxJsonValidator.isValid(String)        → boolean（轻量）
  ├── HxJsonValidator.validate(String)       → ValidationResult（结构化错误）
  ├── HxJsonValidator.validate(Reader)       → ValidationResult（流式）
  └── HxJsonValidator.validate(CharSequence) → ValidationResult（兼容 Kotlin）
```

## 2. 核心类

```java
public final class HxJsonValidator {
    public static final String VERSION = "2026.10.14";
    public static boolean isValid(String json);
    public static ValidationResult validate(String json);
    public static ValidationResult validate(Reader reader);
    public static ValidationResult validate(CharSequence cs);
}

public final class ValidationResult {
    public final boolean valid;
    public final JsonErrorCode code;     // null if valid
    public final int line;               // 1-indexed
    public final int column;             // 1-indexed
    public final int offset;              // 0-indexed
    public final String snippet;          // ± 1 行上下文
    public final String message;          // 人类可读
    @Override public String toString();   // 大模型友好格式
}

public enum JsonErrorCode {
    ERR_EXPECT_VALUE,
    ERR_OBJECT_KEY_OR_CLOSE,
    // ... (见 fsm-状态转移图.md)
}
```

## 3. 内部实现类

```java
// 主状态机，私有
class JsonFsm {
    private State state = State.S0;
    private final Deque<ContainerFrame> stack = new ArrayDeque<>();
    private final int[] lineCol = {1, 0};  // line, col
    private int offset = 0;
    private final StringBuilder snippetBuf = new StringBuilder();
    // ... 内部方法 ...
    
    /** 主入口：处理一个字符，返回当前是否继续 */
    void feed(int c) throws JsonParseException;
    void endOfInput() throws JsonParseException;
    ValidationResult finish();
}

// 容器栈帧：记录 object/array 上下文
class ContainerFrame {
    final State returnState;  // S5 或 S7
    final int containerType;  // 0=object, 1=array
}
```

## 4. 实现路径

### 阶段 1：基础状态机
- 写 `JsonFsm` 主体，处理 6 类状态转移（S0-S7, S20-S23, S30-S33）
- 数字、字面值的子状态先简化为单状态
- **不含**：字符串子状态、转义、unicode

### 阶段 2：完整字符串支持
- 补 S8-S13 状态
- 5 个标准转义：`\" \\ \/ \b \f \n \r \t`
- Unicode 转义：基本多语言平面（U+0000 - U+FFFF）
- 代理对：高 + 低配对（U+D800 - U+DFFF）

### 阶段 3：API 表面
- `HxJsonValidator.isValid(String)` - 短路 boolean
- `HxJsonValidator.validate(String)` - 完整 ValidationResult
- `HxJsonValidator.validate(Reader)` - 流式

### 阶段 4：性能优化
- 字符分类查表（whitespace / digit / hex / alpha）
- `String.charAt(i)` → `char[]` 一次拷贝
- snippetBuf 滚动窗口

### 阶段 5：边界 case 测试
- 把 `fsm-边界case.md` 转成 70+ 单元测试
- 覆盖 RFC 8259 + 实际误判场景

## 5. 关键决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 输入接口 | `String` + `Reader` + `CharSequence` | 覆盖 99% 用例 |
| 错误对象 | 不可变 `ValidationResult` | 线程安全、易序列化 |
| 错误位置 | `{line, column, offset, snippet}` | 大模型可定位修改 |
| 内部状态 | 显式 enum + 栈 | 易调试、易扩展 |
| 转义策略 | 只支持 RFC 8259 标准 | 不支持 JS 风格 `\'` |
| 数字范围 | 不检查 `1e9999` | 语法层不检查值 |
| 性能 | 单字符扫描 | O(n) 足够 |
| 内存 | snippetBuf 滚动 256 字符 | 防 OOM |

## 6. 不做的（明确边界）

❌ JSON Schema 校验（待 #G6 决定）
❌ JSON Pointer / JSON Patch
❌ JSON5 / NDJSON / GeoJSON 兼容
❌ Unicode normalization（直接按 UTF-8 字节流）
❌ 跨调用上下文状态保留（每次 validate 是独立的）
❌ 自定义 hook / interceptor

## 7. 性能预算

| 输入大小 | 期望耗时 | 内存 |
|---|---|---|
| 1 KB | < 1 ms | < 1 MB |
| 100 KB | < 50 ms | < 5 MB |
| 1 MB | < 500 ms | < 20 MB |
| 10 MB | < 5 s | < 100 MB（拒绝更大） |

## 8. 风险与缓解

| 风险 | 缓解 |
|---|---|
| FSM 实现 bug 漏掉 RFC 8259 边界 | 70+ 单测 case 覆盖 |
| 性能不达标 | 阶段 4 优化 + 性能单测 |
| 大模型无法解析错误信息 | ValidationResult.toString() 设计成自然语言 |
| Android 兼容问题 | 阶段 5 才集成 Android，实测在 Android 5+ |
| JitPack 发布失败 | 复用姊妹项目发布流程 |

## 9. 里程碑

- **M1** (本任务完成)：3 份调研报告（状态图、边界 case、技术路径）
- **M2**：Java 实现 `JsonFsm` + `HxJsonValidator` + `ValidationResult`（预计 800 行）
- **M3**：70+ 单测全绿 + 覆盖率 > 90%
- **M4**：集成到 sisterfuture，对比 JSONTokener 误判率
- **M5**：发布 v1.0 到 JitPack

## 10. 关联

- **上游**：
  - #919489454955 sisterfuture 现有校验代码（已完成）
  - #919489450614 Android JSON 校验库选型对比（待定）
  - #919489450991 主人 7 个准备问题（待主人回答）
- **下游**：
  - 库代码本体开发
  - 集成到 sisterfuture 替换 JSONTokener
  - 发布到 JitPack

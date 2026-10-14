# HxJsonValidator

基于有限状态自动机（FSM）的 Android JSON 语法校验器。

> 从 SisterFuture (https://github.com/hxcan/sisterfuture) 主工程孵化的独立库。

## 特性（计划）

- 基于 FSM，按字符位置（line/column/offset）报告错误
- 结构化错误信息
- 脱离 Android Framework，可独立 JVM 单元测试
- 流式可中断

## 版本

当前快照：2026.10.14（脚手架阶段，无业务实现）

## 完整源码说明

由于首次 push 受网络限制，**当前 GitHub 仓库仅含 README**。
完整 14 个文件脚手架已打包为 git bundle：

- 本地路径：/home/hxcan/SoftwareDevelop/hxjsonvalidator.a/HxJsonValidator.bundle
- HEAD: e88daae
- 验证：git bundle verify HxJsonValidator.bundle
- 恢复：git clone HxJsonValidator.bundle hxjsonvalidator

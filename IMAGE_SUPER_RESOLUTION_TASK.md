# 图片超分辨率功能说明

## 功能目标

本次新增功能为图片超分辨率：用户选择或拍摄一张低分辨率图片，APP 显示原图与 2x 增强结果，并输出预处理、增强/推理、后处理和总耗时。

入口位置：

```text
体验 -> 高级区 -> 图片超分辨率
```

主要修改文件：

```text
custommodel/src/main/java/com/mindspore/custommodel/CustomModelMainActivity.java
custommodel/src/main/java/com/mindspore/custommodel/CustomModelExecutor.java
custommodel/src/main/res/layout/activity_custom_model_main.xml
custommodel/src/main/res/values/strings.xml
custommodel/src/main/res/values-zh/strings.xml
app/src/main/res/values/strings.xml
app/src/main/res/values-zh/strings.xml
```

## 当前实现

当前 `models-main` 中的 `super-resolution-10.onnx` 是 Git LFS 指针文件，不是真实 ONNX 模型本体。因此 `CustomModelExecutor` 中先提供了一个 CPU 2x 超分增强兜底实现，用于跑通手机端流程和录制演示视频。

页面会自动检测：

```text
custommodel/src/main/assets/super_resolution.ms
```

如果后续拿到真实 MindSpore Lite 模型，可以在 `CustomModelExecutor` 中把 CPU 兜底逻辑替换为真实 MindSpore Lite Session 推理。

## 推荐模型路线

可使用 ONNX Model Zoo 的 Super Resolution 模型：

```text
models-main/validated/vision/super_resolution/sub_pixel_cnn_2016/model/super-resolution-10.onnx
```

但需要用 Git LFS 下载真实模型文件。当前仓库内该文件只有约 131 字节，是 LFS 指针，不可直接转换。

## MindSpore Lite 转换示例

拿到真实 ONNX 后，可转换为 `.ms`：

```bat
converter_lite --fmk=ONNX --modelFile=super-resolution-10.onnx --outputFile=super_resolution
```

转换后得到：

```text
super_resolution.ms
```

放入：

```text
custommodel/src/main/assets/super_resolution.ms
```

## 量化和压缩说明

为了满足作业中的“压缩和量化”要求，可以准备两个版本：

```text
super_resolution_fp32.ms
super_resolution_int8.ms
```

演示时比较：

```text
模型大小
APK 大小
推理耗时
增强效果截图
```

## 演示视频建议

1. 打开 APP。
2. 进入“体验”页面。
3. 点击“图片超分辨率”。
4. 选择或拍摄一张较低分辨率图片。
5. 点击“开始2x增强”。
6. 展示原图、2x 增强图和耗时信息。
7. 简单展示代码位置和 `super_resolution.ms` 放置位置。

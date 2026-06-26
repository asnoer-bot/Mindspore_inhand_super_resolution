# Web Upload Notes

This package is prepared for GitHub web upload.

The original project contains two large static MindSpore Lite library files:

- imageObject/src/main/cpp/mindspore-lite-1.1.0-inference-android/lib/aarch32/libmindspore-lite.a
- imageObject/src/main/cpp/mindspore-lite-1.1.0-inference-android/lib/aarch64/libmindspore-lite.a

Each file is larger than GitHub web upload's 25 MB single-file limit, so they
are omitted from the web-upload package. They belong to the original sample
dependency and are not part of the added image super-resolution feature.

The added feature code is mainly in:

- custommodel/src/main/java/com/mindspore/custommodel/CustomModelMainActivity.java
- custommodel/src/main/java/com/mindspore/custommodel/CustomModelExecutor.java
- custommodel/src/main/res/layout/activity_custom_model_main.xml
- custommodel/src/main/res/values/strings.xml
- custommodel/src/main/res/values-zh/strings.xml
- IMAGE_SUPER_RESOLUTION_TASK.md

# MindSpore InHand Image Super Resolution Homework

This repository upload is a compact homework submission package.

The added feature is **Image Super Resolution** in the `custommodel` module.
It provides:

- image selection from gallery
- camera input
- 2x image enhancement
- side-by-side original/enhanced display
- result size and timing display
- enhanced image saving
- repeated-enhancement protection

Key modified files:

- `custommodel/src/main/java/com/mindspore/custommodel/CustomModelMainActivity.java`
- `custommodel/src/main/java/com/mindspore/custommodel/CustomModelExecutor.java`
- `custommodel/src/main/res/layout/activity_custom_model_main.xml`
- `custommodel/src/main/res/values/strings.xml`
- `custommodel/src/main/res/values-zh/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`

The full local Android project can build the debug APK with:

```bat
cd /d D:\Mindspore_inhand-main
set JAVA_HOME=D:\jdk-17.0.19+10
set PATH=%JAVA_HOME%\bin;%PATH%
gradlew.bat :app:assembleDebug
```

The generated APK is:

```text
D:\Mindspore_inhand-main\app\build\outputs\apk\debug\app-debug.apk
```

Note: This compact upload intentionally excludes build cache directories and
large original dependency binaries that are not part of the added feature.

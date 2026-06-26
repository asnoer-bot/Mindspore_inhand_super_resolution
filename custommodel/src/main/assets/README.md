Place the MindSpore Lite super-resolution model here:

```text
custommodel/src/main/assets/super_resolution.ms
```

The current app screen can run with the CPU 2x super-resolution fallback. After
adding `super_resolution.ms`, replace the fallback section in
`CustomModelExecutor` with the real MindSpore Lite session input/output logic.

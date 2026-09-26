# ReaderLB currently has no custom shrinking rules.
-keep class com.readerlb.app.storage.ShizukuFileService { *; }


# ReaderLB Bridge is launched by class name from app_process, so R8 cannot
# discover the entry point through normal Android component references.
-keep class com.readerlb.app.storage.bridge.ReaderLbBridgeMain {
    public static void main(java.lang.String[]);
}

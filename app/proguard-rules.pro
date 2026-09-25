# Room and Compose ship their own consumer rules, so nothing extra is needed yet.
# Keep the listener service name stable because Android binds to it by class name from the manifest.
-keep class com.buyless.app.service.PaymentListenerService

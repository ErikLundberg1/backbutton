# Accessibility Service Input
Since this app doesn't launch a "View" like a normal app, but rather runs in
the background as an accessibility service, you need to configure both your
IDE and your phone before you can run this.

## IDE
In IntelliJ, go to Run (in the top bar, not the green arrow) -> Edit
Configurations -> Launch Options, and change the _Launch_ drop down
menu to _Nothing_.

## Phone
After you have successfully built and executed the application, on your device
go to Settings -> Accessibility, and enable the service. (As of right now it is
named "com.example.app.inputtest.InputTestService".)

That's it! You should have a grey button on the top of your screen that can
perform swipes.

# Regarding generation of keypresses

## Instrumentation.sendKeyDownUpSync()
This requires the `android.permission.INJECT_EVENTS` permission, which is reserved for system applications. It is only possible if the device is rooted = no go.

I had an idea that you would be able to inject keypresses as follows:

```java
button.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View view) {
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KEYCODE_VOLUME_DOWN));
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KEYCODE_VOLUME_DOWN));
    }
});
```

Without knowing exactly why, this does not seem to work for binding keys to the switch access. I _think_ that it only can dispatch key events to the service itself, and they are not readable by other apps or the system.
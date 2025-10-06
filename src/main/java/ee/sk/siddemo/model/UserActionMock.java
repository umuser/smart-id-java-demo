package ee.sk.siddemo.model;

public enum UserActionMock {
    NONE,
    QR_CODE,
    WEB2APP,
    APP2APP;

    public boolean isSameDevice() {
        return this == WEB2APP || this == APP2APP;
    }
}
